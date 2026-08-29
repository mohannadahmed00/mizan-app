package com.giraffe.mizanapp.domain.sync

import com.giraffe.mizanapp.domain.identity.AccountSession
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeriveSyncStatusTest {

    private val floor: LocalDate = LocalDate.of(2026, 6, 1)
    private val signedIn = AccountSession.SignedIn(userId = "u-1", email = "a@example.test")
    private val settled = RecordCoverage.completeFrom(floor)
    private val backfilling = RecordCoverage(knownFrom = floor, complete = false)

    @Test
    fun `signed out is signed out, whatever else is true`() {
        assertEquals(
            SyncStatus.NotSignedIn,
            deriveSyncStatus(AccountSession.SignedOut, pendingCount = 0, reachable = true, coverage = settled),
        )
        assertEquals(
            SyncStatus.NotSignedIn,
            deriveSyncStatus(AccountSession.SignedOut, pendingCount = 12, reachable = false, coverage = backfilling),
        )
    }

    @Test
    fun `nothing pending and reachable is up to date`() {
        assertEquals(
            SyncStatus.UpToDate,
            deriveSyncStatus(signedIn, pendingCount = 0, reachable = true, coverage = settled),
        )
    }

    @Test
    fun `nothing pending and unreachable is still up to date`() {
        // There is nothing waiting to be sent, so being offline is not a state
        // worth reporting at all (Principle IX).
        assertEquals(
            SyncStatus.UpToDate,
            deriveSyncStatus(signedIn, pendingCount = 0, reachable = false, coverage = settled),
        )
    }

    @Test
    fun `pending and reachable reports the count`() {
        assertEquals(
            SyncStatus.Pending(7),
            deriveSyncStatus(signedIn, pendingCount = 7, reachable = true, coverage = settled),
        )
    }

    @Test
    fun `pending and unreachable is not syncing`() {
        assertEquals(
            SyncStatus.NotSyncing,
            deriveSyncStatus(signedIn, pendingCount = 7, reachable = false, coverage = settled),
        )
    }

    /**
     * The precedence that matters: a user whose earlier history is still
     * arriving must be told that before being told about a queue, because it is
     * what explains a screen that looks emptier than it should (FR-023b).
     */
    @Test
    fun `incomplete coverage outranks a pending queue`() {
        assertEquals(
            SyncStatus.LoadingEarlierDays(floor),
            deriveSyncStatus(signedIn, pendingCount = 30, reachable = true, coverage = backfilling),
        )
        assertEquals(
            SyncStatus.LoadingEarlierDays(floor),
            deriveSyncStatus(signedIn, pendingCount = 30, reachable = false, coverage = backfilling),
        )
    }

    @Test
    fun `the loading state carries the floor so the UI can say how far back it has got`() {
        val status = deriveSyncStatus(signedIn, pendingCount = 0, reachable = true, coverage = backfilling)

        assertEquals(SyncStatus.LoadingEarlierDays(floor), status)
    }

    /**
     * Principle IX, enforced structurally rather than by reading the copy: no
     * combination of inputs may produce a state outside the five that exist,
     * and none of the five is a failure.
     */
    @Test
    fun `no combination of inputs produces a failure state`() {
        val sessions = listOf(AccountSession.SignedOut, signedIn)
        val counts = listOf(0, 1, 500)
        val reachability = listOf(true, false)
        val coverages = listOf(settled, backfilling, RecordCoverage(null, complete = false))

        for (session in sessions) {
            for (count in counts) {
                for (reachable in reachability) {
                    for (coverage in coverages) {
                        val status = deriveSyncStatus(session, count, reachable, coverage)
                        assertTrue(
                            "unexpected status $status",
                            status is SyncStatus.NotSignedIn ||
                                status is SyncStatus.UpToDate ||
                                status is SyncStatus.Pending ||
                                status is SyncStatus.NotSyncing ||
                                status is SyncStatus.LoadingEarlierDays,
                        )
                    }
                }
            }
        }
    }
}
