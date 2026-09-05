package com.giraffe.mizanapp.data

import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.notification.AndroidNotificationPresenter
import com.giraffe.mizanapp.domain.notification.AnchorSubject
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent
import com.giraffe.mizanapp.domain.notification.anchorKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNotificationPresenterTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val presenter = AndroidNotificationPresenter(context)
    private val manager get() = NotificationManagerCompat.from(context)

    private fun anchor(sectionId: String = "asr") = NotificationAnchor(
        NotificationCategory.PRAYER_WINDOW,
        Instant.parse("2026-09-04T12:20:00Z"),
        AnchorSubject.PrayerWindow(LocalDate.of(2026, 9, 4), sectionId, Instant.parse("2026-09-04T13:00:00Z")),
    )

    private fun content(category: NotificationCategory = NotificationCategory.PRAYER_WINDOW) =
        NotificationContent(category, "title", mapOf("body" to "text"), "TODAY:asr")

    private fun activeTags(): Set<String> = manager.activeNotifications.map { it.tag }.toSet()

    @After fun tearDown() = runBlocking {
        manager.activeNotifications.forEach { manager.cancel(it.tag, it.id) }
    }

    @Test fun postingCreatesOneActiveNotificationTaggedWithAnchorKey() = runBlocking {
        val a = anchor()
        presenter.post(a, content())
        assertTrue(a.anchorKey in activeTags())
    }

    @Test fun withdrawRemovesIt() = runBlocking {
        val a = anchor()
        presenter.post(a, content())
        presenter.withdraw(a.anchorKey)
        assertTrue(a.anchorKey !in activeTags())
    }

    @Test fun withdrawForKeyNeverPostedIsNoOp() = runBlocking {
        presenter.withdraw("never-posted-key")
    }

    @Test fun postingTwiceForSameKeyReplacesRatherThanStacks() = runBlocking {
        val a = anchor()
        presenter.post(a, content())
        presenter.post(a, content())
        assertEquals(1, manager.activeNotifications.count { it.tag == a.anchorKey })
    }

    @Test fun eachCategoryGetsItsOwnChannelCreatedBeforeFirstPost() = runBlocking {
        presenter.post(anchor("fajr"), content(NotificationCategory.PRAYER_WINDOW))
        presenter.post(NotificationAnchor(NotificationCategory.STREAK_AT_RISK, Instant.parse("2026-09-04T18:00:00Z"), AnchorSubject.Day(LocalDate.of(2026, 9, 4))), content(NotificationCategory.STREAK_AT_RISK))
        val nm = context.getSystemService(NotificationManager::class.java)
        val channelIds = nm.notificationChannels.map { it.id }.toSet()
        assertTrue("mizan_prayer_window" in channelIds)
        assertTrue("mizan_streak_at_risk" in channelIds)
    }
}
