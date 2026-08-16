package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The extracted per-date derivation `buildWeekSummary` used to inline
 * (research.md R2). Independent of the seven-date, Saturday-start shape a
 * week imposes — `buildMonthOverview` (`006`) is the reason it now stands
 * alone.
 */
class BuildDayCellsTest {

    private val catalogue = DayFixtures.catalogue

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

    @Test
    fun `a date after today is not yet elapsed`() {
        val today = LocalDate.parse("2026-08-10")
        val future = LocalDate.parse("2026-08-11")

        val cells = buildDayCells(
            dates = listOf(future),
            today = today,
            recordStart = today,
            plans = emptyList(),
            completions = emptyList(),
            projectedAvailable = emptyMap(),
            coverage = RecordCoverage.completeFrom(today),
        )

        assertEquals(DayCellState.NOT_YET_ELAPSED, cells.single().state)
    }

    @Test
    fun `a date before record start is outside the record`() {
        val date = LocalDate.parse("2026-08-01")
        val recordStart = LocalDate.parse("2026-08-05")

        val cells = buildDayCells(
            dates = listOf(date),
            today = recordStart,
            recordStart = recordStart,
            plans = emptyList(),
            completions = emptyList(),
            projectedAvailable = emptyMap(),
            coverage = RecordCoverage.completeFrom(recordStart),
        )

        assertEquals(DayCellState.OUTSIDE_RECORD, cells.single().state)
    }

    @Test
    fun `null record start is outside the record for every date`() {
        val date = LocalDate.parse("2026-08-01")

        val cells = buildDayCells(
            dates = listOf(date),
            today = date,
            recordStart = null,
            plans = emptyList(),
            completions = emptyList(),
            projectedAvailable = emptyMap(),
            coverage = RecordCoverage.completeFrom(null),
        )

        assertEquals(DayCellState.OUTSIDE_RECORD, cells.single().state)
    }

    @Test
    fun `a stored plan with zero earned is nothing recorded`() {
        val date = LocalDate.parse("2026-08-05")
        val plan = planFor(date)

        val cells = buildDayCells(
            dates = listOf(date),
            today = date,
            recordStart = date,
            plans = listOf(plan),
            completions = emptyList(),
            projectedAvailable = emptyMap(),
            coverage = RecordCoverage.completeFrom(date),
        )

        assertEquals(DayCellState.NOTHING_RECORDED, cells.single().state)
        assertEquals(plan.availablePoints, cells.single().available)
    }

    @Test
    fun `earned equal to available is fully recorded`() {
        val date = LocalDate.parse("2026-08-05")
        val plan = planFor(date)
        val completions = fullyComplete(plan)

        val cells = buildDayCells(
            dates = listOf(date),
            today = date,
            recordStart = date,
            plans = listOf(plan),
            completions = completions,
            projectedAvailable = emptyMap(),
            coverage = RecordCoverage.completeFrom(date),
        )

        assertEquals(DayCellState.FULLY_RECORDED, cells.single().state)
        assertEquals(plan.availablePoints, cells.single().earned)
    }

    @Test
    fun `earned between zero and available is partly recorded`() {
        val date = LocalDate.parse("2026-08-05")
        val plan = planFor(date)
        val task = plan.plannedTasks.first()
        val oneCompletion = listOf(
            Completion(
                id = newId(),
                dayPlanId = plan.id,
                taskSlug = task.taskSlug,
                creditedDate = plan.date,
                pointsAwarded = task.points,
                recordedAt = plan.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            ),
        )

        val cells = buildDayCells(
            dates = listOf(date),
            today = date,
            recordStart = date,
            plans = listOf(plan),
            completions = oneCompletion,
            projectedAvailable = emptyMap(),
            coverage = RecordCoverage.completeFrom(date),
        )

        assertEquals(DayCellState.PARTLY_RECORDED, cells.single().state)
    }

    @Test
    fun `a date with no stored plan takes its available from the projection map`() {
        val date = LocalDate.parse("2026-08-05")
        val projected = projectAvailablePoints(catalogue, version = 1, date = date)

        val cells = buildDayCells(
            dates = listOf(date),
            today = date,
            recordStart = date,
            plans = emptyList(),
            completions = emptyList(),
            projectedAvailable = mapOf(date to projected),
            coverage = RecordCoverage.completeFrom(date),
        )

        assertEquals(DayCellState.NOTHING_RECORDED, cells.single().state)
        assertEquals(projected, cells.single().available)
    }

    @Test
    fun `output preserves input date order and size whatever the input`() {
        val dates = listOf(
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-15"),
            LocalDate.parse("2026-08-03"),
        )

        val cells = buildDayCells(
            dates = dates,
            today = LocalDate.parse("2026-08-31"),
            recordStart = LocalDate.parse("2026-08-01"),
            plans = emptyList(),
            completions = emptyList(),
            projectedAvailable = dates.associateWith { 0 },
            coverage = RecordCoverage.completeFrom(LocalDate.parse("2026-08-01")),
        )

        assertEquals(dates, cells.map { it.date })
    }
}
