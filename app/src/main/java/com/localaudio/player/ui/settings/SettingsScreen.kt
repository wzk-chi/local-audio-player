package com.localaudio.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.data.model.FolderItem
import com.localaudio.player.data.model.ScanState
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.ui.components.ChoiceRow
import com.localaudio.player.ui.components.DurationSettingRow
import com.localaudio.player.ui.components.SettingCard
import com.localaudio.player.ui.components.SwitchRow

@Composable
fun SettingsScreen(
    settings: AppSettings,
    folders: List<FolderItem>,
    scanStates: Map<String, ScanState>,
    onThemeClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onSetShowWhenLocked: (Boolean) -> Unit,
    onSetShowStaticArtwork: (Boolean) -> Unit,
    onSetTimerEnabled: (Boolean) -> Unit,
    onSetWaitForCurrentEnd: (Boolean) -> Unit,
    onSetSeekStep: (Long) -> Unit,
    onEditDuration: (Long?) -> Unit,
    onDeleteTimerDuration: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onRescanFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescanAll: () -> Unit,
    onClearFolders: () -> Unit,
) {
    var seekText by remember(settings.seekStepMs) { mutableStateOf((settings.seekStepMs / 1000L).toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "管理外观、播放和音乐库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SettingCard("外观", "界面显示与首页行为") {
            ChoiceRow("主题模式", themeLabel(settings.themeMode), "跟随系统、浅色或深色", onThemeClick, showDivider = true)
            ChoiceRow("首页顶栏", headerLabel(settings.homeHeaderMode), "固定、隐藏或随滚动自动隐藏", onHeaderClick, showDivider = false)
            SwitchRow(
                title = "锁屏上方显示",
                subtitle = "锁屏时保持 App 界面可见并可操作",
                checked = settings.showWhenLocked,
                onCheckedChange = onSetShowWhenLocked,
                showDivider = true,
            )
        }

        SettingCard("播放", "控制播放方式和操作跨度") {
            SwitchRow("自动定时", "手动开始播放且当前没有定时时自动开启", settings.timerEnabled, onSetTimerEnabled, showDivider = true)
            SwitchRow("播放完当前音频后暂停", "定时到期后等待当前音频结束", settings.waitForCurrentEnd, onSetWaitForCurrentEnd, showDivider = true)
            SwitchRow(
                title = "播放页显示静态封面",
                subtitle = "显示播放页中的抽象封面",
                checked = settings.showStaticArtwork,
                onCheckedChange = onSetShowStaticArtwork,
                showDivider = false,
            )
            Text(
                "快进 / 快退跨度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
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

        SettingCard("定时暂停", "快速选择或管理定时选项") {
            settings.timerDurationOptionsMs.forEachIndexed { index, duration ->
                DurationSettingRow(
                    duration = duration,
                    onEdit = { onEditDuration(duration) },
                    onDelete = { onDeleteTimerDuration(duration) },
                    canDelete = settings.timerDurationOptionsMs.size > 1,
                    showDivider = index < settings.timerDurationOptionsMs.lastIndex,
                )
            }
            OutlinedButton(onClick = { onEditDuration(null) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("添加定时长度")
            }
        }

        SettingCard("音乐库", "管理文件夹和扫描状态") {
            FilledTonalButton(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) {
                Text("添加音乐文件夹")
            }
            if (folders.isEmpty()) {
                Text(
                    "还没有添加音乐文件夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    folders.forEach { folder ->
                        FolderSettingCard(
                            folder = folder,
                            state = scanStates[folder.uri] ?: ScanState.Idle,
                            onRescan = onRescanFolder,
                            onRemove = onRemoveFolder,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onRescanAll, modifier = Modifier.weight(1f)) { Text("重新扫描") }
                TextButton(onClick = onClearFolders, modifier = Modifier.weight(1f)) {
                    Text("清除文件夹", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FolderSettingCard(
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                folder.displayName,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                color = statusColor.copy(alpha = 0.14f),
                contentColor = statusColor,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    folderStatus(state),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onRescan(folder.uri) }) { Text("重扫") }
                TextButton(onClick = { onRemove(folder.uri) }) { Text("移除") }
            }
        }
    }
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
