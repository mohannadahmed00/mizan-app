package com.giraffe.mizanapp.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.notification.AlarmNotificationScheduler
import com.giraffe.mizanapp.data.notification.NotificationTriggerReceiver
import com.giraffe.mizanapp.domain.notification.AnchorSubject
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.anchorKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmNotificationSchedulerTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = AlarmNotificationScheduler(context)
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun anchor(sectionId: String, minute: Long) = NotificationAnchor(
        NotificationCategory.PRAYER_WINDOW,
        Instant.parse("2026-09-04T12:00:00Z").plusSeconds(minute * 60),
        AnchorSubject.PrayerWindow(LocalDate.of(2026, 9, 4), sectionId, Instant.parse("2026-09-04T13:00:00Z")),
    )

    private fun pendingIntentExists(key: String): Boolean {
        val intent = Intent(context, NotificationTriggerReceiver::class.java).putExtra(AlarmNotificationScheduler.EXTRA_ANCHOR_KEY, key)
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
    }

    @Test fun replaceAllReplacesThePreviousSetEntirely() = runBlocking {
        val a = anchor("fajr", 1)
        val b = anchor("dhuhr", 2)
        val c = anchor("asr", 3)
        scheduler.replaceAll(listOf(a, b, c))
        assertEquals(true, pendingIntentExists(a.anchorKey))
        val d = anchor("maghrib", 4)
        scheduler.replaceAll(listOf(d))
        assertEquals(false, pendingIntentExists(a.anchorKey))
        assertEquals(false, pendingIntentExists(b.anchorKey))
        assertEquals(false, pendingIntentExists(c.anchorKey))
        assertEquals(true, pendingIntentExists(d.anchorKey))
        scheduler.cancelAll()
    }

    @Test fun cancelAllLeavesNonePending() = runBlocking {
        val a = anchor("fajr", 1)
        val b = anchor("dhuhr", 2)
        scheduler.replaceAll(listOf(a, b))
        scheduler.cancelAll()
        assertEquals(false, pendingIntentExists(a.anchorKey))
        assertEquals(false, pendingIntentExists(b.anchorKey))
    }

    @Test fun deliveryModeMatchesExactAlarmAvailability() {
        val expected = if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) DeliveryMode.EXACT else DeliveryMode.RELAXED
        assertEquals(expected, scheduler.deliveryMode())
    }

    @Test fun scheduleRefreshSurvivesReplaceAllOfUnrelatedAnchors() = runBlocking {
        scheduler.scheduleRefresh(Instant.parse("2026-09-04T23:50:00Z"))
        scheduler.replaceAll(listOf(anchor("fajr", 1)))
        assertEquals(true, pendingIntentExists(AlarmNotificationScheduler.REFRESH_KEY))
        scheduler.cancelAll()
    }

    @Test fun scheduleAtSurvivesReplaceAllOfUnrelatedAnchors() = runBlocking {
        val heldKey = "WEEK:2026-08-29"
        scheduler.scheduleAt(heldKey, Instant.parse("2026-09-04T22:00:00Z"))
        scheduler.replaceAll(listOf(anchor("fajr", 1)))
        assertEquals(true, pendingIntentExists(heldKey))
        scheduler.cancelAll()
    }
}
