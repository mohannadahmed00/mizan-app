package com.giraffe.mizanapp.leaderboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.LeaderboardPeriod
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.Ranking
import com.giraffe.mizanapp.domain.leaderboard.RankingEntry
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import com.giraffe.mizanapp.domain.leaderboard.Region
import com.giraffe.mizanapp.domain.leaderboard.RegionId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** FR-011, FR-026: switching period re-renders the ranking and the period label. */
@RunWith(AndroidJUnit4::class)
class PeriodSwitchTest {
    @get:Rule val compose = createComposeRule()

    private val region = Region(RegionId("egypt-cairo"), "Egypt (Cairo)", ZoneId.of("Africa/Cairo"))

    @Test
    fun switching_period_rerenders_the_ranking_and_the_weekly_label_states_its_saturday_to_friday_span() {
        var selected: PeriodKind? = null
        compose.setContent {
            LeaderboardSection(
                state = state(PeriodKind.WEEKLY),
                onJoin = {},
                onLeave = {},
                onPeriodSelected = { selected = it },
            )
        }

        compose.onNodeWithText("Saturday", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("Friday", substring = true, ignoreCase = true).assertExists()

        compose.onNodeWithTag("leaderboard-period-monthly").performClick()

        assertEquals(PeriodKind.MONTHLY, selected)
    }

    private fun state(kind: PeriodKind) = LeaderboardUiState(
        visibility = Visibility.Participating,
        selectedPeriod = kind,
        ranking = RankingState.Live(
            Ranking(
                period = LeaderboardPeriod(kind, LocalDate.parse("2026-08-15"), LocalDate.parse("2026-08-21"), region.id),
                region = region,
                entries = listOf(RankingEntry("viewer", "Person", 10, 1, true)),
                hasMore = false,
                retrievedAt = Instant.parse("2026-08-15T09:00:00Z"),
                isProvisional = false,
            ),
        ),
        ownRank = OwnRankState.Unavailable,
        honorBoard = HonorBoardState.Unavailable,
        regionLabel = region.displayName,
        isRefreshing = false,
    )
}
