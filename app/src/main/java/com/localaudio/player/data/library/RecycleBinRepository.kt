package com.localaudio.player.data.library

import android.net.Uri
import android.provider.DocumentsContract
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.RecycleBinState
import com.localaudio.player.data.model.RecycleFolder
import com.localaudio.player.data.model.RecycleItem
import com.localaudio.player.data.scan.ScannedDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns soft-delete records and the URI-based blacklist used by scans. */
class RecycleBinRepository(private val store: RecycleBinStore) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(store.readState())
    private var blockedUriKeys: Set<String> = emptySet()
    private var blockedDocumentKeys: Set<String> = emptySet()
    private var blockedDirectoryKeys: Set<String> = emptySet()

    val state: StateFlow<RecycleBinState> = _state.asStateFlow()

    init {
        rebuildIndexes(_state.value)
    }

    @Synchronized
    fun isUriBlocked(uri: String): Boolean {
        val identity = documentIdentity(uri)
        return identity.normalizedUri in blockedUriKeys ||
            identity.documentKey?.let { it in blockedDocumentKeys } == true
    }

    @Synchronized
    fun isDirectoryBlocked(rootFolderUri: String, documentId: String): Boolean {
        return directoryKey(rootFolderUri, documentId) in blockedDirectoryKeys
    }

    @Synchronized
    fun entriesFor(keys: Set<String>): Pair<List<RecycleItem>, List<RecycleFolder>> {
        val current = _state.value
        val folderKeys = keys.filter { it.startsWith(FOLDER_KEY_PREFIX) }
            .map { it.removePrefix(FOLDER_KEY_PREFIX) }.toSet()
        val folders = current.folders.filter { it.key in folderKeys }
        val itemKeys = keys.filter { it.startsWith(ITEM_KEY_PREFIX) }
            .map { it.removePrefix(ITEM_KEY_PREFIX) }.toMutableSet()
        val folderUriKeys = folders.mapTo(HashSet()) { normalizeUri(it.uri) }
        val folderDocumentKeys = folders.mapNotNullTo(HashSet()) {
            documentIdentity(it.uri).documentKey
        }
        val selectedItems = current.items.filter { item ->
            if (item.key in itemKeys) return@filter true
            val deletedByFolderUri = item.deletedByFolderUri ?: return@filter false
            val identity = documentIdentity(deletedByFolderUri)
            identity.normalizedUri in folderUriKeys || identity.documentKey in folderDocumentKeys
        }
        return selectedItems to folders
    }

    @Synchronized
    fun softDeleteItems(items: List<AudioItem>, deletedByFolderUri: String? = null) {
        val current = _state.value
        val nextItems = replaceSoftDeletedItems(
            existingItems = current.items,
            items = items,
            deletedByFolderUri = deletedByFolderUri,
        )
        updateState(current.copy(items = nextItems))
    }

    /** Updates a deleted directory and all of its audio children in one state/persistence operation. */
    @Synchronized
    fun softDeleteDirectory(items: List<AudioItem>, folder: RecycleFolder) {
        val current = _state.value
        val nextItems = replaceSoftDeletedItems(
            existingItems = current.items,
            items = items,
            deletedByFolderUri = folder.uri,
        )
        val nextFolders = current.folders.filterNot { it.key == folder.key } + folder
        updateState(current.copy(items = nextItems, folders = nextFolders))
    }

    /** Refreshes deleted directory paths discovered outside the app. */
    @Synchronized
    fun updateScannedDirectories(directories: List<ScannedDirectory>) {
        if (directories.isEmpty() || _state.value.folders.isEmpty()) return
        val current = _state.value
        val candidates = directories.filter { directory ->
            current.folders.any { folder -> sameRoot(folder.rootFolderUri, directory.rootFolderUri) }
        }
        if (candidates.isEmpty()) return

        val matchedCandidates = HashSet<Int>()
        val nextFolders = current.folders.toMutableList()
        var nextItems = current.items
        current.folders.forEachIndexed { index, folder ->
            val candidateIndex = candidates.indices.firstOrNull { candidateIndex ->
                candidateIndex !in matchedCandidates &&
                    sameRoot(folder.rootFolderUri, candidates[candidateIndex].rootFolderUri) &&
                    sameDocument(folder.uri, candidates[candidateIndex].uri.toString())
            } ?: return@forEachIndexed
            matchedCandidates += candidateIndex
            val directory = candidates[candidateIndex]
            val nextUri = directory.uri.toString()
            if (
                folder.uri == nextUri &&
                folder.relativePath == directory.relativePath &&
                folder.title == directory.title
            ) return@forEachIndexed

            nextFolders[index] = folder.copy(
                uri = nextUri,
                relativePath = directory.relativePath,
                title = directory.title,
            )
            nextItems = nextItems.map { item ->
                if (item.deletedByFolderUri != null &&
                    sameDocument(item.deletedByFolderUri, folder.uri) &&
                    item.folderUri == folder.rootFolderUri
                ) {
                    item.copy(
                        relativePath = replacePath(item.relativePath, folder.relativePath, directory.relativePath),
                        deletedByFolderUri = nextUri,
                    )
                } else {
                    item
                }
            }
        }
        if (nextFolders != current.folders || nextItems != current.items) {
            updateState(current.copy(items = nextItems, folders = nextFolders))
        }
    }

    /** Filters a completed scan and updates URI/Document ID records that remain in the recycle bin. */
    @Synchronized
    fun filterScannedItems(
        items: List<AudioItem>,
        rebindRenamedPaths: Boolean = false,
    ): List<AudioItem> {
        val current = _state.value
        val nextItems = current.items.toMutableList()
        val visible = ArrayList<AudioItem>(items.size)
        val matchedRecycleIndices = HashSet<Int>()
        val uriCandidates = HashMap<String, MutableList<Int>>()
        val documentCandidates = HashMap<String, MutableList<Int>>()
        if (!rebindRenamedPaths) {
            nextItems.forEachIndexed { index, recycleItem ->
                val identity = documentIdentity(recycleItem.uri)
                uriCandidates.getOrPut(identity.normalizedUri) { ArrayList() } += index
                identity.documentKey?.let { key ->
                    documentCandidates.getOrPut(key) { ArrayList() } += index
                }
            }
        }

        fun firstUnmatched(candidates: List<Int>?): Int =
            candidates?.firstOrNull { it !in matchedRecycleIndices } ?: -1

        items.distinctBy { it.key }.forEach { item ->
            val identity = documentIdentity(item.uri.toString())
            var existingIndex = if (rebindRenamedPaths) {
                nextItems.withIndex().firstOrNull { indexedItem ->
                    !matchedRecycleIndices.contains(indexedItem.index) &&
                        sameDocument(indexedItem.value.uri, item.uri.toString())
                }?.index ?: -1
            } else {
                firstUnmatched(uriCandidates[identity.normalizedUri]).takeIf { it >= 0 }
                    ?: firstUnmatched(identity.documentKey?.let { documentCandidates[it] })
            }
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

    @Synchronized
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

    @Synchronized
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

    @Synchronized
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

    @Synchronized
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
        rebuildIndexes(next)
        executor.execute { runCatching { store.writeState(next) } }
    }

    private fun rebuildIndexes(state: RecycleBinState) {
        val uriKeys = HashSet<String>(state.items.size)
        val documentKeys = HashSet<String>(state.items.size)
        state.items.forEach { item ->
            val identity = documentIdentity(item.uri)
            uriKeys += identity.normalizedUri
            identity.documentKey?.let(documentKeys::add)
        }
        val directoryKeys = HashSet<String>(state.folders.size)
        state.folders.forEach { folder ->
            documentIdFromUri(folder.uri)?.let { documentId ->
                directoryKeys += directoryKey(folder.rootFolderUri, documentId)
            }
        }
        blockedUriKeys = uriKeys
        blockedDocumentKeys = documentKeys
        blockedDirectoryKeys = directoryKeys
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

    private fun replaceSoftDeletedItems(
        existingItems: List<RecycleItem>,
        items: List<AudioItem>,
        deletedByFolderUri: String?,
    ): List<RecycleItem> {
        if (items.isEmpty()) return existingItems

        val uriCandidates = HashMap<String, MutableList<Int>>(existingItems.size)
        val documentCandidates = HashMap<String, MutableList<Int>>(existingItems.size)
        existingItems.forEachIndexed { index, recycleItem ->
            val identity = documentIdentity(recycleItem.uri)
            uriCandidates.getOrPut(identity.normalizedUri) { ArrayList() } += index
            identity.documentKey?.let { key ->
                documentCandidates.getOrPut(key) { ArrayList() } += index
            }
        }

        val removed = BooleanArray(existingItems.size)
        val replacements = ArrayList<RecycleItem>(items.size)
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val identity = documentIdentity(item.uri.toString())
            val candidates = LinkedHashSet<Int>()
            uriCandidates[identity.normalizedUri]?.let(candidates::addAll)
            identity.documentKey?.let { key ->
                documentCandidates[key]?.let(candidates::addAll)
            }
            val existingIndex = candidates
                .asSequence()
                .filterNot(removed::get)
                .minOrNull()
            val existing = existingIndex?.let(existingItems::get)
            candidates.forEach { removed[it] = true }
            replacements += item.toRecycleItem(
                deletedAtMs = existing?.deletedAtMs ?: now,
                existing = existing,
                deletedByFolderUri = deletedByFolderUri,
            )
        }

        return buildList(existingItems.size - removed.count { it } + replacements.size) {
            existingItems.forEachIndexed { index, item ->
                if (!removed[index]) add(item)
            }
            addAll(replacements)
        }
    }

    private fun normalizeUri(value: String): String = Uri.parse(value).normalizeScheme().toString()

    private fun sameRoot(left: String, right: String): Boolean =
        normalizeUri(left) == normalizeUri(right)

    private data class DocumentIdentity(
        val normalizedUri: String,
        val documentKey: String?,
    )

    private fun documentIdentity(value: String): DocumentIdentity {
        val uri = Uri.parse(value)
        val documentKey = runCatching {
            val documentId = DocumentsContract.getDocumentId(uri)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
            "${uri.authority}:$treeDocumentId:$documentId"
        }.getOrNull()
        return DocumentIdentity(
            normalizedUri = uri.normalizeScheme().toString(),
            documentKey = documentKey,
        )
    }

    private fun directoryKey(rootFolderUri: String, documentId: String): String =
        "${normalizeUri(rootFolderUri)}\u0000$documentId"

    private fun documentIdFromUri(value: String): String? = runCatching {
        DocumentsContract.getDocumentId(Uri.parse(value))
    }.getOrNull()

    private fun sameDocument(left: String?, right: String): Boolean {
        if (left == null) return false
        val leftIdentity = documentIdentity(left)
        val rightIdentity = documentIdentity(right)
        return leftIdentity.normalizedUri == rightIdentity.normalizedUri ||
            leftIdentity.documentKey != null && leftIdentity.documentKey == rightIdentity.documentKey
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
