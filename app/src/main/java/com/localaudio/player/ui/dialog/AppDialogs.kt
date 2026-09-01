package com.localaudio.player.ui.dialog

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.app.HomeActionTarget
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.REPEAT_ALL
import com.localaudio.player.data.settings.REPEAT_OFF
import com.localaudio.player.data.settings.REPEAT_ONE
import com.localaudio.player.data.model.DirectorySkipRule
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.components.SettingSwitchRow
import com.localaudio.player.ui.util.durationLabel

@Composable
internal fun RenameHomeDialog(
    target: HomeActionTarget,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(target) { mutableStateOf(target.displayName()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.trim().isNotEmpty()) {
                Text(stringResource(R.string.dialog_save))
            }
        },
    )
}

@Composable
internal fun DeleteHomeDialog(
    target: HomeActionTarget,
    deleteSource: Boolean,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember(target) { mutableStateOf(deleteSource) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (deleteSource) "确认删除源文件？" else "删除项目？")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.deleteDescription())
                if (!deleteSource) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked = !checked },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { checked = it },
                        )
                        Text("同时删除源文件")
                    }
                } else {
                    Text(
                        "源文件删除后无法从回收站还原。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked) }) {
                Text(
                    if (deleteSource) "确认删除" else stringResource(R.string.dialog_delete),
                    color = if (deleteSource) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

private fun HomeActionTarget.displayName(): String = when (this) {
    is HomeActionTarget.Audio -> item.title
    is HomeActionTarget.Directory -> location.name
}

private fun HomeActionTarget.deleteDescription(): String = when (this) {
    is HomeActionTarget.Audio -> "确定删除“${item.title}”吗？"
    is HomeActionTarget.Directory -> "确定删除文件夹“${location.name}”及其中内容吗？"
}

@Composable
internal fun AutoSkipEditorDialog(
    title: String,
    segmentId: String?,
    audioKey: String,
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onTest: (Long, Long) -> Unit,
    onSave: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var editedStartMs by remember(segmentId, audioKey, startMs) { mutableStateOf(startMs) }
    var editedEndMs by remember(segmentId, audioKey, endMs) { mutableStateOf(endMs) }
    var preciseEditor by remember { mutableStateOf<AutoSkipTimeField?>(null) }
    val start = editedStartMs
    val end = editedEndMs
    val validationMessage = autoSkipRangeValidationMessage(start, end, durationMs)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_skip_editor_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AutoSkipTimeAdjuster(
                    label = stringResource(R.string.auto_skip_start_time),
                    valueMs = editedStartMs,
                    maxDurationMs = durationMs,
                    onValueChange = { editedStartMs = it },
                    onPreciseEdit = { preciseEditor = AutoSkipTimeField.START },
                )
                AutoSkipTimeAdjuster(
                    label = stringResource(R.string.auto_skip_end_time),
                    valueMs = editedEndMs,
                    maxDurationMs = durationMs,
                    onValueChange = { editedEndMs = it },
                    onPreciseEdit = { preciseEditor = AutoSkipTimeField.END },
                )
                validationMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_cancel))
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    enabled = validationMessage == null,
                    onClick = { onTest(start, end) },
                ) {
                    Text(stringResource(R.string.auto_skip_test))
                }
                TextButton(
                    enabled = validationMessage == null,
                    onClick = { onSave(start, end) },
                ) {
                    Text(stringResource(R.string.dialog_save))
                }
            }
        },
    )
    preciseEditor?.let { field ->
        val initialValue = if (field == AutoSkipTimeField.START) editedStartMs else editedEndMs
        val otherValue = if (field == AutoSkipTimeField.START) editedEndMs else editedStartMs
        AutoSkipPreciseTimeDialog(
            field = field,
            initialValueMs = initialValue,
            otherValueMs = otherValue,
            durationMs = durationMs,
            onSave = { value ->
                if (field == AutoSkipTimeField.START) {
                    editedStartMs = value
                } else {
                    editedEndMs = value
                }
                preciseEditor = null
            },
            onDismiss = { preciseEditor = null },
        )
    }
}

private enum class AutoSkipTimeField {
    START,
    END,
}

@Composable
private fun AutoSkipTimeAdjuster(
    label: String,
    valueMs: Long,
    maxDurationMs: Long,
    onValueChange: (Long) -> Unit,
    onPreciseEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(
            onClick = {
                onValueChange(adjustAutoSkipTime(valueMs, -AUTO_SKIP_ADJUST_STEP_MS, maxDurationMs))
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.auto_skip_adjust_decrease),
            )
        }
        OutlinedButton(
            onClick = onPreciseEdit,
            modifier = Modifier.weight(1.6f),
        ) {
            Text(
                text = formatAutoSkipDisplayTime(valueMs),
                maxLines = 1,
            )
        }
        IconButton(
            onClick = {
                onValueChange(adjustAutoSkipTime(valueMs, AUTO_SKIP_ADJUST_STEP_MS, maxDurationMs))
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.auto_skip_adjust_increase),
            )
        }
    }
}

