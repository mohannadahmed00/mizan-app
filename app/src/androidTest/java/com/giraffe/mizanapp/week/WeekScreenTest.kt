package com.giraffe.mizanapp.week

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.week.DayCellState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val weekStart = LocalDate.parse("2026-08-08")

    private fun cellFor(offset: Long, state: DayCellState, earned: Int = 0, available: Int = 69): DayCellUi {
        val date = weekStart.plusDays(offset)
        return DayCellUi(
            date = date,
            dayLabel = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")[offset.toInt()],
            hijriLabel = if (state == DayCellState.OUTSIDE_RECORD || state == DayCellState.NOT_YET_ELAPSED) null else "1 Muharram",
            earnedPoints = earned,
            availablePoints = available,
            state = state,
        )
    }

    private fun readyState(): WeekUiState = WeekUiState(
        status = WeekUiState.Status.Ready,
        startDate = weekStart,
        days = listOf(
            cellFor(0, DayCellState.OUTSIDE_RECORD),
            cellFor(1, DayCellState.NOTHING_RECORDED),
            cellFor(2, DayCellState.PARTLY_RECORDED, earned = 30, available = 74),
            cellFor(3, DayCellState.FULLY_RECORDED, earned = 69, available = 69),
            cellFor(4, DayCellState.NOTHING_RECORDED),
            cellFor(5, DayCellState.NOT_YET_ELAPSED, available = 74),
            cellFor(6, DayCellState.NOT_YET_ELAPSED, available = 76),
        ),
        earnedPoints = 99,
        elapsedAvailablePoints = 281,
        weekTargetPoints = 500,
    )

    /** Matches any node whose test tag starts with "day-cell-", regardless of date. */
    private val anyDayCell = SemanticsMatcher("has a day-cell-* test tag") { node ->
        node.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("day-cell-")
    }

    @Test
    fun seven_day_cells_appear_saturday_before_friday() {
        compose.setContent { WeekScreen(state = readyState(), onEvent = {}) }

        compose.onAllNodes(anyDayCell, useUnmergedTree = true).assertCountEquals(7)

        val saturdayTop = compose.onNodeWithTag("day-cell-2026-08-08").getBoundsInRoot().top
        val fridayTop = compose.onNodeWithTag("day-cell-2026-08-14").getBoundsInRoot().top
        assertTrue("Saturday must render above Friday", saturdayTop <= fridayTop)
    }

    @Test
    fun headline_and_target_are_separate_figures() {
        compose.setContent { WeekScreen(state = readyState(), onEvent = {}) }

        // The headline reads "99 of 281" (earned of elapsedAvailable); the
        // target of 500 must appear as a distinct, separately-findable node,
        // never combined into the same fraction.
        compose.onNodeWithText("99 of 281", substring = true).assertExists()
        compose.onNodeWithText("500", substring = true).assertExists()
        compose.onAllNodesWithText("99 of 500", substring = true).assertCountEquals(0)
    }

    @Test
    fun tapping_an_openable_cell_emits_open_day() {
        var opened: LocalDate? = null
        compose.setContent {
            WeekScreen(state = readyState(), onEvent = { if (it is WeekEvent.OpenDay) opened = it.date })
        }

        compose.onNodeWithTag("day-cell-2026-08-10").performClick() // PARTLY_RECORDED

        assertEquals(LocalDate.parse("2026-08-10"), opened)
    }

    @Test
    fun tapping_a_non_openable_cell_emits_nothing() {
        var eventCount = 0
        compose.setContent {
            WeekScreen(state = readyState(), onEvent = { eventCount++ })
        }

        compose.onNodeWithTag("day-cell-2026-08-08").performClick() // OUTSIDE_RECORD
        compose.onNodeWithTag("day-cell-2026-08-13").performClick() // NOT_YET_ELAPSED

        assertEquals(0, eventCount)
    }

    @Test
    fun no_shame_language_appears_anywhere() {
        compose.setContent { WeekScreen(state = readyState(), onEvent = {}) }

        listOf("missed", "failed", "behind", "to go").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
        compose.onAllNodesWithText("-", substring = true).assertCountEquals(0)
    }
}
