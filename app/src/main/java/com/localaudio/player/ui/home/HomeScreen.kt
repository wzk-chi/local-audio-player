package com.localaudio.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.app.HomeRow
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.ui.util.formatTime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HomeScreen(
    modifier: Modifier = Modifier,
    location: FolderLocation?,
    rows: List<HomeRow>,
    playingKey: String?,
    headerMode: HomeHeaderMode,
    listBottomAligned: Boolean,
    onBack: () -> Unit,
    onLocateCurrent: () -> Unit,
    onDirectoryClick: (FolderLocation) -> Unit,
    onAudioClick: (AudioItem) -> Unit,
    onRename: (HomeRow) -> Unit,
    onDelete: (HomeRow) -> Unit,
    onAddFolder: () -> Unit,
) {
    val listState = rememberLazyListState()
    var headerVisible by remember { mutableStateOf(headerMode != HomeHeaderMode.HIDDEN) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var locateRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(headerMode) {
        headerVisible = headerMode != HomeHeaderMode.HIDDEN
        previousIndex = listState.firstVisibleItemIndex
        if (headerMode == HomeHeaderMode.AUTO) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                headerVisible = index <= previousIndex
                previousIndex = index
            }
        }
    }

    val playingRowIndex = remember(rows, playingKey) {
        rows.indexOfFirst { row ->
            row is HomeRow.Audio && row.item.key == playingKey
        }
    }
    LaunchedEffect(locateRequest, playingKey, playingRowIndex, listBottomAligned) {
        if (locateRequest == 0 || playingKey == null || playingRowIndex < 0) {
            return@LaunchedEffect
        }
        listState.animateScrollToItem(playingRowIndex)
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
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
            EmptyHome(inDirectory = location != null, onAddFolder = onAddFolder)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        space = 6.dp,
                        alignment = if (listBottomAligned) Alignment.Bottom else Alignment.Top,
                    ),
                ) {
                    items(rows, key = { rowKey(it) }) { row ->
                        when (row) {
                            is HomeRow.Directory -> HomeRowWithActions(
                                row = row,
                                onRename = onRename,
                                onDelete = onDelete,
                            ) { onLongClick ->
                                DirectoryRow(row.location, onDirectoryClick, onLongClick)
                            }
                            is HomeRow.Audio -> HomeRowWithActions(
                                row = row,
                                onRename = onRename,
                                onDelete = onDelete,
                            ) { onLongClick ->
                                AudioRow(row.item, playingKey, onAudioClick, onLongClick)
                            }
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
private fun HomeRowWithActions(
    row: HomeRow,
    onRename: (HomeRow) -> Unit,
    onDelete: (HomeRow) -> Unit,
    content: @Composable (onLongClick: () -> Unit) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        content { menuExpanded = true }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    menuExpanded = false
                    onRename(row)
                },
                leadingIcon = {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuExpanded = false
                    onDelete(row)
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
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

    var totalItemHeight = 0.0
    visibleItems.forEach { item -> totalItemHeight += item.size }
    val averageItemHeight = (totalItemHeight / visibleItems.size).toFloat()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeHeader(
    location: FolderLocation?,
    playingKey: String?,
    onBack: () -> Unit,
    onLocateCurrent: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = location?.name ?: stringResource(R.string.home_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (location != null) {
                IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_back))
                }
            }
        },
        actions = {
            IconButton(
                onClick = onLocateCurrent,
                enabled = playingKey != null,
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.home_locate_current))
            }
        },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DirectoryRow(
    location: FolderLocation,
    onClick: (FolderLocation) -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = { onClick(location) }, onLongClick = onLongClick),
        headlineContent = { Text(location.name) },
        supportingContent = { Text(stringResource(R.string.home_folder)) },
        leadingContent = {
            Icon(Icons.Filled.Folder, contentDescription = null)
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AudioRow(
    item: AudioItem,
    playingKey: String?,
    onClick: (AudioItem) -> Unit,
    onLongClick: () -> Unit,
) {
    val active = item.key == playingKey
    val titleColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val durationColor = if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = { onClick(item) }, onLongClick = onLongClick),
        headlineContent = {
            Text(
                text = item.title,
                color = titleColor,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = durationColor,
                )
                Text(
                    formatTime(item.durationMs),
                    modifier = Modifier.padding(start = 4.dp),
                    color = durationColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
    )
}

@Composable
private fun EmptyHome(inDirectory: Boolean, onAddFolder: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(if (inDirectory) R.string.home_empty_folder else R.string.home_empty_library),
                style = MaterialTheme.typography.titleMedium,
            )
            if (!inDirectory) Button(onClick = onAddFolder) { Text(stringResource(R.string.home_add_folder)) }
        }
    }
}

private fun rowKey(row: HomeRow): String = when (row) {
    is HomeRow.Directory -> "directory:${row.location.folderUri}:${row.location.relativePath}"
    is HomeRow.Audio -> "audio:${row.item.key}"
}
