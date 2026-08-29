package com.giraffe.mizanapp.leaderboard

import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RankingState

data class LeaderboardUiState(
    val visibility: Visibility,
    val selectedPeriod: PeriodKind,
    val ranking: RankingState,
    val ownRank: OwnRankState,
    val honorBoard: HonorBoardState,
    val regionLabel: String?,
    val isRefreshing: Boolean,
)

sealed interface Visibility {
    data object Hidden : Visibility
    data object Invitation : Visibility
    data object Participating : Visibility
}

data class RankingRowUiModel(
    val displayName: String,
    val points: String,
    val position: String,
    val isViewer: Boolean,
)
