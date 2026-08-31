package com.localaudio.player.playback

import android.os.Handler
import android.os.Looper
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.settings.FADE_DURATION_STEP_MS
import com.localaudio.player.data.settings.MAX_FADE_DURATION_MS
import com.localaudio.player.data.settings.MIN_FADE_DURATION_MS
import com.localaudio.player.data.settings.SettingsRepository
import com.localaudio.player.data.skip.AutoSkipRepository
import com.localaudio.player.data.skip.DirectorySkipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

class PlaybackCoordinator(
    private val settingsRepository: SettingsRepository,
    private val autoSkipRepository: AutoSkipRepository,
    private val directorySkipRepository: DirectorySkipRepository,
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
    private var fadeOutActive = false
    private var fadeTickActive = false
    private var autoSkipPreview: AutoSkipPreview? = null

    private val tick = object : Runnable {
        override fun run() {
            tickScheduled = false
            checkTimer()
            checkAutoSkipPreview()
            checkAutoSkip()
            updateFadeVolume()
            publishState()
            if (player?.isPlaying() == true || timerState.expireAt > 0L || timerState.waitingForTrackEnd) {
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
        publishState()
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
    }

    private fun playQueue(items: List<AudioItem>, index: Int) {
        if (items.isEmpty()) return
        autoSkipPreview = null
        queue = items.toList()
        currentIndex = index.coerceIn(0, queue.lastIndex)
        consecutiveLoadFailures = 0
        selectTrack(currentIndex, shouldPlay = true, restartAutomaticTimer = true)
    }

    private fun jumpToItem(index: Int) {
        if (index !in queue.indices) return
        consecutiveLoadFailures = 0
        selectTrack(index, shouldPlay = true, restartAutomaticTimer = true)
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
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.isPrepared) {
            loadCurrent()
            return
        }
        skipCurrentSegmentIfNeeded()
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
                savedPositionMs = currentPlayer.positionMs()
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
        player?.seekTo(target)
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
        handledTerminalGeneration = -1L
        pendingSeekMs = savedPositionMs
        val generation = currentPlayer.load(item.uri)
        currentGeneration = generation
        publishState()
    }

    private fun selectTrack(
        index: Int,
        shouldPlay: Boolean,
        restartAutomaticTimer: Boolean,
    ) {
        autoSkipPreview = null
        currentIndex = index
        savedPositionMs = 0L
        pendingSeekMs = null
        desiredPlaying = shouldPlay
        resetFadeForTrack()
        if (restartAutomaticTimer) startAutomaticTimer()
        loadCurrent()
    }

    private fun onPrepared(event: PlayerEvent.Prepared) {
        val currentPlayer = player ?: return
        consecutiveLoadFailures = 0
        val target = (pendingSeekMs ?: savedPositionMs).coerceIn(0L, event.durationMs.coerceAtLeast(0L))
        savedPositionMs = target
        pendingSeekMs = null
        if (target > 0L) currentPlayer.seekTo(target)
        val settings = settingsRepository.state.value
        fadeInComplete = !settings.fadeEnabled || target >= settings.fadeDurationMs
        fadeOutActive = false
        updateFadeVolume(target)
        if (desiredPlaying) {
            skipCurrentSegmentIfNeeded()
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
        skipCurrentSegmentIfNeeded()
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
        fadeInComplete = !settingsRepository.state.value.fadeEnabled
        fadeOutActive = false
        fadeTickActive = !fadeInComplete
    }

    private fun updateFadeVolume(positionOverrideMs: Long? = null): Boolean {
        val currentPlayer = player ?: return false
        if (!currentPlayer.isPrepared) return false
        val settings = settingsRepository.state.value
        if (!settings.fadeEnabled) {
            currentPlayer.setVolume(1f)
            fadeInComplete = true
            fadeOutActive = false
            fadeTickActive = false
            return false
        }

        val fadeDuration = settings.fadeDurationMs
            .coerceIn(MIN_FADE_DURATION_MS, MAX_FADE_DURATION_MS)
            .let { (it / FADE_DURATION_STEP_MS) * FADE_DURATION_STEP_MS }
        val position = (positionOverrideMs ?: currentPosition()).coerceAtLeast(0L)
        val duration = currentPlayer.durationMs().coerceAtLeast(0L)
        var volume = 1f
        var active = false

        if (!fadeInComplete) {
            val fadeInProgress = (position.toFloat() / fadeDuration.toFloat()).coerceIn(0f, 1f)
            volume = min(volume, fadeInProgress)
            if (position >= fadeDuration) {
                fadeInComplete = true
            } else {
                active = true
            }
        }

        fadeOutActive = duration > 0L && position >= duration - fadeDuration
        if (fadeOutActive) {
            val fadeOutProgress = ((duration - position).toFloat() / fadeDuration.toFloat())
                .coerceIn(0f, 1f)
            volume = min(volume, fadeOutProgress)
            active = true
        }

        currentPlayer.setVolume(volume)
        fadeTickActive = active
        return active
    }

    private fun skipCurrentSegmentIfNeeded(): Boolean {
        val currentPlayer = player ?: return false
        if (!currentPlayer.isPrepared) return false
        val item = queue.getOrNull(currentIndex) ?: return false
        val position = currentPosition()
        val duration = currentPlayer.durationMs().coerceAtLeast(0L)
        val segments = autoSkipPreview
            ?.takeIf { it.audioKey == item.key }
            ?.let { listOf(SkipRange(it.startMs, it.endMs)) }
            ?: (autoSkipRepository.segmentsFor(item.key)
                .map { SkipRange(it.startMs, it.endMs) } + directorySkipSegments(item, duration))
        val sortedSegments = segments.sortedBy { it.startMs }
        var target = sortedSegments
            .firstOrNull { position >= it.startMs && position < it.endMs }
            ?.endMs
        if (target != null) {
            var expanded: Boolean
            do {
                expanded = false
                sortedSegments.forEach { segment ->
                    if (segment.startMs <= target!! && segment.endMs > target!!) {
                        target = segment.endMs
                        expanded = true
                    }
                }
            } while (expanded)
        }
        val effectiveTarget = target?.coerceAtMost(duration)
            ?.takeIf { it > position }
            ?: return false
        savedPositionMs = effectiveTarget
        pendingSeekMs = null
        currentPlayer.seekTo(effectiveTarget)
        return true
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
            mainHandler.postDelayed(tick, if (fadeTickActive) 50L else 500L)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun currentPosition(): Long = if (player?.isPrepared == true) {
        player?.positionMs() ?: savedPositionMs
    } else {
        pendingSeekMs ?: savedPositionMs
    }

    private fun currentDuration(): Long = if (player?.isPrepared == true) {
        player?.durationMs() ?: 0L
    } else {
        queue.getOrNull(currentIndex)?.durationMs ?: 0L
    }

    private fun persistPlayback() {
        if (queue.isNotEmpty() && currentIndex in queue.indices) {
            playbackStore.writeSnapshot(
                PlaybackSnapshot(queue, currentIndex, currentPosition()),
            )
        }
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

        fun subtractSafely(value: Long, amount: Long): Long =
            if (value <= amount) 0L else value - amount
    }
}
