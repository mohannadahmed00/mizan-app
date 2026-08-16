package com.giraffe.mizanapp

import android.app.Application
import com.giraffe.mizanapp.data.sync.SyncScheduler
import com.giraffe.mizanapp.di.mizanModules
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class MizanApplication : Application(), KoinComponent {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MizanApplication)
            workManagerFactory()
            modules(mizanModules)
        }
        // Picks up anything already queued from a previous run — e.g. a
        // change recorded just before the app was last closed offline.
        get<SyncScheduler>().schedule()
    }
}
