package com.localaudio.player.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderItem
import com.localaudio.player.data.model.ScanState
import com.localaudio.player.data.scan.MediaScanner
import com.localaudio.player.data.skip.AutoSkipRepository
import com.localaudio.player.data.skip.DirectorySkipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class LibraryRepository(
    private val context: Context,
    private val scanner: MediaScanner,
    private val store: LibraryStore,
    private val autoSkipRepository: AutoSkipRepository,
    private val directorySkipRepository: DirectorySkipRepository,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jobs = HashMap<String, Future<*>>()
    private val scanTokens = HashMap<String, Long>()
    private var nextScanToken = 0L
    private val _state = MutableStateFlow(LibraryState())

    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        executor.execute {
            val savedFolders = store.readFolders()
            val cachedItems = store.readItems()
            post {
                val current = _state.value
                val currentFolderUris = current.folders.mapTo(HashSet()) { it.uri }
                val loadedFolders = savedFolders.filterNot { it.uri in currentFolderUris }
                val folders = (current.folders + loadedFolders).distinctBy { it.uri }
                val folderUris = folders.mapTo(HashSet()) { it.uri }
                _state.value = current.copy(
                    folders = folders,
                    items = (current.items + cachedItems)
                        .filter { it.folderUri in folderUris }
                        .distinctBy { it.key },
                )
                loadedFolders.forEach { startScan(it) }
            }
        }
    }

    fun addFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { return }
        if (_state.value.folders.any { it.uri == uri.toString() }) return
        executor.execute {
            val folder = FolderItem(uri.toString(), queryDisplayName(uri) ?: fallbackFolderName(uri))
            post {
                if (_state.value.folders.any { it.uri == folder.uri }) return@post
                _state.update { it.copy(folders = it.folders + folder) }
                persist { store.writeFolders(_state.value.folders) }
                startScan(folder)
            }
        }
    }

    fun removeFolder(uriString: String) {
        clearFolderResources(uriString)
        directorySkipRepository.removeForFolder(uriString)
        _state.update {
            it.copy(
                folders = it.folders.filterNot { folder -> folder.uri == uriString },
                items = it.items.filterNot { item -> item.folderUri == uriString },
                scanStates = it.scanStates - uriString,
            )
        }
        val folders = _state.value.folders
        val items = _state.value.items
        autoSkipRepository.reconcile(
            validContentHashes = items.mapNotNull { it.contentHash }.toSet(),
            unresolvedAudioUris = items.filter { it.contentHash == null }
                .mapTo(HashSet()) { it.uri.toString() },
        )
        persist {
            store.writeFolders(folders)
            store.writeItems(items)
        }
    }

    fun rescan(uriString: String) {
        _state.value.folders.firstOrNull { it.uri == uriString }?.let { startScan(it) }
    }

    fun rescanAll() = _state.value.folders.forEach(::startScan)

    private fun startScan(folder: FolderItem) {
        jobs[folder.uri]?.cancel(true)
        val scanToken = ++nextScanToken
        scanTokens[folder.uri] = scanToken
        val publishedKeys = HashSet<String>(_state.value.items.size + 32).apply {
            _state.value.items.forEach { add(it.key) }
        }
        _state.update { it.copy(scanStates = it.scanStates + (folder.uri to ScanState.Scanning(0, 0))) }
        jobs[folder.uri] = executor.submit {
            try {
                val result = scanner.scanFolder(
                    Uri.parse(folder.uri),
                    folder.displayName,
                    onProgress = { scanned, found ->
                        postCurrent(folder.uri, scanToken) {
                            _state.update {
                                it.copy(scanStates = it.scanStates + (folder.uri to ScanState.Scanning(scanned, found)))
                            }
                        }
                    },
                    onItems = { batch ->
                        postCurrent(folder.uri, scanToken) {
                            val fresh = batch.filter { publishedKeys.add(it.key) }
                            if (fresh.isNotEmpty()) _state.update { it.copy(items = it.items + fresh) }
                        }
                    },
                )
                postCurrent(folder.uri, scanToken) {
                    val items = (_state.value.items.filterNot { it.folderUri == folder.uri } + result)
                        .distinctBy { it.key }
                    autoSkipRepository.reconcile(
                        validContentHashes = items.mapNotNull { it.contentHash }.toSet(),
                        unresolvedAudioUris = items.filter { it.contentHash == null }
                            .mapTo(HashSet()) { it.uri.toString() },
                    )
                    _state.update {
                        it.copy(
                            items = items,
                            scanStates = it.scanStates + (folder.uri to ScanState.Done(result.size)),
                        )
                    }
                    jobs.remove(folder.uri)
                    persist { store.writeItems(items) }
                }
            } catch (error: Exception) {
                postCurrent(folder.uri, scanToken) {
                    jobs.remove(folder.uri)
                    _state.update {
                        it.copy(
                            scanStates = it.scanStates +
                                (folder.uri to ScanState.Failed(error.message ?: "扫描失败")),
                        )
                    }
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(uri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun post(block: () -> Unit) = mainHandler.post(block)

    private fun persist(block: () -> Unit) {
        executor.execute(block)
    }

    private fun postCurrent(folderUri: String, scanToken: Long, block: () -> Unit) {
        mainHandler.post {
            if (scanTokens[folderUri] == scanToken) block()
        }
    }

    private fun clearFolderResources(uriString: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        jobs.remove(uriString)?.cancel(true)
        scanTokens.remove(uriString)
    }
}
