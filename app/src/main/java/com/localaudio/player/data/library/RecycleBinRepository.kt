package com.localaudio.player.data.library

import android.net.Uri
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.RecycleBinState
import com.localaudio.player.data.model.RecycleFolder
import com.localaudio.player.data.model.RecycleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns soft-delete records and the URI/hash blacklist used by scans. */
class RecycleBinRepository(private val store: RecycleBinStore) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(store.readState())

    val state: StateFlow<RecycleBinState> = _state.asStateFlow()

    fun blockedUris(): Set<String> = _state.value.items.mapTo(HashSet()) { normalizeUri(it.uri) }

    fun isUriBlocked(uri: String): Boolean = blockedUris().contains(normalizeUri(uri))

    fun entriesFor(
        keys: Set<String>,
        includeUnrelatedItemsInFolders: Boolean = false,
    ): Pair<List<RecycleItem>, List<RecycleFolder>> {
        val current = _state.value
        val folderKeys = keys.filter { it.startsWith(FOLDER_KEY_PREFIX) }
            .map { it.removePrefix(FOLDER_KEY_PREFIX) }.toSet()
        val folders = current.folders.filter { it.key in folderKeys }
        val itemKeys = keys.filter { it.startsWith(ITEM_KEY_PREFIX) }
            .map { it.removePrefix(ITEM_KEY_PREFIX) }.toMutableSet()
        folders.forEach { folder ->
            val folderItems = current.items.filter {
                it.folderUri == folder.rootFolderUri &&
                    (includeUnrelatedItemsInFolders || it.deletedWithFolder) &&
                    isInPath(it.relativePath, folder.relativePath)
            }
            folderItems.forEach { itemKeys += it.key }
            val folderHashes = folderItems.mapNotNull { it.contentHash }.toSet()
            current.items.filter { it.contentHash in folderHashes }.forEach { itemKeys += it.key }
        }
        return current.items.filter { it.key in itemKeys } to folders
    }

    fun softDeleteItems(items: List<AudioItem>, deletedWithFolder: Boolean = false) {
        val now = System.currentTimeMillis()
        val nextItems = _state.value.items.toMutableList()
        items.forEach { item ->
            val existing = nextItems.firstOrNull { normalizeUri(it.uri) == normalizeUri(item.uri.toString()) }
            val replacement = item.toRecycleItem(
                deletedAtMs = existing?.deletedAtMs ?: now,
                existing = existing,
                deletedWithFolder = deletedWithFolder,
            )
            nextItems.removeAll { normalizeUri(it.uri) == normalizeUri(item.uri.toString()) }
            nextItems += replacement
        }
        updateState(_state.value.copy(items = nextItems))
    }

    fun softDeleteFolder(folder: RecycleFolder) {
        val folders = _state.value.folders.filterNot { it.key == folder.key } + folder
        updateState(_state.value.copy(folders = folders))
    }

    /** Filters a completed scan and records newly found paths matching a blacklist hash. */
    fun filterScannedItems(items: List<AudioItem>): List<AudioItem> {
        val current = _state.value
        val nextItems = current.items.toMutableList()
        val visible = ArrayList<AudioItem>(items.size)
        val scannedUris = items.mapTo(HashSet()) { normalizeUri(it.uri.toString()) }
        val blockedHashes = current.items.mapNotNull { it.contentHash }.toHashSet()
        items.distinctBy { it.key }.forEach { item ->
            val existingIndex = nextItems.indexOfFirst {
                normalizeUri(it.uri) == normalizeUri(item.uri.toString())
            }
            val blockedByFolder = current.folders.any { folder ->
                folder.rootFolderUri == item.folderUri &&
                    isInPath(item.relativePath, folder.relativePath)
            }
            val blockedByHash = item.contentHash?.takeIf { it.isNotBlank() } in blockedHashes
            if (existingIndex >= 0 || blockedByFolder || blockedByHash) {
                val hashIndex = item.contentHash?.let { hash ->
                    nextItems.indexOfFirst { it.contentHash == hash }
                } ?: -1
                val movedItemIndex = if (existingIndex < 0 && blockedByHash && hashIndex >= 0) {
                    val oldUri = normalizeUri(nextItems[hashIndex].uri)
                    hashIndex.takeIf { oldUri !in scannedUris } ?: -1
                } else {
                    -1
                }
                val existing = nextItems.getOrNull(existingIndex.takeIf { it >= 0 } ?: movedItemIndex)
                val replacement = item.toRecycleItem(existing?.deletedAtMs ?: System.currentTimeMillis(), existing)
                when {
                    existingIndex >= 0 -> nextItems[existingIndex] = replacement
                    movedItemIndex >= 0 -> nextItems[movedItemIndex] = replacement
                    else -> nextItems += replacement
                }
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

    fun updateFolderPath(rootFolderUri: String, oldPath: String, newPath: String, newTitle: String) {
        val current = _state.value
        val nextItems = current.items.map { item ->
            if (item.folderUri == rootFolderUri && isInPath(item.relativePath, oldPath)) {
                item.copy(relativePath = replacePath(item.relativePath, oldPath, newPath))
            } else item
        }
        val nextFolders = current.folders.map { folder ->
            if (folder.rootFolderUri == rootFolderUri && folder.relativePath == oldPath) {
                folder.copy(relativePath = newPath, title = newTitle)
            } else if (folder.rootFolderUri == rootFolderUri && isInPath(folder.relativePath, oldPath)) {
                folder.copy(relativePath = replacePath(folder.relativePath, oldPath, newPath))
            } else folder
        }
        updateState(current.copy(items = nextItems, folders = nextFolders))
    }

    fun updateRootFolderName(rootFolderUri: String, title: String) {
        val current = _state.value
        updateState(
            current.copy(
                items = current.items.map { if (it.folderUri == rootFolderUri) it.copy(folderName = title) else it },
                folders = current.folders.map {
                    if (it.rootFolderUri == rootFolderUri && it.relativePath.isEmpty()) {
                        it.copy(title = title)
                    } else it
                },
            ),
        )
    }

    fun remove(keys: Set<String>, includeUnrelatedItemsInFolders: Boolean = false) {
        val current = _state.value
        val (items, folders) = entriesFor(keys, includeUnrelatedItemsInFolders)
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
        deletedWithFolder: Boolean = false,
    ) = RecycleItem(
        uri = uri.toString(),
        contentHash = contentHash ?: existing?.contentHash,
        title = title,
        artist = artist,
        durationMs = durationMs,
        folderUri = folderUri,
        folderName = folderName,
        relativePath = relativePath,
        deletedAtMs = deletedAtMs,
        deletedWithFolder = deletedWithFolder || existing?.deletedWithFolder == true,
    )

    private fun normalizeUri(value: String): String = Uri.parse(value).normalizeScheme().toString()

    private companion object {
        const val ITEM_KEY_PREFIX = "item:"
        const val FOLDER_KEY_PREFIX = "folder:"

        fun isInPath(path: String, parent: String): Boolean =
            parent.isEmpty() || path == parent || path.startsWith("$parent/")

        fun replacePath(path: String, oldPath: String, newPath: String): String =
            if (path == oldPath) newPath else newPath + path.removePrefix(oldPath)
    }
}
