package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.SyncingCompletionRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.streak.buildStreakSummary
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC-016: a completion recorded offline that lands only after its
 * leaderboard period has already closed changes nothing about that closed
 * period, and still counts in full everywhere the participant's own record
 * is read (FR-025a). Both halves are required — the second is what bounds
 * the offline-freeze tradeoff.
 */
class LateSyncAfterFreezeTest : DbTestBase() {

    private val userId = "user-1"

    @Test
    fun a_late_sync_never_moves_a_closed_period_but_the_day_still_counts_locally_in_full() = runBlocking {
        seedAndPlanToday()
        val today = time.today()
        val plan = requireNotNull(dayPlans.planFor(today))

        val accountScope = AccountScope(db.accountScopeDao(), time)
        accountScope.set(userId, "person@example.test", null)
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val outbox = Outbox(db, time)
        val syncing = SyncingCompletionRepository(completions, outbox, accountScope, db)

        // The leaderboard period is already closed before the device goes offline.
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
        fake.seedEntries(PeriodKind.WEEKLY, REGION, emptyList(), periodStart = weekStart(today))
        fake.markPeriodClosed(PeriodKind.WEEKLY, REGION)
        val closedBefore = Json.encodeToString((fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value)

        // A full day, recorded entirely offline.
        fake.unreachable = true
        plan.plannedTasks.forEach { syncing.record(today, it.taskSlug) }
        val scoreBefore = scoreDay(plan, completions.liveBetween(today, today))
        val datesBefore = completions.observeConsistencyDates().first()
        val streakBefore = buildStreakSummary(datesBefore, time.today(), time.now(), time.today().plusDays(1).atStartOfDay(time.zone()).toInstant(), dayPlans.earliestPlanDate())

        // Reconnect: the day's completions upload, then the aggregation runs.
        fake.unreachable = false
        val engine = SyncEngine(db, outbox, accountScope, fake, catalogue, time)
        engine.drain()
        fake.recomputeOpenPeriods()

        val closedAfter = Json.encodeToString((fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value)
        assertEquals("a completion arriving after the freeze must not enter the closed period", closedBefore, closedAfter)

        val scoreAfter = scoreDay(requireNotNull(dayPlans.planFor(today)), completions.liveBetween(today, today))
        val datesAfter = completions.observeConsistencyDates().first()
        val streakAfter = buildStreakSummary(datesAfter, time.today(), time.now(), time.today().plusDays(1).atStartOfDay(time.zone()).toInstant(), dayPlans.earliestPlanDate())
        assertEquals("Today/Week/History must still count the day in full", scoreBefore, scoreAfter)
        assertEquals("the streak must still count the day in full", streakBefore, streakAfter)
        assertTrue("at least one task must have been recorded", scoreAfter.earned > 0)
    }

    private fun weekStart(date: LocalDate): String = WeekBoundary.weekContaining(date).start.toString()

    private companion object {
        const val REGION = "egypt-cairo"
    }
}
