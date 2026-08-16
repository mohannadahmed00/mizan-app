package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.day.scoreDay
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A device with weeks of local-only history signs in. Every figure must be
 * exactly what it was before, and the account must end up holding one copy of
 * everything (US1, FR-010, FR-011).
 */
class SignInMigrationTest : DbTestBase() {

    private lateinit var fake: FakeRemoteDataSource
    private lateinit var engine: SyncEngine
    private val userId = "user-1"

    @Before
    fun setUpEngine() {
        fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        engine = SyncEngine(db, outbox, accountScope, fake, catalogue, time)
    }

    private val startDate: LocalDate = LocalDate.of(2026, 7, 27)

    private suspend fun seed21Days(): Map<LocalDate, Pair<Int, Int>> {
        catalogue.seedIfNeeded()
        val totals = LinkedHashMap<LocalDate, Pair<Int, Int>>()
        for (i in 0 until 21) {
            val date = startDate.plusDays(i.toLong())
            time.setDate(date)
            dayPlans.ensurePlanFor(date)
            val plan = requireNotNull(dayPlans.planFor(date))
            plan.plannedTasks.forEachIndexed { index, task ->
                if (index % 2 == 0) completions.record(date, task.taskSlug)
            }
            val score = scoreDay(plan, completions.liveBetween(date, date))
            totals[date] = score.earned to score.available
        }
        return totals
    }

    @Test
    fun migration_preserves_every_figure_and_the_account_ends_up_with_one_copy_of_everything() = runBlocking {
        val before = seed21Days()

        engine.migrateOnSignIn(userId)

        for ((date, expected) in before) {
            val plan = requireNotNull(dayPlans.planFor(date))
            val score = scoreDay(plan, completions.liveBetween(date, date))
            assertEquals("earned points changed for $date", expected.first, score.earned)
            assertEquals("available points changed for $date", expected.second, score.available)
        }

        assertTrue(db.dayPlanDao().unsynced().isEmpty())
        assertTrue(db.completionDao().unsynced().isEmpty())

        val (remoteDayRecords, remoteCompletions) = fake.rows()
        assertEquals(21, remoteDayRecords.size)
        assertTrue(remoteDayRecords.all { it.userId == userId })

        val expectedCompletionCount = completions.liveBetween(startDate, startDate.plusDays(20)).size
        assertEquals(expectedCompletionCount, remoteCompletions.size)
        assertTrue(remoteCompletions.all { it.userId == userId })
    }

    @Test
    fun every_local_row_including_planned_tasks_is_claimed_for_the_user() = runBlocking {
        seed21Days()

        engine.migrateOnSignIn(userId)

        db.query("SELECT COUNT(*) FROM day_plans WHERE userId IS NULL", null).use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM planned_tasks WHERE userId IS NULL", null).use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM completions WHERE userId IS NULL", null).use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun re_running_the_whole_migration_changes_nothing_anywhere() = runBlocking {
        val before = seed21Days()
        engine.migrateOnSignIn(userId)
        val (firstDayRecords, firstCompletions) = fake.rows()

        engine.migrateOnSignIn(userId)

        val (secondDayRecords, secondCompletions) = fake.rows()
        assertEquals(firstDayRecords.size, secondDayRecords.size)
        assertEquals(firstCompletions.size, secondCompletions.size)
        for ((date, expected) in before) {
            val plan = requireNotNull(dayPlans.planFor(date))
            val score = scoreDay(plan, completions.liveBetween(date, date))
            assertEquals(expected.first, score.earned)
            assertEquals(expected.second, score.available)
        }
    }
}
