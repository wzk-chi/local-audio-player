package com.localaudio.player.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderItem
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.model.RecycleFolder
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
    private val recycleBinRepository: RecycleBinRepository,
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
            val cachedItems = recycleBinRepository.filterScannedItems(store.readItems())
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
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
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

    /** Removes a library root without touching its source files. */
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
        reconcileAutoSkip()
        persistLibrary()
    }

    fun rescan(uriString: String) {
        _state.value.folders.firstOrNull { it.uri == uriString }?.let { startScan(it) }
    }

    fun rescanAll() = _state.value.folders.forEach(::startScan)

    fun deleteAudio(
        item: AudioItem,
        deleteSource: Boolean,
        onResult: (Result<Set<String>>) -> Unit,
    ) {
        val current = _state.value.items.firstOrNull { it.key == item.key } ?: run {
            post { onResult(Result.failure(IllegalStateException("音频已不存在"))) }
            return
        }
        if (!deleteSource) {
            val targets = if (current.contentHash.isNullOrBlank()) {
                listOf(current)
            } else {
                _state.value.items.filter { it.contentHash == current.contentHash }
            }
            recycleBinRepository.softDeleteItems(targets)
            removeVisibleItems(targets)
            persistLibrary()
            post { onResult(Result.success(targets.mapTo(HashSet()) { it.key })) }
            return
        }

        executor.execute {
            val deleted = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, current.uri)
            }.getOrDefault(false)
            post {
                if (!deleted) {
                    onResult(Result.failure(IllegalStateException("无法删除源文件")))
                } else {
                    removeVisibleItems(listOf(current))
                    persistLibrary()
                    onResult(Result.success(setOf(current.key)))
                }
            }
        }
    }

    fun deleteDirectory(
        location: FolderLocation,
        deleteSource: Boolean,
        onResult: (Result<Set<String>>) -> Unit,
    ) {
        val currentItems = itemsIn(location)
        executor.execute {
            val directoryUri = resolveDirectoryUri(location)
            if (directoryUri == null) {
                post { onResult(Result.failure(IllegalStateException("无法定位文件夹"))) }
                return@execute
            }
            if (deleteSource) {
                val deleted = runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, directoryUri)
                }.getOrDefault(false)
                post {
                    if (!deleted) {
                        onResult(Result.failure(IllegalStateException("无法删除源文件夹")))
                    } else {
                        if (location.relativePath.isEmpty()) {
                            directorySkipRepository.removeForFolder(location.folderUri)
                        } else {
                            directorySkipRepository.removePath(
                                location.folderUri,
                                location.relativePath,
                            )
                        }
                        removeLibraryDirectory(location, currentItems)
                        persistLibrary()
                        onResult(Result.success(currentItems.mapTo(HashSet()) { it.key }))
                    }
                }
            } else {
                val targets = currentItems.flatMap { item ->
                    if (item.contentHash.isNullOrBlank()) {
                        listOf(item)
                    } else {
                        _state.value.items.filter { it.contentHash == item.contentHash }
                    }
                }.distinctBy { it.key }
                val recycleFolder = RecycleFolder(
                    uri = directoryUri.toString(),
                    rootFolderUri = location.folderUri,
                    relativePath = location.relativePath,
                    title = location.name,
                    deletedAtMs = System.currentTimeMillis(),
                )
                post {
                    recycleBinRepository.softDeleteItems(targets)
                    recycleBinRepository.softDeleteFolder(recycleFolder)
                    removeLibraryDirectory(location, targets)
                    persistLibrary()
                    onResult(Result.success(targets.mapTo(HashSet()) { it.key }))
                }
            }
        }
    }

    fun renameAudio(
        item: AudioItem,
        requestedName: String,
        onResult: (Result<AudioItem>) -> Unit,
    ) {
        val name = requestedName.trim()
        if (name.isBlank() || name.contains('/') || name.contains('\\')) {
            post { onResult(Result.failure(IllegalArgumentException("名称不能为空或包含路径分隔符"))) }
            return
        }
        executor.execute {
            val actualName = queryDisplayName(item.uri) ?: item.title
            val extension = actualName.substringAfterLast('.', "")
                .takeIf { actualName.lastIndexOf('.') > 0 }
            val displayName = if (extension.isNullOrBlank() || name.endsWith(".$extension", ignoreCase = true)) {
                name
            } else {
                "$name.$extension"
            }
            val renamedUri = runCatching {
                DocumentsContract.renameDocument(context.contentResolver, item.uri, displayName)
            }.getOrNull()
            post {
                if (renamedUri == null) {
                    onResult(Result.failure(IllegalStateException("重命名失败")))
                } else {
                    val updated = item.copy(
                        uri = renamedUri,
                        title = displayName.substringBeforeLast('.', displayName),
                    )
                    _state.update { state ->
                        state.copy(items = state.items.map { current ->
                            if (current.key == item.key) updated else current
                        })
                    }
                    autoSkipRepository.updateAudioSnapshot(item.uri.toString(), updated)
                    persistLibrary()
                    onResult(Result.success(updated))
                }
            }
        }
    }

    fun renameDirectory(
        location: FolderLocation,
        requestedName: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val name = requestedName.trim()
        if (name.isBlank() || name.contains('/') || name.contains('\\')) {
            post { onResult(Result.failure(IllegalArgumentException("名称不能为空或包含路径分隔符"))) }
            return
        }
        executor.execute {
            val directoryUri = resolveDirectoryUri(location)
            val renamed = directoryUri?.let {
                runCatching { DocumentsContract.renameDocument(context.contentResolver, it, name) }.getOrNull()
            }
            post {
                if (renamed == null) {
                    onResult(Result.failure(IllegalStateException("重命名文件夹失败")))
                } else {
                    if (location.relativePath.isEmpty()) {
                        _state.update { state ->
                            state.copy(
                                folders = state.folders.map { folder ->
                                    if (folder.uri == location.folderUri) folder.copy(displayName = name) else folder
                                },
                                items = state.items.map { item ->
                                    if (item.folderUri == location.folderUri) item.copy(folderName = name) else item
                                },
                            )
                        }
                        recycleBinRepository.updateRootFolderName(location.folderUri, name)
                        autoSkipRepository.updateRootFolderSnapshot(location.folderUri, name)
                    } else {
                        val parentPath = location.relativePath.substringBeforeLast('/', "")
                        val newPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                        val oldPath = location.relativePath
                        _state.update { state ->
                            state.copy(
                                items = state.items.map { item ->
                                    if (item.folderUri == location.folderUri && isInPath(item.relativePath, oldPath)) {
                                        item.copy(relativePath = replacePath(item.relativePath, oldPath, newPath))
                                    } else item
                                },
                            )
                        }
                        recycleBinRepository.updateFolderPath(
                            rootFolderUri = location.folderUri,
                            oldPath = oldPath,
                            newPath = newPath,
                            newTitle = name,
                        )
                        autoSkipRepository.updatePathSnapshot(location.folderUri, oldPath, newPath)
                        directorySkipRepository.updatePath(location.folderUri, oldPath, newPath)
                    }
                    persistLibrary()
                    _state.value.folders.firstOrNull { it.uri == location.folderUri }?.let(::startScan)
                    onResult(Result.success(Unit))
                }
            }
        }
    }

    fun restoreRecycle(keys: Set<String>, onResult: (Result<Unit>) -> Unit) {
        val before = recycleBinRepository.state.value
        val (items, folders) = recycleBinRepository.entriesFor(keys)
        recycleBinRepository.restore(keys)
        val rootsToScan = (items.map { it.folderUri } + folders.map { it.rootFolderUri }).toSet()
        val currentFolders = _state.value.folders.toMutableList()
        folders.filter { it.relativePath.isEmpty() }.forEach { folder ->
            if (currentFolders.none { it.uri == folder.rootFolderUri }) {
                currentFolders += FolderItem(folder.rootFolderUri, folder.title)
            }
        }
        items.forEach { item ->
            if (currentFolders.none { it.uri == item.folderUri }) {
                currentFolders += FolderItem(item.folderUri, item.folderName)
            }
        }
        if (currentFolders != _state.value.folders) {
            _state.update { it.copy(folders = currentFolders) }
        }
        persistLibrary()
        rootsToScan.forEach { root ->
            _state.value.folders.firstOrNull { it.uri == root }?.let(::startScan)
        }
        if (before == recycleBinRepository.state.value) {
            post { onResult(Result.failure(IllegalArgumentException("没有可还原的项目"))) }
        } else {
            post { onResult(Result.success(Unit)) }
        }
    }

    fun cleanRecycle(keys: Set<String>, onResult: (Result<Unit>) -> Unit) {
        val (items, folders) = recycleBinRepository.entriesFor(keys)
        if (items.isEmpty() && folders.isEmpty()) {
            post { onResult(Result.failure(IllegalArgumentException("没有可清理的项目"))) }
            return
        }
        executor.execute {
            var success = true
            folders.forEach { folder ->
                val deleted = runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(folder.uri))
                }.getOrDefault(false)
                if (!deleted) success = false
            }
            if (success) {
                items.filterNot { item ->
                    folders.any { folder ->
                        item.folderUri == folder.rootFolderUri && isInPath(item.relativePath, folder.relativePath)
                    }
                }.forEach { item ->
                    val deleted = runCatching {
                        DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.uri))
                    }.getOrDefault(false)
                    if (!deleted) success = false
                }
            }
            post {
                if (success) {
                    recycleBinRepository.remove(keys)
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(IllegalStateException("部分源文件无法清理")))
                }
            }
        }
    }

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
                            val fresh = batch.filter { !recycleBinRepository.isUriBlocked(it.uri.toString()) }
                                .filter { publishedKeys.add(it.key) }
                            if (fresh.isNotEmpty()) _state.update { it.copy(items = it.items + fresh) }
                        }
                    },
                    isUriBlocked = recycleBinRepository::isUriBlocked,
                )
                postCurrent(folder.uri, scanToken) {
                    autoSkipRepository.updateSnapshots(result)
                    val visibleResult = recycleBinRepository.filterScannedItems(result)
                    val items = (_state.value.items.filterNot { it.folderUri == folder.uri } + visibleResult)
                        .distinctBy { it.key }
                    reconcileAutoSkip(items)
                    _state.update {
                        it.copy(
                            items = items,
                            scanStates = it.scanStates + (folder.uri to ScanState.Done(visibleResult.size)),
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

    private fun itemsIn(location: FolderLocation): List<AudioItem> = _state.value.items.filter {
        it.folderUri == location.folderUri && isInPath(it.relativePath, location.relativePath)
    }

    private fun removeVisibleItems(items: List<AudioItem>) {
        if (items.isEmpty()) return
        val keys = items.mapTo(HashSet()) { it.key }
        _state.update { state -> state.copy(items = state.items.filterNot { it.key in keys }) }
        reconcileAutoSkip()
    }

    private fun removeLibraryDirectory(location: FolderLocation, items: List<AudioItem>) {
        val keys = items.mapTo(HashSet()) { it.key }
        if (location.relativePath.isEmpty()) {
            jobs.remove(location.folderUri)?.cancel(true)
            scanTokens.remove(location.folderUri)
        }
        _state.update { state ->
            state.copy(
                folders = if (location.relativePath.isEmpty()) {
                    state.folders.filterNot { it.uri == location.folderUri }
                } else state.folders,
                items = state.items.filterNot { it.key in keys },
                scanStates = if (location.relativePath.isEmpty()) {
                    state.scanStates - location.folderUri
                } else state.scanStates,
            )
        }
        reconcileAutoSkip()
    }

    private fun reconcileAutoSkip(items: List<AudioItem> = _state.value.items) {
        autoSkipRepository.reconcile(
            validContentHashes = items.mapNotNull { it.contentHash }.toSet(),
            unresolvedAudioUris = items.filter { it.contentHash == null }
                .mapTo(HashSet()) { it.uri.toString() },
        )
    }

    private fun persistLibrary() {
        persist {
            store.writeFolders(_state.value.folders)
            store.writeItems(_state.value.items)
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        val documentUri = if (DocumentsContract.isTreeUri(uri)) {
            val id = DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.buildDocumentUriUsingTree(uri, id)
        } else uri
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun resolveDirectoryUri(location: FolderLocation): Uri? = runCatching {
        val root = Uri.parse(location.folderUri)
        var documentId = DocumentsContract.getTreeDocumentId(root)
        if (location.relativePath.isNotEmpty()) {
            location.relativePath.split('/').forEach { segment ->
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, documentId)
                documentId = context.contentResolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    var found: String? = null
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameColumn) == segment &&
                            cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR
                        ) {
                            found = cursor.getString(idColumn)
                            break
                        }
                    }
                    found
                } ?: return@runCatching null
            }
        }
        DocumentsContract.buildDocumentUriUsingTree(root, documentId)
    }.getOrNull()

    private fun clearFolderResources(uriString: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        jobs.remove(uriString)?.cancel(true)
        scanTokens.remove(uriString)
    }

    private fun post(block: () -> Unit) = mainHandler.post(block)

    private fun persist(block: () -> Unit) {
        executor.execute(block)
    }

    private fun postCurrent(folderUri: String, scanToken: Long, block: () -> Unit) {
        mainHandler.post {
            if (scanTokens[folderUri] == scanToken) block()
        }
    }

    private companion object {
        fun isInPath(path: String, parent: String): Boolean =
            path == parent || (parent.isNotEmpty() && path.startsWith("$parent/"))

        fun replacePath(path: String, oldPath: String, newPath: String): String =
            if (path == oldPath) newPath else newPath + path.removePrefix(oldPath)
    }
}
