package com.localaudio.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.data.model.RecycleBinState
import com.localaudio.player.data.model.RecycleFolder
import com.localaudio.player.data.model.RecycleItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    state: RecycleBinState,
    onBack: () -> Unit,
    onRestore: (Set<String>) -> Unit,
    onClean: (Set<String>) -> Unit,
) {
    val entries = remember(state) {
        buildRecycleEntries(state)
    }
    var selected by remember(entries) { mutableStateOf(emptySet<String>()) }
    var folderPath by remember { mutableStateOf(emptyList<String>()) }
    var showCleanConfirmation by remember { mutableStateOf(false) }
    val openedFolder = remember(entries, folderPath) {
        findFolder(entries, folderPath)
    }
    val selectableKeys = remember(entries) {
        entries.flatMapTo(HashSet(), ::selectionKeys)
    }
    val allSelected = selectableKeys.isNotEmpty() && selected.containsAll(selectableKeys)

    BackHandler(enabled = folderPath.isNotEmpty()) {
        folderPath = folderPath.dropLast(1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(openedFolder?.title ?: stringResource(R.string.recycle_bin_title)) },
            navigationIcon = {
                IconButton(onClick = {
                    if (folderPath.isNotEmpty()) {
                        folderPath = folderPath.dropLast(1)
                    } else {
                        onBack()
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(
                            if (openedFolder != null) {
                                R.string.recycle_bin_folder_back
                            } else {
                                R.string.recycle_bin_back
                            },
                        ),
                    )
                }
            },
            actions = {
                if (entries.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selected = if (allSelected) emptySet() else selectableKeys
                        },
                    ) {
                        Text(stringResource(if (allSelected) R.string.recycle_bin_deselect_all else R.string.recycle_bin_select_all))
                    }
                }
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            if (openedFolder != null) {
                RecycleFolderContents(
                    entry = openedFolder,
                    selected = selected,
                    onOpenFolder = { folderPath += it.key },
                    onSelectionChange = { entry, checked ->
                        selected = updateSelection(entries, selected, entry, checked)
                    },
                )
            } else if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.recycle_bin_empty),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is RecycleEntry.Folder -> RecycleFolderRow(
                                entry = entry,
                                selected = entry.key in selected,
                                onOpen = { folderPath += entry.key },
                                onSelectedChange = { checked ->
                                    selected = updateSelection(entries, selected, entry, checked)
                                },
                            )
                            is RecycleEntry.Audio -> RecycleAudioRow(
                                title = entry.title,
                                description = entry.description,
                                selected = entry.key in selected,
                                onSelectedChange = { checked ->
                                    selected = updateSelection(entries, selected, entry, checked)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (entries.isNotEmpty()) {
            RecycleActions(
                selected = selected,
                onRestore = { onRestore(selected); selected = emptySet() },
                onClean = { showCleanConfirmation = true },
            )
        }
    }

    if (showCleanConfirmation) {
        AlertDialog(
            onDismissRequest = { showCleanConfirmation = false },
            title = { Text(stringResource(R.string.recycle_bin_clean_title)) },
            text = { Text(stringResource(R.string.recycle_bin_clean_message, selected.size)) },
            dismissButton = {
                TextButton(onClick = { showCleanConfirmation = false }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanConfirmation = false
                        onClean(selected)
                        selected = emptySet()
                    },
                ) {
                    Text(stringResource(R.string.recycle_bin_clean), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun RecycleFolderContents(
    entry: RecycleEntry.Folder,
    selected: Set<String>,
    onOpenFolder: (RecycleEntry.Folder) -> Unit,
    onSelectionChange: (RecycleEntry, Boolean) -> Unit,
) {
    if (entry.children.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.recycle_bin_folder_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(entry.children, key = { it.key }) { child ->
                when (child) {
                    is RecycleEntry.Folder -> RecycleFolderRow(
                        entry = child,
                        selected = child.key in selected,
                        onOpen = { onOpenFolder(child) },
                        onSelectedChange = { checked -> onSelectionChange(child, checked) },
                    )
                    is RecycleEntry.Audio -> RecycleAudioRow(
                        title = child.title,
                        description = child.description,
                        selected = child.key in selected,
                        onSelectedChange = { checked -> onSelectionChange(child, checked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecycleFolderRow(
    entry: RecycleEntry.Folder,
    selected: Boolean,
    onOpen: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen),
        leadingContent = {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(
                text = entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = if (entry.children.isEmpty()) {
                    entry.description
                } else {
                    "${entry.audioCount} 个音频 · ${entry.description}"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            RecycleCheckbox(checked = selected, onCheckedChange = onSelectedChange)
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun RecycleAudioRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onSelectedChange(!selected) },
        leadingContent = {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            RecycleCheckbox(checked = selected, onCheckedChange = onSelectedChange)
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun RecycleCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(32.dp),
    )
}

@Composable
private fun RecycleActions(
    selected: Set<String>,
    onRestore: () -> Unit,
    onClean: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRestore,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                Text(stringResource(R.string.recycle_bin_restore), modifier = Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(
                onClick = onClean,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(stringResource(R.string.recycle_bin_clean), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private sealed interface RecycleEntry {
    val key: String
    val title: String
    val description: String
    val deletedAtMs: Long
    val audioCount: Int

    data class Audio(private val item: RecycleItem) : RecycleEntry {
        override val key: String = "item:${item.key}"
        override val title: String = item.title
        override val description: String = displayPath(item.folderName, item.relativePath)
        override val deletedAtMs: Long = item.deletedAtMs
        override val audioCount: Int = 1
    }

    data class Folder(
        private val folder: RecycleFolder?,
        private val rootFolderUri: String,
        private val relativePath: String,
        val children: List<RecycleEntry>,
    ) : RecycleEntry {
        override val key: String = folder?.let { "folder:${it.key}" }
            ?: "virtual-folder:$rootFolderUri:$relativePath"
        override val title: String = folder?.title ?: relativePath.substringAfterLast('/')
        override val description: String = folder?.relativePath?.ifBlank { "源文件夹" } ?: relativePath
        override val deletedAtMs: Long = folder?.deletedAtMs
            ?: children.maxOfOrNull { it.deletedAtMs } ?: 0L
        override val audioCount: Int = children.sumOf { it.audioCount }
    }
}

private fun buildRecycleEntries(state: RecycleBinState): List<RecycleEntry> {
    val folderNodes = HashMap<FolderNodeKey, FolderNode>()
    val topLevelItems = mutableListOf<RecycleItem>()

    fun ensureNode(rootFolderUri: String, relativePath: String): FolderNode {
        val rootKey = FolderNodeKey(rootFolderUri, "")
        val root = folderNodes.getOrPut(rootKey) {
            FolderNode(rootFolderUri = rootFolderUri, relativePath = "")
        }
        var current: FolderNode? = null
        var currentPath = ""
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return root
        }
        parts.forEach { part ->
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            val key = FolderNodeKey(rootFolderUri, currentPath)
            current = folderNodes.getOrPut(key) {
                FolderNode(rootFolderUri = rootFolderUri, relativePath = currentPath)
            }
        }
        return requireNotNull(current)
    }

    state.folders.forEach { folder ->
        ensureNode(folder.rootFolderUri, folder.relativePath).folder = folder
    }

    state.items.forEach { item ->
        if (item.deletedByFolderUri != null) {
            ensureNode(item.folderUri, item.relativePath).items += item
        } else {
            topLevelItems += item
        }
    }

    val rootNodes = folderNodes.values.filterTo(LinkedHashSet()) { node ->
        if (node.relativePath.isEmpty()) {
            true
        } else {
            val parentPath = node.relativePath.substringBeforeLast('/', "")
            folderNodes[FolderNodeKey(node.rootFolderUri, parentPath)]?.children?.add(node)
            false
        }
    }

    fun buildFolder(node: FolderNode): RecycleEntry.Folder {
        val children = buildList {
            node.children.forEach { add(buildFolder(it)) }
            node.items.forEach { add(RecycleEntry.Audio(it)) }
        }.sortedWith(compareByDescending<RecycleEntry> { it.deletedAtMs }.thenBy { it.key })
        return RecycleEntry.Folder(
            folder = node.folder,
            rootFolderUri = node.rootFolderUri,
            relativePath = node.relativePath,
            children = children,
        )
    }

    fun addTopLevel(node: FolderNode, result: MutableList<RecycleEntry>) {
        if (node.folder != null) {
            result += buildFolder(node)
        } else {
            node.children.forEach { child -> addTopLevel(child, result) }
            node.items.forEach { item -> result += RecycleEntry.Audio(item) }
        }
    }

    return buildList {
        rootNodes.forEach { node -> addTopLevel(node, this) }
        topLevelItems.forEach { add(RecycleEntry.Audio(it)) }
    }.sortedWith(compareByDescending<RecycleEntry> { it.deletedAtMs }.thenBy { it.key })
}

private data class FolderNodeKey(
    val rootFolderUri: String,
    val relativePath: String,
)

private class FolderNode(
    val rootFolderUri: String,
    val relativePath: String,
    var folder: RecycleFolder? = null,
    val children: MutableList<FolderNode> = mutableListOf(),
    val items: MutableList<RecycleItem> = mutableListOf(),
)

private fun findFolder(entries: List<RecycleEntry>, path: List<String>): RecycleEntry.Folder? {
    var children = entries
    var current: RecycleEntry.Folder? = null
    path.forEach { key ->
        current = children.filterIsInstance<RecycleEntry.Folder>().firstOrNull { it.key == key }
        children = current?.children ?: emptyList()
    }
    return current
}

private fun selectionKeys(entry: RecycleEntry): Set<String> = buildSet {
    fun collect(current: RecycleEntry) {
        add(current.key)
        if (current is RecycleEntry.Folder) {
            current.children.forEach(::collect)
        }
    }
    collect(entry)
}

private fun updateSelection(
    entries: List<RecycleEntry>,
    selected: Set<String>,
    entry: RecycleEntry,
    checked: Boolean,
): Set<String> {
    val next = selected.toMutableSet()
    if (checked) {
        next += selectionKeys(entry)
    } else {
        next -= selectionKeys(entry)
    }
    return normalizeSelection(entries, next)
}

private fun normalizeSelection(entries: List<RecycleEntry>, selected: MutableSet<String>): Set<String> {
    fun normalize(entry: RecycleEntry): Boolean = when (entry) {
        is RecycleEntry.Audio -> entry.key in selected
        is RecycleEntry.Folder -> {
            val childrenFullySelected = if (entry.children.isEmpty()) {
                true
            } else {
                var allSelected = true
                entry.children.forEach { child ->
                    if (!normalize(child)) allSelected = false
                }
                allSelected
            }
            if (entry.children.isNotEmpty()) {
                if (childrenFullySelected) {
                    selected += entry.key
                } else {
                    selected -= entry.key
                }
            }
            entry.key in selected && childrenFullySelected
        }
    }
    entries.forEach(::normalize)
    return selected
}

private fun isInPath(path: String, parent: String): Boolean =
    parent.isEmpty() || path == parent || path.startsWith("$parent/")

private fun displayPath(folderName: String, relativePath: String): String =
    if (relativePath.isBlank()) folderName else "$folderName/$relativePath"
