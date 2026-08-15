package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `buildWeekSummary`'s widened precondition (research.md R2, tasks.md T006-T008):
 * `projectedAvailable` must now cover every date in the week with no stored
 * plan that is at or after the record start - not only dates after today.
 * This is what stops history from reporting a fabricated 0/0 for a week it
 * never backfilled (clarification Q2).
 */
class BuildWeekSummaryElapsedTest {

    private val catalogue = DayFixtures.catalogue
    private val weekStart = LocalDate.parse("2026-08-08") // a Saturday
    private val week = WeekBoundary.weekContaining(weekStart)

    private var idCounter = 0
    private fun newId(): String = "elapsed-id-${idCounter++}"

    private fun planFor(date: LocalDate, version: Int = 1, origin: PlanOrigin = PlanOrigin.OPENED): DayPlan =
        buildDayPlan(catalogue, version = version, date = date, origin = origin, newId = ::newId)

    @Test
    fun `elapsed date with no stored plan reports projected availability, not zero`() {
        val elapsedNoPlanDate = week.dates[2] // Monday
        val today = week.dates[4] // Friday - everything up to it has elapsed

        val summary = buildWeekSummary(
            week = week,
            today = today,
            recordStart = week.start,
            plans = emptyList(), // nothing stored at all
            completions = emptyList(),
            projectedAvailable = mapOf(elapsedNoPlanDate to 69),
        )

        val cell = summary.days.first { it.date == elapsedNoPlanDate }
        assertEquals(69, cell.available)
        assertEquals(DayCellState.NOTHING_RECORDED, cell.state)
    }

    @Test
    fun `a stored plan always wins over the projection`() {
        val date = week.dates[1]
        val today = week.dates[4]
        val stored = planFor(date) // real fixture plan, availablePoints = 74 on a Sunday-equivalent weekday

        val summary = buildWeekSummary(
            week = week,
            today = today,
            recordStart = week.start,
            plans = listOf(stored),
            completions = emptyList(),
            // Deliberately conflicting projection - the stored value must win.
            projectedAvailable = mapOf(date to 999),
        )

        val cell = summary.days.first { it.date == date }
        assertEquals(stored.availablePoints, cell.available)
    }

    @Test
    fun `dates before the record start still report zero available`() {
        val outsideDate = week.dates[0]
        val recordStart = week.dates[2]
        val today = week.dates[4]

        val summary = buildWeekSummary(
            week = week,
            today = today,
            recordStart = recordStart,
            plans = emptyList(),
            completions = emptyList(),
            // A projection entry exists for this date anyway - it must still be ignored.
            projectedAvailable = mapOf(outsideDate to 69),
        )

        val cell = summary.days.first { it.date == outsideDate }
        assertEquals(DayCellState.OUTSIDE_RECORD, cell.state)
        assertEquals(0, cell.available)
    }
}
