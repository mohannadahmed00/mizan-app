package com.giraffe.mizanapp

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkerFactory
import com.giraffe.mizanapp.data.sync.SyncScheduler
import com.giraffe.mizanapp.di.mizanModules
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

/**
 * [Configuration.Provider] is what makes [SyncWorker][com.giraffe.mizanapp.data.sync.SyncWorker]
 * constructible at all: the manifest disables WorkManager's default
 * auto-init (which would otherwise run before [onCreate] and initialize
 * WorkManager with the reflection factory, before Koin's worker constructor
 * is ever registered), so WorkManager instead pulls its
 * [Configuration] from here — lazily, on first use, which is always after
 * [onCreate] has started Koin.
 */
class MizanApplication : Application(), Configuration.Provider, KoinComponent {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(get<WorkerFactory>()).build()

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
