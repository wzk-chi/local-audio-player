package com.localaudio.player.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.app.HomeRow
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.ui.util.formatTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    location: FolderLocation?,
    rows: List<HomeRow>,
    playingKey: String?,
    hasLibrary: Boolean,
    headerMode: HomeHeaderMode,
    onBack: () -> Unit,
    onDirectoryClick: (FolderLocation) -> Unit,
    onAudioClick: (AudioItem) -> Unit,
    onAddFolder: () -> Unit,
) {
    val listState = rememberLazyListState()
    val textMeasurer = rememberTextMeasurer()
    var headerVisible by remember { mutableStateOf(headerMode != HomeHeaderMode.HIDDEN) }
    var previousIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(headerMode, listState) {
        headerVisible = headerMode != HomeHeaderMode.HIDDEN
        previousIndex = listState.firstVisibleItemIndex
        if (headerMode == HomeHeaderMode.AUTO) {
            snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { index ->
                headerVisible = index <= previousIndex
                previousIndex = index
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (headerVisible) HomeHeader(location = location, onBack = onBack)
        if (rows.isEmpty()) {
            EmptyHome(hasLibrary = hasLibrary, onAddFolder = onAddFolder)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(rows, key = { rowKey(it) }) { row ->
                    when (row) {
                        is HomeRow.Directory -> DirectoryRow(row.location, onDirectoryClick)
                        is HomeRow.Audio -> AudioRow(row.item, playingKey, textMeasurer, onAudioClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(location: FolderLocation?, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (location != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "返回上一层")
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = location?.name ?: "首页",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DirectoryRow(location: FolderLocation, onClick: (FolderLocation) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick(location) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(location.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioRow(item: AudioItem, playingKey: String?, textMeasurer: TextMeasurer, onClick: (AudioItem) -> Unit) {
    val active = item.key == playingKey
    val titleStyle = MaterialTheme.typography.bodyLarge
    val titleColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val durationColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    val density = LocalDensity.current
                    val maxWidthPx = with(density) { maxWidth.roundToPx() }
                    val measured = textMeasurer.measure(
                        text = AnnotatedString(item.title),
                        style = titleStyle,
                        constraints = Constraints(maxWidth = maxWidthPx),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    val firstEnd = measured.getLineEnd(0, visibleEnd = true).coerceIn(0, item.title.length)
                    val firstLine = item.title.substring(0, firstEnd).trimEnd()
                    val secondLine = item.title.substring(firstEnd).trimStart('\n', '\r')

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = firstLine,
                            modifier = Modifier.fillMaxWidth(),
                            color = titleColor,
                            style = titleStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = secondLine,
                                modifier = Modifier.weight(1f),
                                color = titleColor,
                                style = titleStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_schedule),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = durationColor,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(formatTime(item.durationMs), style = MaterialTheme.typography.labelMedium, color = durationColor)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().clickable { onClick(item) },
            colors = ListItemDefaults.colors(
                containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                headlineColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                supportingColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EmptyHome(hasLibrary: Boolean, onAddFolder: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (hasLibrary) "此文件夹为空" else "还没有音乐文件夹", style = MaterialTheme.typography.titleMedium)
            if (!hasLibrary) Button(onClick = onAddFolder) { Text("添加文件夹") }
        }
    }
}

private fun rowKey(row: HomeRow): String = when (row) {
    is HomeRow.Directory -> "directory:${row.location.folderUri}:${row.location.relativePath}"
    is HomeRow.Audio -> "audio:${row.item.key}"
}