@Composable
private fun AutoSkipPreciseTimeDialog(
    field: AutoSkipTimeField,
    initialValueMs: Long,
    otherValueMs: Long,
    durationMs: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(field, initialValueMs) {
        mutableStateOf(formatAutoSkipDisplayTime(initialValueMs))
    }
    val parsedValueMs = parseAutoSkipDisplayTime(text)
    val validationMessage = if (parsedValueMs == null) {
        stringResource(R.string.auto_skip_invalid_time_format)
    } else {
        val start = if (field == AutoSkipTimeField.START) parsedValueMs else otherValueMs
        val end = if (field == AutoSkipTimeField.END) parsedValueMs else otherValueMs
        autoSkipRangeValidationMessage(
            startMs = start,
            endMs = end,
            durationMs = durationMs,
            checkNonNegative = true,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (field == AutoSkipTimeField.START) {
                        R.string.auto_skip_start_time
                    } else {
                        R.string.auto_skip_end_time
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { character ->
                            character.isDigit() || character == ':' || character == '.'
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationMessage != null,
                    label = { Text(stringResource(R.string.auto_skip_time_input_label)) },
                    supportingText = {
                        Text(
                            validationMessage ?: stringResource(R.string.auto_skip_time_format_hint),
                            color = if (validationMessage != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
        confirmButton = {
            TextButton(
                enabled = validationMessage == null,
                onClick = { parsedValueMs?.let(onSave) },
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
    )
}

@Composable
private fun autoSkipRangeValidationMessage(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    checkNonNegative: Boolean = false,
): String? = when {
    checkNonNegative && (startMs < 0L || endMs < 0L) ->
        stringResource(R.string.auto_skip_invalid_time)
    endMs <= startMs -> stringResource(
        R.string.auto_skip_invalid_end,
        startMs / 60_000L,
        (startMs / 1_000L % 60L).toInt(),
    )
    durationMs > 0L && endMs > durationMs -> stringResource(
        R.string.auto_skip_duration_exceeded,
        durationMs / 60_000L,
        (durationMs / 1_000L % 60L).toInt(),
    )
    else -> null
}

private const val AUTO_SKIP_ADJUST_STEP_MS = 100L

private fun adjustAutoSkipTime(valueMs: Long, deltaMs: Long, maxDurationMs: Long): Long {
    val adjusted = if (deltaMs < 0L) {
        (valueMs + deltaMs).coerceAtLeast(0L)
    } else if (valueMs > Long.MAX_VALUE - deltaMs) {
        Long.MAX_VALUE
    } else {
        valueMs + deltaMs
    }
    return if (maxDurationMs > 0L) adjusted.coerceAtMost(maxDurationMs) else adjusted
}

private fun formatAutoSkipDisplayTime(timeMs: Long): String {
    val safeTimeMs = timeMs.coerceAtLeast(0L)
    val minutes = safeTimeMs / 60_000L
    val seconds = (safeTimeMs / 1_000L % 60L).toString().padStart(2, '0')
    val milliseconds = (safeTimeMs % 1_000L).toString().padStart(3, '0')
    return "$minutes:$seconds.$milliseconds"
}

private fun parseAutoSkipDisplayTime(text: String): Long? {
    val parts = text.split(':')
    if (parts.size != 2) return null
    val minutes = parts[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
    val secondParts = parts[1].split('.')
    if (secondParts.size > 2) return null
    val seconds = secondParts[0].toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
    val fractionText = secondParts.getOrNull(1).orEmpty()
    if (fractionText.length > 3 || (fractionText.isNotEmpty() && !fractionText.all(Char::isDigit))) {
        return null
    }
    val fraction = fractionText.padEnd(3, '0').toLongOrNull() ?: 0L
    if (minutes > (Long.MAX_VALUE - seconds * 1_000L - fraction) / 60_000L) return null
    return minutes * 60_000L + seconds * 1_000L + fraction
}

@Composable
internal fun QueueDialog(
    state: PlaybackState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.currentIndex, state.queue.size) {
        if (state.currentIndex in state.queue.indices) {
            listState.scrollToItem(state.currentIndex)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_queue)) },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                itemsIndexed(state.queue, key = { _, item -> item.key }) { index, item ->
                    val active = index == state.currentIndex
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(index) },
                        headlineContent = {
                            Text(
                                text = item.title,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (active) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
internal fun ModeDialog(
    state: PlaybackState,
    onSelect: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ChoiceOption(stringResource(R.string.dialog_mode_order), PlayMode(REPEAT_OFF, false)),
        ChoiceOption(stringResource(R.string.dialog_mode_repeat_one), PlayMode(REPEAT_ONE, false)),
        ChoiceOption(stringResource(R.string.dialog_mode_repeat_all), PlayMode(REPEAT_ALL, false)),
        ChoiceOption(stringResource(R.string.dialog_mode_shuffle), PlayMode(REPEAT_OFF, true)),
    )
    val selected = when {
        state.shuffleEnabled -> PlayMode(REPEAT_OFF, true)
        state.repeatMode == REPEAT_ONE -> PlayMode(REPEAT_ONE, false)
        state.repeatMode == REPEAT_ALL -> PlayMode(REPEAT_ALL, false)
        else -> PlayMode(REPEAT_OFF, false)
    }
    ChoiceDialog(
        title = stringResource(R.string.dialog_play_mode),
        options = options,
        selected = selected,
        onSelect = { onSelect(it.repeatMode, it.shuffleEnabled) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun SelectableRadioListItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
        headlineContent = headlineContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun DirectorySkipDialog(
    rule: DirectorySkipRule?,
    directoryLabel: String,
    audioCount: Int,
    onSave: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var startText by remember(rule?.key, rule?.modifiedAtMs) {
        mutableStateOf(rule?.startSeconds?.toString() ?: "0")
    }
    var endText by remember(rule?.key, rule?.modifiedAtMs) {
        mutableStateOf(rule?.endSeconds?.toString() ?: "0")
    }
    val startSeconds = startText.toLongOrNull()?.takeIf { it >= 0L }
    val endSeconds = endText.toLongOrNull()?.takeIf { it >= 0L }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_skip_boundaries)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = directoryLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.dialog_skip_directory_count, audioCount))
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.dialog_skip_start_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.dialog_skip_end_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = stringResource(R.string.dialog_skip_zero_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
        confirmButton = {
            TextButton(
                enabled = startSeconds != null && endSeconds != null,
                onClick = {
                    if (startSeconds != null && endSeconds != null) {
                        onSave(startSeconds, endSeconds)
                    }
                },
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
    )
}

@Composable
internal fun TimerDialog(
    state: PlaybackState,
    settings: AppSettings,
    onSetEnabled: (Boolean) -> Unit,
    onSetWaitForEnd: (Boolean) -> Unit,
    onSelectDuration: (Long) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_timer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_timer),
                    checked = settings.timerEnabled,
                    onCheckedChange = onSetEnabled,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 3.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_wait_for_end),
                    checked = settings.waitForCurrentEnd,
                    onCheckedChange = onSetWaitForEnd,
                )
                Text(
                    text = stringResource(R.string.dialog_timer_duration),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                settings.timerDurationOptionsMs.forEach { duration ->
                    val selected = state.timerActive && duration == state.activeTimerDurationMs
                    SelectableRadioListItem(
                        selected = selected,
                        onClick = { onSelectDuration(duration) },
                        headlineContent = { Text(durationLabel(duration)) },
                    )
                }
            }
        },
        dismissButton = {
            if (state.timerActive) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.dialog_stop_timer)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = selected == option.value
                    SelectableRadioListItem(
                        selected = isSelected,
                        onClick = { onSelect(option.value) },
                        headlineContent = { Text(option.label) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
internal fun SeekStepDialog(
    valueMs: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf((valueMs / 1000L).toString()) }
    val valueSeconds = text.toLongOrNull()?.takeIf { it in 1L..(Long.MAX_VALUE / 1_000L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_seek_step)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text(stringResource(R.string.dialog_seconds)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
        confirmButton = {
            TextButton(
                enabled = valueSeconds != null && valueSeconds > 0L,
                onClick = { valueSeconds?.let { onSave(it * 1000L) } },
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
    )
}

@Composable
internal fun TimerDurationDialog(
    settings: AppSettings,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_timer_length)) },
        text = {
            Column {
                settings.timerDurationOptionsMs.forEach { duration ->
                    val selected = duration == settings.timerDurationMs
                    SelectableRadioListItem(
                        selected = selected,
                        onClick = { onSelect(duration) },
                        headlineContent = { Text(durationLabel(duration)) },
                        trailingContent = {
                            IconButton(
                                onClick = { onDelete(duration) },
                                enabled = settings.timerDurationOptionsMs.size > 1,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.dialog_delete),
                                )
                            }
                        },
                    )
                }
                TextButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dialog_add_custom_duration))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
internal fun AddDurationDialog(
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val durationMs = text.toLongOrNull()
        ?.takeIf { it in 1L..(Long.MAX_VALUE / 60_000L) }
        ?.times(60_000L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_duration)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text(stringResource(R.string.dialog_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
        confirmButton = {
            TextButton(
                enabled = durationMs != null,
                onClick = { durationMs?.let(onSave) },
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
    )
}

internal data class ChoiceOption<T>(
    val label: String,
    val value: T,
)

private data class PlayMode(
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
)
