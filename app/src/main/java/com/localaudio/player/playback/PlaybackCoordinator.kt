package com.localaudio.player.playback

import android.os.Handler
import android.os.Looper
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackCoordinator(
    private val settingsRepository: SettingsRepository,
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

    private val tick = object : Runnable {
        override fun run() {
            tickScheduled = false
            checkTimer()
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

    private fun play() {
        if (queue.isEmpty()) return
        startAutomaticTimer()
        desiredPlaying = true
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.isPrepared) {
            loadCurrent()
            return
        }
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
        when (
            val move = queueNavigator.previous(
                queueSize = queue.size,
                currentIndex = currentIndex,
                repeatMode = settingsRepository.state.value.repeatMode,
            )
        ) {
            is QueueMove.Select -> {
                selectTrack(move.index, shouldPlay = true, restartAutomaticTimer = true)
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
        currentIndex = index
        savedPositionMs = 0L
        pendingSeekMs = null
        desiredPlaying = shouldPlay
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
        if (desiredPlaying) {
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
                    shouldPlay = !manual || desiredPlaying || wasPlaying,
                    restartAutomaticTimer = manual,
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

    private fun scheduleTick() {
        if (!tickScheduled) {
            tickScheduled = true
            mainHandler.postDelayed(tick, 500L)
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
            timerSource = timerState.source,
        )
    }
}
