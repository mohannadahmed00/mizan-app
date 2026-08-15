package com.giraffe.mizanapp.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class StreakElementTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readyState_displaysBothFigures() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(current = 5, longest = 12, todayCounted = true),
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("5", substring = true).assertExists()
        composeTestRule.onNodeWithText("12", substring = true).assertExists()
    }

    @Test
    fun zeroStreak_readsAsAnInvitationNotAResult() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(current = 0, longest = 0, todayCounted = false),
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("begin", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun resolving_showsNoDigitAtAll() {
        composeTestRule.setContent {
            StreakElement(panel = StreakPanelUi.Resolving, onRetry = {})
        }

        composeTestRule.onAllNodesWithText("0").assertCountEquals(0)
    }

    @Test
    fun unavailable_showsMessageAndRetryInvokesCallback() {
        var retried = false
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Unavailable("could not read the record"),
                onRetry = { retried = true },
            )
        }

        composeTestRule.onNodeWithText("Couldn't read your streak", substring = true).assertExists()
        composeTestRule.onNodeWithText("Retry", substring = true).performClick()
        assert(retried)
    }

    @Test
    fun todayPending_isDistinctFromTodayCounted() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(current = 5, longest = 12, todayCounted = false),
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("5", substring = true).assertExists()
        composeTestRule.onNodeWithTag("streak-today-pending").assertExists()
    }

    @Test
    fun todayCounted_hasNoPendingMarker() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(current = 5, longest = 12, todayCounted = true),
                onRetry = {},
            )
        }

        composeTestRule.onAllNodesWithTag("streak-today-pending").assertCountEquals(0)
    }

    private fun sevenDayWindow(state: com.giraffe.mizanapp.domain.streak.ActivityState) =
        (0..6).map { ActivityDayUi(java.time.LocalDate.parse("2026-08-19").minusDays((6 - it).toLong()), state) }

    @Test
    fun mixedRecentActivity_rendersSevenPositions() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(
                    current = 3,
                    longest = 12,
                    todayCounted = true,
                    recentActivity = sevenDayWindow(com.giraffe.mizanapp.domain.streak.ActivityState.COUNTED),
                    showBreakNotice = false,
                ),
                onRetry = {},
            )
        }

        composeTestRule.onAllNodesWithTag("streak-activity-day").assertCountEquals(7)
    }

    @Test
    fun breakNotice_presentsLongestAsStanding() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(
                    current = 0,
                    longest = 38,
                    todayCounted = false,
                    recentActivity = sevenDayWindow(com.giraffe.mizanapp.domain.streak.ActivityState.NOT_RECORDED),
                    showBreakNotice = true,
                ),
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("38-day", substring = true).assertExists()
        composeTestRule.onNodeWithText("stands", substring = true, ignoreCase = true).assertExists()
        listOf("missed", "failed", "lost", "broken").forEach { forbidden ->
            composeTestRule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun noBreakNotice_saysNothingAboutAnEndedRun() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(
                    current = 0,
                    longest = 38,
                    todayCounted = false,
                    recentActivity = sevenDayWindow(com.giraffe.mizanapp.domain.streak.ActivityState.OUTSIDE_RECORD),
                    showBreakNotice = false,
                ),
                onRetry = {},
            )
        }

        composeTestRule.onAllNodesWithText("stands", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun atRisk_namesWhatIsStillPossible() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(
                    current = 12,
                    longest = 12,
                    todayCounted = false,
                    recentActivity = sevenDayWindow(com.giraffe.mizanapp.domain.streak.ActivityState.COUNTED),
                    showBreakNotice = false,
                    isAtRisk = true,
                ),
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("still open", substring = true, ignoreCase = true).assertExists()
        listOf("lose", "losing", "penalty", "warning", "!").forEach { forbidden ->
            composeTestRule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun notAtRisk_showsNoNudge() {
        composeTestRule.setContent {
            StreakElement(
                panel = StreakPanelUi.Ready(
                    current = 12,
                    longest = 12,
                    todayCounted = true,
                    recentActivity = sevenDayWindow(com.giraffe.mizanapp.domain.streak.ActivityState.COUNTED),
                    showBreakNotice = false,
                    isAtRisk = false,
                ),
                onRetry = {},
            )
        }

        composeTestRule.onAllNodesWithText("still open", substring = true, ignoreCase = true).assertCountEquals(0)
    }
}
