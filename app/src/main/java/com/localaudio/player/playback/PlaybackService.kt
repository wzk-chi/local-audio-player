package com.localaudio.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as SessionPlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.localaudio.player.LocalAudioApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Android service shell for the playback coordinator and framework media session. */
class PlaybackService : Service() {
    inner class LocalBinder : Binder() {
        val state: StateFlow<PlaybackState>
            get() = coordinator.state

        fun dispatch(command: PlaybackCommand) {
            coordinator.dispatch(command)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        val container = (application as LocalAudioApplication).container
        coordinator = PlaybackCoordinator(
            settingsRepository = container.settingsRepository,
            playbackStore = container.playbackStore,
            queueNavigator = QueueNavigator(),
            sleepTimer = SleepTimer(),
            mainHandler = Handler(Looper.getMainLooper()),
            onPlaybackStarted = { startForeground(NOTIFICATION_ID, notification()) },
        )
        coordinator.attachPlayer(PlatformPlayer(this, coordinator::onPlayerEvent))
        createNotificationChannel()
        mediaSession = MediaSession(this, "LocalAudio")
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = coordinator.dispatch(PlaybackCommand.Play)
            override fun onPause() = coordinator.dispatch(PlaybackCommand.Pause)
            override fun onSkipToNext() = coordinator.dispatch(PlaybackCommand.Next)
            override fun onSkipToPrevious() = coordinator.dispatch(PlaybackCommand.Previous)
            override fun onSeekTo(pos: Long) = coordinator.dispatch(PlaybackCommand.SeekTo(pos))
        }, Handler(Looper.getMainLooper()))
        mediaSession.setSessionActivity(mainActivityPendingIntent())
        mediaSession.isActive = true
        serviceScope.launch {
            coordinator.state.collect { state -> updateMediaSession(state) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> coordinator.dispatch(PlaybackCommand.Play)
            ACTION_PAUSE -> coordinator.dispatch(PlaybackCommand.Pause)
            ACTION_NEXT -> coordinator.dispatch(PlaybackCommand.Next)
            ACTION_PREVIOUS -> coordinator.dispatch(PlaybackCommand.Previous)
        }
        return START_STICKY
    }

    private fun updateMediaSession(current: PlaybackState) {
        val actions = SessionPlaybackState.ACTION_PLAY or SessionPlaybackState.ACTION_PAUSE or
            SessionPlaybackState.ACTION_SKIP_TO_NEXT or SessionPlaybackState.ACTION_SKIP_TO_PREVIOUS or
            SessionPlaybackState.ACTION_SEEK_TO
        mediaSession.setPlaybackState(
            SessionPlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (current.isPlaying) SessionPlaybackState.STATE_PLAYING else SessionPlaybackState.STATE_PAUSED,
                    current.positionMs,
                    1f,
                )
                .build(),
        )
        current.currentItem?.let { item ->
            mediaSession.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, current.durationMs)
                    .build(),
            )
        }
    }

    private fun notification(): Notification {
        val current = coordinator.state.value
        val item = current.currentItem
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (current.isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle(item?.title ?: "LocalAudio")
            .setContentText(item?.artist ?: "")
            .setContentIntent(mainActivityPendingIntent())
            .setOngoing(current.isPlaying)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                    "上一首",
                    servicePendingIntent(ACTION_PREVIOUS),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        this,
                        if (current.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    ),
                    if (current.isPlaying) "暂停" else "播放",
                    servicePendingIntent(if (current.isPlaying) ACTION_PAUSE else ACTION_PLAY),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_next),
                    "下一首",
                    servicePendingIntent(ACTION_NEXT),
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "播放控制", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun mainActivityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        requireNotNull(packageManager.getLaunchIntentForPackage(packageName)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        serviceScope.cancel()
        coordinator.close()
        if (::mediaSession.isInitialized) mediaSession.release()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "playback_control"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.localaudio.player.PLAY"
        const val ACTION_PAUSE = "com.localaudio.player.PAUSE"
        const val ACTION_NEXT = "com.localaudio.player.NEXT"
        const val ACTION_PREVIOUS = "com.localaudio.player.PREVIOUS"
    }
}
