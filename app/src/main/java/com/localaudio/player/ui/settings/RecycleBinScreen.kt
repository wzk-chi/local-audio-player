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
import androidx.compose.material.icons.filled.ChevronRight
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
    var openedFolderKey by remember { mutableStateOf<String?>(null) }
    var showCleanConfirmation by remember { mutableStateOf(false) }
    val openedFolder = entries
        .asSequence()
        .filterIsInstance<RecycleEntry.Folder>()
        .firstOrNull { it.key == openedFolderKey }
    val selectableKeys = remember(entries) {
        entries.flatMapTo(HashSet()) { entry ->
            when (entry) {
                is RecycleEntry.Folder -> buildSet {
                    add(entry.key)
                    entry.children.forEach { add("item:${it.key}") }
                }
                is RecycleEntry.Audio -> setOf(entry.key)
            }
        }
    }
    val allSelected = selectableKeys.isNotEmpty() && selected.containsAll(selectableKeys)

    BackHandler(enabled = openedFolder != null) {
        openedFolderKey = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(openedFolder?.title ?: stringResource(R.string.recycle_bin_title)) },
            navigationIcon = {
                IconButton(onClick = {
                    if (openedFolder != null) openedFolderKey = null else onBack()
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
                    onChildSelectedChange = { key, checked ->
                        val next = if (checked) selected + key else selected - key
                        val childKeys = openedFolder.children.mapTo(HashSet()) { "item:${it.key}" }
                        selected = if (childKeys.isNotEmpty() && next.containsAll(childKeys)) {
                            next + openedFolder.key
                        } else {
                            next - openedFolder.key
                        }
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
                                onOpen = { openedFolderKey = entry.key },
                                onSelectedChange = { checked ->
                                    val childKeys = entry.children.mapTo(HashSet()) { "item:${it.key}" }
                                    selected = if (checked) {
                                        selected + entry.key + childKeys
                                    } else {
                                        selected - entry.key - childKeys
                                    }
                                },
                            )
                            is RecycleEntry.Audio -> RecycleAudioRow(
                                title = entry.title,
                                description = entry.description,
                                selected = entry.key in selected,
                                onSelectedChange = { checked ->
                                    selected = if (checked) selected + entry.key else selected - entry.key
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
    onChildSelectedChange: (String, Boolean) -> Unit,
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
            items(entry.children, key = { "item:${it.key}" }) { item ->
                val key = "item:${item.key}"
                RecycleAudioRow(
                    title = item.title,
                    description = displayPath(item.folderName, item.relativePath),
                    selected = key in selected,
                    onSelectedChange = { checked -> onChildSelectedChange(key, checked) },
                )
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
                    "${entry.children.size} 个音频 · ${entry.description}"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RecycleCheckbox(checked = selected, onCheckedChange = onSelectedChange)
            }
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

    data class Audio(private val item: RecycleItem) : RecycleEntry {
        override val key: String = "item:${item.key}"
        override val title: String = item.title
        override val description: String = displayPath(item.folderName, item.relativePath)
        override val deletedAtMs: Long = item.deletedAtMs
    }

    data class Folder(
        private val folder: RecycleFolder,
        val children: List<RecycleItem>,
    ) : RecycleEntry {
        override val key: String = "folder:${folder.key}"
        override val title: String = folder.title
        override val description: String = folder.relativePath.ifBlank { "源文件夹" }
        override val deletedAtMs: Long = folder.deletedAtMs
    }
}

private fun buildRecycleEntries(state: RecycleBinState): List<RecycleEntry> {
    val folders = state.folders.sortedWith(
        compareByDescending<RecycleFolder> { it.deletedAtMs }.thenBy { it.key },
    )
    val childrenByFolder = folders.associate { it.key to mutableListOf<RecycleItem>() }
    val topLevelItems = mutableListOf<RecycleItem>()

    state.items.forEach { item ->
        val owner = folders
            .asSequence()
            .filter { folder ->
                folder.rootFolderUri == item.folderUri &&
                    isInPath(item.relativePath, folder.relativePath)
            }
            .sortedWith(compareByDescending<RecycleFolder> { it.relativePath.length }.thenBy { it.key })
            .firstOrNull()
        if (owner == null) {
            topLevelItems += item
        } else {
            childrenByFolder.getValue(owner.key) += item
        }
    }

    return buildList {
        folders.forEach { folder ->
            add(
                RecycleEntry.Folder(
                    folder = folder,
                    children = childrenByFolder.getValue(folder.key)
                        .sortedWith(compareByDescending<RecycleItem> { it.deletedAtMs }.thenBy { it.key }),
                ),
            )
        }
        topLevelItems
            .sortedWith(compareByDescending<RecycleItem> { it.deletedAtMs }.thenBy { it.key })
            .forEach { add(RecycleEntry.Audio(it)) }
    }.sortedWith(compareByDescending<RecycleEntry> { it.deletedAtMs }.thenBy { it.key })
}

private fun isInPath(path: String, parent: String): Boolean =
    parent.isEmpty() || path == parent || path.startsWith("$parent/")

private fun displayPath(folderName: String, relativePath: String): String =
    if (relativePath.isBlank()) folderName else "$folderName/$relativePath"
