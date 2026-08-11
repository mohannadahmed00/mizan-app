package com.giraffe.mizanapp.week

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A week that could not be fully backfilled shows no figures at all — never
 * a total computed over an incomplete set of days (FR-014b), and never
 * blame directed at the user (FR-014c).
 */
@RunWith(AndroidJUnit4::class)
class WeekFailureStateTest {

    @get:Rule
    val compose = createComposeRule()

    private val failedState = WeekUiState(status = WeekUiState.Status.CouldNotLoad("storage is unavailable"))

    @Test
    fun no_day_labels_appear() {
        compose.setContent { WeekScreen(state = failedState, onEvent = {}) }

        listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri").forEach { label ->
            compose.onAllNodesWithText(label, substring = false).assertCountEquals(0)
        }
    }

    @Test
    fun no_points_figures_appear() {
        compose.setContent { WeekScreen(state = failedState, onEvent = {}) }

        compose.onAllNodesWithText("of", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Week target", substring = true).assertCountEquals(0)
    }

    @Test
    fun retry_emits_exactly_one_retry_event() {
        var retryCount = 0
        compose.setContent {
            WeekScreen(state = failedState, onEvent = { if (it is WeekEvent.Retry) retryCount++ })
        }

        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun the_message_blames_the_app_never_the_user() {
        compose.setContent { WeekScreen(state = failedState, onEvent = {}) }

        listOf("you", "your", "missed", "failed").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }
}
