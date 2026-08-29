package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.SupabaseAccountRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-007a/b/d, US5 AS5: both sign-out modes end the session; only the
 * removing one clears local records, and neither one touches the account or
 * the catalogue.
 */
class SignOutModesTest : DbTestBase() {

    private val userId = "user-1"
    private val email = "user@example.test"

    private fun newAccountRepository() = SupabaseAccountRepository(
        client = null,
        accountScope = AccountScope(db.accountScopeDao(), time),
        db = db,
        outbox = Outbox(db, time),
        time = time,
    )

    private suspend fun seedSignedInHistory(fake: FakeRemoteDataSource): Map<LocalDate, Pair<Int, Int>> {
        catalogue.seedIfNeeded()
        val engine = SyncEngine(db, Outbox(db, time), AccountScope(db.accountScopeDao(), time), fake, catalogue, time)
        val totals = LinkedHashMap<LocalDate, Pair<Int, Int>>()
        val start = time.today().minusDays(4)
        for (i in 0..4) {
            val date = start.plusDays(i.toLong())
            time.setDate(date)
            dayPlans.ensurePlanFor(date)
            val plan = requireNotNull(dayPlans.planFor(date))
            plan.plannedTasks.take(2).forEach { completions.record(date, it.taskSlug) }
            val score = scoreDay(plan, completions.liveBetween(date, date))
            totals[date] = score.earned to score.available
        }
        engine.migrateOnSignIn(userId)
        return totals
    }

    @Test
    fun keep_local_records_leaves_every_row_intact_and_recording_still_works() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val before = seedSignedInHistory(fake)

        newAccountRepository().signOut(SignOutMode.KEEP_LOCAL_RECORDS)

        for ((date, expected) in before) {
            val plan = requireNotNull(dayPlans.planFor(date))
            val score = scoreDay(plan, completions.liveBetween(date, date))
            assertEquals("earned points changed for $date", expected.first, score.earned)
            assertEquals("available points changed for $date", expected.second, score.available)
        }

        val today = time.today()
        dayPlans.ensurePlanFor(today)
        val plan = requireNotNull(dayPlans.planFor(today))
        // seedSignedInHistory already recorded the first two tasks on every
        // seeded date, including today; use one it left untouched.
        val untouchedSlug = plan.plannedTasks.drop(2).first().taskSlug
        val outcome = completions.record(today, untouchedSlug)
        assertTrue("recording must still work after a plain sign-out", outcome is RecordOutcome.Recorded)
    }

    @Test
    fun remove_local_records_clears_records_and_sync_state_but_leaves_the_catalogue_and_the_remote_alone() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        seedSignedInHistory(fake)
        val (remoteDaysBefore, remoteCompletionsBefore) = fake.rows()

        newAccountRepository().signOut(SignOutMode.REMOVE_LOCAL_RECORDS)

        assertEquals(0, db.dayPlanDao().countPlans())
        assertEquals(0, db.completionDao().countAll())
        assertEquals(0, db.outboxDao().count())
        assertNull(db.syncCursorDao().get("backfill_floor"))
        assertNull(db.accountScopeDao().get())
        assertTrue("the catalogue must be untouched", db.catalogueDao().countTasks() > 0)

        val (remoteDaysAfter, remoteCompletionsAfter) = fake.rows()
        assertEquals("the fake remote must still hold everything it had accepted", remoteDaysBefore, remoteDaysAfter)
        assertEquals(remoteCompletionsBefore, remoteCompletionsAfter)
    }

    @Test
    fun signing_the_same_account_back_in_returns_the_full_record_with_no_duplication() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val before = seedSignedInHistory(fake)
        newAccountRepository().signOut(SignOutMode.REMOVE_LOCAL_RECORDS)

        val accountScope = AccountScope(db.accountScopeDao(), time)
        accountScope.set(userId, email, null)
        val engine = SyncEngine(db, Outbox(db, time), accountScope, fake, catalogue, time)
        engine.migrateOnSignIn(userId)
        engine.pull()

        for ((date, expected) in before) {
            val plan = requireNotNull(dayPlans.planFor(date))
            val score = scoreDay(plan, completions.liveBetween(date, date))
            assertEquals("earned points changed for $date", expected.first, score.earned)
            assertEquals("available points changed for $date", expected.second, score.available)
        }
        val (remoteDaysAfter, _) = fake.rows()
        assertEquals("re-signing in must not duplicate a single remote day record", before.size, remoteDaysAfter.size)
    }
}
