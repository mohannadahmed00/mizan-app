package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.DrainOutcome
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.identity.AccountSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A session that cannot be renewed ends locally, but **nothing else moves**
 * (FR-006, US2 AS6): every record, the whole outbox, and `account_scope` all
 * stay exactly as they were, and recording keeps working. Ending the *live*
 * Supabase session itself is exercised through [SyncEngine]'s injected
 * callback here — driving a genuine authenticated Supabase session to expiry
 * would need a real prior sign-in, which is exercised by hand in quickstart
 * SC-001 rather than scripted in this suite.
 */
class SessionExpiryTest : DbTestBase() {

    private val userId = "user-1"

    @Test
    fun a_session_that_cannot_be_renewed_ends_locally_and_touches_nothing_else() = runBlocking {
        seedAndPlanToday()
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        accountScope.set(userId, "user@example.test", null)

        val plan = requireNotNull(dayPlans.planFor(time.today()))
        completions.record(time.today(), plan.plannedTasks.first().taskSlug)
        val expectedScore = scoreDay(plan, completions.liveBetween(time.today(), time.today()))

        var sessionEndedCalls = 0
        val engine = SyncEngine(db, outbox, accountScope, fake, time) { sessionEndedCalls++ }

        engine.claimLocalRecords(userId)
        engine.enqueueUnsynced()
        val pendingBefore = db.outboxDao().count()
        // The token could not be renewed — every write now reports NotAuthenticated.
        fake.forceNotAuthenticated = true

        val outcome = engine.drain()

        assertEquals(DrainOutcome.StoppedUnauthenticated, outcome)
        assertEquals(1, sessionEndedCalls)

        // Every record is untouched.
        val scoreAfter = scoreDay(requireNotNull(dayPlans.planFor(time.today())), completions.liveBetween(time.today(), time.today()))
        assertEquals(expectedScore.earned, scoreAfter.earned)
        assertEquals(expectedScore.available, scoreAfter.available)

        // The outbox still holds every undrained entry.
        assertEquals(pendingBefore, db.outboxDao().count())

        // account_scope is untouched.
        assertEquals(AccountSession.SignedIn(userId, "user@example.test", null), accountScope.current())

        // Recording still works.
        val recordOutcome = completions.record(time.today(), plan.plannedTasks[1].taskSlug)
        assertTrue(recordOutcome is com.giraffe.mizanapp.domain.repository.RecordOutcome.Recorded)
    }
}
