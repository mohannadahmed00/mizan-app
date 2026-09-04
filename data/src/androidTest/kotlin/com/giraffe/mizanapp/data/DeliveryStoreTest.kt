package com.giraffe.mizanapp.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.notification.DeliveryStore
import com.giraffe.mizanapp.domain.notification.DeliveryRecord
import com.giraffe.mizanapp.domain.notification.DeliveryState
import com.giraffe.mizanapp.domain.notification.DiscardReason
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeliveryStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MizanDatabase
    private lateinit var store: DeliveryStore

    @Before fun setUp() {
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, TEST_DB).build()
        store = DeliveryStore(db.notificationDao())
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test fun writingThenReadingRoundTripsEveryFieldIncludingNullReasonAndNullHeldUntil() = runBlocking {
        val record = DeliveryRecord("WEEK:2026-08-29", NotificationCategory.WEEKLY_SUMMARY, DeliveryState.DELIVERED, null, Instant.parse("2026-09-04T18:00:00Z"), null)
        store.record(record)
        assertEquals(record, store.records().single())
    }

    @Test fun writingTheSameAnchorKeyTwiceLeavesExactlyOneRow() = runBlocking {
        val key = "STREAK:2026-09-04"
        store.record(DeliveryRecord(key, NotificationCategory.STREAK_AT_RISK, DeliveryState.HELD, null, Instant.parse("2026-09-04T18:00:00Z"), Instant.parse("2026-09-04T19:00:00Z")))
        store.record(DeliveryRecord(key, NotificationCategory.STREAK_AT_RISK, DeliveryState.DISCARDED, DiscardReason.QUIET_HOURS, Instant.parse("2026-09-04T20:00:00Z"), null))
        val rows = store.records()
        assertEquals(1, rows.size)
        assertEquals(DeliveryState.DISCARDED, rows.single().state)
    }

    @Test fun pruneBeforeRemovesOnlyRowsOlderThanCutoff() = runBlocking {
        val old = DeliveryRecord("WEEK:2026-01-01", NotificationCategory.WEEKLY_SUMMARY, DeliveryState.DELIVERED, null, Instant.parse("2026-01-01T00:00:00Z"), null)
        val recent = DeliveryRecord("WEEK:2026-09-04", NotificationCategory.WEEKLY_SUMMARY, DeliveryState.DELIVERED, null, Instant.parse("2026-09-04T18:00:00Z"), null)
        store.record(old)
        store.record(recent)
        store.prune(Instant.parse("2026-09-04T18:00:00Z"))
        assertEquals(listOf(recent), store.records())
    }

    @Test fun emptyTableReadsAsEmptyListNeverNull() = runBlocking {
        assertTrue(store.records().isEmpty())
    }

    private companion object {
        const val TEST_DB = "delivery-store-test.db"
    }
}
