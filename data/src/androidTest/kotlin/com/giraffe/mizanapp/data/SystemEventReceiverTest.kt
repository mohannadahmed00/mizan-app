package com.giraffe.mizanapp.data

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.giraffe.mizanapp.data.notification.SystemEventReceiver
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SystemEventReceiverTest {

    private lateinit var context: Context
    private val receiver = SystemEventReceiver()

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun refreshCount() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SystemEventReceiver.REFRESH_WORK_NAME).get().count { !it.state.isFinished }

    @Test fun bootCompletedEnqueuesBareRefresh() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, refreshCount())
    }

    @Test fun myPackageReplacedEnqueuesBareRefresh() {
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(1, refreshCount())
    }

    @Test fun timezoneChangedEnqueuesBareRefresh() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(1, refreshCount())
    }

    @Test fun timeSetEnqueuesBareRefresh() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        assertEquals(1, refreshCount())
    }

    @Test fun unrelatedActionEnqueuesNothing() {
        receiver.onReceive(context, Intent("some.unrelated.ACTION"))
        assertEquals(0, refreshCount())
    }
}
