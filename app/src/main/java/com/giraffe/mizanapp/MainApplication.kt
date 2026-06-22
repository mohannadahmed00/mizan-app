package com.giraffe.mizanapp

import android.app.Application
import com.giraffe.mizanapp.di.MyApp
import com.giraffe.mizanapp.worker.SyncHijriDatesWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.plugin.module.dsl.startKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin<MyApp> {
            androidContext(this@MainApplication)
            workManagerFactory()
        }
        SyncHijriDatesWorker.scheduleInitialSync(this)
    }
}