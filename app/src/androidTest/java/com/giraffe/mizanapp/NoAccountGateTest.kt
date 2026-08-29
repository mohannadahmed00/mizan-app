package com.giraffe.mizanapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.daysummary.DaySummaryScreen
import com.giraffe.mizanapp.daysummary.DaySummaryUiState
import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.history.HistoryScreen
import com.giraffe.mizanapp.history.HistoryUiState
import com.giraffe.mizanapp.insights.InsightsScreen
import com.giraffe.mizanapp.insights.InsightsUiState
import com.giraffe.mizanapp.sync.SyncStatusBar
import com.giraffe.mizanapp.today.TodayScreen
import com.giraffe.mizanapp.today.TodayUiState
import com.giraffe.mizanapp.week.DayCellUi
import com.giraffe.mizanapp.week.WeekEvent
import com.giraffe.mizanapp.week.WeekScreen
import com.giraffe.mizanapp.week.WeekUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FR-004, SC-007: with no account configured, the whole Phase 2-6 product —
 * Today, Week, History, Insights, a past day, and back — renders its normal
 * content. Nothing prompts, blocks, or nags for an account beyond the single,
 * dismissible entry point on Today, and [SyncStatusBar] renders nothing.
 */
@RunWith(AndroidJUnit4::class)
class NoAccountGateTest {

    @get:Rule
    val compose = createComposeRule()

    private sealed interface Screen {
        data object Today : Screen
        data object Week : Screen
        data object History : Screen
        data object Insights : Screen
        data object Day : Screen
    }

    private val pastDate: LocalDate = LocalDate.of(2026, 3, 10)

    private val weekWithOneOpenableDay = WeekUiState(
        status = WeekUiState.Status.Ready,
        days = listOf(
            DayCellUi(
                date = pastDate,
                dayLabel = "Tue",
                hijriLabel = null,
                earnedPoints = 0,
                availablePoints = 10,
                state = DayCellState.NOTHING_RECORDED,
            ),
        ),
    )

    @Composable
    private fun Harness(stack: List<Screen>, onPush: (Screen) -> Unit) {
        when (stack.last()) {
            Screen.Today -> TodayScreen(
                state = TodayUiState(status = TodayUiState.Status.Ready),
                onEvent = {},
                onOpenWeek = { onPush(Screen.Week) },
                syncStatus = SyncStatus.NotSignedIn,
            )
            Screen.Week -> WeekScreen(
                state = weekWithOneOpenableDay,
                onEvent = { event ->
                    when (event) {
                        is WeekEvent.OpenDay -> onPush(Screen.Day)
                        WeekEvent.OpenHistory -> onPush(Screen.History)
                        WeekEvent.OpenInsights -> onPush(Screen.Insights)
                        else -> Unit
                    }
                },
                syncStatus = SyncStatus.NotSignedIn,
            )
            Screen.History -> HistoryScreen(
                state = HistoryUiState(status = HistoryUiState.Status.RecordNotStarted),
                onEvent = {},
            )
            Screen.Insights -> InsightsScreen(
                state = InsightsUiState(status = InsightsUiState.Status.RecordNotStarted),
                onEvent = {},
            )
            Screen.Day -> DaySummaryScreen(
                state = DaySummaryUiState(status = DaySummaryUiState.Status.NoRecord),
            )
        }
    }

    @Test
    fun the_whole_product_is_reachable_and_complete_with_no_account() {
        var stack by mutableStateOf(listOf<Screen>(Screen.Today))
        compose.setContent { Harness(stack) { stack = stack + it } }

        compose.onNodeWithText("Week").performClick()
        compose.runOnIdle { assertEquals(Screen.Week, stack.last()) }

        // Week -> a past day -> back.
        compose.onNodeWithTag("day-cell-$pastDate").performClick()
        compose.runOnIdle { assertEquals(Screen.Day, stack.last()) }
        compose.runOnIdle { stack = stack.dropLast(1) }
        compose.runOnIdle { assertEquals(Screen.Week, stack.last()) }

        // Week -> History -> back.
        compose.onNodeWithTag("open-history-button").performClick()
        compose.runOnIdle { assertEquals(Screen.History, stack.last()) }
        compose.runOnIdle { stack = stack.dropLast(1) }
        compose.runOnIdle { assertEquals(Screen.Week, stack.last()) }

        // Week -> Insights -> back.
        compose.onNodeWithTag("open-insights-button").performClick()
        compose.runOnIdle { assertEquals(Screen.Insights, stack.last()) }
        compose.runOnIdle { stack = stack.dropLast(1) }
        compose.runOnIdle { assertEquals(Screen.Week, stack.last()) }
    }

    @Test
    fun every_screen_in_the_flow_renders_its_normal_content() {
        var current by mutableStateOf<Screen>(Screen.Today)
        compose.setContent { Harness(listOf(current)) {} }

        compose.onNodeWithText("Week").assertExists()

        compose.runOnIdle { current = Screen.Week }
        compose.onNodeWithTag("day-cell-$pastDate").assertExists()

        compose.runOnIdle { current = Screen.History }
        compose.onNodeWithText("record", substring = true, ignoreCase = true).assertExists()

        compose.runOnIdle { current = Screen.Insights }
        compose.onNodeWithText("record", substring = true, ignoreCase = true).assertExists()

        compose.runOnIdle { current = Screen.Day }
        compose.onRoot().assertExists()
    }

    @Test
    fun no_screen_in_the_flow_renders_an_account_prompt_or_a_sync_status_line() {
        val screens = listOf(Screen.Today, Screen.Week, Screen.History, Screen.Insights, Screen.Day)
        var current by mutableStateOf(screens.first())
        compose.setContent { Harness(listOf(current)) {} }

        for (screen in screens) {
            compose.runOnIdle { current = screen }

            compose.onAllNodes(hasText("sign in", substring = true, ignoreCase = true)).assertCountEquals(0)
            if (screen != Screen.Today) {
                // Today's own dismissible entry point is the one allowed exception (FR-004).
                compose.onAllNodes(hasText("account", substring = true, ignoreCase = true)).assertCountEquals(0)
            }
            // NotSignedIn renders nothing (contracts/ui-state.md SyncStatusBar table).
            compose.onAllNodes(hasText("backed up", substring = true, ignoreCase = true)).assertCountEquals(0)
            compose.onAllNodes(hasText("waiting to be sent", substring = true, ignoreCase = true)).assertCountEquals(0)
            compose.onAllNodes(hasText("syncing", substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }
}
