package com.localaudio.player.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localaudio.player.data.library.LibraryRepository
import com.localaudio.player.data.library.LibraryState
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.SettingsRepository
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class AppViewModel(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackConnection: PlaybackConnection,
    private val homeRowsBuilder: HomeRowsBuilder,
) : ViewModel() {
    private val navigation = MutableStateFlow(
        NavigationState(homeLocation = settingsRepository.state.value.savedHomeLocation),
    )
    private val _effects = Channel<AppEffect>(Channel.BUFFERED)

    val effects: Flow<AppEffect> = _effects.receiveAsFlow()
    val settings: StateFlow<AppSettings> = settingsRepository.state

    private val homeContent = combine(
        libraryRepository.state,
        navigation.map { it.homeLocation }.distinctUntilChanged(),
    ) { library, location ->
        HomeContent(
            library = library,
            location = location,
            rows = homeRowsBuilder.rows(library, location),
        )
    }

    private val visibleNavigation = navigation
        .map { VisibleNavigation(it.screen, it.dialog) }
        .distinctUntilChanged()

    private val contentState = combine(
        homeContent,
        settingsRepository.state,
        visibleNavigation,
    ) { home, settings, currentNavigation ->
        AppUiState(
            screen = currentNavigation.screen,
            dialog = currentNavigation.dialog,
            homeLocation = home.location,
            homeRows = home.rows,
            library = home.library,
            settings = settings,
        )
    }

    val uiState: StateFlow<AppUiState> = combine(
        contentState,
        playbackConnection.state,
    ) { content, playback ->
        content.copy(playback = playback)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 0,
            replayExpirationMillis = 0,
        ),
        initialValue = AppUiState(settings = settingsRepository.state.value),
    )

    init {
        playbackConnection.connect()
    }

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.SelectScreen -> navigation.update { it.copy(screen = event.screen) }
            AppEvent.Back -> goBack()
            AppEvent.LocateCurrent -> locateCurrent()
            is AppEvent.OpenDirectory -> updateHomeLocation(event.location)
            is AppEvent.PlayAudio -> playAudio(event.item)
            AppEvent.AddFolder -> _effects.trySend(AppEffect.OpenFolderPicker)
            is AppEvent.FolderSelected -> libraryRepository.addFolder(event.uri)
            is AppEvent.Playback -> playbackConnection.dispatch(event.command)
            is AppEvent.ShowDialog -> navigation.update { it.copy(dialog = event.dialog) }
            AppEvent.DismissDialog -> navigation.update { it.copy(dialog = null) }
            is AppEvent.UpdateSetting -> updateSetting(event.change)
            is AppEvent.RescanFolder -> libraryRepository.rescan(event.uri)
            is AppEvent.RemoveFolder -> removeFolder(event.uri)
            AppEvent.RescanAll -> libraryRepository.rescanAll()
            AppEvent.EnsureNotificationPermission -> ensureNotificationPermission()
            AppEvent.NotificationPermissionHandled -> settingsRepository.markNotificationRequested()
        }
    }

    override fun onCleared() {
        playbackConnection.close()
        super.onCleared()
    }

    private fun goBack() {
        when (navigation.value.screen) {
            AppScreen.LIBRARY_SETTINGS -> navigation.update { it.copy(screen = AppScreen.SETTINGS) }
            AppScreen.PLAYER, AppScreen.SETTINGS -> navigation.update { it.copy(screen = AppScreen.HOME) }
            AppScreen.HOME -> navigation.value.homeLocation?.let { updateHomeLocation(it.parent()) }
        }
    }

    private fun updateHomeLocation(location: FolderLocation?) {
        navigation.update { it.copy(homeLocation = location) }
        settingsRepository.updateSavedHomeLocation(location)
    }

    private fun locateCurrent() {
        playbackConnection.state.value.currentItem?.let { item ->
            val name = item.relativePath.substringAfterLast('/').ifEmpty { item.folderName }
            updateHomeLocation(
                FolderLocation(
                    folderUri = item.folderUri,
                    rootName = item.folderName,
                    relativePath = item.relativePath,
                    name = name,
                ),
            )
        }
    }

    private fun playAudio(item: AudioItem) {
        homeRowsBuilder.queueFor(libraryRepository.state.value.items, item)?.let { (queue, index) ->
            playbackConnection.dispatch(
                PlaybackCommand.PlayQueue(queue, index),
            )
        }
    }

    private fun removeFolder(uri: String) {
        if (navigation.value.homeLocation?.folderUri == uri) {
            updateHomeLocation(null)
        }
        libraryRepository.removeFolder(uri)
    }

    private fun updateSetting(change: SettingChange) {
        when (change) {
            is SettingChange.SetThemeMode -> settingsRepository.updateThemeMode(change.value)
            is SettingChange.SetHomeHeaderMode -> settingsRepository.updateHomeHeaderMode(change.value)
            is SettingChange.SetHomeListBottomAligned -> settingsRepository.updateHomeListBottomAligned(change.value)
            is SettingChange.SetShowAlbumCover -> settingsRepository.updateShowAlbumCover(change.value)
            is SettingChange.SetShowWhenLocked -> settingsRepository.updateShowWhenLocked(change.value)
            is SettingChange.SetTimerEnabled -> settingsRepository.updateTimerEnabled(change.value)
            is SettingChange.SetTimerDuration -> settingsRepository.updateTimerDurationMs(change.valueMs)
            is SettingChange.SetWaitForCurrentEnd -> settingsRepository.updateWaitForCurrentEnd(change.value)
            is SettingChange.SetSeekStep -> settingsRepository.updateSeekStepMs(change.valueMs)
            is SettingChange.AddTimerDuration -> {
                val settings = settingsRepository.state.value
                settingsRepository.updateTimerDurationOptions(settings.timerDurationOptionsMs + change.valueMs)
            }

            is SettingChange.DeleteTimerDuration -> {
                val settings = settingsRepository.state.value
                settingsRepository.updateTimerDurationOptions(
                    settings.timerDurationOptionsMs - change.valueMs,
                )
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (!settingsRepository.notificationRequested()) {
            _effects.trySend(AppEffect.RequestNotificationPermission)
        }
    }

    private data class NavigationState(
        val screen: AppScreen = AppScreen.HOME,
        val dialog: AppDialog? = null,
        val homeLocation: FolderLocation? = null,
    )

    private data class VisibleNavigation(
        val screen: AppScreen,
        val dialog: AppDialog?,
    )

    private data class HomeContent(
        val library: LibraryState,
        val location: FolderLocation?,
        val rows: List<HomeRow>,
    )
}
