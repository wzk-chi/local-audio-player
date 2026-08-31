package com.localaudio.player.data.library

import android.net.Uri
import android.provider.DocumentsContract
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.RecycleBinState
import com.localaudio.player.data.model.RecycleFolder
import com.localaudio.player.data.model.RecycleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns soft-delete records and the URI-based blacklist used by scans. */
class RecycleBinRepository(private val store: RecycleBinStore) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(store.readState())

    val state: StateFlow<RecycleBinState> = _state.asStateFlow()

    fun isUriBlocked(uri: String): Boolean = _state.value.items.any { item ->
        sameDocument(item.uri, uri)
    }

    fun isDirectoryBlocked(rootFolderUri: String, documentId: String): Boolean {
        val normalizedRootUri = normalizeUri(rootFolderUri)
        return _state.value.folders.any { folder ->
            normalizeUri(folder.rootFolderUri) == normalizedRootUri &&
                documentIdFromUri(folder.uri) == documentId
        }
    }

    fun entriesFor(keys: Set<String>): Pair<List<RecycleItem>, List<RecycleFolder>> {
        val current = _state.value
        val folderKeys = keys.filter { it.startsWith(FOLDER_KEY_PREFIX) }
            .map { it.removePrefix(FOLDER_KEY_PREFIX) }.toSet()
        val folders = current.folders.filter { it.key in folderKeys }
        val itemKeys = keys.filter { it.startsWith(ITEM_KEY_PREFIX) }
            .map { it.removePrefix(ITEM_KEY_PREFIX) }.toMutableSet()
        folders.forEach { folder ->
            val folderItems = current.items.filter { item ->
                sameDocument(item.deletedByFolderUri, folder.uri)
            }
            folderItems.forEach { itemKeys += it.key }
        }
        return current.items.filter { it.key in itemKeys } to folders
    }

    fun softDeleteItems(items: List<AudioItem>, deletedByFolderUri: String? = null) {
        val now = System.currentTimeMillis()
        val nextItems = _state.value.items.toMutableList()
        items.forEach { item ->
            val existing = nextItems.firstOrNull { sameDocument(it.uri, item.uri.toString()) }
            val replacement = item.toRecycleItem(
                deletedAtMs = existing?.deletedAtMs ?: now,
                existing = existing,
                deletedByFolderUri = deletedByFolderUri,
            )
            nextItems.removeAll { sameDocument(it.uri, item.uri.toString()) }
            nextItems += replacement
        }
        updateState(_state.value.copy(items = nextItems))
    }

    fun softDeleteFolder(folder: RecycleFolder) {
        val folders = _state.value.folders.filterNot { it.key == folder.key } + folder
        updateState(_state.value.copy(folders = folders))
    }

    /** Filters a completed scan and updates URI/Document ID records that remain in the recycle bin. */
    fun filterScannedItems(
        items: List<AudioItem>,
        rebindRenamedPaths: Boolean = false,
    ): List<AudioItem> {
        val current = _state.value
        val nextItems = current.items.toMutableList()
        val visible = ArrayList<AudioItem>(items.size)
        val matchedRecycleIndices = HashSet<Int>()
        items.distinctBy { it.key }.forEach { item ->
            var existingIndex = nextItems.withIndex().firstOrNull { indexedItem ->
                !matchedRecycleIndices.contains(indexedItem.index) &&
                    sameDocument(indexedItem.value.uri, item.uri.toString())
            }?.index ?: -1
            if (existingIndex < 0 && rebindRenamedPaths) {
                val candidateIndices = nextItems.mapIndexedNotNull { index, recycleItem ->
                    if (
                        !matchedRecycleIndices.contains(index) &&
                        sameDocument(recycleItem.folderUri, item.folderUri) &&
                        recycleItem.relativePath == item.relativePath &&
                        recycleItem.title == item.title &&
                        sameDocumentName(recycleItem.uri, item.uri.toString())
                    ) {
                        index
                    } else {
                        null
                    }
                }
                existingIndex = candidateIndices.singleOrNull() ?: -1
            }
            if (existingIndex >= 0) {
                matchedRecycleIndices += existingIndex
                val existing = nextItems[existingIndex]
                nextItems[existingIndex] = item.toRecycleItem(
                    deletedAtMs = existing.deletedAtMs,
                    existing = existing,
                    deletedByFolderUri = existing.deletedByFolderUri,
                )
            } else {
                visible += item
            }
        }
        if (nextItems != current.items) updateState(current.copy(items = nextItems))
        return visible
    }

    fun restore(keys: Set<String>): RecycleBinState {
        val current = _state.value
        val expanded = entriesFor(keys)
        val itemKeys = expanded.first.mapTo(HashSet()) { it.key }
        val folderKeys = expanded.second.mapTo(HashSet()) { it.key }
        val next = current.copy(
            items = current.items.filterNot { it.key in itemKeys },
            folders = current.folders.filterNot { it.key in folderKeys },
        )
        updateState(next)
        return next
    }

    fun updateFolderPath(
        rootFolderUri: String,
        oldPath: String,
        newPath: String,
        newTitle: String,
        oldFolderUri: String,
        newFolderUri: String,
    ) {
        val current = _state.value
        val nextItems = current.items.map { item ->
            val nextPath = if (item.folderUri == rootFolderUri && isInPath(item.relativePath, oldPath)) {
                replacePath(item.relativePath, oldPath, newPath)
            } else {
                item.relativePath
            }
            val nextDeletedByFolderUri = item.deletedByFolderUri?.let { deletedByFolderUri ->
                if (sameDocument(deletedByFolderUri, oldFolderUri)) newFolderUri else deletedByFolderUri
            }
            item.copy(relativePath = nextPath, deletedByFolderUri = nextDeletedByFolderUri)
        }
        val nextFolders = current.folders.map { folder ->
            val nextUri = if (sameDocument(folder.uri, oldFolderUri)) newFolderUri else folder.uri
            if (folder.rootFolderUri == rootFolderUri && folder.relativePath == oldPath) {
                folder.copy(uri = nextUri, relativePath = newPath, title = newTitle)
            } else if (folder.rootFolderUri == rootFolderUri && isInPath(folder.relativePath, oldPath)) {
                folder.copy(uri = nextUri, relativePath = replacePath(folder.relativePath, oldPath, newPath))
            } else if (nextUri != folder.uri) {
                folder.copy(uri = nextUri)
            } else {
                folder
            }
        }
        updateState(current.copy(items = nextItems, folders = nextFolders))
    }

    fun updateRootFolderName(rootFolderUri: String, title: String, newFolderUri: String? = null) {
        val current = _state.value
        val currentRootFolderUri = current.folders.firstOrNull {
            it.rootFolderUri == rootFolderUri && it.relativePath.isEmpty()
        }?.uri
        updateState(
            current.copy(
                items = current.items.map { item ->
                    val deletedByFolderUri = if (
                        newFolderUri != null &&
                        item.deletedByFolderUri != null &&
                        sameDocument(item.deletedByFolderUri, currentRootFolderUri ?: "")
                    ) {
                        newFolderUri
                    } else {
                        item.deletedByFolderUri
                    }
                    if (item.folderUri == rootFolderUri) {
                        item.copy(folderName = title, deletedByFolderUri = deletedByFolderUri)
                    } else {
                        item.copy(deletedByFolderUri = deletedByFolderUri)
                    }
                },
                folders = current.folders.map {
                    if (it.rootFolderUri == rootFolderUri && it.relativePath.isEmpty()) {
                        it.copy(title = title, uri = newFolderUri ?: it.uri)
                    } else it
                },
            ),
        )
    }

    fun remove(keys: Set<String>) {
        val current = _state.value
        val (items, folders) = entriesFor(keys)
        val itemKeys = items.mapTo(HashSet()) { it.key }
        val folderKeys = folders.mapTo(HashSet()) { it.key }
        updateState(
            current.copy(
                items = current.items.filterNot { it.key in itemKeys },
                folders = current.folders.filterNot { it.key in folderKeys },
            ),
        )
    }

    private fun updateState(next: RecycleBinState) {
        if (next == _state.value) return
        _state.value = next
        executor.execute { runCatching { store.writeState(next) } }
    }

    private fun AudioItem.toRecycleItem(
        deletedAtMs: Long,
        existing: RecycleItem?,
        deletedByFolderUri: String? = null,
    ) = RecycleItem(
        uri = uri.toString(),
        title = title,
        artist = artist,
        durationMs = durationMs,
        folderUri = folderUri,
        folderName = folderName,
        relativePath = relativePath,
        deletedAtMs = deletedAtMs,
        deletedByFolderUri = deletedByFolderUri ?: existing?.deletedByFolderUri,
    )

    private fun normalizeUri(value: String): String = Uri.parse(value).normalizeScheme().toString()

    private fun documentIdFromUri(value: String): String? = runCatching {
        DocumentsContract.getDocumentId(Uri.parse(value))
    }.getOrNull()

    private fun documentKey(value: String): String? = runCatching {
        val uri = Uri.parse(value)
        val documentId = documentIdFromUri(value) ?: return@runCatching null
        "${uri.authority}:${DocumentsContract.getTreeDocumentId(uri)}:$documentId"
    }.getOrNull()

    private fun sameDocument(left: String?, right: String): Boolean {
        if (left == null) return false
        if (normalizeUri(left) == normalizeUri(right)) return true
        val leftKey = documentKey(left) ?: return false
        return leftKey == documentKey(right)
    }

    private fun sameDocumentName(left: String, right: String): Boolean =
        documentName(left)?.let { it == documentName(right) } == true

    private fun documentName(value: String): String? =
        documentIdFromUri(value)?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    private companion object {
        const val ITEM_KEY_PREFIX = "item:"
        const val FOLDER_KEY_PREFIX = "folder:"

        fun isInPath(path: String, parent: String): Boolean =
            parent.isEmpty() || path == parent || path.startsWith("$parent/")

        fun replacePath(path: String, oldPath: String, newPath: String): String =
            if (path == oldPath) newPath else newPath + path.removePrefix(oldPath)
    }
}
