package com.localaudio.player.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.localaudio.player.data.loudness.LOUDNESS_SMOOTH_DURATION_MS
import com.localaudio.player.data.loudness.LoudnessRepository
import com.localaudio.player.data.loudness.gainDb
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.settings.FADE_DURATION_STEP_MS
import com.localaudio.player.data.settings.EqualizerSettings
import com.localaudio.player.data.settings.MAX_FADE_DURATION_MS
import com.localaudio.player.data.settings.MIN_FADE_DURATION_MS
import com.localaudio.player.data.settings.SettingsRepository
import com.localaudio.player.data.skip.AutoSkipRepository
import com.localaudio.player.data.skip.DirectorySkipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackCoordinator(
    private val settingsRepository: SettingsRepository,
    private val autoSkipRepository: AutoSkipRepository,
    private val directorySkipRepository: DirectorySkipRepository,
    private val loudnessRepository: LoudnessRepository,
    private val playbackStore: PlaybackStore,
    private val queueNavigator: QueueNavigator,
    private val sleepTimer: SleepTimer,
    private val mainHandler: Handler,
    private val onPlaybackStarted: () -> Unit,
) {
    private val _state = MutableStateFlow(PlaybackState())
    private var player: PlatformPlayer? = null
    private var queue = emptyList<AudioItem>()
    private var currentIndex = -1
    private var desiredPlaying = false
    private var savedPositionMs = 0L
    private var pendingSeekMs: Long? = null
    private var currentGeneration = 0L
    private var handledTerminalGeneration = -1L
    private var consecutiveLoadFailures = 0
    private var timerState = SleepTimerState()
    private var tickScheduled = false
    private var fadeInComplete = true
    private var fadeInStartedAtMs: Long? = null
    private var fadeTickActive = false
    private var outputVolume = 1f
    private var loudnessCurrentGainDb = 0f
    private var loudnessTargetGainDb = 0f
    private var loudnessTransitionStartedAtMs: Long? = null
    private var loudnessTransitionStartDb = 0f
    private var loudnessTransitionActive = false
    private var autoSkipPreview: AutoSkipPreview? = null
    private var cachedSkipRanges: List<SkipRange> = emptyList()
    private var cachedSkipContentHash: String? = null
    private var cachedSkipFolderUri: String? = null
    private var cachedSkipRelativePath: String? = null
    private var cachedSkipDurationMs = Long.MIN_VALUE
    private var cachedAutoSkipRevision = Long.MIN_VALUE
    private var cachedDirectorySkipRevision = Long.MIN_VALUE
    private var cachedSkipPreview: AutoSkipPreview? = null
    private var appliedVolumePlayer: PlatformPlayer? = null
    private var appliedVolume = -1f
    private var appliedGainDb = Float.NaN
    private var appliedEqualizer: EqualizerSettings? = null
    private var lastPositionPersistedAtMs = SystemClock.elapsedRealtime()
    private var lastPersistedPositionMs = savedPositionMs

    private val tick = object : Runnable {
        override fun run() {
            tickScheduled = false
            checkTimer()
            checkAutoSkipPreview()
            checkAutoSkip()
            updateLoudnessGain()
            updateFadeVolume()
            persistPositionIfNeeded()
            publishState()
            if (player?.isPlaying() == true || timerState.expireAt > 0L ||
                (timerState.waitingForTrackEnd && player?.isPlaying() == true) ||
                loudnessTransitionActive
            ) {
                scheduleTick()
            }
        }
    }

    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        val snapshot = playbackStore.readSnapshot()
        queue = snapshot?.queue ?: emptyList()
        currentIndex = snapshot?.currentIndex ?: -1
        savedPositionMs = snapshot?.positionMs ?: 0L
        resetFadeForTrack()
        publishState()
    }

    fun attachPlayer(player: PlatformPlayer) {
        this.player = player
        appliedVolumePlayer = null
        appliedGainDb = Float.NaN
        appliedEqualizer = null
        updateLoudnessGain(animate = false)
        publishState()
    }

    /** Refreshes output gain after settings or a background loudness analysis changes. */
    fun refresh() {
        runOnMain {
            requestLoudnessAnalysis()
            updateLoudnessGain()
            updateFadeVolume()
            if (loudnessTransitionActive) scheduleTick()
            publishState()
        }
    }

    fun dispatch(command: PlaybackCommand) {
        runOnMain { dispatchOnMain(command) }
    }

    private fun dispatchOnMain(command: PlaybackCommand) {
        when (command) {
            is PlaybackCommand.PlayQueue -> playQueue(command.items, command.index)
            is PlaybackCommand.PreviewAutoSkip -> previewAutoSkip(
                items = command.queue,
                index = command.index,
                startMs = command.startMs,
                endMs = command.endMs,
            )
            PlaybackCommand.CancelAutoSkipPreview -> cancelAutoSkipPreview()
            is PlaybackCommand.JumpToItem -> jumpToItem(command.index)
            PlaybackCommand.Play -> play()
            PlaybackCommand.Pause -> pause()
            PlaybackCommand.Next -> next()
            PlaybackCommand.Previous -> previous()
            is PlaybackCommand.SeekTo -> seekTo(command.positionMs)
            is PlaybackCommand.SeekBy -> seekTo(currentPosition() + command.deltaMs)
            is PlaybackCommand.RemoveItems -> removeItems(command.keys)
            is PlaybackCommand.ReplaceItem -> replaceItem(command.oldKey, command.item)
            is PlaybackCommand.ReplaceItems -> replaceItems(command.itemsByKey)
            is PlaybackCommand.SetPlayMode -> setPlayMode(command.repeatMode, command.shuffleEnabled)
            is PlaybackCommand.StartTimer -> startTimer(command.durationMs)
            PlaybackCommand.StopTimer -> stopTimer()
        }
    }

    fun onPlayerEvent(event: PlayerEvent) {
        runOnMain { onPlayerEventOnMain(event) }
    }

    private fun onPlayerEventOnMain(event: PlayerEvent) {
        if (event.generation != currentGeneration) return
        when (event) {
            is PlayerEvent.Prepared -> onPrepared(event)
            is PlayerEvent.SeekCompleted -> onSeekCompleted()
            is PlayerEvent.Completed -> handleTerminal(failed = false)
            is PlayerEvent.Failed -> handleTerminal(failed = true)
        }
    }

    fun close() {
        mainHandler.removeCallbacks(tick)
        tickScheduled = false
        persistPlayback()
        player?.release()
        player = null
        appliedVolumePlayer = null
        appliedGainDb = Float.NaN
        appliedEqualizer = null
    }

    private fun playQueue(items: List<AudioItem>, index: Int) {
        if (items.isEmpty()) return
        autoSkipPreview = null
        consecutiveLoadFailures = 0
        selectTrack(
            index = index,
            shouldPlay = true,
            restartAutomaticTimer = true,
            targetQueue = items.toList(),
        )
    }

    private fun jumpToItem(index: Int) {
        if (index !in queue.indices) return
        consecutiveLoadFailures = 0
        selectTrack(
            index = index,
            shouldPlay = true,
            restartAutomaticTimer = true,
        )
    }

    private fun previewAutoSkip(items: List<AudioItem>, index: Int, startMs: Long, endMs: Long) {
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceAtLeast(0L)
        if (items.isEmpty() || end <= start) return
        queue = items.toList()
        currentIndex = index.coerceIn(0, queue.lastIndex)
        consecutiveLoadFailures = 0
        autoSkipPreview = AutoSkipPreview(queue[currentIndex].key, start, end)
        savedPositionMs = subtractSafely(start, PREVIEW_PADDING_MS)
        pendingSeekMs = savedPositionMs
        desiredPlaying = true
        resetFadeForTrack()
        loadCurrent()
    }

    private fun cancelAutoSkipPreview() {
        if (autoSkipPreview == null) return
        desiredPlaying = false
        pausePlayer()
        autoSkipPreview = null
        persistPlayback()
        publishState()
    }

    private fun play() {
        if (queue.isEmpty()) return
        startAutomaticTimer()
        desiredPlaying = true
        requestLoudnessAnalysis()
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.isPrepared) {
            loadCurrent()
            return
        }
        if (!currentPlayer.isPlaying()) {
            restartFadeIn(startImmediately = true)
        }
        if (skipCurrentSegmentIfNeeded()) {
            restartFadeIn(startImmediately = true)
        }
        updateFadeVolume()
        if (!currentPlayer.play()) {
            desiredPlaying = false
            publishState()
            return
        }
        scheduleTick()
        publishState()
        onPlaybackStarted()
    }

    private fun pause() {
        desiredPlaying = false
        pausePlayer()
        persistPlayback()
        publishState()
    }

    private fun pausePlayer() {
        player?.let { currentPlayer ->
            if (currentPlayer.isPrepared) {
                savedPositionMs = currentPosition()
                currentPlayer.pause()
            } else {
                savedPositionMs = pendingSeekMs ?: savedPositionMs
            }
        }
    }

    private fun next() {
        consecutiveLoadFailures = 0
        advance(manual = true)
    }

    private fun previous() {
        if (queue.isEmpty()) return
        consecutiveLoadFailures = 0
        val shouldPlay = desiredPlaying || player?.isPlaying() == true
        when (
            val move = queueNavigator.previous(
                queueSize = queue.size,
                currentIndex = currentIndex,
                repeatMode = settingsRepository.state.value.repeatMode,
            )
        ) {
            is QueueMove.Select -> {
                selectTrack(
                    index = move.index,
                    shouldPlay = shouldPlay,
                    restartAutomaticTimer = shouldPlay,
                )
            }

            QueueMove.Stop -> Unit
        }
    }

    private fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, currentDuration())
        savedPositionMs = target
        pendingSeekMs = target
        val currentPlayer = player
        val wasPlaying = currentPlayer?.isPlaying() == true
        currentPlayer?.seekTo(target)
        restartFadeIn(startImmediately = wasPlaying)
        persistPlayback()
        publishState()
    }

    private fun setPlayMode(repeatMode: Int, shuffleEnabled: Boolean) {
        settingsRepository.updateRepeatMode(repeatMode)
        settingsRepository.updateShuffleEnabled(shuffleEnabled)
        publishState()
    }

    private fun startTimer(
        durationMs: Long,
        source: TimerSource = TimerSource.MANUAL,
    ) {
        timerState = sleepTimer.start(durationMs, source)
        scheduleTick()
        publishState()
    }

    private fun stopTimer() {
        timerState = sleepTimer.stop()
        publishState()
    }

    private fun loadCurrent() {
        val item = queue.getOrNull(currentIndex) ?: return
        val currentPlayer = player ?: return
        requestLoudnessAnalysis()
        updateLoudnessGain(animate = false)
        handledTerminalGeneration = -1L
        pendingSeekMs = savedPositionMs
        appliedVolumePlayer = null
        appliedGainDb = Float.NaN
        appliedEqualizer = null
        val generation = currentPlayer.load(item.uri)
        currentGeneration = generation
        publishState()
    }

    private fun selectTrack(
        index: Int,
        shouldPlay: Boolean,
        restartAutomaticTimer: Boolean,
        targetQueue: List<AudioItem> = queue,
    ) {
        if (targetQueue.isEmpty()) return
        autoSkipPreview = null
        val selection = TrackSelection(
            queue = targetQueue,
            index = index.coerceIn(0, targetQueue.lastIndex),
            shouldPlay = shouldPlay,
            restartAutomaticTimer = restartAutomaticTimer,
        )
        applyTrackSelection(selection)
    }

    private fun replaceItem(oldKey: String, item: AudioItem) {
        replaceItems(mapOf(oldKey to item))
    }

    private fun replaceItems(itemsByKey: Map<String, AudioItem>) {
        if (itemsByKey.isEmpty()) return
        val oldCurrentItem = queue.getOrNull(currentIndex)
        if (oldCurrentItem == null && queue.none { it.key in itemsByKey }) return
        val nextQueue = queue.map { itemsByKey[it.key] ?: it }
        if (nextQueue == queue) return
        queue = nextQueue
        val updatedCurrentItem = queue.getOrNull(currentIndex)
        if (oldCurrentItem != null && updatedCurrentItem != null) {
            if (oldCurrentItem.uri != updatedCurrentItem.uri) {
                savedPositionMs = currentPosition()
                loadCurrent()
            } else {
                requestLoudnessAnalysis()
                updateLoudnessGain()
            }
        }
        persistPlayback()
        publishState()
    }

    private fun onSeekCompleted() {
        pendingSeekMs?.let { target ->
            savedPositionMs = target
            pendingSeekMs = null
        }
        publishState()
    }

    private fun removeItems(keys: Set<String>) {
        if (keys.isEmpty() || queue.isEmpty()) return
        val currentItem = queue.getOrNull(currentIndex)
        val currentWasRemoved = currentItem?.key in keys
        val wasPlaying = desiredPlaying || player?.isPlaying() == true
        val oldIndex = currentIndex
        val nextQueue = queue.filterNot { it.key in keys }
        if (nextQueue.isEmpty()) {
            desiredPlaying = false
            player?.release()
            queue = emptyList()
            currentIndex = -1
            savedPositionMs = 0L
            pendingSeekMs = null
            persistPlayback()
            publishState()
            return
        }
        if (currentWasRemoved) {
            val nextIndex = oldIndex.coerceAtMost(nextQueue.lastIndex)
            selectTrack(
                index = nextIndex,
                shouldPlay = wasPlaying,
                restartAutomaticTimer = wasPlaying,
                targetQueue = nextQueue,
            )
        } else {
            val removedBeforeCurrent = queue.take(oldIndex).count { it.key in keys }
            queue = nextQueue
            currentIndex = (oldIndex - removedBeforeCurrent).coerceIn(0, queue.lastIndex)
            persistPlayback()
            publishState()
        }
    }

    private fun applyTrackSelection(selection: TrackSelection) {
        autoSkipPreview = null
        queue = selection.queue
        currentIndex = selection.index
        savedPositionMs = 0L
        pendingSeekMs = null
        desiredPlaying = selection.shouldPlay
        resetFadeForTrack()
        resetLoudnessForTrack(queue.getOrNull(currentIndex))
        if (selection.restartAutomaticTimer) startAutomaticTimer()
        loadCurrent()
    }

    private fun onPrepared(event: PlayerEvent.Prepared) {
        val currentPlayer = player ?: return
        consecutiveLoadFailures = 0
        val target = (pendingSeekMs ?: savedPositionMs).coerceIn(0L, event.durationMs.coerceAtLeast(0L))
        savedPositionMs = target
        pendingSeekMs = null
        if (target > 0L) currentPlayer.seekTo(target)
        restartFadeIn(startImmediately = false)
        updateFadeVolume()
        if (desiredPlaying) {
            skipCurrentSegmentIfNeeded()
            restartFadeIn(startImmediately = true)
            updateFadeVolume()
            if (currentPlayer.play()) {
                scheduleTick()
            } else {
                desiredPlaying = false
            }
        }
        publishState()
        if (desiredPlaying) onPlaybackStarted()
    }

    private fun handleTerminal(failed: Boolean) {
        if (handledTerminalGeneration == currentGeneration) return
        handledTerminalGeneration = currentGeneration
        if (autoSkipPreview?.audioKey == queue.getOrNull(currentIndex)?.key) {
            if (player?.isPrepared == true) savedPositionMs = player?.positionMs() ?: savedPositionMs
            desiredPlaying = false
            autoSkipPreview = null
            player?.pause()
            persistPlayback()
            publishState()
            return
        }
        if (failed) consecutiveLoadFailures++
        if (player?.isPrepared == true) savedPositionMs = player?.positionMs() ?: savedPositionMs
        player?.release()
        if (!failed && timerState.waitingForTrackEnd) {
            desiredPlaying = false
            timerState = sleepTimer.stop()
            persistPlayback()
            publishState()
        } else if (failed && consecutiveLoadFailures >= queue.size.coerceAtLeast(1)) {
            desiredPlaying = false
            persistPlayback()
            publishState()
        } else {
            advance(manual = false)
        }
    }

    private fun advance(manual: Boolean) {
        if (queue.isEmpty()) return
        if (!manual && timerState.waitingForTrackEnd) {
            desiredPlaying = false
            pausePlayer()
            timerState = sleepTimer.stop()
            persistPlayback()
            publishState()
            return
        }
        val settings = settingsRepository.state.value
        val wasPlaying = player?.isPlaying() == true
        val shouldPlay = desiredPlaying || wasPlaying
        when (
            val move = queueNavigator.next(
                queueSize = queue.size,
                currentIndex = currentIndex,
                repeatMode = settings.repeatMode,
                shuffle = settings.shuffleEnabled,
                automatic = !manual,
            )
        ) {
            is QueueMove.Select -> {
                selectTrack(
                    index = move.index,
                    shouldPlay = shouldPlay,
                    restartAutomaticTimer = manual && shouldPlay,
                )
            }

            QueueMove.Stop -> {
                desiredPlaying = false
                pausePlayer()
                persistPlayback()
                publishState()
            }
        }
    }

    private fun startAutomaticTimer() {
        val settings = settingsRepository.state.value
        if (settings.timerEnabled && timerState.source != TimerSource.MANUAL) {
            startTimer(settings.timerDurationMs, TimerSource.AUTOMATIC)
        }
    }

    private fun checkTimer() {
        when (
            sleepTimer.check(
                state = timerState,
                isPlaying = player?.isPlaying() == true,
                waitForCurrentEnd = settingsRepository.state.value.waitForCurrentEnd,
            )
        ) {
            TimerDecision.None -> Unit
            TimerDecision.StopNow -> {
                desiredPlaying = false
                pausePlayer()
                timerState = sleepTimer.stop()
                persistPlayback()
            }

            TimerDecision.WaitForTrackEnd -> {
                timerState = timerState.copy(expireAt = 0L, waitingForTrackEnd = true)
            }
        }
    }

    private fun checkAutoSkip() {
        if (player?.isPlaying() != true) return
        if (skipCurrentSegmentIfNeeded()) {
            restartFadeIn(startImmediately = true)
        }
    }

    private fun checkAutoSkipPreview() {
        val preview = autoSkipPreview ?: return
        val currentPlayer = player ?: return
        if (!currentPlayer.isPrepared || !currentPlayer.isPlaying()) return
        val duration = currentPlayer.durationMs().coerceAtLeast(0L)
        val stopAt = preview.stopAt(duration)
        if (currentPosition() >= stopAt) {
            currentPlayer.seekTo(stopAt)
            currentPlayer.pause()
            savedPositionMs = stopAt
            pendingSeekMs = null
            desiredPlaying = false
            autoSkipPreview = null
            persistPlayback()
        }
    }

    private fun resetFadeForTrack() {
        val fadeEnabled = settingsRepository.state.value.fadeEnabled
        fadeInComplete = !fadeEnabled
        fadeInStartedAtMs = null
        fadeTickActive = false
        outputVolume = if (fadeEnabled) 0f else 1f
    }

    private fun resetLoudnessForTrack(item: AudioItem?) {
        val target = targetLoudnessGainDb(item)
        loudnessCurrentGainDb = target
        loudnessTargetGainDb = target
        loudnessTransitionStartedAtMs = null
        loudnessTransitionStartDb = target
        loudnessTransitionActive = false
    }

    private fun restartFadeIn(startImmediately: Boolean) {
        val settings = settingsRepository.state.value
        if (!settings.fadeEnabled) {
            fadeInComplete = true
            fadeInStartedAtMs = null
            fadeTickActive = false
            setOutputVolume(player, 1f)
            return
        }
        fadeInComplete = false
        fadeInStartedAtMs = if (startImmediately) SystemClock.uptimeMillis() else null
        fadeTickActive = startImmediately
        setOutputVolume(player, 0f)
        if (startImmediately) rescheduleFadeTick()
    }

    private fun updateFadeVolume(): Boolean {
        val currentPlayer = player ?: return false
        if (!currentPlayer.isPrepared) return false
        val settings = settingsRepository.state.value
        if (!settings.fadeEnabled) {
            setOutputVolume(currentPlayer, 1f)
            fadeInComplete = true
            fadeInStartedAtMs = null
            fadeTickActive = false
            return false
        }

        val fadeDuration = fadeDurationMs(settings.fadeDurationMs)
        var volume = outputVolume
        var active = false

        if (!fadeInComplete) {
            val startTime = fadeInStartedAtMs
            val elapsed = if (startTime == null) {
                0L
            } else {
                (SystemClock.uptimeMillis() - startTime).coerceAtLeast(0L)
            }
            volume = (elapsed.toFloat() / fadeDuration.toFloat()).coerceIn(0f, 1f)
            if (startTime != null && elapsed >= fadeDuration) {
                fadeInComplete = true
                fadeInStartedAtMs = null
                volume = 1f
            } else {
                active = startTime != null
            }
        }

        setOutputVolume(currentPlayer, volume)
        fadeTickActive = active
        return active
    }

    private fun updateLoudnessGain(animate: Boolean = player?.isPlaying() == true): Boolean {
        val target = targetLoudnessGainDb(queue.getOrNull(currentIndex))
        if (kotlin.math.abs(target - loudnessTargetGainDb) > GAIN_EPSILON) {
            loudnessTargetGainDb = target
            if (animate) {
                loudnessTransitionStartDb = loudnessCurrentGainDb
                loudnessTransitionStartedAtMs = SystemClock.uptimeMillis()
                loudnessTransitionActive = true
            } else {
                loudnessCurrentGainDb = target
                loudnessTransitionStartedAtMs = null
                loudnessTransitionActive = false
            }
        } else if (!animate && loudnessTransitionActive) {
            loudnessCurrentGainDb = loudnessTargetGainDb
            loudnessTransitionStartedAtMs = null
            loudnessTransitionActive = false
        }

        if (loudnessTransitionActive) {
            val startedAt = loudnessTransitionStartedAtMs ?: SystemClock.uptimeMillis().also {
                loudnessTransitionStartedAtMs = it
            }
            val elapsed = (SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / LOUDNESS_SMOOTH_DURATION_MS.toFloat())
                .coerceIn(0f, 1f)
            loudnessCurrentGainDb = loudnessTransitionStartDb +
                (loudnessTargetGainDb - loudnessTransitionStartDb) * progress
            if (progress >= 1f) {
                loudnessCurrentGainDb = loudnessTargetGainDb
                loudnessTransitionStartedAtMs = null
                loudnessTransitionActive = false
            }
        }
        setOutputVolume(player, outputVolume)
        return loudnessTransitionActive
    }

    private fun targetLoudnessGainDb(item: AudioItem?): Float {
        val settings = settingsRepository.state.value
        if (!settings.loudnessEnabled) return 0f
        val hash = item?.contentHash?.takeIf { it.isNotBlank() } ?: return 0f
        return loudnessRepository.resultFor(hash)?.gainDb() ?: 0f
    }

    private fun requestLoudnessAnalysis() {
        if (!settingsRepository.state.value.loudnessEnabled ||
            (!desiredPlaying && player?.isPlaying() != true)
        ) return
        queue.getOrNull(currentIndex)?.let(loudnessRepository::ensureAnalysis)
    }

    private fun fadeDurationMs(value: Long): Long = value
        .coerceIn(MIN_FADE_DURATION_MS, MAX_FADE_DURATION_MS)
        .let { (it / FADE_DURATION_STEP_MS) * FADE_DURATION_STEP_MS }
        .coerceAtLeast(FADE_DURATION_STEP_MS)

    private fun setOutputVolume(currentPlayer: PlatformPlayer?, volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        outputVolume = normalized
        if (currentPlayer == null || !currentPlayer.isPrepared) {
            appliedVolumePlayer = null
            appliedGainDb = Float.NaN
            appliedEqualizer = null
            return
        }
        val equalizer = settingsRepository.state.value.equalizer
        if (appliedVolumePlayer === currentPlayer &&
            appliedVolume == normalized &&
            appliedGainDb == loudnessCurrentGainDb &&
            appliedEqualizer == equalizer
        ) return
        currentPlayer.setGainAndVolume(
            gainDb = loudnessCurrentGainDb,
            volume = normalized,
            equalizer = equalizer,
        )
        appliedVolumePlayer = currentPlayer
        appliedVolume = normalized
        appliedGainDb = loudnessCurrentGainDb
        appliedEqualizer = equalizer
    }

    private fun rescheduleFadeTick() {
        mainHandler.removeCallbacks(tick)
        tickScheduled = false
        scheduleTick()
    }

    private fun skipCurrentSegmentIfNeeded(): Boolean {
        val currentPlayer = player ?: return false
        if (!currentPlayer.isPrepared) return false
        val item = queue.getOrNull(currentIndex) ?: return false
        val position = currentPosition()
        val duration = currentPlayer.durationMs().coerceAtLeast(0L)
        val ranges = skipRangesFor(item, duration)
        var target: Long? = null
        var low = 0
        var high = ranges.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val range = ranges[middle]
            when {
                position < range.startMs -> high = middle - 1
                position >= range.endMs -> low = middle + 1
                else -> {
                    target = range.endMs
                    break
                }
            }
        }
        val effectiveTarget = target?.coerceAtMost(duration)
            ?.takeIf { it > position }
            ?: return false
        savedPositionMs = effectiveTarget
        pendingSeekMs = null
        currentPlayer.seekTo(effectiveTarget)
        return true
    }

    /**
     * Builds the effective ranges only when the current track, duration, preview, or rules change.
     * The returned ranges are sorted and merged, which makes the tick a binary search with no
     * per-tick collection or interval allocations.
     */
    private fun skipRangesFor(item: AudioItem, durationMs: Long): List<SkipRange> {
        val activePreview = autoSkipPreview
        val preview = if (activePreview != null && activePreview.audioKey == item.key) {
            activePreview
        } else {
            null
        }
        val autoSkipRevision = autoSkipRepository.revision
        val directorySkipRevision = directorySkipRepository.revision
        if (cachedSkipContentHash == item.contentHash &&
            cachedSkipFolderUri == item.folderUri &&
            cachedSkipRelativePath == item.relativePath &&
            cachedSkipDurationMs == durationMs &&
            cachedAutoSkipRevision == autoSkipRevision &&
            cachedDirectorySkipRevision == directorySkipRevision &&
            cachedSkipPreview == preview
        ) {
            return cachedSkipRanges
        }

        val candidates = ArrayList<SkipRange>()
        if (preview != null) {
            candidates += SkipRange(preview.startMs, preview.endMs)
        } else {
            item.contentHash
                ?.let { autoSkipRepository.segmentsFor(it) }
                ?.forEach { segment ->
                    candidates += SkipRange(segment.startMs, segment.endMs)
                }
            candidates += directorySkipSegments(item, durationMs)
        }
        candidates.sortBy { it.startMs }

        val merged = ArrayList<SkipRange>(candidates.size)
        candidates.forEach { range ->
            if (range.endMs <= range.startMs) return@forEach
            val lastIndex = merged.lastIndex
            if (lastIndex >= 0 && range.startMs <= merged[lastIndex].endMs) {
                val last = merged[lastIndex]
                if (range.endMs > last.endMs) {
                    merged[lastIndex] = last.copy(endMs = range.endMs)
                }
            } else {
                merged += range
            }
        }

        cachedSkipContentHash = item.contentHash
        cachedSkipFolderUri = item.folderUri
        cachedSkipRelativePath = item.relativePath
        cachedSkipDurationMs = durationMs
        cachedAutoSkipRevision = autoSkipRevision
        cachedDirectorySkipRevision = directorySkipRevision
        cachedSkipPreview = preview
        cachedSkipRanges = if (merged.isEmpty()) emptyList() else merged
        return cachedSkipRanges
    }

    private fun directorySkipSegments(item: AudioItem, durationMs: Long): List<SkipRange> {
        val rule = directorySkipRepository.ruleFor(item.folderUri, item.relativePath) ?: return emptyList()
        val ranges = ArrayList<SkipRange>(2)
        val startMs = rule.startSeconds.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
        if (startMs > 0L) {
            ranges += SkipRange(0L, startMs.coerceAtMost(durationMs))
        }
        val endMs = rule.endSeconds.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
        if (endMs > 0L && durationMs > 0L) {
            ranges += SkipRange((durationMs - endMs).coerceAtLeast(0L), durationMs)
        }
        return ranges.filter { it.endMs > it.startMs }
    }

    private fun scheduleTick() {
        if (!tickScheduled) {
            tickScheduled = true
            mainHandler.postDelayed(
                tick,
                if (fadeTickActive || loudnessTransitionActive) 50L else 500L,
            )
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun currentPosition(): Long = pendingSeekMs
        ?: if (player?.isPrepared == true) player?.positionMs() ?: savedPositionMs else savedPositionMs

    private fun currentDuration(): Long = if (player?.isPrepared == true) {
        player?.durationMs() ?: 0L
    } else {
        queue.getOrNull(currentIndex)?.durationMs ?: 0L
    }

    private fun persistPlayback() {
        if (queue.isNotEmpty() && currentIndex in queue.indices) {
            val position = currentPosition()
            playbackStore.writeSnapshot(
                PlaybackSnapshot(queue, currentIndex, position),
            )
            lastPositionPersistedAtMs = SystemClock.elapsedRealtime()
            lastPersistedPositionMs = position
        } else {
            playbackStore.clearSnapshot()
        }
    }

    private fun persistPositionIfNeeded() {
        if (player?.isPlaying() != true || queue.isEmpty() || currentIndex !in queue.indices) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPositionPersistedAtMs < POSITION_PERSIST_INTERVAL_MS) return
        val position = currentPosition()
        if (position == lastPersistedPositionMs) return
        playbackStore.writePosition(position)
        lastPositionPersistedAtMs = now
        lastPersistedPositionMs = position
    }

    private fun publishState() {
        val settings = settingsRepository.state.value
        _state.value = PlaybackState(
            queue = queue,
            currentIndex = currentIndex,
            positionMs = currentPosition().coerceAtLeast(0L),
            durationMs = currentDuration().coerceAtLeast(0L),
            isPlaying = player?.isPlaying() == true,
            repeatMode = settings.repeatMode,
            shuffleEnabled = settings.shuffleEnabled,
            timerExpireAt = timerState.expireAt,
            activeTimerDurationMs = timerState.durationMs,
            timerRemainingMs = sleepTimer.remainingMs(timerState),
            timerWaitingForEnd = timerState.waitingForTrackEnd,
        )
    }

    private data class SkipRange(
        val startMs: Long,
        val endMs: Long,
    )

    private data class TrackSelection(
        val queue: List<AudioItem>,
        val index: Int,
        val shouldPlay: Boolean,
        val restartAutomaticTimer: Boolean,
    )

    private data class AutoSkipPreview(
        val audioKey: String,
        val startMs: Long,
        val endMs: Long,
    ) {
        fun stopAt(durationMs: Long): Long {
            val paddedEnd = if (endMs > Long.MAX_VALUE - PREVIEW_PADDING_MS) {
                Long.MAX_VALUE
            } else {
                endMs + PREVIEW_PADDING_MS
            }
            return if (durationMs > 0L) paddedEnd.coerceAtMost(durationMs) else paddedEnd
        }
    }

    private companion object {
        const val PREVIEW_PADDING_MS = 3_000L
        const val GAIN_EPSILON = 0.001f
        const val POSITION_PERSIST_INTERVAL_MS = 10_000L

        fun subtractSafely(value: Long, amount: Long): Long =
            if (value <= amount) 0L else value - amount
    }
}
