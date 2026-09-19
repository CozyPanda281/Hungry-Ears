package com.hungryears.music

import android.app.Application
import com.hungryears.music.di.AppContainer

class HungryEarsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}