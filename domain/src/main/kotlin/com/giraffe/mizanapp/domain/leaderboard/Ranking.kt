package com.giraffe.mizanapp.domain.leaderboard

import java.time.Instant

/** Contains only the server-issued facts a ranking row is allowed to expose. */
data class RankingEntry(
    val userId: String,
    val displayName: String,
    val points: Int,
    val position: Int,
    val isViewer: Boolean,
)

/** A bounded server-ranked page with enough context to label its freshness. */
data class Ranking(
    val period: LeaderboardPeriod,
    val region: Region,
    val entries: List<RankingEntry>,
    val hasMore: Boolean,
    val retrievedAt: Instant,
    val isProvisional: Boolean,
)

/** Makes the viewer's row reachable independently of page position. */
data class OwnRank(
    val entry: RankingEntry,
    val neighbours: List<RankingEntry>,
    val totalParticipants: Int,
)

/** Distinguishes a live page from a visibly aged cache or no usable data. */
sealed interface RankingState {
    data object Unavailable : RankingState
    data class Cached(val ranking: Ranking) : RankingState
    data class Live(val ranking: Ranking) : RankingState
}
