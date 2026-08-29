package com.giraffe.mizanapp.leaderboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-008, FR-034, FR-035, FR-036: the leaderboard section degrades in
 * isolation — an unavailable state names the system, never the person, and a
 * cached page is stamped with its age rather than rendered as current.
 */
@RunWith(AndroidJUnit4::class)
class LeaderboardDegradationTest {
    @get:Rule val compose = createComposeRule()

    private val region = Region(RegionId("egypt-cairo"), "Egypt (Cairo)", ZoneId.of("Africa/Cairo"))

    @Test
    fun unavailable_state_names_the_system_never_the_person() {
        compose.setContent {
            LeaderboardSection(state = state(RankingState.Unavailable), onJoin = {}, onLeave = {}, onPeriodSelected = {})
        }

        compose.onNodeWithText("Standings aren't available right now").assertExists()
        FORBIDDEN.forEach { word ->
            compose.onNodeWithText(word, substring = true, ignoreCase = true).assertDoesNotExist()
        }
    }

    @Test
    fun a_cached_page_states_its_age_rather_than_rendering_silently_as_current() {
        compose.setContent {
            LeaderboardSection(
                state = state(RankingState.Cached(ranking(Instant.parse("2026-03-14T09:40:00Z")))),
                onJoin = {},
                onLeave = {},
                onPeriodSelected = {},
            )
        }

        compose.onNodeWithTag("leaderboard-cache-age").assertExists()
    }

    private fun state(ranking: RankingState) = LeaderboardUiState(
        visibility = Visibility.Participating,
        selectedPeriod = PeriodKind.WEEKLY,
        ranking = ranking,
        ownRank = OwnRankState.Unavailable,
        honorBoard = HonorBoardState.Unavailable,
        regionLabel = region.displayName,
        isRefreshing = false,
    )

    private fun ranking(retrievedAt: Instant) = Ranking(
        period = LeaderboardPeriod(PeriodKind.WEEKLY, LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-20"), region.id),
        region = region,
        entries = listOf(RankingEntry("viewer", "Person", 10, 1, true)),
        hasMore = false,
        retrievedAt = retrievedAt,
        isProvisional = false,
    )

    private companion object {
        val FORBIDDEN = listOf(
            "failed", "failure", "error", "you didn't", "you haven't", "your fault", "offline", "retry now",
        )
    }
}
