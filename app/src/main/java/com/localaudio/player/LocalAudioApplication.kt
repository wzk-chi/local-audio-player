package com.localaudio.player

import android.app.Application
import com.localaudio.player.di.AppContainer

class LocalAudioApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
