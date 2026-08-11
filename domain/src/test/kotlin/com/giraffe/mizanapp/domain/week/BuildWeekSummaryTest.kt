package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure aggregate over a week's plans and completions. The most important
 * test in this feature — it proves the 500 fixture and the two-denominator
 * rule that keeps a Sunday morning from reading as 10% of a week (FR-009a).
 */
class BuildWeekSummaryTest {

    private val catalogue = DayFixtures.catalogue
    private val weekStart = LocalDate.parse("2026-08-08") // a Saturday
    private val week = com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(weekStart)

    private var idCounter = 0
    private fun newId(): String = "id-${idCounter++}"

    private fun planFor(date: LocalDate, origin: PlanOrigin = PlanOrigin.OPENED): DayPlan =
        buildDayPlan(catalogue, version = 1, date = date, origin = origin, newId = ::newId)

    private fun fullyComplete(plan: DayPlan): List<Completion> =
        plan.plannedTasks.flatMap { task ->
            (0 until task.maxOccurrencesPerDay).map { i ->
                Completion(
                    id = newId(),
                    dayPlanId = plan.id,
                    taskSlug = task.taskSlug,
                    creditedDate = plan.date,
                    pointsAwarded = task.points,
                    recordedAt = plan.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(i.toLong()),
                )
            }
        }

    private fun projectionFor(dates: List<LocalDate>): Map<LocalDate, Int> =
        dates.associateWith { projectAvailablePoints(catalogue, version = 1, date = it) }

    @Test
    fun `the 500 fixture - a fully elapsed week with everything completed`() {
        val plans = week.dates.map { planFor(it) }
        val completions = plans.flatMap { fullyComplete(it) }

        val summary = buildWeekSummary(
            week = week,
            today = week.end, // Friday: the whole week has elapsed
            recordStart = week.start,
            plans = plans,
            completions = completions,
            projectedAvailable = emptyMap(),
        )

        assertEquals(500, summary.score.earned)
        assertEquals(500, summary.score.elapsedAvailable)
        assertEquals(500, summary.score.weekTarget)
        assertEquals(1.0f, summary.score.fraction, 0.0001f)
    }

    @Test
    fun `nothing completed reads zero earned with every day nothing-recorded`() {
        val plans = week.dates.map { planFor(it) }

        val summary = buildWeekSummary(
            week = week,
            today = week.end,
            recordStart = week.start,
            plans = plans,
            completions = emptyList(),
            projectedAvailable = emptyMap(),
        )

        assertEquals(0, summary.score.earned)
        assertEquals(500, summary.score.elapsedAvailable)
        assertTrue(summary.days.all { it.state == DayCellState.NOTHING_RECORDED })
    }

    @Test
    fun `mid-week the denominator covers only elapsed days`() {
        val today = LocalDate.parse("2026-08-11") // Tuesday: Sat, Sun, Mon, Tue elapsed
        val elapsedDates = week.dates.filter { !it.isAfter(today) }
        val futureDates = week.dates.filter { it.isAfter(today) }
        val plans = elapsedDates.map { planFor(it) }

        val summary = buildWeekSummary(
            week = week,
            today = today,
            recordStart = week.start,
            plans = plans,
            completions = emptyList(),
            projectedAvailable = projectionFor(futureDates),
        )

        assertEquals(281, summary.score.elapsedAvailable)
        assertEquals(500, summary.score.weekTarget)
    }

    @Test
    fun `cumulative elapsed available across the week`() {
        val expected = listOf(69, 138, 212, 281, 350, 424, 500)

        week.dates.forEachIndexed { index, today ->
            val elapsedDates = week.dates.filter { !it.isAfter(today) }
            val futureDates = week.dates.filter { it.isAfter(today) }
            val plans = elapsedDates.map { planFor(it) }

            val summary = buildWeekSummary(
                week = week,
                today = today,
                recordStart = week.start,
                plans = plans,
                completions = emptyList(),
                projectedAvailable = projectionFor(futureDates),
            )

            assertEquals("day index $index ($today)", expected[index], summary.score.elapsedAvailable)
            assertEquals("day index $index ($today) week target", 500, summary.score.weekTarget)
        }
    }

    @Test
    fun `days always has exactly seven entries whatever is missing`() {
        val summary = buildWeekSummary(
            week = week,
            today = week.start,
            recordStart = week.start,
            plans = emptyList(),
            completions = emptyList(),
            projectedAvailable = projectionFor(week.dates.drop(1)),
        )

        assertEquals(7, summary.days.size)
    }

