package com.localaudio.player.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localaudio.player.data.library.LibraryRepository
import com.localaudio.player.data.library.LibraryState
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.AutoSkipSegment
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.SettingsRepository
import com.localaudio.player.data.skip.AutoSkipRepository
import com.localaudio.player.data.skip.DirectorySkipRepository
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackConnection
import com.localaudio.player.playback.PlaybackState
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
            AppEvent.CancelAutoSkipMark -> activeAutoSkipMark.value = null
            AppEvent.OpenAutoSkipSettings -> navigateTo(AppScreen.AUTO_SKIP_SETTINGS)
            is AppEvent.DeleteAutoSkipSegment -> autoSkipRepository.delete(event.id)
            is AppEvent.EditAutoSkipSegment -> editAutoSkipSegment(event.id)
            is AppEvent.PlayAutoSkipAudio -> playAutoSkipAudio(event.segmentId)
            is AppEvent.TestAutoSkipSegment -> testAutoSkipSegment(event)
            is AppEvent.SaveAutoSkipSegment -> saveAutoSkipSegment(event)
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
            AppEvent.DismissDialog -> {
                if (navigation.value.dialog is AppDialog.AutoSkipEditor) {
                    playbackConnection.dispatch(PlaybackCommand.CancelAutoSkipPreview)
                }
                navigation.update { it.copy(dialog = null) }
            }
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

    private fun playAutoSkipAudio(segmentId: String) {
        autoSkipRepository.state.value
            .firstOrNull { it.id == segmentId }
            ?.let(::findAudioForSegment)
            ?.let { item ->
                playAudio(item)
                navigateTo(AppScreen.PLAYER)
            }
    }

    private fun findAudioForSegment(segment: AutoSkipSegment): AudioItem? {
        val audioItems = libraryRepository.state.value.items
        val snapshotKey = Uri.parse(segment.audioUri).normalizeScheme().toString()
        return audioItems.firstOrNull {
            it.key == snapshotKey && it.contentHash == segment.contentHash
        } ?: audioItems.firstOrNull { it.contentHash == segment.contentHash }
    }

    private fun startAutoSkipMark() {
        if (activeAutoSkipMark.value != null) return
        val playback = playbackConnection.state.value
        val item = playback.currentItem ?: return
        if (item.contentHash.isNullOrBlank()) {
            showMessage("音频哈希仍在计算，请稍后再试")
            return
        }
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
        val endMs = playback.positionMs.coerceAtLeast(0L)
        val durationMs = knownDuration(item, playback)
        validateAutoSkipTimes(active.startMs, endMs, durationMs)?.let {
            showMessage(it)
            return
        }
        activeAutoSkipMark.value = null
        navigation.update {
            it.copy(
                dialog = AppDialog.AutoSkipEditor(
                    audioKey = item.key,
                    segmentId = null,
                    startMs = active.startMs,
                    endMs = endMs,
                    durationMs = durationMs,
                ),
            )
        }
    }

    private fun editAutoSkipSegment(id: String) {
        val segment = autoSkipRepository.state.value.firstOrNull { it.id == id } ?: return
        val item = findAudioForSegment(segment) ?: return
        val playback = playbackConnection.state.value
        navigation.update {
            it.copy(
                dialog = AppDialog.AutoSkipEditor(
                    audioKey = item.key,
                    segmentId = segment.id,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    durationMs = knownDuration(item, playback),
                ),
            )
        }
    }

    private fun testAutoSkipSegment(event: AppEvent.TestAutoSkipSegment) {
        val item = libraryRepository.state.value.items.firstOrNull { it.key == event.audioKey } ?: return
        val playback = playbackConnection.state.value
        val durationMs = knownDuration(item, playback)
        validateAutoSkipTimes(event.startMs, event.endMs, durationMs)?.let {
            showMessage(it)
            return
        }
        homeRowsBuilder.queueFor(libraryRepository.state.value.items, item)?.let { (queue, index) ->
            playbackConnection.dispatch(
                PlaybackCommand.PreviewAutoSkip(
                    queue = queue,
                    index = index,
                    startMs = event.startMs,
                    endMs = event.endMs,
                ),
            )
        }
    }

    private fun saveAutoSkipSegment(event: AppEvent.SaveAutoSkipSegment) {
        val item = libraryRepository.state.value.items.firstOrNull { it.key == event.audioKey } ?: run {
            showMessage("音频已不存在，无法保存自动跳过标记")
            return
        }
        if (item.contentHash.isNullOrBlank()) {
            showMessage("音频哈希仍在计算，请稍后再保存")
            return
        }
        val playback = playbackConnection.state.value
        val durationMs = knownDuration(item, playback)
        validateAutoSkipTimes(event.startMs, event.endMs, durationMs)?.let {
            showMessage(it)
            return
        }
        val saved = if (event.segmentId == null) {
            autoSkipRepository.add(item, event.startMs, event.endMs)
        } else {
            autoSkipRepository.update(event.segmentId, event.startMs, event.endMs)
        }
        if (saved == null) {
            showMessage("自动跳过标记保存失败")
            return
        }
        playbackConnection.dispatch(PlaybackCommand.CancelAutoSkipPreview)
        navigation.update { it.copy(dialog = null) }
    }

    private fun knownDuration(item: AudioItem, playback: PlaybackState): Long {
        return if (playback.currentItem?.key == item.key && playback.durationMs > 0L) {
            playback.durationMs
        } else {
            item.durationMs.coerceAtLeast(0L)
        }
    }

    private fun validateAutoSkipTimes(startMs: Long, endMs: Long, durationMs: Long): String? {
        if (startMs < 0L || endMs < 0L) return "标记时间必须是非负数"
        if (endMs <= startMs) {
            return "结束标记时间必须晚于 ${formatAutoSkipTime(startMs)}"
        }
        if (durationMs > 0L && endMs > durationMs) {
            return "结束标记时间不能超过音频时长 ${formatAutoSkipTime(durationMs)}"
        }
        return null
    }

    private fun formatAutoSkipTime(timeMs: Long): String {
        val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
        return "${totalSeconds / 60L}分${(totalSeconds % 60L).toString().padStart(2, '0')}秒"
    }

    private fun showMessage(message: String) {
        _effects.trySend(AppEffect.ShowMessage(message))
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
