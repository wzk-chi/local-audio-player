package com.localaudio.player.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.app.AppDialog
import com.localaudio.player.app.AppEvent
import com.localaudio.player.app.AppUiState
import com.localaudio.player.app.SettingChange
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.REPEAT_ALL
import com.localaudio.player.data.settings.REPEAT_ONE
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.components.SwitchRow
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
            onSelect = { repeat, shuffle ->
                onEvent(
                    AppEvent.Playback(
                        PlaybackCommand.SetPlayMode(repeat, shuffle),
                    ),
                )
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.Timer -> TimerDialog(
            state = state.playback,
            settings = state.settings,
            onSetEnabled = { onEvent(AppEvent.UpdateSetting(SettingChange.SetTimerEnabled(it))) },
            onSetWaitForEnd = { onEvent(AppEvent.UpdateSetting(SettingChange.SetWaitForCurrentEnd(it))) },
            onSelectDuration = { duration ->
                onEvent(AppEvent.UpdateSetting(SettingChange.SetTimerDuration(duration)))
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
            title = "主题模式",
            options = listOf("跟随系统", "浅色", "深色"),
            selected = when (state.settings.themeMode) { ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2; else -> 0 },
            onSelect = { index ->
                onEvent(
                    AppEvent.UpdateSetting(
                        SettingChange.SetThemeMode(if (index == 1) ThemeMode.LIGHT else if (index == 2) ThemeMode.DARK else ThemeMode.SYSTEM),
                    ),
                )
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.Header -> ChoiceDialog(
            title = "首页顶栏",
            options = listOf("固定", "隐藏", "自动隐藏"),
            selected = when (state.settings.homeHeaderMode) { HomeHeaderMode.HIDDEN -> 1; HomeHeaderMode.AUTO -> 2; else -> 0 },
            onSelect = { index ->
                onEvent(
                    AppEvent.UpdateSetting(
                        SettingChange.SetHomeHeaderMode(if (index == 1) HomeHeaderMode.HIDDEN else if (index == 2) HomeHeaderMode.AUTO else HomeHeaderMode.FIXED),
                    ),
                )
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        AppDialog.ClearFolders -> AlertDialog(
            onDismissRequest = { onEvent(AppEvent.DismissDialog) },
            title = { Text("清除所有文件夹？") },
            text = { Text("将移除全部文件夹并释放访问权限。") },
            dismissButton = { TextButton(onClick = { onEvent(AppEvent.DismissDialog) }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(AppEvent.ClearFolders)
                    onEvent(AppEvent.DismissDialog)
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
        )
        is AppDialog.EditDuration -> DurationDialog(
            existingMs = current.existingMs,
            onSave = { value ->
                if (current.existingMs == null) {
                    onEvent(AppEvent.UpdateSetting(SettingChange.AddTimerDuration(value)))
                } else {
                    onEvent(AppEvent.UpdateSetting(SettingChange.EditTimerDuration(current.existingMs, value)))
                }
                onEvent(AppEvent.DismissDialog)
            },
            onDismiss = { onEvent(AppEvent.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
private fun QueueDialog(state: PlaybackState, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("播放列表") }, text = {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(state.queue) { item ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(state.queue.indexOf(item)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.queue.indexOf(item) == state.currentIndex, onClick = { onSelect(state.queue.indexOf(item)) })
                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun ModeDialog(state: PlaybackState, onSelect: (Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    val labels = listOf("顺序", "单曲循环", "列表循环", "随机")
    val selected = when { state.shuffleEnabled -> 3; state.repeatMode == REPEAT_ONE -> 1; state.repeatMode == REPEAT_ALL -> 2; else -> 0 }
    ChoiceDialog(title = "播放顺序", options = labels, selected = selected, onSelect = { index -> onSelect(if (index == 1) REPEAT_ONE else if (index == 2) REPEAT_ALL else 0, index == 3) }, onDismiss = onDismiss)
}

@Composable
private fun TimerDialog(state: PlaybackState, settings: AppSettings, onSetEnabled: (Boolean) -> Unit, onSetWaitForEnd: (Boolean) -> Unit, onSelectDuration: (Long) -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("定时暂停") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SwitchRow("自动定时", "手动开始播放时开启，自动切歌不会触发", settings.timerEnabled, onSetEnabled, showDivider = true)
            SwitchRow("播放完当前音频后暂停", "定时到期后等待当前音频结束", settings.waitForCurrentEnd, onSetWaitForEnd, showDivider = false)
            Text("定时时长", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            settings.timerDurationOptionsMs.forEach { duration ->
                OutlinedButton(onClick = { onSelectDuration(duration) }, modifier = Modifier.fillMaxWidth()) { Text(if (duration == settings.timerDurationMs) "✓ ${durationLabel(duration)}" else durationLabel(duration)) }
            }
            if (state.timerActive) TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("关闭当前定时") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            options.forEachIndexed { index, option ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(index) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == index, onClick = { onSelect(index) })
                    Text(option)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun DurationDialog(existingMs: Long?, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember(existingMs) { mutableStateOf(existingMs?.let { (it / 60_000L).toString() } ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existingMs == null) "添加定时长度" else "编辑定时长度") }, text = {
        OutlinedTextField(value = text, onValueChange = { text = it.filter(Char::isDigit) }, singleLine = true, label = { Text("分钟") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { TextButton(onClick = { text.toLongOrNull()?.takeIf { it > 0 }?.let { onSave(it * 60_000L) } }) { Text("保存") } })
}
