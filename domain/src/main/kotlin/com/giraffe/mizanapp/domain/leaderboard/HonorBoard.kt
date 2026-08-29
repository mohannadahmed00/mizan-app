package com.giraffe.mizanapp.domain.leaderboard

/** Exactly enough to render a name and mark the viewer — never points, never a position (Rule D). */
data class HonorBoardMember(
    val displayName: String,
    val isViewer: Boolean,
)

/** Qualifying members for one WEEKLY or MONTHLY period (FR-027a). No ordering that reads as ranking. */
data class HonorBoard(
    val period: LeaderboardPeriod,
    val members: List<HonorBoardMember>,
    val viewerQualified: Boolean,
)

sealed interface HonorBoardState {
    data object Unavailable : HonorBoardState
    data class Available(val honorBoard: HonorBoard) : HonorBoardState
}

/** SC-011: qualification depends only on consistency, never on points (FR-027). */
fun qualifiesForHonorBoard(daysEngaged: Int, threshold: Int): Boolean {
    TODO("T073")
}
