package com.giraffe.mizanapp.week

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Week navigation affordances. At a bound, the affordance is simply
 * unavailable — no error, no message, no explanation (FR-018).
 */
@RunWith(AndroidJUnit4::class)
class WeekNavigationScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val weekStart = LocalDate.parse("2026-08-08")

    private fun stateWith(canGoPrevious: Boolean, canGoNext: Boolean): WeekUiState = WeekUiState(
        status = WeekUiState.Status.Ready,
        startDate = weekStart,
        days = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            DayCellUi(
                date = date,
                dayLabel = "D$offset",
                hijriLabel = "label",
                earnedPoints = 0,
                availablePoints = 69,
                state = com.giraffe.mizanapp.domain.week.DayCellState.NOTHING_RECORDED,
            )
        },
        earnedPoints = 0,
        elapsedAvailablePoints = 483,
        weekTargetPoints = 500,
        canGoPrevious = canGoPrevious,
        canGoNext = canGoNext,
    )

    @Test
    fun tapping_previous_when_available_emits_previous_week() {
        var events = 0
        compose.setContent {
            WeekScreen(
                state = stateWith(canGoPrevious = true, canGoNext = true),
                onEvent = { if (it is WeekEvent.PreviousWeek) events++ },
            )
        }

        compose.onNodeWithTag("previous-week-button").performClick()

        assertEquals(1, events)
    }

    @Test
    fun tapping_next_when_available_emits_next_week() {
        var events = 0
        compose.setContent {
            WeekScreen(
                state = stateWith(canGoPrevious = true, canGoNext = true),
                onEvent = { if (it is WeekEvent.NextWeek) events++ },
            )
        }

        compose.onNodeWithTag("next-week-button").performClick()

        assertEquals(1, events)
    }

    @Test
    fun previous_unavailable_is_disabled_emits_nothing_and_shows_no_message() {
        var events = 0
        compose.setContent {
            WeekScreen(
                state = stateWith(canGoPrevious = false, canGoNext = true),
                onEvent = { events++ },
            )
        }

        compose.onNodeWithTag("previous-week-button").performClick()

        assertEquals(0, events)
        listOf("earliest", "cannot", "no more").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun next_unavailable_is_disabled_emits_nothing_and_shows_no_message() {
        var events = 0
        compose.setContent {
            WeekScreen(
                state = stateWith(canGoPrevious = true, canGoNext = false),
                onEvent = { events++ },
            )
        }

        compose.onNodeWithTag("next-week-button").performClick()

        assertEquals(0, events)
        listOf("earliest", "cannot", "no more").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }
}
