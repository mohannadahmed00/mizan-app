package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A migration interrupted mid-way — connection dropped after some rows were
 * already durably written server-side — must be resumable without ever
 * duplicating a remote row or losing a local one (US1 AS4, US2 AS4/AS5).
 */
class SignInMigrationResumeTest : DbTestBase() {

    private val userId = "user-1"
    private val startDate: LocalDate = LocalDate.of(2026, 7, 27)

    private suspend fun seed21Days() {
        catalogue.seedIfNeeded()
        for (i in 0 until 21) {
            val date = startDate.plusDays(i.toLong())
            time.setDate(date)
            dayPlans.ensurePlanFor(date)
            val plan = requireNotNull(dayPlans.planFor(date))
            plan.plannedTasks.forEachIndexed { index, task ->
                if (index % 2 == 0) completions.record(date, task.taskSlug)
            }
        }
    }

    private fun engineOver(fake: FakeRemoteDataSource): SyncEngine {
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        return SyncEngine(db, outbox, accountScope, fake, catalogue, time)
    }

    private fun assertSingleCopyOfEverythingAndLocalRowsIntact(fake: FakeRemoteDataSource) = runBlocking {
        assertTrue(db.dayPlanDao().unsynced().isEmpty())
        assertTrue(db.completionDao().unsynced().isEmpty())

        val (dayRecords, remoteCompletions) = fake.rows()
        assertEquals(21, dayRecords.size)
        assertEquals(dayRecords.size, dayRecords.distinctBy { it.userId to it.date }.size)
        assertEquals(remoteCompletions.size, remoteCompletions.distinctBy { it.id }.size)

        val localCompletionCount = completions.liveBetween(startDate, startDate.plusDays(20)).size
        assertEquals(localCompletionCount, remoteCompletions.size)
    }

    private fun resumeCase(dropAfter: Int) = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val engine = engineOver(fake)
        seed21Days()

        fake.dropAfter = dropAfter
        engine.migrateOnSignIn(userId)
        engine.migrateOnSignIn(userId)

        assertSingleCopyOfEverythingAndLocalRowsIntact(fake)
    }

    @Test
    fun resumes_cleanly_after_dropping_5_rows() = resumeCase(5)

    @Test
    fun resumes_cleanly_after_dropping_50_rows() = resumeCase(50)

    @Test
    fun resumes_cleanly_after_dropping_150_rows() = resumeCase(150)

    /**
     * The ambiguous case: the client cannot tell whether the write landed, so
     * it marks its rows `syncedAt` on the server's word alone. Whatever
     * *did* land must never appear twice, and every local row must stay
     * exactly as recorded — an ambiguous remote acknowledgement is never
     * grounds to touch a local figure (FR-010).
     */
    @Test
    fun an_ambiguous_acknowledged_but_discarded_write_never_duplicates_or_corrupts_local_state() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val engine = engineOver(fake)
        seed21Days()
        val expectedCompletionCount = completions.liveBetween(startDate, startDate.plusDays(20)).size

        fake.acknowledgeButDiscard = true
        engine.migrateOnSignIn(userId)
        fake.acknowledgeButDiscard = false
        engine.migrateOnSignIn(userId)

        val (dayRecords, remoteCompletions) = fake.rows()
        assertEquals(dayRecords.size, dayRecords.distinctBy { it.userId to it.date }.size)
        assertEquals(remoteCompletions.size, remoteCompletions.distinctBy { it.id }.size)

        val localCompletionCount = completions.liveBetween(startDate, startDate.plusDays(20)).size
        assertEquals(expectedCompletionCount, localCompletionCount)
    }
}
