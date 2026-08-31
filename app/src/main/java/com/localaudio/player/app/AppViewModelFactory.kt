package com.localaudio.player.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.localaudio.player.di.AppContainer

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(
            libraryRepository = container.libraryRepository,
            settingsRepository = container.settingsRepository,
            autoSkipRepository = container.autoSkipRepository,
            directorySkipRepository = container.directorySkipRepository,
            recycleBinRepository = container.recycleBinRepository,
            playbackConnection = container.createPlaybackConnection(),
            homeRowsBuilder = HomeRowsBuilder(),
        ) as T
    }
}
