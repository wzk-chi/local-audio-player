package com.localaudio.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.data.model.RecycleBinState
import com.localaudio.player.data.model.RecycleFolder
import com.localaudio.player.data.model.RecycleItem
import com.localaudio.player.ui.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    state: RecycleBinState,
    onBack: () -> Unit,
    onRestore: (Set<String>) -> Unit,
    onClean: (Set<String>) -> Unit,
) {
    val entries = remember(state) {
        buildList {
            state.folders.forEach { add(RecycleEntry.Folder(it)) }
            state.items.forEach { add(RecycleEntry.Audio(it)) }
        }.sortedWith(compareByDescending<RecycleEntry> { it.deletedAtMs }.thenBy { it.key })
    }
    var selected by remember(entries) { mutableStateOf(emptySet<String>()) }
    var showCleanConfirmation by remember { mutableStateOf(false) }
    val allSelected = entries.isNotEmpty() && selected.size == entries.size

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.recycle_bin_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.recycle_bin_back),
                    )
                }
            },
            actions = {
                if (entries.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selected = if (allSelected) emptySet() else entries.mapTo(HashSet()) { it.key }
                        },
                    ) {
                        Text(stringResource(if (allSelected) R.string.recycle_bin_deselect_all else R.string.recycle_bin_select_all))
                    }
                }
            },
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.recycle_bin_empty),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        entries.forEach { entry ->
                            RecycleRow(
                                entry = entry,
                                selected = entry.key in selected,
                                onSelectedChange = { checked ->
                                    selected = if (checked) selected + entry.key else selected - entry.key
                                },
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { onRestore(selected); selected = emptySet() },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Text(stringResource(R.string.recycle_bin_restore), modifier = Modifier.padding(start = 8.dp))
                        }
                        TextButton(
                            onClick = { showCleanConfirmation = true },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(
                                stringResource(R.string.recycle_bin_clean),
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
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
private fun RecycleRow(
    entry: RecycleEntry,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectedChange(!selected) },
        leadingContent = {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
        },
        headlineContent = {
            Text(
                text = entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (entry.durationMs > 0L) {
                    Text(
                        text = formatTime(entry.durationMs),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = {
            Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private sealed interface RecycleEntry {
    val key: String
    val title: String
    val description: String
    val durationMs: Long
    val deletedAtMs: Long
    val icon: androidx.compose.ui.graphics.vector.ImageVector

    data class Audio(private val item: RecycleItem) : RecycleEntry {
        override val key: String = "item:${item.key}"
        override val title: String = item.title
        override val description: String = displayPath(item.folderName, item.relativePath)
        override val durationMs: Long = item.durationMs
        override val deletedAtMs: Long = item.deletedAtMs
        override val icon = Icons.Filled.MusicNote
    }

    data class Folder(private val folder: RecycleFolder) : RecycleEntry {
        override val key: String = "folder:${folder.key}"
        override val title: String = folder.title
        override val description: String = folder.relativePath.ifBlank { folder.rootFolderUri }
        override val durationMs: Long = 0L
        override val deletedAtMs: Long = folder.deletedAtMs
        override val icon = Icons.Filled.Folder
    }
}

private fun displayPath(folderName: String, relativePath: String): String =
    if (relativePath.isBlank()) folderName else "$folderName/$relativePath"
