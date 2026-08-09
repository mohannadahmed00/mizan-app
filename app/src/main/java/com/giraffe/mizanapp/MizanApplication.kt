package com.giraffe.mizanapp

import android.app.Application
import com.giraffe.mizanapp.di.mizanModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MizanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MizanApplication)
            modules(mizanModules)
        }
    }
}
