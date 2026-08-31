package com.localaudio.player.ui.dialog

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import com.localaudio.player.app.AppDialog
import com.localaudio.player.app.AppEvent
import com.localaudio.player.app.AppUiState
import com.localaudio.player.app.SettingChange
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.REPEAT_ALL
import com.localaudio.player.data.settings.REPEAT_OFF
import com.localaudio.player.data.settings.REPEAT_ONE
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.data.model.DirectorySkipRule
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.components.SettingSwitchRow
import com.localaudio.player.ui.util.durationLabel

@Composable
internal fun AppDialogs(
    state: AppUiState,
    onEvent: (AppEvent) -> Unit,
) {
    val dialog = state.dialog
    when (val current = dialog) {
        AppDialog.Queue -> QueueDialog(
            state = state.playback,
            onSelect = {
                onEvent(AppEvent.Playback(PlaybackCommand.JumpToItem(it)))
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.Mode -> ModeDialog(
            state = state.playback,
            onSelect = { repeatMode, shuffleEnabled ->
                onEvent(
                    AppEvent.Playback(
                        PlaybackCommand.SetPlayMode(repeatMode, shuffleEnabled),
                    ),
                )
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        is AppDialog.DirectorySkip -> {
            val directoryItems = state.library.items.filter {
                it.folderUri == current.folderUri && it.relativePath == current.relativePath
            }
            val directoryLabel = directoryItems.firstOrNull()?.let { item ->
                if (current.relativePath.isEmpty()) item.folderName else "${item.folderName}/${current.relativePath}"
            } ?: current.relativePath.ifEmpty { current.folderUri }
            DirectorySkipDialog(
                rule = state.directorySkipRules.firstOrNull {
                    it.folderUri == current.folderUri && it.relativePath == current.relativePath
                },
                directoryLabel = directoryLabel,
                audioCount = directoryItems.size,
                onSave = { startSeconds, endSeconds ->
                    onEvent(
                        AppEvent.SaveDirectorySkip(
                            folderUri = current.folderUri,
                            relativePath = current.relativePath,
                            startSeconds = startSeconds,
                            endSeconds = endSeconds,
                        ),
                    )
                    onEvent(AppEvent.DismissDialog)
                },
                onDismiss = { onEvent(AppEvent.DismissDialog) },
            )
        }
        AppDialog.Timer -> TimerDialog(
            state = state.playback,
            settings = state.settings,
            onSetEnabled = { onEvent(AppEvent.UpdateSetting(SettingChange.SetTimerEnabled(it))) },
            onSetWaitForEnd = { onEvent(AppEvent.UpdateSetting(SettingChange.SetWaitForCurrentEnd(it))) },
            onSelectDuration = { duration ->
                onEvent(AppEvent.Playback(PlaybackCommand.StartTimer(duration)))
                onEvent(AppEvent.DismissDialog)
            },
            onStop = {
                onEvent(AppEvent.Playback(PlaybackCommand.StopTimer))
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                ChoiceOption(stringResource(R.string.theme_system), ThemeMode.SYSTEM),
                ChoiceOption(stringResource(R.string.theme_light), ThemeMode.LIGHT),
                ChoiceOption(stringResource(R.string.theme_dark), ThemeMode.DARK),
            ),
            selected = state.settings.themeMode,
            onSelect = { mode ->
                onEvent(
                    AppEvent.UpdateSetting(
                        SettingChange.SetThemeMode(mode),
                    ),
                )
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.Header -> ChoiceDialog(
            title = stringResource(R.string.settings_home_header),
            options = listOf(
                ChoiceOption(stringResource(R.string.header_fixed), HomeHeaderMode.FIXED),
                ChoiceOption(stringResource(R.string.header_hidden), HomeHeaderMode.HIDDEN),
                ChoiceOption(stringResource(R.string.header_auto), HomeHeaderMode.AUTO),
            ),
            selected = state.settings.homeHeaderMode,
            onSelect = { mode ->
                onEvent(
                    AppEvent.UpdateSetting(
                        SettingChange.SetHomeHeaderMode(mode),
                    ),
                )
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.SeekStep -> SeekStepDialog(
            valueMs = state.settings.seekStepMs,
            onSave = { valueMs ->
                onEvent(AppEvent.UpdateSetting(SettingChange.SetSeekStep(valueMs)))
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.TimerDuration -> TimerDurationDialog(
            settings = state.settings,
            onSelect = { duration ->
                onEvent(AppEvent.UpdateSetting(SettingChange.SetTimerDuration(duration)))
                onEvent(AppEvent.DismissDialog)
            },
            onAdd = { onEvent(AppEvent.ShowDialog(AppDialog.AddDuration)) },
            onDelete = { onEvent(AppEvent.UpdateSetting(SettingChange.DeleteTimerDuration(it))) },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.AddDuration -> AddDurationDialog(
            onSave = { value ->
                onEvent(AppEvent.UpdateSetting(SettingChange.AddTimerDuration(value)))
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
private fun QueueDialog(
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
                itemsIndexed(state.queue) { index, item ->
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
private fun ModeDialog(
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
private fun DirectorySkipDialog(
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
private fun TimerDialog(
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
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelectDuration(duration) },
                            ),
                        leadingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                            )
                        },
                        headlineContent = { Text(durationLabel(duration)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
private fun <T> ChoiceDialog(
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
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.value) },
                            ),
                        leadingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )
                        },
                        headlineContent = { Text(option.label) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
private fun SeekStepDialog(
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
private fun TimerDurationDialog(
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
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(duration) },
                            ),
                        leadingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                            )
                        },
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
private fun AddDurationDialog(
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

private data class ChoiceOption<T>(
    val label: String,
    val value: T,
)

private data class PlayMode(
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
)
