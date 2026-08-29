package com.giraffe.mizanapp.week

import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import com.giraffe.mizanapp.today.FakeCatalogueRepository
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeCompletionRepository
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import com.giraffe.mizanapp.today.FakeRecordCoverageRepository
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
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
class WeekViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock().apply { setDate(LocalDate.parse("2026-08-14")) } // Friday: a fully elapsed week
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        catalogue: FakeCatalogueRepository = FakeCatalogueRepository(),
        seedPlans: (FakeDayPlanRepository, FakeCompletionRepository) -> Unit = { _, _ -> },
    ): WeekViewModel {
        val plans = FakeDayPlanRepository(time = clock)
        val policy = DayWritePolicy(clock)
        val completions = FakeCompletionRepository(plans, policy, clock)
        seedPlans(plans, completions)

        val getWeekSummary = GetWeekSummary(plans, completions, catalogue, clock, FakeRecordCoverageRepository())
        return WeekViewModel(getWeekSummary, catalogue, clock, plans)
    }

    @Test
    fun `initial state is loading`() {
        val vm = viewModel()

        assertTrue(vm.state.value.status is WeekUiState.Status.Loading)
    }

    @Test
    fun `a fully seeded week becomes ready with seven day cells`() = runTest(dispatcher) {
        val vm = viewModel(seedPlans = { plans, _ ->
            for (offset in 0L..6L) {
                val date = LocalDate.parse("2026-08-08").plusDays(offset)
                kotlinx.coroutines.runBlocking { plans.ensurePlanFor(date) }
            }
        })
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Ready, got ${state.status}", state.status is WeekUiState.Status.Ready)
        assertEquals(7, state.days.size)
        assertEquals(500, state.elapsedAvailablePoints)
        assertEquals(500, state.weekTargetPoints)
    }

    @Test
    fun `progress fraction divides by elapsed available`() = runTest(dispatcher) {
        val vm = viewModel(seedPlans = { plans, completions ->
            kotlinx.coroutines.runBlocking {
                plans.ensurePlanFor(LocalDate.parse("2026-08-08"))
                completions.record(LocalDate.parse("2026-08-08"), "fajr-1")
            }
        })
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(state.earnedPoints.toFloat() / state.elapsedAvailablePoints, state.progressFraction, 0.0001f)
    }

    @Test
    fun `SC-013a - the rendered week does not change after settling`() = runTest(dispatcher) {
        // Backfilling here requires an anchor plan predating the week, or
        // every date is outside the record and there is nothing to
        // backfill (see GetWeekSummaryBackfillTest's documented reasoning).
        val vm = viewModel(seedPlans = { plans, _ ->
            kotlinx.coroutines.runBlocking {
                val today = clock.today()
                clock.setDate(LocalDate.parse("2026-01-05"))
                plans.ensurePlanFor(LocalDate.parse("2026-01-05"))
                clock.setDate(today)
            }
        })
        advanceUntilIdle()
        val firstReady = vm.state.value
        assertTrue("expected Ready, got ${firstReady.status}", firstReady.status is WeekUiState.Status.Ready)

        // Nothing further is running — the coroutine scheduler has no more
        // work, which is the proxy for "all writes have settled".
        advanceUntilIdle()
        val settled = vm.state.value

        assertEquals("no cell may change under the user once rendered", firstReady, settled)
    }

    @Test
    fun `an unseeded catalogue reports catalogue unavailable not an empty week`() = runTest(dispatcher) {
        val vm = viewModel(
            catalogue = FakeCatalogueRepository(
                failWith = listOf(CatalogueDefect.NoCatalogue("/catalogue/valid-catalogue.json")),
            ),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.status is WeekUiState.Status.CatalogueUnavailable)
    }
}
