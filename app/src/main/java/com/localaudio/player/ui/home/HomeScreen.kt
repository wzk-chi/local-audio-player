package com.localaudio.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.app.HomeRow
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.ui.util.formatTime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    location: FolderLocation?,
    rows: List<HomeRow>,
    playingKey: String?,
    hasLibrary: Boolean,
    headerMode: HomeHeaderMode,
    onBack: () -> Unit,
    onLocateCurrent: () -> Unit,
    onDirectoryClick: (FolderLocation) -> Unit,
    onAudioClick: (AudioItem) -> Unit,
    onAddFolder: () -> Unit,
) {
    val listState = rememberLazyListState()
    var headerVisible by remember { mutableStateOf(headerMode != HomeHeaderMode.HIDDEN) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var locateRequest by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(locateRequest, rows, playingKey) {
        if (locateRequest == 0 || playingKey == null) return@LaunchedEffect
        val index = rows.indexOfFirst { row ->
            row is HomeRow.Audio && row.item.key == playingKey
        }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (headerVisible) {
            HomeHeader(
                location = location,
                playingKey = playingKey,
                onBack = onBack,
                onLocateCurrent = {
                    locateRequest += 1
                    onLocateCurrent()
                },
            )
        }
        if (rows.isEmpty()) {
            EmptyHome(hasLibrary = hasLibrary, onAddFolder = onAddFolder)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rows, key = { rowKey(it) }) { row ->
                        when (row) {
                            is HomeRow.Directory -> DirectoryRow(row.location, onDirectoryClick)
                            is HomeRow.Audio -> AudioRow(row.item, playingKey, onAudioClick)
                        }
                    }
                }
                HomeScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 12.dp, bottom = 12.dp)
                        .width(16.dp)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun HomeScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemSpacingPx = with(density) { 6.dp.toPx() }
    val metrics by remember(listState, itemSpacingPx) {
        derivedStateOf { calculateScrollbarMetrics(listState.layoutInfo, itemSpacingPx) }
    }
    val latestMetrics by rememberUpdatedState(metrics)
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val thumbColor = MaterialTheme.colorScheme.primary
    val draggableState = rememberDraggableState { delta ->
        val currentMetrics = latestMetrics
        val thumbTravelPx = trackHeightPx * (1f - (currentMetrics?.thumbFraction ?: 1f))
        if (currentMetrics != null && thumbTravelPx > 0f) {
            coroutineScope.launch {
                listState.scrollBy(delta * currentMetrics.scrollRangePx / thumbTravelPx)
            }
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                enabled = metrics != null,
            ),
    ) {
        val currentMetrics = metrics ?: return@Canvas
        val thumbHeight = (size.height * currentMetrics.thumbFraction).coerceAtLeast(1f)
        val thumbOffset = (size.height - thumbHeight) * currentMetrics.scrollFraction
        val trackWidth = 4.dp.toPx().coerceAtMost(size.width)
        val thumbWidth = 8.dp.toPx().coerceAtMost(size.width)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset((size.width - trackWidth) / 2f, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2f),
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset((size.width - thumbWidth) / 2f, thumbOffset),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f),
        )
    }
}

private data class ScrollbarMetrics(
    val thumbFraction: Float,
    val scrollFraction: Float,
    val scrollRangePx: Float,
)

private fun calculateScrollbarMetrics(
    layoutInfo: LazyListLayoutInfo,
    itemSpacingPx: Float,
): ScrollbarMetrics? {
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    if (layoutInfo.totalItemsCount == 0 || visibleItems.isEmpty() || viewportHeight <= 0f) return null

    val averageItemHeight = visibleItems.map { it.size }.average().toFloat()
    val averageItemExtent = averageItemHeight + itemSpacingPx
    val estimatedContentHeight = averageItemExtent * layoutInfo.totalItemsCount
    val scrollRangePx = (estimatedContentHeight - viewportHeight).coerceAtLeast(0f)
    if (scrollRangePx <= 0f) return null

    val firstVisibleItem = visibleItems.first()
    val currentScrollPx = (
        firstVisibleItem.index * averageItemExtent + (-firstVisibleItem.offset).coerceAtLeast(0)
    ).coerceIn(0f, scrollRangePx)

    return ScrollbarMetrics(
        thumbFraction = (viewportHeight / estimatedContentHeight).coerceIn(0.08f, 1f),
        scrollFraction = (currentScrollPx / scrollRangePx).coerceIn(0f, 1f),
        scrollRangePx = scrollRangePx,
    )
}

@Composable
private fun HomeHeader(
    location: FolderLocation?,
    playingKey: String?,
    onBack: () -> Unit,
    onLocateCurrent: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (location != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "返回上一层")
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
            Text(
                text = location?.name ?: "首页",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onLocateCurrent,
                enabled = playingKey != null,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_my_location),
                    contentDescription = "定位当前播放",
                )
            }
        }
    }
}

@Composable
private fun DirectoryRow(location: FolderLocation, onClick: (FolderLocation) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onClick(location) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(location.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("文件夹", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AudioRow(item: AudioItem, playingKey: String?, onClick: (AudioItem) -> Unit) {
    val active = item.key == playingKey
    val titleColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val durationColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick(item) },
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        tonalElevation = if (active) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = durationColor,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(formatTime(item.durationMs), style = MaterialTheme.typography.labelMedium, color = durationColor)
        }
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
