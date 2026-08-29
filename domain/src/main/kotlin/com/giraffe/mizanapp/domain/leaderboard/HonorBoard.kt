package com.giraffe.mizanapp.domain.leaderboard

/** Placeholder state required by the repository seam before Honor Board behavior is implemented. */
sealed interface HonorBoardState {
    data object Unavailable : HonorBoardState
}

/** SC-011: qualification depends only on consistency, never on points (FR-027). */
fun qualifiesForHonorBoard(daysEngaged: Int, threshold: Int): Boolean {
    TODO("T073")
}
