package com.localaudio.player.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
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
private fun QueueDialog(state: PlaybackState, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentIndex, state.queue.size) {
        if (state.currentIndex in state.queue.indices) {
            listState.scrollToItem(state.currentIndex)
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("播放列表") }, text = {
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
            SettingSwitchRow("自动定时", settings.timerEnabled, onSetEnabled, showDivider = true)
            SettingSwitchRow("播放完当前音频后暂停", settings.waitForCurrentEnd, onSetWaitForEnd)
            Text("定时时长", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            settings.timerDurationOptionsMs.forEach { duration ->
                ListItem(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectDuration(duration) },
                    leadingContent = {
                        RadioButton(
                            selected = state.timerActive && duration == state.activeTimerDurationMs,
                            onClick = { onSelectDuration(duration) },
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
                TextButton(onClick = onStop) { Text("取消本次定时") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            options.forEachIndexed { index, option ->
                ListItem(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                    leadingContent = {
                        RadioButton(
                            selected = selected == index,
                            onClick = { onSelect(index) },
                        )
                    },
                    headlineContent = { Text(option) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
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
        title = { Text("快进 / 快退跨度") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text("秒数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(
                enabled = valueSeconds != null && valueSeconds > 0L,
                onClick = { valueSeconds?.let { onSave(it * 1000L) } },
            ) {
                Text("保存")
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
        title = { Text("定时长度") },
        text = {
            Column {
                settings.timerDurationOptionsMs.forEach { duration ->
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(duration) },
                        leadingContent = {
                            RadioButton(
                                selected = duration == settings.timerDurationMs,
                                onClick = { onSelect(duration) },
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
                                    contentDescription = "删除",
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
                    Text("添加自定义时长")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun AddDurationDialog(onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val durationMs = text.toLongOrNull()
        ?.takeIf { it in 1L..(Long.MAX_VALUE / 60_000L) }
        ?.times(60_000L)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加定时长度") }, text = {
        OutlinedTextField(value = text, onValueChange = { text = it.filter(Char::isDigit) }, singleLine = true, label = { Text("分钟") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { TextButton(enabled = durationMs != null, onClick = { durationMs?.let(onSave) }) { Text("保存") } })
}
