package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.projectAvailablePoints
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildMonthOverview` - `buildDayCells` (`006` research.md R2) over a
 * calendar month's dates instead of a week's seven.
 */
class BuildMonthOverviewTest {

    private val catalogue = DayFixtures.catalogue

    private var idCounter = 0
    private fun newId(): String = "id-${idCounter++}"

    private fun planFor(date: LocalDate): DayPlan =
        buildDayPlan(catalogue, version = 1, date = date, origin = PlanOrigin.OPENED, newId = ::newId)

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

    @Test
    fun `a full month with mixed states renders one day cell per calendar date in order`() {
        val month = YearMonth.of(2026, 8) // 31 days
        val today = LocalDate.parse("2026-08-31")
        val recordStart = LocalDate.parse("2026-08-01")

        val fullyDone = planFor(LocalDate.parse("2026-08-01"))
        val partial = planFor(LocalDate.parse("2026-08-02"))
        val partialTask = partial.plannedTasks.first()

        val plans = listOf(fullyDone, partial)
        val completions = fullyComplete(fullyDone) + listOf(
            Completion(
                id = newId(),
                dayPlanId = partial.id,
                taskSlug = partialTask.taskSlug,
                creditedDate = partial.date,
                pointsAwarded = partialTask.points,
                recordedAt = partial.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            ),
        )
        val remainingDates = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(month.atEndOfMonth()) }.toList()
            .filter { it != fullyDone.date && it != partial.date }
        val projected = remainingDates.associateWith { projectAvailablePoints(catalogue, 1, it) }

        val overview = buildMonthOverview(month, today, recordStart, plans, completions, projected)

        assertEquals(31, overview.days.size)
        assertEquals(month.atDay(1), overview.days.first().date)
        assertEquals(month.atEndOfMonth(), overview.days.last().date)
        assertEquals(DayCellState.FULLY_RECORDED, overview.days[0].state)
        assertEquals(DayCellState.PARTLY_RECORDED, overview.days[1].state)
        assertTrue(overview.days.drop(2).all { it.state == DayCellState.NOTHING_RECORDED })
        assertEquals(month, overview.month)
    }

    @Test
    fun `a month straddling the record start shows outside-record before it`() {
        val month = YearMonth.of(2026, 8)
        val recordStart = LocalDate.parse("2026-08-15")
        val today = LocalDate.parse("2026-08-31")
        val dates = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(month.atEndOfMonth()) }.toList()
        val projected = dates.filter { !it.isBefore(recordStart) }.associateWith { projectAvailablePoints(catalogue, 1, it) }

        val overview = buildMonthOverview(month, today, recordStart, emptyList(), emptyList(), projected)

        assertTrue(overview.days.filter { it.date.isBefore(recordStart) }.all { it.state == DayCellState.OUTSIDE_RECORD })
        assertTrue(overview.days.filter { !it.date.isBefore(recordStart) }.all { it.state == DayCellState.NOTHING_RECORDED })
    }

    @Test
    fun `the current month shows not-yet-elapsed for every date after today`() {
        val month = YearMonth.of(2026, 8)
        val today = LocalDate.parse("2026-08-10")
        val recordStart = LocalDate.parse("2026-08-01")
        val dates = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(month.atEndOfMonth()) }.toList()
        val elapsed = dates.filter { !it.isAfter(today) }
        val future = dates.filter { it.isAfter(today) }
        val projected = future.associateWith { projectAvailablePoints(catalogue, 1, it) }

        val overview = buildMonthOverview(month, today, recordStart, emptyList(), emptyList(), projected)

        assertTrue(overview.days.filter { it.date.isAfter(today) }.all { it.state == DayCellState.NOT_YET_ELAPSED })
        assertTrue(elapsed.isNotEmpty())
    }

    @Test
    fun `a 28-day month renders exactly 28 day cells`() {
        val month = YearMonth.of(2027, 2) // not a leap year
        val overview = buildMonthOverview(month, month.atEndOfMonth(), month.atDay(1), emptyList(), emptyList(), emptyMap())

        assertEquals(28, overview.days.size)
    }

    @Test
    fun `a single day of history still renders the whole month with no crash`() {
        val month = YearMonth.of(2026, 8)
        val today = LocalDate.parse("2026-08-01")
        val recordStart = today
        val onlyPlan = planFor(today)
        val dates = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(month.atEndOfMonth()) }.toList()
        val future = dates.filter { it.isAfter(today) }
        val projected = future.associateWith { projectAvailablePoints(catalogue, 1, it) }

        val overview = buildMonthOverview(month, today, recordStart, listOf(onlyPlan), emptyList(), projected)

        assertEquals(31, overview.days.size)
        assertEquals(DayCellState.NOTHING_RECORDED, overview.days.first().state)
        assertTrue(overview.days.drop(1).all { it.state == DayCellState.NOT_YET_ELAPSED })
    }
}
