package com.giraffe.mizanapp.leaderboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.leaderboard.HonorBoard
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardMember
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.LeaderboardPeriod
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RegionId
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** FR-027, FR-029, FR-030, FR-027a. */
@RunWith(AndroidJUnit4::class)
class HonorBoardPanelTest {
    @get:Rule val compose = createComposeRule()

    private val weekly = LeaderboardPeriod(
        PeriodKind.WEEKLY,
        LocalDate.parse("2026-08-15"),
        LocalDate.parse("2026-08-21"),
        RegionId("egypt-cairo"),
    )

    @Test
    fun members_render_unordered_with_no_points_or_position_text() {
        val state = HonorBoardState.Available(
            HonorBoard(weekly, listOf(HonorBoardMember("Alice", false), HonorBoardMember("Bob", true)), viewerQualified = true),
        )
        compose.setContent { HonorBoardPanel(kind = PeriodKind.WEEKLY, state = state) }

        compose.onNodeWithText("Alice").assertExists()
        compose.onNodeWithText("Bob").assertExists()
        compose.onNodeWithText("points", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun a_non_qualifying_viewer_sees_no_statement_about_themselves() {
        val state = HonorBoardState.Available(
            HonorBoard(weekly, listOf(HonorBoardMember("Alice", false)), viewerQualified = false),
        )
        compose.setContent { HonorBoardPanel(kind = PeriodKind.WEEKLY, state = state) }

        compose.onNodeWithText("you", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("short", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("qualify", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun selecting_the_daily_period_shows_no_panel_at_all() {
        val state = HonorBoardState.Available(
            HonorBoard(weekly, listOf(HonorBoardMember("Alice", false)), viewerQualified = true),
        )
        compose.setContent { HonorBoardPanel(kind = PeriodKind.DAILY, state = state) }

        compose.onRoot().onChildren().assertCountEquals(0)
    }
}
