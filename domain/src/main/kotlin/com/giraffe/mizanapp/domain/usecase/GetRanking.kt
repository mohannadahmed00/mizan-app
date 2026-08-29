package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import com.giraffe.mizanapp.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow

class GetRanking(private val repository: LeaderboardRepository) {
    operator fun invoke(kind: PeriodKind): Flow<RankingState> = repository.observeRanking(kind)
}
