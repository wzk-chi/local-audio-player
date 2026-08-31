package com.localaudio.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.data.model.FolderItem
import com.localaudio.player.data.model.ScanState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsScreen(
    folders: List<FolderItem>,
    scanStates: Map<String, ScanState>,
    onBack: () -> Unit,
    onAddFolder: () -> Unit,
    onRescanFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescanAll: () -> Unit,
) {
    var folderToRemove by remember { mutableStateOf<FolderItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.library_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back))
                }
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (folders.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(stringResource(R.string.home_empty_library), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.library_empty_description),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                FilledTonalButton(onClick = onAddFolder) {
                                    Text(stringResource(R.string.home_add_folder))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            folders.forEachIndexed { index, folder ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    )
                                }
                                FolderSettingRow(
                                    folder = folder,
                                    state = scanStates[folder.uri] ?: ScanState.Idle,
                                    onRescan = onRescanFolder,
                                    onRemove = { folderToRemove = folder },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (folders.isNotEmpty()) {
            LibraryActions(
                onAddFolder = onAddFolder,
                onRescanAll = onRescanAll,
            )
        }
    }

    folderToRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text(stringResource(R.string.library_remove_title)) },
            text = { Text(stringResource(R.string.library_remove_message, folder.displayName)) },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) { Text(stringResource(R.string.library_cancel)) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFolder(folder.uri)
                        folderToRemove = null
                    },
                ) {
                    Text(stringResource(R.string.library_remove), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun LibraryActions(
    onAddFolder: () -> Unit,
    onRescanAll: () -> Unit,
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
                onClick = onRescanAll,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(stringResource(R.string.library_rescan), modifier = Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(
                onClick = onAddFolder,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Text(stringResource(R.string.home_add_folder), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FolderSettingRow(
    folder: FolderItem,
    state: ScanState,
    onRescan: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val statusColor = when (state) {
        ScanState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        is ScanState.Scanning -> MaterialTheme.colorScheme.primary
        is ScanState.Done -> MaterialTheme.colorScheme.tertiary
        is ScanState.Failed -> MaterialTheme.colorScheme.error
    }

    ListItem(
        headlineContent = {
            Text(
                folder.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                folderStatus(state),
                color = statusColor,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Icon(Icons.Filled.Folder, contentDescription = null)
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onRescan(folder.uri) }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.library_rescan_folder, folder.displayName),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.library_remove_folder, folder.displayName),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun folderStatus(state: ScanState): String = when (state) {
    ScanState.Idle -> stringResource(R.string.library_scan_idle)
    is ScanState.Scanning -> stringResource(R.string.library_scan_scanning, state.scanned, state.found)
    is ScanState.Done -> stringResource(R.string.library_scan_done, state.audioCount)
    is ScanState.Failed -> stringResource(R.string.library_scan_failed, state.reason)
}