    @Test
    fun `day states resolve correctly`() {
        val fullyDone = planFor(week.dates[0])
        val partial = planFor(week.dates[1])
        val nothing = planFor(week.dates[2])
        // week.dates[3..6] are NOT_YET_ELAPSED (after today, dates[2])
        val today = week.dates[2]

        val partialTask = partial.plannedTasks.first()
        val completions = fullyComplete(fullyDone) +
            listOf(
                Completion(
                    id = newId(),
                    dayPlanId = partial.id,
                    taskSlug = partialTask.taskSlug,
                    creditedDate = partial.date,
                    pointsAwarded = partialTask.points,
                    recordedAt = partial.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                ),
            )

        val summary = buildWeekSummary(
            week = week,
            today = today,
            recordStart = week.dates[0], // dates[0..2] are in-record and elapsed
            plans = listOf(fullyDone, partial, nothing),
            completions = completions,
            projectedAvailable = projectionFor(week.dates.drop(3)),
        )

        assertEquals(DayCellState.FULLY_RECORDED, summary.days[0].state)
        assertEquals(DayCellState.PARTLY_RECORDED, summary.days[1].state)
        assertEquals(DayCellState.NOTHING_RECORDED, summary.days[2].state)
        assertEquals(DayCellState.NOT_YET_ELAPSED, summary.days[3].state)
        assertEquals(DayCellState.NOT_YET_ELAPSED, summary.days[6].state)
    }

    @Test
    fun `a date before the record start is outside the record`() {
        val summary = buildWeekSummary(
            week = week,
            today = week.end,
            recordStart = week.dates[2],
            plans = week.dates.drop(2).map { planFor(it) },
            completions = emptyList(),
            projectedAvailable = emptyMap(),
        )

        assertEquals(DayCellState.OUTSIDE_RECORD, summary.days[0].state)
        assertEquals(DayCellState.OUTSIDE_RECORD, summary.days[1].state)
        assertEquals(DayCellState.NOTHING_RECORDED, summary.days[2].state)
    }

    @Test
    fun `the 500 fixture and the cumulative sequence hold across a month and year boundary`() {
        val crossingStart = LocalDate.parse("2026-12-26") // a Saturday; week ends 2027-01-01
        val crossingWeek = com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(crossingStart)
        val expectedCumulative = listOf(69, 138, 212, 281, 350, 424, 500)

        // (h-a) the 500 fixture, repeated on the boundary-crossing week.
        val plans = crossingWeek.dates.map { planFor(it) }
        val completions = plans.flatMap { fullyComplete(it) }
        val fullSummary = buildWeekSummary(
            week = crossingWeek,
            today = crossingWeek.end,
            recordStart = crossingWeek.start,
            plans = plans,
            completions = completions,
            projectedAvailable = emptyMap(),
        )
        assertEquals(500, fullSummary.score.earned)
        assertEquals(500, fullSummary.score.elapsedAvailable)
        assertEquals(500, fullSummary.score.weekTarget)
        assertEquals(
            listOf(69, 69, 74, 69, 69, 74, 76),
            fullSummary.days.map { it.available },
        )

        // (h-d) the cumulative elapsed-available sequence, repeated on the same week.
        crossingWeek.dates.forEachIndexed { index, today ->
            val elapsedDates = crossingWeek.dates.filter { !it.isAfter(today) }
            val futureDates = crossingWeek.dates.filter { it.isAfter(today) }
            val cumulativePlans = elapsedDates.map { planFor(it) }

            val summary = buildWeekSummary(
                week = crossingWeek,
                today = today,
                recordStart = crossingWeek.start,
                plans = cumulativePlans,
                completions = emptyList(),
                projectedAvailable = projectionFor(futureDates),
            )

            assertEquals("day index $index ($today)", expectedCumulative[index], summary.score.elapsedAvailable)
        }
    }

    @Test
    fun `a tombstoned completion contributes nothing`() {
        val plan = planFor(week.dates[0])
        val task = plan.plannedTasks.first()
        val reversed = Completion(
            id = newId(),
            dayPlanId = plan.id,
            taskSlug = task.taskSlug,
            creditedDate = plan.date,
            pointsAwarded = task.points,
            recordedAt = plan.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            reversedAt = plan.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(1),
        )

        val summary = buildWeekSummary(
            week = week,
            today = week.dates[0],
            recordStart = week.dates[0],
            plans = listOf(plan),
            completions = listOf(reversed),
            projectedAvailable = projectionFor(week.dates.drop(1)),
        )

        assertEquals(0, summary.score.earned)
    }
}
