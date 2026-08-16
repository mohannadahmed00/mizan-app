package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.SyncingCompletionRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Recording and undoing must be indistinguishable from the offline product,
 * whatever the remote is doing (FR-014, SC-002). Asserted structurally — no
 * `RemoteDataSource` reachable from the decorator's constructor at all —
 * rather than by a timing ratio, which would be a flaky proxy for the real
 * guarantee.
 */
class OfflineRecordingUnaffectedTest : DbTestBase() {

    private lateinit var fake: FakeRemoteDataSource
    private lateinit var syncing: SyncingCompletionRepository

    @Before
    fun setUpSyncing() = runBlocking {
        seedAndPlanToday()
        fake = FakeRemoteDataSource().apply { unreachable = true }
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        syncing = SyncingCompletionRepository(completions, outbox, accountScope, db)
    }

    @Test
    fun the_decorator_has_no_RemoteDataSource_constructor_parameter() {
        val hasRemoteParam = SyncingCompletionRepository::class.java.constructors.any { ctor ->
            ctor.parameterTypes.any { RemoteDataSource::class.java.isAssignableFrom(it) }
        }
        assertFalse("SyncingCompletionRepository must not depend on RemoteDataSource", hasRemoteParam)
    }

    @Test
    fun recording_and_undoing_200_times_never_touches_the_remote_and_matches_the_undecorated_outcomes() = runBlocking {
        val slug = "fajr-1"
        repeat(200) {
            val start = System.nanoTime()
            val outcome = syncing.record(time.today(), slug)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("record took ${elapsedMs}ms, expected < 250ms", elapsedMs < 250)
            assertTrue(
                "unexpected outcome type $outcome",
                outcome is RecordOutcome.Recorded || outcome is RecordOutcome.AtLimit,
            )

            val undoStart = System.nanoTime()
            syncing.undoLast(time.today(), slug)
            val undoElapsedMs = (System.nanoTime() - undoStart) / 1_000_000
            assertTrue("undoLast took ${undoElapsedMs}ms, expected < 250ms", undoElapsedMs < 250)
        }

        assertEquals(0L, fake.readCount)
        assertEquals(emptyList<Any>(), fake.rows().first)
        assertEquals(emptyList<Any>(), fake.rows().second)
    }
}
