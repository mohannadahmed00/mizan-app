package com.giraffe.mizanapp.domain.leaderboard

/** Placeholder state required by the repository seam before Honor Board behavior is implemented. */
sealed interface HonorBoardState {
    data object Unavailable : HonorBoardState
}
