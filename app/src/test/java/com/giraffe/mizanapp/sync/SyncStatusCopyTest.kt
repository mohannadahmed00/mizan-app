package com.giraffe.mizanapp.sync

import com.giraffe.mizanapp.domain.sync.SyncStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every [SyncStatus] renders exactly the copy `contracts/ui-state.md` names,
 * and none of it uses a word Principle IX and Conventions §6 forbid
 * (SC-011).
 */
class SyncStatusCopyTest {

    private val forbidden = listOf(
        "failed", "failure", "error", "lost", "missing", "problem", "wrong",
        "you didn't", "you haven't", "retry now",
    )

    @Test
    fun `NotSignedIn renders nothing`() {
        assertNull(syncStatusText(SyncStatus.NotSignedIn))
    }

    @Test
    fun `UpToDate renders Backed up`() {
        assertEquals("Backed up", syncStatusText(SyncStatus.UpToDate))
    }

    @Test
    fun `Pending renders the count`() {
        assertEquals("7 changes waiting to be sent", syncStatusText(SyncStatus.Pending(7)))
    }

    @Test
    fun `NotSyncing renders Not syncing right now`() {
        assertEquals("Not syncing right now", syncStatusText(SyncStatus.NotSyncing))
    }

    @Test
    fun `LoadingEarlierDays renders Still loading earlier days`() {
        assertEquals(
            "Still loading earlier days",
            syncStatusText(SyncStatus.LoadingEarlierDays(LocalDate.of(2026, 6, 1))),
        )
    }

    @Test
    fun `no rendered copy contains a forbidden word`() {
        val statuses = listOf(
            SyncStatus.NotSignedIn,
            SyncStatus.UpToDate,
            SyncStatus.Pending(3),
            SyncStatus.NotSyncing,
            SyncStatus.LoadingEarlierDays(null),
        )
        for (status in statuses) {
            val text = syncStatusText(status) ?: continue
            for (word in forbidden) {
                assertFalse("\"$text\" must not contain \"$word\"", text.contains(word, ignoreCase = true))
            }
        }
    }
}
