package com.localaudio.player.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.localaudio.player.R
import com.localaudio.player.app.AppDialog
import com.localaudio.player.app.AppEvent
import com.localaudio.player.app.AppScreen
import com.localaudio.player.app.AppUiState
import com.localaudio.player.app.HomeActionTarget
import com.localaudio.player.app.HomeRow
import com.localaudio.player.app.SettingChange
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.dialog.AppDialogs
import com.localaudio.player.ui.home.HomeScreen
import com.localaudio.player.ui.player.PlaybackBar
import com.localaudio.player.ui.player.PlaybackProgressSlider
import com.localaudio.player.ui.player.PlayerScreen
import com.localaudio.player.ui.settings.LibrarySettingsScreen
import com.localaudio.player.ui.settings.AutoSkipScreen
import com.localaudio.player.ui.settings.RecycleBinScreen
import com.localaudio.player.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.distinctUntilChanged

private const val MAIN_PAGE_COUNT = 3

@Composable
fun LocalAudioApp(
    state: AppUiState,
    onEvent: (AppEvent) -> Unit,
) {
    val dispatchPlayback: (PlaybackCommand) -> Unit = { command ->
        onEvent(AppEvent.Playback(command))
    }
    val togglePlayback = {
        dispatchPlayback(if (state.playback.isPlaying) PlaybackCommand.Pause else PlaybackCommand.Play)
    }
    val isSecondarySettings = state.screen == AppScreen.LIBRARY_SETTINGS ||
        state.screen == AppScreen.AUTO_SKIP_SETTINGS ||
        state.screen == AppScreen.RECYCLE_BIN
    val pagerState = rememberPagerState(
        initialPage = mainPageFor(state.screen),
        pageCount = { MAIN_PAGE_COUNT },
    )
    val latestScreen by rememberUpdatedState(state.screen)

    LaunchedEffect(state.screen, pagerState) {
        if (!isSecondarySettings) {
            val targetPage = mainPageFor(state.screen)
            if (pagerState.currentPage != targetPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val screen = screenForMainPage(page)
                if (latestScreen != screen) onEvent(AppEvent.SelectScreen(screen))
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (isSecondarySettings) {
                    when (state.screen) {
                        AppScreen.LIBRARY_SETTINGS -> LibrarySettingsScreen(
                            folders = state.library.folders,
                            scanStates = state.library.scanStates,
                            onBack = { onEvent(AppEvent.Back) },
                            onAddFolder = { onEvent(AppEvent.AddFolder) },
                            onRescanFolder = { onEvent(AppEvent.RescanFolder(it)) },
                            onRemoveFolder = { onEvent(AppEvent.RemoveFolder(it)) },
                            onRescanAll = { onEvent(AppEvent.RescanAll) },
                        )
                        AppScreen.AUTO_SKIP_SETTINGS -> AutoSkipScreen(
                            segments = state.autoSkipSegments,
                            audioItems = state.library.items,
                            onBack = { onEvent(AppEvent.Back) },
                            onPlay = { onEvent(AppEvent.PlayAutoSkipAudio(it)) },
                            onEdit = { onEvent(AppEvent.EditAutoSkipSegment(it)) },
                            onDelete = { onEvent(AppEvent.DeleteAutoSkipSegment(it)) },
                        )
                        AppScreen.RECYCLE_BIN -> RecycleBinScreen(
                            state = state.recycleBin,
                            onBack = { onEvent(AppEvent.Back) },
                            onRestore = { onEvent(AppEvent.RestoreRecycle(it)) },
                            onClean = { onEvent(AppEvent.CleanRecycle(it)) },
                        )
                        else -> error("Unexpected secondary screen: ${state.screen}")
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (screenForMainPage(page)) {
                            AppScreen.HOME -> Column(modifier = Modifier.fillMaxSize()) {
                                HomeScreen(
                                    modifier = Modifier.weight(1f),
                                    location = state.homeLocation,
                                    rows = state.homeRows,
                                    playingKey = state.playback.currentItem?.key,
                                    headerMode = state.settings.homeHeaderMode,
                                    listBottomAligned = state.settings.homeListBottomAligned,
                                    onBack = { onEvent(AppEvent.Back) },
                                    onLocateCurrent = { onEvent(AppEvent.LocateCurrent) },
                                    onDirectoryClick = { onEvent(AppEvent.OpenDirectory(it)) },
                                    onAudioClick = { onEvent(AppEvent.PlayAudio(it)) },
                                    onRename = { row ->
                                        onEvent(
                                            AppEvent.ShowDialog(
                                                AppDialog.Rename(row.toHomeActionTarget()),
                                            ),
                                        )
                                    },
                                    onDelete = { row ->
                                        onEvent(
                                            AppEvent.ShowDialog(
                                                AppDialog.Delete(row.toHomeActionTarget()),
                                            ),
                                        )
                                    },
                                    onAddFolder = { onEvent(AppEvent.AddFolder) },
                                )
                                PlaybackControls(
                                    state = state.playback,
                                    onPlayPause = togglePlayback,
                                    onNext = { dispatchPlayback(PlaybackCommand.Next) },
                                    onPrevious = { dispatchPlayback(PlaybackCommand.Previous) },
                                    onOpenPlayer = { onEvent(AppEvent.SelectScreen(AppScreen.PLAYER)) },
                                    onSeekTo = { dispatchPlayback(PlaybackCommand.SeekTo(it)) },
                                )
                            }
                            AppScreen.PLAYER -> PlayerScreen(
                                modifier = Modifier.fillMaxSize(),
                                state = state.playback,
                                seekStepMs = state.settings.seekStepMs,
                                showAlbumCover = state.settings.showAlbumCover,
                                onPlayPause = togglePlayback,
                                onNext = { dispatchPlayback(PlaybackCommand.Next) },
                                onPrevious = { dispatchPlayback(PlaybackCommand.Previous) },
                                onSeekBy = { dispatchPlayback(PlaybackCommand.SeekBy(it)) },
                                onSeekTo = { dispatchPlayback(PlaybackCommand.SeekTo(it)) },
                                onOpenQueue = { onEvent(AppEvent.ShowDialog(AppDialog.Queue)) },
                                onOpenTimer = { onEvent(AppEvent.ShowDialog(AppDialog.Timer)) },
                                onOpenMode = { onEvent(AppEvent.ShowDialog(AppDialog.Mode)) },
                                onOpenDirectorySkip = {
                                    state.playback.currentItem?.let { item ->
                                        onEvent(
                                            AppEvent.ShowDialog(
                                                AppDialog.DirectorySkip(item.folderUri, item.relativePath),
                                            ),
                                        )
                                    }
                                },
                                isAutoSkipMarking = state.activeAutoSkipMark != null,
                                onStartAutoSkipMark = { onEvent(AppEvent.StartAutoSkipMark) },
                                onFinishAutoSkipMark = { onEvent(AppEvent.FinishAutoSkipMark) },
                                onCancelAutoSkipMark = { onEvent(AppEvent.CancelAutoSkipMark) },
                            )
                            AppScreen.SETTINGS -> Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SettingsScreen(
                                        settings = state.settings,
                                        folderCount = state.library.folders.size,
                                        recycleBinCount = state.recycleBin.entryCount,
                                        onThemeClick = { onEvent(AppEvent.ShowDialog(AppDialog.Theme)) },
                                        onHeaderClick = { onEvent(AppEvent.ShowDialog(AppDialog.Header)) },
                                        onSetHomeListBottomAligned = {
                                            onEvent(AppEvent.UpdateSetting(SettingChange.SetHomeListBottomAligned(it)))
                                        },
                                        onSetShowAlbumCover = {
                                            onEvent(AppEvent.UpdateSetting(SettingChange.SetShowAlbumCover(it)))
                                        },
                                        onSetShowWhenLocked = {
                                            onEvent(AppEvent.UpdateSetting(SettingChange.SetShowWhenLocked(it)))
                                        },
                                        onSetFadeEnabled = {
                                            onEvent(AppEvent.UpdateSetting(SettingChange.SetFadeEnabled(it)))
                                        },
                                        onSetFadeDuration = {
                                            onEvent(AppEvent.UpdateSetting(SettingChange.SetFadeDuration(it)))
                                        },
                                        onSetTimerEnabled = {
                                            onEvent(AppEvent.UpdateSetting(SettingChange.SetTimerEnabled(it)))
                                        },
                                        onSetWaitForCurrentEnd = {
                                            onEvent(AppEvent.UpdateSetting(SettingChange.SetWaitForCurrentEnd(it)))
                                        },
                                        onSeekStepClick = { onEvent(AppEvent.ShowDialog(AppDialog.SeekStep)) },
                                        onTimerDurationClick = { onEvent(AppEvent.ShowDialog(AppDialog.TimerDuration)) },
                                        autoSkipCount = state.autoSkipSegments.size,
                                        onOpenAutoSkip = { onEvent(AppEvent.OpenAutoSkipSettings) },
                                        onOpenLibrary = { onEvent(AppEvent.SelectScreen(AppScreen.LIBRARY_SETTINGS)) },
                                        onOpenRecycleBin = { onEvent(AppEvent.OpenRecycleBin) },
                                    )
                                }
                                PlaybackControls(
                                    state = state.playback,
                                    onPlayPause = togglePlayback,
                                    onNext = { dispatchPlayback(PlaybackCommand.Next) },
                                    onPrevious = { dispatchPlayback(PlaybackCommand.Previous) },
                                    onOpenPlayer = { onEvent(AppEvent.SelectScreen(AppScreen.PLAYER)) },
                                    onSeekTo = { dispatchPlayback(PlaybackCommand.SeekTo(it)) },
                                )
                            }
                            AppScreen.LIBRARY_SETTINGS, AppScreen.AUTO_SKIP_SETTINGS -> {
                                error("Secondary settings is outside the main pager")
                            }
                            AppScreen.RECYCLE_BIN -> error("Recycle bin is outside the main pager")
                        }
                    }
                }
            }

            val pagerScreen = if (isSecondarySettings) state.screen else screenForMainPage(pagerState.currentPage)
            if (!isSecondarySettings) {
                BottomNavigation(pagerScreen) { onEvent(AppEvent.SelectScreen(it)) }
            }
        }

        AppDialogs(state = state, onEvent = onEvent)
    }
}

