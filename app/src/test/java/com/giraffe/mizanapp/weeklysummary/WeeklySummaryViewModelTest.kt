package com.giraffe.mizanapp.weeklysummary

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetClosedWeekSummary
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.today.FakeCatalogueRepository
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeCompletionRepository
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import com.giraffe.mizanapp.today.FakeRecordCoverageRepository
import com.giraffe.mizanapp.today.loadSeedCatalogue
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklySummaryViewModelTest {

    private lateinit var clock: FakeClock
    private val catalogue = loadSeedCatalogue()

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        clock = FakeClock(instant = LocalDate.of(2026, 9, 18).atTime(9, 0).atZone(java.time.ZoneId.of("Africa/Cairo")).toInstant())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        plans: FakeDayPlanRepository,
        completions: FakeCompletionRepository = FakeCompletionRepository(plans, DayWritePolicy(clock), clock),
        week: WeekKey? = null,
    ): WeeklySummaryViewModel {
        val catalogueRepo = FakeCatalogueRepository(catalogue)
        val coverage = FakeRecordCoverageRepository()
        val useCase = GetClosedWeekSummary(plans, completions, catalogueRepo, coverage)
        return WeeklySummaryViewModel(useCase, plans, completions, clock, week)
    }

    @Test fun `no closed week yet is Waiting with the date the first summary arrives`() = runTest {
        val plans = FakeDayPlanRepository(catalogue = catalogue, time = clock)
        val today = clock.today()
        plans.ensurePlanFor(today) // a plan exists, but only within the current (still open) week
        val viewModel = vm(plans)
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeeklySummaryContent.Waiting
        assertEquals(WeekBoundary.weekContaining(today).end, content.firstSummaryAt)
    }

    @Test fun `one closed week returns Closed with the right figures`() = runTest {
        val plans = FakeDayPlanRepository(catalogue = catalogue, time = clock)
        val completions = FakeCompletionRepository(plans, DayWritePolicy(clock), clock)
        val closedWeek = WeekBoundary.weekContaining(clock.today()).let { WeekBoundary.weekContaining(it.start.minusDays(7)) }
        closedWeek.dates.forEach { plans.seedPlan(buildDayPlan(catalogue, 1, it, PlanOrigin.BACKFILLED) { "id-$it" }) }
        val plan = plans.planFor(closedWeek.start)!!
        val task = plan.plannedTasks.first()
        completions.seed(Completion("c1", plan.id, task.taskSlug, closedWeek.start, task.points, clock.now()))

        val viewModel = vm(plans, completions)
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeeklySummaryContent.Closed
        assertEquals(closedWeek.key, content.weekKey)
        assertEquals(1, content.daysEngaged)
        assertEquals(7, content.daysInWeek)
        assertEquals(1, content.tasksRecorded)
        assertEquals(task.points, content.pointsEarned)
        assertTrue(content.pointsAvailable > 0)
        assertEquals(false, content.quiet)
    }

    @Test fun `a week with no completions returns Closed with quiet true and still fully populated`() = runTest {
        val plans = FakeDayPlanRepository(catalogue = catalogue, time = clock)
        val closedWeek = WeekBoundary.weekContaining(clock.today()).let { WeekBoundary.weekContaining(it.start.minusDays(7)) }
        closedWeek.dates.forEach { plans.seedPlan(buildDayPlan(catalogue, 1, it, PlanOrigin.BACKFILLED) { "id-$it" }) }

        val viewModel = vm(plans)
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeeklySummaryContent.Closed
        assertEquals(true, content.quiet)
        assertEquals(0, content.pointsEarned)
        assertTrue(content.pointsAvailable > 0)
        assertEquals(0, content.daysEngaged)
    }

    @Test fun `a week only partly inside recorded history returns a populated coverage note`() = runTest {
        val plans = FakeDayPlanRepository(catalogue = catalogue, time = clock)
        val closedWeek = WeekBoundary.weekContaining(clock.today()).let { WeekBoundary.weekContaining(it.start.minusDays(7)) }
        // Only the back half of the week has ever had a plan -> the front half is OUTSIDE_RECORD.
        val recordStart = closedWeek.dates[4]
        closedWeek.dates.drop(4).forEach { plans.seedPlan(buildDayPlan(catalogue, 1, it, PlanOrigin.BACKFILLED) { "id-$it" }) }

        val viewModel = vm(plans, week = closedWeek.key)
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeeklySummaryContent.Closed
        assertEquals(recordStart, content.coverage?.coveredFrom)
    }

    @Test fun `a repository failure returns Unavailable never a zeroed Closed`() = runTest {
        val plans = FakeDayPlanRepository(catalogue = catalogue, time = clock)
        val closedWeek = WeekBoundary.weekContaining(clock.today()).let { WeekBoundary.weekContaining(it.start.minusDays(7)) }
        // Seed a plan for one date but leave the rest unopened with a catalogue that cannot resolve
        // a version for them -> GetClosedWeekSummary returns NoCatalogue.
        plans.seedPlan(buildDayPlan(catalogue, 1, closedWeek.start, PlanOrigin.BACKFILLED) { "id-seed" })
        val failingCatalogue = FakeCatalogueRepository(catalogue, failWith = null).let {
            object : com.giraffe.mizanapp.domain.repository.CatalogueRepository by it {
                override suspend fun versionEffectiveOn(date: LocalDate): Int? = null
            }
        }
        val useCase = GetClosedWeekSummary(plans, FakeCompletionRepository(plans, DayWritePolicy(clock), clock), failingCatalogue, FakeRecordCoverageRepository())
        val viewModel = WeeklySummaryViewModel(useCase, plans, FakeCompletionRepository(plans, DayWritePolicy(clock), clock), clock, closedWeek.key)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.content is WeeklySummaryContent.Unavailable)
    }

    @Test fun `earlier and later respect the ends of recorded history`() = runTest {
        val plans = FakeDayPlanRepository(catalogue = catalogue, time = clock)
        val closedWeek = WeekBoundary.weekContaining(clock.today()).let { WeekBoundary.weekContaining(it.start.minusDays(7)) }
        closedWeek.dates.forEach { plans.seedPlan(buildDayPlan(catalogue, 1, it, PlanOrigin.BACKFILLED) { "id-$it" }) }

        val viewModel = vm(plans, week = closedWeek.key)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.canGoEarlier)
        assertEquals(false, viewModel.state.value.canGoLater)
    }

    @Test fun `opening with an explicit WeekKey shows that week rather than the most recent`() = runTest {
        val plans = FakeDayPlanRepository(catalogue = catalogue, time = clock)
        val recentClosed = WeekBoundary.weekContaining(clock.today()).let { WeekBoundary.weekContaining(it.start.minusDays(7)) }
        val olderClosed = WeekBoundary.weekContaining(recentClosed.start.minusDays(7))
        (recentClosed.dates + olderClosed.dates).forEach { plans.seedPlan(buildDayPlan(catalogue, 1, it, PlanOrigin.BACKFILLED) { "id-$it" }) }

        val viewModel = vm(plans, week = olderClosed.key)
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeeklySummaryContent.Closed
        assertEquals(olderClosed.key, content.weekKey)
    }
}
