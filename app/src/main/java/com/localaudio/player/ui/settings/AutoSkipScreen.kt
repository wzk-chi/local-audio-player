package com.localaudio.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.AutoSkipSegment
import com.localaudio.player.ui.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSkipScreen(
    segments: List<AutoSkipSegment>,
    audioItems: List<AudioItem>,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var segmentToDelete by remember { mutableStateOf<AutoSkipSegment?>(null) }
    val orderedSegments = remember(segments) {
        segments.sortedWith(
            compareByDescending<AutoSkipSegment> { it.modifiedAtMs }
                .thenByDescending { it.id },
        )
    }
    val audioByKey = remember(audioItems) { audioItems.associateBy { it.key } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.auto_skip_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.auto_skip_back),
                    )
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (orderedSegments.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.auto_skip_empty),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        orderedSegments.forEachIndexed { index, segment ->
                            if (index > 0) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
                            }
                            val item = audioByKey[segment.audioKey]
                            AutoSkipRow(
                                segment = segment,
                                item = item,
                                onPlay = { onPlay(segment.audioKey) },
                                onEdit = { onEdit(segment.id) },
                                onDelete = { segmentToDelete = segment },
                            )
                        }
                    }
                }
            }
        }
    }

    segmentToDelete?.let { segment ->
        AlertDialog(
            onDismissRequest = { segmentToDelete = null },
            title = { Text(stringResource(R.string.auto_skip_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.auto_skip_delete_message,
                        segment.titleSnapshot,
                        formatTime(segment.startMs),
                        formatTime(segment.endMs),
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { segmentToDelete = null }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(segment.id)
                        segmentToDelete = null
                    },
                ) {
                    Text(
                        stringResource(R.string.dialog_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun AutoSkipRow(
    segment: AutoSkipSegment,
    item: AudioItem?,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = item?.title ?: segment.titleSnapshot
    val path = item?.let { displayPath(it.folderName, it.relativePath) }
        ?: displayPath(segment.folderNameSnapshot, segment.relativePath)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item != null, onClick = onPlay),
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = path,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${formatTime(segment.startMs)} – ${formatTime(segment.endMs)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit, enabled = item != null) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.auto_skip_edit),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.auto_skip_delete),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun displayPath(folderName: String, relativePath: String): String =
    if (relativePath.isBlank()) folderName else "$folderName/$relativePath"
