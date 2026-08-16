package com.localaudio.player.di

import android.content.Context
import com.localaudio.player.data.library.LibraryStore
import com.localaudio.player.data.library.LibraryRepository
import com.localaudio.player.data.scan.MediaScanner
import com.localaudio.player.data.settings.SettingsRepository
import com.localaudio.player.playback.PlaybackConnection
import com.localaudio.player.playback.PlaybackStore

class AppContainer(context: Context) {
    private val context = context.applicationContext
    val settingsRepository = SettingsRepository(context)
    val libraryStore = LibraryStore(context)
    val libraryRepository = LibraryRepository(context, MediaScanner(context), libraryStore)
    val playbackStore = PlaybackStore(context)

    fun createPlaybackConnection(): PlaybackConnection = PlaybackConnection(context)
}
