package com.localaudio.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import com.localaudio.player.R
import com.localaudio.player.data.model.FolderItem
import com.localaudio.player.data.model.ScanState
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.ui.components.SettingCard
import com.localaudio.player.ui.components.SettingItemCard
import com.localaudio.player.ui.util.durationLabel

@Composable
fun SettingsScreen(
    settings: AppSettings,
    folders: List<FolderItem>,
    scanStates: Map<String, ScanState>,
    onThemeClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onSetShowWhenLocked: (Boolean) -> Unit,
    onSetTimerEnabled: (Boolean) -> Unit,
    onSetWaitForCurrentEnd: (Boolean) -> Unit,
    onSetSeekStep: (Long) -> Unit,
    onAddDuration: () -> Unit,
    onDeleteTimerDuration: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onRescanFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescanAll: () -> Unit,
) {
    var seekText by remember(settings.seekStepMs) { mutableStateOf((settings.seekStepMs / 1000L).toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        SettingCard("外观") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onThemeClick),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("主题模式") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(themeLabel(settings.themeMode), color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onHeaderClick),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_home),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("首页顶栏") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(headerLabel(settings.homeHeaderMode), color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_schedule),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("锁屏上方显示") },
                    trailingContent = {
                        Switch(checked = settings.showWhenLocked, onCheckedChange = onSetShowWhenLocked)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        SettingCard("播放") {
            SettingItemCard(R.drawable.ic_forward) {
                Text(
                    "快进 / 快退跨度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = seekText,
                        onValueChange = { seekText = it.filter(Char::isDigit) },
                        singleLine = true,
                        label = { Text("秒数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { seekText.toLongOrNull()?.takeIf { it > 0 }?.let { onSetSeekStep(it * 1000L) } },
                        modifier = Modifier.height(56.dp),
                    ) { Text("保存") }
                }
            }
        }

        SettingCard("定时暂停") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_timer),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("自动定时") },
                    trailingContent = {
                        Switch(checked = settings.timerEnabled, onCheckedChange = onSetTimerEnabled)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_pause),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("播放完当前音频后暂停") },
                    trailingContent = {
                        Switch(checked = settings.waitForCurrentEnd, onCheckedChange = onSetWaitForCurrentEnd)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                "定时长度",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_timer),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        settings.timerDurationOptionsMs.forEach { duration ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                ListItem(
                                    headlineContent = { Text(durationLabel(duration)) },
                                    trailingContent = {
                                        IconButton(
                                            onClick = { onDeleteTimerDuration(duration) },
                                            enabled = settings.timerDurationOptionsMs.size > 1,
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onAddDuration,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加定时长度")
                        }
                    }
                }
            }
        }

        SettingCard("音乐库") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
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
                            onRemove = onRemoveFolder,
                        )
                    }
                    if (folders.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onRescanAll, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("重新扫描")
                        }
                        FilledTonalButton(onClick = onAddFolder, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加文件夹")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FolderSettingRow(
    folder: FolderItem,
    state: ScanState,
    onRescan: (String) -> Unit,
    onRemove: (String) -> Unit,
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
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Surface(
                color = statusColor.copy(alpha = 0.14f),
                contentColor = statusColor,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    folderStatus(state),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        leadingContent = {
            Icon(Icons.Filled.Folder, contentDescription = null)
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onRescan(folder.uri) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重新扫描 ${folder.displayName}")
                }
                IconButton(onClick = { onRemove(folder.uri) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "移除 ${folder.displayName}")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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

private fun folderStatus(state: ScanState): String = when (state) {
    ScanState.Idle -> "未扫描"
    is ScanState.Scanning -> "扫描中：已扫 ${state.scanned}，发现 ${state.found}"
    is ScanState.Done -> "共 ${state.audioCount} 首音频"
    is ScanState.Failed -> "扫描失败：${state.reason}"
}