@Composable
private fun PlaybackControls(
    state: PlaybackState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    PlaybackBar(
        state = state,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onOpenPlayer = onOpenPlayer,
    )
    PlaybackProgressSlider(
        state = state,
        onSeekTo = onSeekTo,
    )
}

@Composable
private fun BottomNavigation(screen: AppScreen, onScreenSelected: (AppScreen) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = screen == AppScreen.HOME,
            onClick = { onScreenSelected(AppScreen.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.nav_home)) },
            label = { Text(stringResource(R.string.nav_home)) },
        )
        NavigationBarItem(
            selected = screen == AppScreen.PLAYER,
            onClick = { onScreenSelected(AppScreen.PLAYER) },
            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.nav_player)) },
            label = { Text(stringResource(R.string.nav_player)) },
        )
        NavigationBarItem(
            selected = screen == AppScreen.SETTINGS || screen == AppScreen.LIBRARY_SETTINGS,
            onClick = { onScreenSelected(AppScreen.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}

private fun mainPageFor(screen: AppScreen): Int = when (screen) {
    AppScreen.HOME -> 0
    AppScreen.PLAYER -> 1
    AppScreen.SETTINGS, AppScreen.LIBRARY_SETTINGS, AppScreen.AUTO_SKIP_SETTINGS, AppScreen.RECYCLE_BIN -> 2
}

private fun screenForMainPage(page: Int): AppScreen = when (page) {
    0 -> AppScreen.HOME
    1 -> AppScreen.PLAYER
    else -> AppScreen.SETTINGS
}

private fun HomeRow.toHomeActionTarget(): HomeActionTarget = when (this) {
    is HomeRow.Audio -> HomeActionTarget.Audio(item)
    is HomeRow.Directory -> HomeActionTarget.Directory(location)
}
