package com.localaudio.player.app

import com.localaudio.player.app.util.compareNatural
import com.localaudio.player.data.library.LibraryState
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation

class HomeRowsBuilder {
    fun rows(state: LibraryState, location: FolderLocation?): List<HomeRow> = if (location == null) {
        state.folders
            .map { HomeRow.Directory(FolderLocation(it.uri, it.displayName, "", it.displayName)) }
            .sortedWith { left, right -> compareNatural(left.location.name, right.location.name) }
    } else {
        buildList {
            addAll(immediateDirectories(state.items, location).map(HomeRow::Directory))
            addAll(audioIn(state.items, location.folderUri, location.relativePath).map(HomeRow::Audio))
        }
    }

    fun queueFor(items: List<AudioItem>, selected: AudioItem): Pair<List<AudioItem>, Int>? {
        val queue = audioIn(items, selected.folderUri, selected.relativePath)
        val index = queue.indexOfFirst { it.key == selected.key }
        return queue.takeIf { index >= 0 }?.let { it to index }
    }

    private fun audioIn(items: List<AudioItem>, folderUri: String, relativePath: String): List<AudioItem> =
        items.asSequence()
            .filter { it.folderUri == folderUri && it.relativePath == relativePath }
            .sortedWith { left, right -> compareNatural(left.title, right.title) }
            .toList()

    private fun immediateDirectories(items: List<AudioItem>, current: FolderLocation): List<FolderLocation> {
        val prefix = if (current.relativePath.isEmpty()) "" else "${current.relativePath}/"
        return items.asSequence()
            .filter { it.folderUri == current.folderUri }
            .mapNotNull { item ->
                if (item.relativePath == current.relativePath || !item.relativePath.startsWith(prefix)) {
                    return@mapNotNull null
                }
                val child = item.relativePath.removePrefix(prefix).substringBefore('/')
                if (child.isBlank()) {
                    null
                } else {
                    FolderLocation(current.folderUri, current.rootName, prefix + child, child)
                }
            }
            .distinctBy { it.relativePath }
            .sortedWith { left, right -> compareNatural(left.name, right.name) }
            .toList()
    }
}
