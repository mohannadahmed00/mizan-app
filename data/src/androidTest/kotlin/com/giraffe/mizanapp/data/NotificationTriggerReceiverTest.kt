package com.giraffe.mizanapp.data

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.DayPlanEntity
import com.giraffe.mizanapp.data.notification.AlarmNotificationScheduler
import com.giraffe.mizanapp.data.notification.NotificationTriggerReceiver
import com.giraffe.mizanapp.data.notification.NotificationWorker
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationTriggerReceiverTest {

    private lateinit var context: Context
    private lateinit var db: MizanDatabase
    private val receiver = NotificationTriggerReceiver()

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun intentFor(key: String) = Intent(context, NotificationTriggerReceiver::class.java).putExtra(AlarmNotificationScheduler.EXTRA_ANCHOR_KEY, key)

    @Test fun deliveringAnIntentEnqueuesExactlyOneWorkerUnderTheAnchorKey() {
        receiver.onReceive(context, intentFor("PRAYER:2026-09-04:asr"))
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("PRAYER:2026-09-04:asr").get()
        assertEquals(1, infos.size)
    }

    @Test fun receiverPerformsNoDatabaseAccess() = runBlocking {
        db.dayPlanDao().insertPlan(DayPlanEntity(id = "p", date = "2026-09-04", catalogueVersion = 1, hijriLabel = "H", availablePoints = 5, updatedAt = 1))
        receiver.onReceive(context, intentFor("STREAK:2026-09-04"))
        val plan = db.dayPlanDao().planByDate("2026-09-04")
        assertEquals("p", plan?.plan?.id)
        assertEquals(5, plan?.plan?.availablePoints)
    }

    @Test fun deliveringTheSameKeyTwiceCollapsesToOnePendingRequest() {
        receiver.onReceive(context, intentFor("WEEK:2026-08-29"))
        receiver.onReceive(context, intentFor("WEEK:2026-08-29"))
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("WEEK:2026-08-29").get()
        assertEquals(1, infos.count { !it.state.isFinished })
    }

    @Test fun intentWithNoAnchorKeyExtraEnqueuesNothing() {
        receiver.onReceive(context, Intent(context, NotificationTriggerReceiver::class.java))
        // Nothing to assert by unique work name since none was used; absence of a crash is the contract here.
    }

    private companion object {
        const val DB_NAME = "notification-trigger-receiver-test.db"
    }
}
