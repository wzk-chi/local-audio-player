package com.localaudio.player.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localaudio.player.data.library.LibraryRepository
import com.localaudio.player.data.library.LibraryState
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.SettingsRepository
import com.localaudio.player.data.skip.AutoSkipRepository
import com.localaudio.player.data.skip.DirectorySkipRepository
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
import kotlinx.coroutines.launch

class AppViewModel(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val autoSkipRepository: AutoSkipRepository,
    private val directorySkipRepository: DirectorySkipRepository,
    private val playbackConnection: PlaybackConnection,
    private val homeRowsBuilder: HomeRowsBuilder,
) : ViewModel() {
    private val navigation = MutableStateFlow(
        NavigationState(homeLocation = settingsRepository.state.value.savedHomeLocation),
    )
    private val activeAutoSkipMark = MutableStateFlow<ActiveAutoSkipMark?>(null)
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
        combine(
            homeContent,
            settingsRepository.state,
            visibleNavigation,
            autoSkipRepository.state,
            activeAutoSkipMark,
        ) { home, settings, currentNavigation, autoSkipSegments, activeMark ->
            AppUiState(
                screen = currentNavigation.screen,
                dialog = currentNavigation.dialog,
                homeLocation = home.location,
                homeRows = home.rows,
                library = home.library,
                settings = settings,
                autoSkipSegments = autoSkipSegments,
                activeAutoSkipMark = activeMark,
            )
        },
        directorySkipRepository.state,
    ) { content, directorySkipRules ->
        content.copy(directorySkipRules = directorySkipRules)
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
        viewModelScope.launch {
            playbackConnection.state
                .map { it.currentItem?.key }
                .distinctUntilChanged()
                .collect { currentKey ->
                    if (activeAutoSkipMark.value?.audioKey != currentKey) {
                        activeAutoSkipMark.value = null
                    }
                }
        }
    }

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.SelectScreen -> navigateTo(event.screen)
            AppEvent.Back -> goBack()
            AppEvent.LocateCurrent -> locateCurrent()
            is AppEvent.OpenDirectory -> updateHomeLocation(event.location)
            is AppEvent.PlayAudio -> playAudio(event.item)
            AppEvent.StartAutoSkipMark -> startAutoSkipMark()
            AppEvent.FinishAutoSkipMark -> finishAutoSkipMark()
            AppEvent.OpenAutoSkipSettings -> navigateTo(AppScreen.AUTO_SKIP_SETTINGS)
            is AppEvent.DeleteAutoSkipSegment -> autoSkipRepository.delete(event.id)
            is AppEvent.PlayAutoSkipAudio -> playAutoSkipAudio(event.audioKey)
            is AppEvent.SaveDirectorySkip -> directorySkipRepository.save(
                folderUri = event.folderUri,
                relativePath = event.relativePath,
                startSeconds = event.startSeconds,
                endSeconds = event.endSeconds,
            )
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
            AppScreen.LIBRARY_SETTINGS -> navigateTo(AppScreen.SETTINGS)
            AppScreen.AUTO_SKIP_SETTINGS -> navigateTo(AppScreen.SETTINGS)
            AppScreen.PLAYER, AppScreen.SETTINGS -> navigateTo(AppScreen.HOME)
            AppScreen.HOME -> navigation.value.homeLocation?.let { updateHomeLocation(it.parent()) }
        }
    }

    private fun navigateTo(screen: AppScreen) {
        if (navigation.value.screen == AppScreen.PLAYER && screen != AppScreen.PLAYER) {
            activeAutoSkipMark.value = null
        }
        navigation.update { it.copy(screen = screen) }
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

    private fun playAutoSkipAudio(audioKey: String) {
        libraryRepository.state.value.items
            .firstOrNull { it.key == audioKey }
            ?.let { item ->
                playAudio(item)
                navigateTo(AppScreen.PLAYER)
            }
    }

    private fun startAutoSkipMark() {
        if (activeAutoSkipMark.value != null) return
        val playback = playbackConnection.state.value
        val item = playback.currentItem ?: return
        activeAutoSkipMark.value = ActiveAutoSkipMark(
            audioKey = item.key,
            startMs = playback.positionMs.coerceAtLeast(0L),
        )
    }

    private fun finishAutoSkipMark() {
        val active = activeAutoSkipMark.value ?: return
        val playback = playbackConnection.state.value
        val item = playback.currentItem
        if (item == null || item.key != active.audioKey) {
            activeAutoSkipMark.value = null
            return
        }
        autoSkipRepository.add(
            item = item,
            startMs = active.startMs,
            endMs = playback.positionMs.coerceAtLeast(0L),
        ) ?: return
        activeAutoSkipMark.value = null
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
            is SettingChange.SetFadeEnabled -> settingsRepository.updateFadeEnabled(change.value)
            is SettingChange.SetFadeDuration -> settingsRepository.updateFadeDurationMs(change.valueMs)
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
