package com.localaudio.player.di

import android.content.Context
import com.localaudio.player.data.database.AudioDatabase
import com.localaudio.player.data.library.LibraryStore
import com.localaudio.player.data.library.LibraryRepository
import com.localaudio.player.data.library.RecycleBinRepository
import com.localaudio.player.data.library.RecycleBinStore
import com.localaudio.player.data.scan.MediaScanner
import com.localaudio.player.data.settings.SettingsRepository
import com.localaudio.player.data.skip.AutoSkipRepository
import com.localaudio.player.data.skip.AutoSkipStore
import com.localaudio.player.data.skip.DirectorySkipRepository
import com.localaudio.player.data.skip.DirectorySkipStore
import com.localaudio.player.playback.PlaybackConnection
import com.localaudio.player.playback.PlaybackStore

class AppContainer(context: Context) {
    private val context = context.applicationContext
    private val database = AudioDatabase(context)
    val settingsRepository = SettingsRepository(context)
    val autoSkipRepository = AutoSkipRepository(AutoSkipStore(database))
    val directorySkipRepository = DirectorySkipRepository(DirectorySkipStore(context))
    val recycleBinRepository = RecycleBinRepository(RecycleBinStore(database))
    private val libraryStore = LibraryStore(context, database)
    val libraryRepository = LibraryRepository(
        context = context,
        scanner = MediaScanner(context),
        store = libraryStore,
        autoSkipRepository = autoSkipRepository,
        directorySkipRepository = directorySkipRepository,
        recycleBinRepository = recycleBinRepository,
    )
    val playbackStore = PlaybackStore(context)

    fun createPlaybackConnection(): PlaybackConnection = PlaybackConnection(context)
}
