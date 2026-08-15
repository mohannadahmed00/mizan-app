package com.giraffe.mizanapp.daysummary

import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.usecase.GetDayDetail
import com.giraffe.mizanapp.today.FakeCatalogueRepository
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeCompletionRepository
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import com.giraffe.mizanapp.today.loadSeedCatalogue
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
class DaySummaryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun useCase(
        plans: FakeDayPlanRepository,
        completions: FakeCompletionRepository = FakeCompletionRepository(plans, DayWritePolicy(clock), clock),
        catalogue: FakeCatalogueRepository = FakeCatalogueRepository(),
    ) = GetDayDetail(plans, completions, catalogue, clock)

    @Test
    fun `initial state is loading`() {
        val plans = FakeDayPlanRepository(time = clock)
        val vm = DaySummaryViewModel(useCase(plans), clock.today().minusDays(1))

        assertTrue(vm.state.value.status is DaySummaryUiState.Status.Loading)
    }

    @Test
    fun `Ready exposes sections in plan order`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        val completions = FakeCompletionRepository(plans, DayWritePolicy(clock), clock)
        val date = clock.today().minusDays(1)
        val plan = runBlocking { (plans.ensurePlanFor(date) as com.giraffe.mizanapp.domain.repository.EnsureOutcome.Created).plan }
        val task = plan.plannedTasks.first { it.taskSlug == "fajr-1" }
        completions.seed(
            com.giraffe.mizanapp.domain.day.Completion(
                id = "seed-1",
                dayPlanId = plan.id,
                taskSlug = task.taskSlug,
                creditedDate = date,
                pointsAwarded = task.points,
                recordedAt = clock.now(),
            ),
        )

        val vm = DaySummaryViewModel(useCase(plans, completions), date)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Ready, got ${state.status}", state.status is DaySummaryUiState.Status.Ready)
        assertEquals(2, state.earnedPoints)
        assertEquals(plan.availablePoints, state.availablePoints)
        assertEquals(date, state.civilDate)
        // Sections should appear in ascending sectionOrder - Fajr (order 1) first.
        assertEquals("fajr", state.sections.first().id)
    }

    @Test
    fun `NoRecord for a date outside the record`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)

        val vm = DaySummaryViewModel(useCase(plans), LocalDate.parse("2020-01-01"))
        advanceUntilIdle()

        assertTrue(vm.state.value.status is DaySummaryUiState.Status.NoRecord)
    }

    @Test
    fun `CatalogueUnavailable is a distinct status`() = runTest(dispatcher) {
        val date = clock.today().minusDays(1)
        val anchor = clock.today().minusDays(10)
        val plans = FakeDayPlanRepository(time = clock, failDates = setOf(date))
        runBlocking { plans.ensurePlanFor(anchor) }

        // A catalogue that cannot resolve a version for the requested date -
        // simulates the case where the plan store also fails, forcing the
        // derive path to discover it has nothing to derive from.
        val useCase = GetDayDetail(
            plans,
            FakeCompletionRepository(plans, DayWritePolicy(clock), clock),
            object : com.giraffe.mizanapp.domain.repository.CatalogueRepository by FakeCatalogueRepository() {
                override suspend fun versionEffectiveOn(d: LocalDate): Int? = null
            },
            clock,
        )
        val vm = DaySummaryViewModel(useCase, date)
        advanceUntilIdle()

        assertTrue(
            "expected CatalogueUnavailable, got ${vm.state.value.status}",
            vm.state.value.status is DaySummaryUiState.Status.CatalogueUnavailable,
        )
    }

    @Test
    fun `a backfilled day and an opened day with nothing recorded produce identical state`() = runTest(dispatcher) {
        val catalogue = loadSeedCatalogue()
        val date = clock.today().minusDays(1)

        val backfilledPlans = FakeDayPlanRepository(time = clock).apply {
            seedPlan(buildDayPlan(catalogue, 1, date, PlanOrigin.BACKFILLED) { "bf" })
        }
        val vmBackfilled = DaySummaryViewModel(useCase(backfilledPlans), date)
        advanceUntilIdle()

        val openedPlans = FakeDayPlanRepository(time = clock).apply {
            seedPlan(buildDayPlan(catalogue, 1, date, PlanOrigin.OPENED) { "op" })
        }
        val vmOpened = DaySummaryViewModel(useCase(openedPlans), date)
        advanceUntilIdle()

        assertEquals(vmBackfilled.state.value, vmOpened.state.value)
    }
}
