package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.leaderboard.LoadMoreResult
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import kotlinx.coroutines.flow.Flow

interface LeaderboardRepository {

    /**
     * Observes the cached ranking for a period. Emits from local storage; a refresh
     * writes into local storage and is never awaited by a ViewModel (Principle IV).
     *
     * Emits with `retrievedAt` set so the caller can render age rather than
     * presenting a cached page as current (FR-036).
     */
    fun observeRanking(kind: PeriodKind): Flow<RankingState>

    /**
     * The viewer's own position and neighbours, independent of which page is
     * loaded — SC-009 requires this without paging through 10 000 rows (research R9).
     */
    fun observeOwnRank(kind: PeriodKind): Flow<OwnRankState>

    /** Extends the loaded page on demand (FR-024). Bounded; never unbounded. */
    suspend fun loadMore(kind: PeriodKind): LoadMoreResult

    /** Requests a refresh. Returns immediately; the result arrives through the Flows. */
    suspend fun refresh(kind: PeriodKind)
}
