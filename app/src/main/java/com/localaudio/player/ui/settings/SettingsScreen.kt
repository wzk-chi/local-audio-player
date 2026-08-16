package com.localaudio.player.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.ui.util.durationLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    folderCount: Int,
    onThemeClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onSetShowWhenLocked: (Boolean) -> Unit,
    onSetTimerEnabled: (Boolean) -> Unit,
    onSetWaitForCurrentEnd: (Boolean) -> Unit,
    onSeekStepClick: () -> Unit,
    onTimerDurationClick: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsSection("外观") {
                    SettingsChoiceRow(
                        title = "主题模式",
                        value = themeLabel(settings.themeMode),
                        onClick = onThemeClick,
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        title = "首页顶栏",
                        value = headerLabel(settings.homeHeaderMode),
                        onClick = onHeaderClick,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "锁屏上方显示",
                        checked = settings.showWhenLocked,
                        onCheckedChange = onSetShowWhenLocked,
                    )
                }
            }
            item {
                SettingsSection("播放") {
                    SettingsChoiceRow(
                        title = "快进 / 快退跨度",
                        value = "${settings.seekStepMs / 1000L} 秒",
                        onClick = onSeekStepClick,
                    )
                }
            }
            item {
                SettingsSection("定时暂停") {
                    SettingsSwitchRow(
                        title = "自动定时",
                        checked = settings.timerEnabled,
                        onCheckedChange = onSetTimerEnabled,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "播放完当前音频后暂停",
                        checked = settings.waitForCurrentEnd,
                        onCheckedChange = onSetWaitForCurrentEnd,
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        title = "定时长度",
                        value = durationLabel(settings.timerDurationMs),
                        onClick = onTimerDurationClick,
                    )
                }
            }
            item {
                SettingsSection("音乐库") {
                    SettingsChoiceRow(
                        title = "音乐库",
                        value = if (folderCount == 0) "尚未添加文件夹" else "$folderCount 个文件夹",
                        onClick = onOpenLibrary,
                    )
                }
            }
        }
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
private fun SettingsChoiceRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(title) },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    else -> "跟随系统"
}

private fun headerLabel(mode: HomeHeaderMode): String = when (mode) {
    HomeHeaderMode.HIDDEN -> "隐藏"
    HomeHeaderMode.AUTO -> "自动隐藏"
    else -> "固定"
}
