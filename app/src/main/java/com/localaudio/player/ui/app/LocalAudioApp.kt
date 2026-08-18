package com.localaudio.player.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.localaudio.player.app.AppDialog
import com.localaudio.player.app.AppEvent
import com.localaudio.player.app.AppScreen
import com.localaudio.player.app.AppUiState
import com.localaudio.player.app.SettingChange
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.ui.dialog.AppDialogs
import com.localaudio.player.ui.home.HomeScreen
import com.localaudio.player.ui.player.PlaybackBar
import com.localaudio.player.ui.player.PlayerScreen
import com.localaudio.player.ui.settings.LibrarySettingsScreen
import com.localaudio.player.ui.settings.SettingsScreen

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

    Surface(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                HomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (state.screen == AppScreen.HOME) 1f else 0f),
                    visible = state.screen == AppScreen.HOME,
                    location = state.homeLocation,
                    rows = state.homeRows,
                    playingKey = state.playback.currentItem?.key,
                    hasLibrary = state.hasLibrary,
                    headerMode = state.settings.homeHeaderMode,
                    onBack = { onEvent(AppEvent.Back) },
                    onLocateCurrent = { onEvent(AppEvent.LocateCurrent) },
                    onDirectoryClick = { onEvent(AppEvent.OpenDirectory(it)) },
                    onAudioClick = { onEvent(AppEvent.PlayAudio(it)) },
                    onAddFolder = { onEvent(AppEvent.AddFolder) },
                )
                PlayerScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (state.screen == AppScreen.PLAYER) 1f else 0f),
                    visible = state.screen == AppScreen.PLAYER,
                    state = state.playback,
                    seekStepMs = state.settings.seekStepMs,
                    onPlayPause = togglePlayback,
                    onNext = { dispatchPlayback(PlaybackCommand.Next) },
                    onPrevious = { dispatchPlayback(PlaybackCommand.Previous) },
                    onSeekBy = { dispatchPlayback(PlaybackCommand.SeekBy(it)) },
                    onSeekTo = { dispatchPlayback(PlaybackCommand.SeekTo(it)) },
                    onOpenQueue = { onEvent(AppEvent.ShowDialog(AppDialog.Queue)) },
                    onOpenTimer = { onEvent(AppEvent.ShowDialog(AppDialog.Timer)) },
                    onOpenMode = { onEvent(AppEvent.ShowDialog(AppDialog.Mode)) },
                )

                if (state.screen == AppScreen.SETTINGS) {
                    SettingsScreen(
                        settings = state.settings,
                        folderCount = state.library.folders.size,
                        onThemeClick = { onEvent(AppEvent.ShowDialog(AppDialog.Theme)) },
                        onHeaderClick = { onEvent(AppEvent.ShowDialog(AppDialog.Header)) },
                        onSetShowWhenLocked = { onEvent(AppEvent.UpdateSetting(SettingChange.SetShowWhenLocked(it))) },
                        onSetTimerEnabled = { onEvent(AppEvent.UpdateSetting(SettingChange.SetTimerEnabled(it))) },
                        onSetWaitForCurrentEnd = { onEvent(AppEvent.UpdateSetting(SettingChange.SetWaitForCurrentEnd(it))) },
                        onSeekStepClick = { onEvent(AppEvent.ShowDialog(AppDialog.SeekStep)) },
                        onTimerDurationClick = { onEvent(AppEvent.ShowDialog(AppDialog.TimerDuration)) },
                        onOpenLibrary = { onEvent(AppEvent.SelectScreen(AppScreen.LIBRARY_SETTINGS)) },
                    )
                } else if (state.screen == AppScreen.LIBRARY_SETTINGS) {
                    LibrarySettingsScreen(
                        folders = state.library.folders,
                        scanStates = state.library.scanStates,
                        onBack = { onEvent(AppEvent.Back) },
                        onAddFolder = { onEvent(AppEvent.AddFolder) },
                        onRescanFolder = { onEvent(AppEvent.RescanFolder(it)) },
                        onRemoveFolder = { onEvent(AppEvent.RemoveFolder(it)) },
                        onRescanAll = { onEvent(AppEvent.RescanAll) },
                    )
                }
            }

            val isLibrarySettings = state.screen == AppScreen.LIBRARY_SETTINGS
            if (state.screen != AppScreen.PLAYER && !isLibrarySettings) {
                PlaybackBar(
                    state = state.playback,
                    onPlayPause = togglePlayback,
                    onNext = { dispatchPlayback(PlaybackCommand.Next) },
                    onPrevious = { dispatchPlayback(PlaybackCommand.Previous) },
                    onOpenPlayer = { onEvent(AppEvent.SelectScreen(AppScreen.PLAYER)) },
                )
            }
            if (!isLibrarySettings) {
                BottomNavigation(state.screen) { onEvent(AppEvent.SelectScreen(it)) }
            }
        }

        AppDialogs(state = state, onEvent = onEvent)
    }
}

@Composable
private fun BottomNavigation(screen: AppScreen, onScreenSelected: (AppScreen) -> Unit) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    NavigationBar(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(MaterialTheme.shapes.large),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = screen == AppScreen.HOME,
            onClick = { onScreenSelected(AppScreen.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
            label = { Text("首页") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = screen == AppScreen.PLAYER,
            onClick = { onScreenSelected(AppScreen.PLAYER) },
            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "播放") },
            label = { Text("播放") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = screen == AppScreen.SETTINGS || screen == AppScreen.LIBRARY_SETTINGS,
            onClick = { onScreenSelected(AppScreen.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
            label = { Text("设置") },
            colors = itemColors,
        )
    }
}
