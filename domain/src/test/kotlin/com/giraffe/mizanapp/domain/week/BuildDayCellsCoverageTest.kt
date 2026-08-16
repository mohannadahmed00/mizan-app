package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.sync.RecordCoverage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * `NOT_YET_KNOWN` is a third, distinct fact from `NOTHING_RECORDED` and
 * `OUTSIDE_RECORD` — a date this device has not fetched yet, not a date it
 * knows to be empty and not a date before the record began (FR-023b).
 */
class BuildDayCellsCoverageTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 16)
    private val recordStart: LocalDate = LocalDate.of(2026, 1, 1)
    private val floor: LocalDate = LocalDate.of(2026, 6, 1)

    private fun cells(coverage: RecordCoverage, dates: List<LocalDate>): List<DayCell> = buildDayCells(
        dates = dates,
        today = today,
        recordStart = recordStart,
        plans = emptyList(),
        completions = emptyList(),
        projectedAvailable = emptyMap(),
        coverage = coverage,
    )

    @Test
    fun `with complete coverage every existing expectation is unchanged`() {
        val dates = listOf(recordStart.minusDays(1), floor.minusDays(10), floor, today, today.plusDays(1))
        val complete = cells(RecordCoverage.completeFrom(recordStart), dates)
        val states = complete.map { it.state }

        assertEquals(
            listOf(
                DayCellState.OUTSIDE_RECORD,
                DayCellState.NOTHING_RECORDED,
                DayCellState.NOTHING_RECORDED,
                DayCellState.NOTHING_RECORDED,
                DayCellState.NOT_YET_ELAPSED,
            ),
            states,
        )
    }

    @Test
    fun `with incomplete coverage a date before knownFrom is NOT_YET_KNOWN`() {
        val backfilling = RecordCoverage(knownFrom = floor, complete = false)
        val cell = cells(backfilling, listOf(floor.minusDays(1))).single()

        assertEquals(DayCellState.NOT_YET_KNOWN, cell.state)
        assertNotEquals(DayCellState.NOTHING_RECORDED, cell.state)
        assertNotEquals(DayCellState.OUTSIDE_RECORD, cell.state)
    }

    @Test
    fun `a date at or after knownFrom is unaffected by incomplete coverage`() {
        val backfilling = RecordCoverage(knownFrom = floor, complete = false)
        val states = cells(backfilling, listOf(floor, floor.plusDays(1))).map { it.state }

        assertEquals(listOf(DayCellState.NOTHING_RECORDED, DayCellState.NOTHING_RECORDED), states)
    }

    @Test
    fun `a future date is still NOT_YET_ELAPSED regardless of coverage`() {
        val backfilling = RecordCoverage(knownFrom = floor, complete = false)
        val cell = cells(backfilling, listOf(today.plusDays(3))).single()

        assertEquals(DayCellState.NOT_YET_ELAPSED, cell.state)
    }
}
