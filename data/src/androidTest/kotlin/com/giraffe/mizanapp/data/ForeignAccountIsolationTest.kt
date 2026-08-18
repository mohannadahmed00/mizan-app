package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.SupabaseAccountRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.LocalRecordWipe
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-013, FR-013a, US5 AS6: a different account signing in on a device that
 * already holds one account's records is detected entirely locally (research
 * R8) and never attributed to the wrong account.
 *
 * The confirmation half (`replaceLocalRecords = false`) is exercised directly
 * against [SupabaseAccountRepository] — it never needs a live code, since the
 * conflict is read from [AccountScope] alone. The replace half needs an
 * actually-verified one-time code, which only a live Supabase project can
 * produce (research R14 — the one thin live test is `T131`); what this test
 * can and does prove locally is exactly what T126's replace branch does
 * *after* that verification succeeds — wipe, then re-attribute — using
 * [LocalRecordWipe] and [AccountScope] the same way the real branch does.
 */
class ForeignAccountIsolationTest : DbTestBase() {

    private val userA = "user-A"
    private val emailA = "a@example.test"
    private val userB = "user-B"
    private val emailB = "b@example.test"

    private fun newAccountRepository() = SupabaseAccountRepository(
        client = null,
        accountScope = AccountScope(db.accountScopeDao(), time),
        db = db,
        outbox = Outbox(db, time),
        time = time,
    )

    private suspend fun seedSignedInAsA(fake: FakeRemoteDataSource) {
        catalogue.seedIfNeeded()
        dayPlans.ensurePlanFor(time.today())
        val plan = requireNotNull(dayPlans.planFor(time.today()))
        completions.record(time.today(), plan.plannedTasks.first().taskSlug)
        val engine = SyncEngine(db, Outbox(db, time), AccountScope(db.accountScopeDao(), time), fake, catalogue, time)
        engine.migrateOnSignIn(userA)
    }

    @Test
    fun a_different_address_returns_the_confirmation_and_opens_no_session_and_changes_nothing() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userA }
        seedSignedInAsA(fake)
        val recordedDaysBefore = db.dayPlanDao().countPlans()
        val completionCountBefore = db.completionDao().countAll()
        val unsyncedBefore = db.outboxDao().count()

        val result = newAccountRepository().confirmCode(emailB, "000000", replaceLocalRecords = false)

        assertTrue(result is CodeConfirmation.WouldReplaceLocalRecords)
        val confirmation = result as CodeConfirmation.WouldReplaceLocalRecords
        assertEquals(emailA, confirmation.currentEmail)
        assertEquals(recordedDaysBefore, confirmation.recordedDays)
        assertEquals(completionCountBefore, confirmation.completionCount)
        assertEquals(unsyncedBefore, confirmation.unsyncedCount)

        // Nothing changed: A is still the scoped account and every row is intact.
        val scope = AccountScope(db.accountScopeDao(), time).current()
        assertTrue(scope is AccountSession.SignedIn)
        assertEquals(userA, (scope as AccountSession.SignedIn).userId)
        assertEquals(recordedDaysBefore, db.dayPlanDao().countPlans())
        assertEquals(completionCountBefore, db.completionDao().countAll())
    }

    @Test
    fun replacing_wipes_A_locally_and_attributes_nothing_of_A_to_B() = runBlocking {
        val fakeA = FakeRemoteDataSource().apply { currentUserId = userA }
        seedSignedInAsA(fakeA)
        val (remoteDaysForA, remoteCompletionsForA) = fakeA.rows()
        assertTrue("sanity: A's history reached the account", remoteDaysForA.isNotEmpty())

        // What T126's replace branch does after a successful code verification.
        LocalRecordWipe(db).wipe()
        val accountScope = AccountScope(db.accountScopeDao(), time)
        accountScope.set(userB, emailB, null)

        assertEquals(0, db.dayPlanDao().countPlans())
        assertEquals(0, db.completionDao().countAll())
        assertEquals(0, db.outboxDao().count())

        val session = accountScope.current()
        assertTrue(session is AccountSession.SignedIn)
        assertEquals(userB, (session as AccountSession.SignedIn).userId)

        // B's account received nothing belonging to A: LocalRecordWipe never
        // issues a remote call, so the fake — still holding exactly what it
        // accepted for A — is unchanged and none of it carries B's id.
        val (remoteDaysAfter, remoteCompletionsAfter) = fakeA.rows()
        assertEquals(remoteDaysForA, remoteDaysAfter)
        assertEquals(remoteCompletionsForA, remoteCompletionsAfter)
        assertTrue(remoteDaysAfter.all { it.userId == userA })
    }
}
