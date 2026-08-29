package com.giraffe.mizanapp.leaderboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.history.HistoryScreen
import com.giraffe.mizanapp.history.HistoryUiState
import com.giraffe.mizanapp.insights.InsightsScreen
import com.giraffe.mizanapp.insights.InsightsUiState
import com.giraffe.mizanapp.today.TodayScreen
import com.giraffe.mizanapp.today.TodayUiState
import com.giraffe.mizanapp.week.WeekScreen
import com.giraffe.mizanapp.week.WeekUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoOptInGateTest {
    @get:Rule val compose = createComposeRule()

    private enum class Surface { TODAY, WEEK, HISTORY, INSIGHTS, PROGRESS }

    @Composable
    private fun SurfaceContent(surface: Surface, visibility: Visibility) {
        when (surface) {
            Surface.TODAY -> TodayScreen(TodayUiState(status = TodayUiState.Status.Ready), {}, {}, syncStatus = SyncStatus.UpToDate)
            Surface.WEEK -> WeekScreen(WeekUiState(status = WeekUiState.Status.Ready), {}, syncStatus = SyncStatus.UpToDate)
            Surface.HISTORY -> HistoryScreen(HistoryUiState(status = HistoryUiState.Status.RecordNotStarted), {})
            Surface.INSIGHTS -> InsightsScreen(InsightsUiState(status = InsightsUiState.Status.RecordNotStarted), {})
            Surface.PROGRESS -> LeaderboardSection(
                state = LeaderboardUiState(
                    visibility = visibility,
                    selectedPeriod = PeriodKind.WEEKLY,
                    ranking = RankingState.Unavailable,
                    ownRank = OwnRankState.Unavailable,
                    honorBoard = HonorBoardState.Unavailable,
                    regionLabel = null,
                    isRefreshing = false,
                ),
                onJoin = {},
            )
        }
    }

    @Test
    fun never_opted_in_exposes_nothing_except_one_invitation_inside_progress() {
        var surface by mutableStateOf(Surface.TODAY)
        compose.setContent { SurfaceContent(surface, Visibility.Invitation) }

        Surface.entries.forEach { next ->
            compose.runOnIdle { surface = next }
            compose.onAllNodes(hasText("Other Participant", substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasText("ranking", substring = true, ignoreCase = true)).assertCountEquals(0)
            val expectedInvitations = if (next == Surface.PROGRESS) 1 else 0
            compose.onAllNodes(hasTestTag("leaderboard-invitation")).assertCountEquals(expectedInvitations)
        }
    }

    @Test
    fun signed_out_progress_has_no_leaderboard_surface_at_all() {
        compose.setContent { SurfaceContent(Surface.PROGRESS, Visibility.Hidden) }
        compose.onAllNodes(hasTestTag("leaderboard-invitation")).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("leaderboard-ranking")).assertCountEquals(0)
    }
}
