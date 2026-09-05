package com.giraffe.mizanapp

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryContent
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryScreen
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklySummaryScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun closed(quiet: Boolean = false) = WeeklySummaryContent.Closed(
        weekKey = WeekKey("2026-08-29"),
        range = "2026-08-29 - 2026-09-04",
        daysEngaged = if (quiet) 0 else 3,
        daysInWeek = 7,
        tasksRecorded = if (quiet) 0 else 5,
        pointsEarned = if (quiet) 0 else 12,
        pointsAvailable = 500,
        streakAtClose = 4,
        coverage = null,
        quiet = quiet,
    )

    @Test fun waitingStateRendersExplanationAndSheetControlButNoFigures() {
        val state = WeeklySummaryUiState(WeeklySummaryContent.Waiting(LocalDate.of(2026, 9, 4)), canGoEarlier = false, canGoLater = false)
        compose.setContent { WeeklySummaryScreen(state = state) }

        compose.onNodeWithTag("weekly-summary-waiting").assertExists()
        compose.onNodeWithTag("open-weekly-sheet-button").assertExists()
        compose.onNodeWithText("12", substring = true).assertDoesNotExist()
    }

    @Test fun closedStateRendersDaysEngagedTasksRecordedAndPointsEarned() {
        val state = WeeklySummaryUiState(closed(), canGoEarlier = true, canGoLater = true)
        compose.setContent { WeeklySummaryScreen(state = state) }

        compose.onNodeWithText("3", substring = true).assertExists()
        compose.onNodeWithText("5", substring = true).assertExists()
        compose.onNodeWithText("12", substring = true).assertExists()
    }

    @Test fun quietWeekRendersWithNoZeroPresentedAsAShortfall() {
        val state = WeeklySummaryUiState(closed(quiet = true), canGoEarlier = true, canGoLater = true)
        compose.setContent { WeeklySummaryScreen(state = state) }

        listOf("missed", "failed", "shortfall", "0%", "nothing done").forEach { forbidden ->
            compose.onNodeWithText(forbidden, substring = true, ignoreCase = true).assertDoesNotExist()
        }
    }

    @Test fun earlierAndLaterControlsAreDisabledAtTheEndsOfRecordedHistory() {
        val state = WeeklySummaryUiState(closed(), canGoEarlier = false, canGoLater = false)
        compose.setContent { WeeklySummaryScreen(state = state) }

        compose.onNodeWithTag("weekly-summary-earlier").assertIsNotEnabled()
        compose.onNodeWithTag("weekly-summary-later").assertIsNotEnabled()
    }
}
