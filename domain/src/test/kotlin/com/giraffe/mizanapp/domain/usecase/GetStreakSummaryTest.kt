package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.time.MidnightBoundaryStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetStreakSummaryTest {

    private val zone = ZoneId.of("Africa/Cairo")

    private fun completion(date: LocalDate, id: String) = Completion(
        id = id,
        dayPlanId = "plan-$date",
        taskSlug = "fajr-1",
        creditedDate = date,
        pointsAwarded = 2,
        recordedAt = date.atTime(9, 0).atZone(zone).toInstant(),
    )

    private fun subject(
        time: FakeTimeProvider,
        completionsRepo: FakeWeekCompletionRepository = FakeWeekCompletionRepository(),
        dayPlansRepo: FakeWeekDayPlanRepository = FakeWeekDayPlanRepository(time = time),
    ) = GetStreakSummary(completionsRepo, dayPlansRepo, time, FakeRecordCoverageRepository(), MidnightBoundaryStatus(time)) to
        (completionsRepo to dayPlansRepo)

    @Test
    fun reports_the_seeded_run() = runTest {
        val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-19")) }
        val (useCase, repos) = subject(time)
        val (completions, _) = repos
        val run = listOf("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19").map(LocalDate::parse)
        run.forEachIndexed { i, d -> completions.seed(completion(d, "c$i")) }

        assertEquals(5, useCase().first().current)
    }

    @Test
    fun empty_record_emits_zero_without_hanging() = runTest {
        val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-19")) }
        val (useCase, _) = subject(time)

        assertEquals(0, useCase().first().current)
    }

    @Test
    fun collecting_creates_no_day_plan() = runTest {
        val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-19")) }
        val (useCase, repos) = subject(time)
        val (_, dayPlans) = repos

        val before = dayPlans.creationCount
        useCase().first()

        assertEquals(before, dayPlans.creationCount)
    }

    @Test
    fun travel_forward_ends_the_run_without_moving_stored_dates() = runTest {
        val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-19")) }
        val (useCase, repos) = subject(time)
        val (completions, _) = repos
        val run = listOf("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19").map(LocalDate::parse)
        run.forEachIndexed { i, d -> completions.seed(completion(d, "c$i")) }
        val storedBefore = completions.liveBetween(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"))
            .map { it.creditedDate }

        time.setZone(zone)
        time.setDate(LocalDate.parse("2026-08-21"))

        assertEquals(0, useCase().first().current)
        val storedAfter = completions.liveBetween(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"))
            .map { it.creditedDate }
        assertEquals(storedBefore, storedAfter)
    }

    @Test
    fun travel_backward_reports_lower_current_without_altering_storage() = runTest {
        val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-19")) }
        val (useCase, repos) = subject(time)
        val (completions, _) = repos
        val run = listOf("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19").map(LocalDate::parse)
        run.forEachIndexed { i, d -> completions.seed(completion(d, "c$i")) }
        val storedBefore = completions.liveBetween(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"))

        time.setDate(LocalDate.parse("2026-08-17"))
        assertEquals(3, useCase().first().current)

        val storedAfter = completions.liveBetween(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"))
        assertEquals(storedBefore, storedAfter)
    }

    @Test
    fun restoring_the_clock_restores_the_original_figure() = runTest {
        val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-19")) }
        val (useCase, repos) = subject(time)
        val (completions, _) = repos
        val run = listOf("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19").map(LocalDate::parse)
        run.forEachIndexed { i, d -> completions.seed(completion(d, "c$i")) }
        val original = useCase().first()

        time.setDate(LocalDate.parse("2026-08-17"))
        useCase().first()
        time.setDate(LocalDate.parse("2026-08-19"))

        assertEquals(original, useCase().first())
    }

    /** No emission may read a `current` lower than both its neighbours (SC-011). */
    private fun assertNoTransientDip(collected: List<StreakSummary>) {
        for (i in 1 until collected.size - 1) {
            val dipped = collected[i].current < collected[i - 1].current && collected[i].current < collected[i + 1].current
            assertFalse("emission $i dipped below both neighbours: $collected", dipped)
        }
    }

    @Test
    fun crossing_twenty_hundred_makes_the_streak_at_risk_with_no_user_action() = runTest {
        val time = FakeTimeProvider().apply {
            setZone(zone)
            setDate(LocalDate.parse("2026-08-19"), LocalTime.of(19, 0))
        }
        val (useCase, repos) = subject(time)
        val (completions, _) = repos
        completions.seed(completion(LocalDate.parse("2026-08-18"), "c0"))

        val collected = mutableListOf<StreakSummary>()
        val job = launch { useCase().collect { collected.add(it) } }
        runCurrent()

        assertEquals(1, collected.size)
        assertFalse(collected[0].isAtRisk)

        time.setDate(LocalDate.parse("2026-08-19"), LocalTime.of(20, 0))
        advanceTimeBy(3_600_000L + 1_000L)
        runCurrent()

        assertTrue(collected.size >= 2)
        assertTrue(collected.last().isAtRisk)
        assertNoTransientDip(collected)

        job.cancelAndJoin()
    }

    @Test
    fun crossing_midnight_moves_the_streak_to_the_new_date_with_no_user_action() = runTest {
        val time = FakeTimeProvider().apply {
            setZone(zone)
            setDate(LocalDate.parse("2026-08-19"), LocalTime.of(23, 30))
        }
        val (useCase, repos) = subject(time)
        val (completions, _) = repos
        completions.seed(completion(LocalDate.parse("2026-08-19"), "c0"))

        val collected = mutableListOf<StreakSummary>()
        val job = launch { useCase().collect { collected.add(it) } }
        runCurrent()

        assertEquals(1, collected.size)
        assertTrue(collected[0].todayCounted)

        time.setDate(LocalDate.parse("2026-08-20"), LocalTime.of(0, 0))
        advanceTimeBy(1_800_000L + 1_000L)
        runCurrent()

        assertTrue(collected.size >= 2)
        assertFalse("a new day begins pending, not counted", collected.last().todayCounted)
        assertNoTransientDip(collected)

        job.cancelAndJoin()
    }
}
