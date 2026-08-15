package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.week.DayCell
import com.giraffe.mizanapp.domain.week.DayCellState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildPersonalBests` - scans a full-record `DayCell` payload for the
 * single best day and best week (FR-004). Never surfaces a "worst" - there
 * is no such field anywhere in [PersonalBests] (Principle IX).
 */
class BuildPersonalBestsTest {

    private fun cell(date: LocalDate, earned: Int, available: Int, state: DayCellState) =
        DayCell(date = date, hijriLabel = null, earned = earned, available = available, state = state)

    @Test
    fun `a single day of history is its own best day and best week`() {
        val today = LocalDate.parse("2026-08-08") // a Saturday - week boundary start
        val cells = listOf(cell(today, 69, 69, DayCellState.FULLY_RECORDED))

        val bests = buildPersonalBests(cells, today)

        assertEquals(today, bests.bestDay?.date)
        assertEquals(69, bests.bestWeek?.available)
        assertEquals(69, bests.bestWeek?.earned)
    }

    @Test
    fun `a tie at 100 percent resolves to the earlier day`() {
        val earlier = LocalDate.parse("2026-08-01")
        val later = LocalDate.parse("2026-08-02")
        val cells = listOf(
            cell(earlier, 69, 69, DayCellState.FULLY_RECORDED),
            cell(later, 69, 69, DayCellState.FULLY_RECORDED),
        )

        val bests = buildPersonalBests(cells, later)

        assertEquals(earlier, bests.bestDay?.date)
    }

    @Test
    fun `outside-record and not-yet-elapsed cells are never selected as best day`() {
        val today = LocalDate.parse("2026-08-05")
        val cells = listOf(
            cell(LocalDate.parse("2026-08-01"), 0, 0, DayCellState.OUTSIDE_RECORD),
            cell(LocalDate.parse("2026-08-02"), 30, 69, DayCellState.PARTLY_RECORDED),
            cell(LocalDate.parse("2026-08-10"), 0, 69, DayCellState.NOT_YET_ELAPSED),
        )

        val bests = buildPersonalBests(cells, today)

        assertEquals(LocalDate.parse("2026-08-02"), bests.bestDay?.date)
    }

    @Test
    fun `a week with zero available is excluded from best week`() {
        val today = LocalDate.parse("2026-08-08")
        val cells = listOf(cell(today, 0, 0, DayCellState.OUTSIDE_RECORD))

        val bests = buildPersonalBests(cells, today)

        assertNull(bests.bestWeek)
    }

    @Test
    fun `an empty record returns null for both fields`() {
        val bests = buildPersonalBests(emptyList(), LocalDate.parse("2026-08-08"))

        assertNull(bests.bestDay)
        assertNull(bests.bestWeek)
    }

    @Test
    fun `best week sums only the cells actually present, not a padded seven`() {
        // A record starting mid-week: only Thu/Fri present for that week.
        val today = LocalDate.parse("2026-08-07") // Friday
        val cells = listOf(
            cell(LocalDate.parse("2026-08-06"), 30, 30, DayCellState.FULLY_RECORDED), // Thursday
            cell(today, 39, 39, DayCellState.FULLY_RECORDED), // Friday
        )

        val bests = buildPersonalBests(cells, today)

        assertEquals(69, bests.bestWeek?.available)
        assertTrue(bests.bestWeek!!.fraction <= 1f)
    }
}
