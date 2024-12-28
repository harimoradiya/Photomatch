package com.example.photomatch

import android.app.Application
import com.example.photomatch.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PhotoMatchApp :  Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@PhotoMatchApp)
            modules(appModule)
        }
    }
}