package com.localaudio.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.FADE_DURATION_STEP_MS
import com.localaudio.player.data.settings.MAX_FADE_DURATION_MS
import com.localaudio.player.data.settings.MIN_FADE_DURATION_MS
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.ui.components.SettingChoiceRow
import com.localaudio.player.ui.components.SettingSwitchRow
import com.localaudio.player.ui.util.durationLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    folderCount: Int,
    recycleBinCount: Int,
    autoSkipCount: Int,
    onThemeClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onSetHomeListBottomAligned: (Boolean) -> Unit,
    onSetShowAlbumCover: (Boolean) -> Unit,
    onSetShowWhenLocked: (Boolean) -> Unit,
    onSetFadeEnabled: (Boolean) -> Unit,
    onSetFadeDuration: (Long) -> Unit,
    onSetLoudnessEnabled: (Boolean) -> Unit,
    onSetTimerEnabled: (Boolean) -> Unit,
    onSetWaitForCurrentEnd: (Boolean) -> Unit,
    onSeekStepClick: () -> Unit,
    onTimerDurationClick: () -> Unit,
    onOpenAutoSkip: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenRecycleBin: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsSection(stringResource(R.string.settings_appearance)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_theme),
                        value = themeLabel(settings.themeMode),
                        onClick = onThemeClick,
                    )
                    SettingsDivider()
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_home_header),
                        value = headerLabel(settings.homeHeaderMode),
                        onClick = onHeaderClick,
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_home_list_bottom_aligned),
                        checked = settings.homeListBottomAligned,
                        onCheckedChange = onSetHomeListBottomAligned,
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_show_album_cover),
                        checked = settings.showAlbumCover,
                        onCheckedChange = onSetShowAlbumCover,
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_show_when_locked),
                        checked = settings.showWhenLocked,
                        onCheckedChange = onSetShowWhenLocked,
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_playback)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_seek_step),
                        value = stringResource(R.string.settings_seconds, settings.seekStepMs / 1000L),
                        onClick = onSeekStepClick,
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_fade),
                        checked = settings.fadeEnabled,
                        onCheckedChange = onSetFadeEnabled,
                    )
                    FadeDurationRow(
                        durationMs = settings.fadeDurationMs,
                        enabled = settings.fadeEnabled,
                        onDurationChange = onSetFadeDuration,
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_loudness),
                        checked = settings.loudnessEnabled,
                        onCheckedChange = onSetLoudnessEnabled,
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_auto_skip)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_auto_skip_management),
                        value = if (autoSkipCount == 0) {
                            stringResource(R.string.settings_auto_skip_empty)
                        } else {
                            stringResource(R.string.settings_auto_skip_count, autoSkipCount)
                        },
                        onClick = onOpenAutoSkip,
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_sleep_timer)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_auto_timer),
                        checked = settings.timerEnabled,
                        onCheckedChange = onSetTimerEnabled,
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_wait_for_end),
                        checked = settings.waitForCurrentEnd,
                        onCheckedChange = onSetWaitForCurrentEnd,
                    )
                    SettingsDivider()
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_auto_timer_duration),
                        value = durationLabel(settings.timerDurationMs),
                        onClick = onTimerDurationClick,
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_library)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_library),
                        value = if (folderCount == 0) {
                            stringResource(R.string.settings_no_folders)
                        } else {
                            stringResource(R.string.settings_folder_count, folderCount)
                        },
                        onClick = onOpenLibrary,
                    )
                    SettingsDivider()
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_recycle_bin),
                        value = if (recycleBinCount == 0) {
                            stringResource(R.string.settings_recycle_bin_empty)
                        } else {
                            stringResource(R.string.settings_recycle_bin_count, recycleBinCount)
                        },
                        onClick = onOpenRecycleBin,
                    )
                }
            }
        }
    }
}

@Composable
private fun FadeDurationRow(
    durationMs: Long,
    enabled: Boolean,
    onDurationChange: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_fade_duration),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_milliseconds, durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = durationMs.toFloat(),
            onValueChange = { rawValue ->
                val stepped = (rawValue.toLong() / FADE_DURATION_STEP_MS) * FADE_DURATION_STEP_MS
                onDurationChange(
                    stepped.coerceIn(MIN_FADE_DURATION_MS, MAX_FADE_DURATION_MS),
                )
            },
            valueRange = MIN_FADE_DURATION_MS.toFloat()..MAX_FADE_DURATION_MS.toFloat(),
            steps = ((MAX_FADE_DURATION_MS - MIN_FADE_DURATION_MS) / FADE_DURATION_STEP_MS - 1).toInt(),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

@Composable
private fun headerLabel(mode: HomeHeaderMode): String = when (mode) {
    HomeHeaderMode.HIDDEN -> stringResource(R.string.header_hidden)
    HomeHeaderMode.AUTO -> stringResource(R.string.header_auto)
    else -> stringResource(R.string.header_fixed)
}
