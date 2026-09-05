package com.giraffe.mizanapp.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.notification.NotificationPreferencesStore
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationPreferences
import com.giraffe.mizanapp.domain.notification.QuietHours
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationPreferencesStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MizanDatabase
    private lateinit var store: NotificationPreferencesStore

    @Before fun setUp() {
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, TEST_DB).build()
        store = NotificationPreferencesStore(db.notificationDao())
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test fun freshDatabaseReturnsDefault() = runBlocking {
        assertEquals(NotificationPreferences.DEFAULT, store.preferences())
    }

    @Test fun savingAndReloadingRoundTripsEveryFieldIncludingMidnightCrossingQuietHours() = runBlocking {
        val value = NotificationPreferences(
            enabled = setOf(NotificationCategory.PRAYER_WINDOW, NotificationCategory.WEEKLY_SUMMARY),
            allSilenced = true,
            quietHours = QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0)),
        )
        store.save(value)
        assertEquals(value, store.preferences())
    }

    @Test fun observePreferencesEmitsAfterSave() = runBlocking {
        val value = NotificationPreferences(setOf(NotificationCategory.STREAK_AT_RISK), false, null)
        store.save(value)
        assertEquals(value, store.observePreferences().first())
    }

    private companion object {
        const val TEST_DB = "notification-preferences-test.db"
    }
}
