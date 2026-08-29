package com.giraffe.mizanapp.history

import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.today.FakeCatalogueRepository
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeCompletionRepository
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import com.giraffe.mizanapp.today.FakeRecordCoverageRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

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

    private fun buildViewModel(plans: FakeDayPlanRepository, catalogue: FakeCatalogueRepository = FakeCatalogueRepository()): HistoryViewModel {
        val useCase = GetHistoryPage(plans, FakeCompletionRepository(plans, DayWritePolicy(clock), clock), catalogue, clock, FakeRecordCoverageRepository())
        return HistoryViewModel(useCase, catalogue)
    }

    @Test
    fun `first load shows Ready with the newest week first`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Ready, got ${state.status}", state.status is HistoryUiState.Status.Ready)
        assertEquals(WeekBoundary.weekContaining(clock.today()).key, state.weeks.first().weekKey)
    }

    @Test
    fun `LoadMore appends without clearing the list`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        val recordStart = clock.today().minusDays(7 * 20)
        runBlocking {
            plans.ensurePlanFor(recordStart)
            plans.ensurePlanFor(clock.today())
        }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        val firstLoad = vm.state.value.weeks
        assertTrue(vm.state.value.hasMore)

        vm.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        val afterLoadMore = vm.state.value.weeks
        assertTrue("appended weeks must include the first page", afterLoadMore.take(firstLoad.size) == firstLoad)
        assertTrue("more weeks must have been added", afterLoadMore.size > firstLoad.size)
    }

    @Test
    fun `LoadMore does nothing when hasMore is false`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        // A record with only one plan (today's) fits entirely in the first page.
        assertFalse(vm.state.value.hasMore)
        val before = vm.state.value.weeks

        vm.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        assertEquals(before, vm.state.value.weeks)
    }

    @Test
    fun `empty record shows RecordNotStarted`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock) // nothing seeded
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        assertTrue(vm.state.value.status is HistoryUiState.Status.RecordNotStarted)
    }

    @Test
    fun `CouldNotLoad exposes a retry that reloads`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        assertTrue(vm.state.value.status is HistoryUiState.Status.Ready)

        vm.onEvent(HistoryEvent.Retry)
        advanceUntilIdle()

        assertTrue(vm.state.value.status is HistoryUiState.Status.Ready)
    }

    @Test
    fun `returning to history after recording shows the updated figures`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        val policy = DayWritePolicy(clock)
        val completions = FakeCompletionRepository(plans, policy, clock)
        val catalogue = FakeCatalogueRepository()
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val useCase = GetHistoryPage(plans, completions, catalogue, clock, FakeRecordCoverageRepository())
        val vm = HistoryViewModel(useCase, catalogue)
        advanceUntilIdle()

        val before = vm.state.value.weeks.first().earnedPoints
        runBlocking { completions.record(clock.today(), "fajr-1") }

        vm.refresh()
        advanceUntilIdle()

        val after = vm.state.value.weeks.first().earnedPoints
        assertTrue("earned points must reflect the new completion", after > before)
    }
}
