package com.localaudio.player.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackConnection(
    context: Context,
) : Closeable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = MutableStateFlow<PlaybackService.LocalBinder?>(null)
    private var bindingRequested = false
    private var bound = false

    val state: StateFlow<PlaybackState> = binder
        .flatMapLatest { current -> current?.state ?: flowOf(PlaybackState()) }
        .stateIn(scope, SharingStarted.Eagerly, PlaybackState())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val currentBinder = service as PlaybackService.LocalBinder
            binder.value = currentBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder.value = null
            bound = false
        }
    }

    fun connect() {
        if (bindingRequested) return
        bindingRequested = true
        val intent = Intent(context, PlaybackService::class.java)
        context.startService(intent)
        bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun dispatch(command: PlaybackCommand) {
        binder.value?.dispatch(command)
    }

    override fun close() {
        if (!bindingRequested) return
        bindingRequested = false
        binder.value = null
        if (bound) {
            context.unbindService(serviceConnection)
            bound = false
        }
        scope.cancel()
    }
}
