package com.giraffe.mizanapp.leaderboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind

/**
 * FR-027a: rendered for WEEKLY and MONTHLY only — for DAILY, absent entirely,
 * not empty and not disabled. Renders qualifying members unordered, with no
 * points and no position, and says nothing about a non-qualifying viewer.
 */
@Composable
fun HonorBoardPanel(
    kind: PeriodKind,
    state: HonorBoardState,
    modifier: Modifier = Modifier,
) {
    TODO("T077")
}
