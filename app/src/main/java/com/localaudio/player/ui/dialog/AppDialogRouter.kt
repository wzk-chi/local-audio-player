package com.localaudio.player.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.localaudio.player.R
import com.localaudio.player.app.AppDialog
import com.localaudio.player.app.AppEvent
import com.localaudio.player.app.AppUiState
import com.localaudio.player.app.SettingChange
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.DirectorySkipRule
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackState

private class DirectoryItemsCache {
    var sourceItems: List<AudioItem>? = null
    var value: List<AudioItem> = emptyList()
}

@Composable
private fun rememberDirectoryItems(
    items: List<AudioItem>,
    folderUri: String,
    relativePath: String,
): List<AudioItem> {
    val cache = remember(folderUri, relativePath) { DirectoryItemsCache() }
    if (cache.sourceItems !== items) {
        cache.sourceItems = items
        cache.value = items.filter {
            it.folderUri == folderUri && it.relativePath == relativePath
        }
    }
    return cache.value
}

private class AudioItemCache {
    var sourceItems: List<AudioItem>? = null
    var byKey: Map<String, AudioItem> = emptyMap()
}

@Composable
private fun rememberAudioItem(items: List<AudioItem>, key: String): AudioItem? {
    val cache = remember { AudioItemCache() }
    if (cache.sourceItems !== items) {
        cache.sourceItems = items
        cache.byKey = items.associateBy { it.key }
    }
    return cache.byKey[key]
}

private class DirectorySkipRuleCache {
    var sourceRules: List<DirectorySkipRule>? = null
    var byLocation: Map<Pair<String, String>, DirectorySkipRule> = emptyMap()
}

@Composable
private fun rememberDirectorySkipRule(
    rules: List<DirectorySkipRule>,
    folderUri: String,
    relativePath: String,
): DirectorySkipRule? {
    val cache = remember { DirectorySkipRuleCache() }
    if (cache.sourceRules !== rules) {
        cache.sourceRules = rules
        cache.byLocation = rules.associateBy { it.folderUri to it.relativePath }
    }
    return cache.byLocation[folderUri to relativePath]
}

@Composable
internal fun AppDialogs(
    state: AppUiState,
    playback: PlaybackState,
    onEvent: (AppEvent) -> Unit,
) {
    val dialog = state.dialog
    when (val current = dialog) {
        AppDialog.Queue -> QueueDialog(
            state = playback,
            onSelect = {
                onEvent(AppEvent.Playback(PlaybackCommand.JumpToItem(it)))
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.Mode -> ModeDialog(
            state = playback,
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
        is AppDialog.Rename -> RenameHomeDialog(
            target = current.target,
            onSave = { name -> onEvent(AppEvent.RenameHomeItem(current.target, name)) },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        is AppDialog.Delete -> DeleteHomeDialog(
            target = current.target,
            deleteSource = current.deleteSource,
            onConfirm = { checked ->
                if (current.deleteSource) {
                    onEvent(AppEvent.DeleteHomeItem(current.target, true))
                } else if (checked) {
                    onEvent(AppEvent.ShowDialog(AppDialog.Delete(current.target, true)))
                } else {
                    onEvent(AppEvent.DeleteHomeItem(current.target, false))
                }
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        is AppDialog.DirectorySkip -> {
            val directoryItems = rememberDirectoryItems(
                items = state.library.items,
                folderUri = current.folderUri,
                relativePath = current.relativePath,
            )
            val directoryLabel = directoryItems.firstOrNull()?.let { item ->
                if (current.relativePath.isEmpty()) item.folderName else "${item.folderName}/${current.relativePath}"
            } ?: current.relativePath.ifEmpty { current.folderUri }
            DirectorySkipDialog(
                rule = rememberDirectorySkipRule(
                    rules = state.directorySkipRules,
                    folderUri = current.folderUri,
                    relativePath = current.relativePath,
                ),
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
        is AppDialog.AutoSkipEditor -> {
            val item = rememberAudioItem(state.library.items, current.audioKey)
            AutoSkipEditorDialog(
                title = item?.title ?: current.audioKey,
                segmentId = current.segmentId,
                audioKey = current.audioKey,
                startMs = current.startMs,
                endMs = current.endMs,
                durationMs = current.durationMs,
                onTest = { start, end ->
                    onEvent(
                        AppEvent.TestAutoSkipSegment(
                            audioKey = current.audioKey,
                            startMs = start,
                            endMs = end,
                        ),
                    )
                },
                onSave = { start, end ->
                    onEvent(
                        AppEvent.SaveAutoSkipSegment(
                            audioKey = current.audioKey,
                            segmentId = current.segmentId,
                            startMs = start,
                            endMs = end,
                        ),
                    )
                },
                onDismiss = { onEvent(AppEvent.DismissDialog) },
            )
        }
        AppDialog.Timer -> TimerDialog(
            state = playback,
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
