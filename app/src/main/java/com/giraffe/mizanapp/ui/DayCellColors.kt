package com.giraffe.mizanapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.giraffe.mizanapp.domain.week.DayCellState

/**
 * The one color mapping for [DayCellState], shared by `WeekScreen` and
 * `InsightsScreen`'s month grid (`006` research.md R3) — extracted so the
 * SC-006 "zero red anywhere" audit has exactly one table to check, not two.
 */
@Composable
fun containerColorFor(state: DayCellState): Color = when (state) {
    DayCellState.FULLY_RECORDED -> MaterialTheme.colorScheme.secondaryContainer
    DayCellState.PARTLY_RECORDED -> MaterialTheme.colorScheme.tertiaryContainer
    DayCellState.NOTHING_RECORDED -> MaterialTheme.colorScheme.surfaceVariant
    DayCellState.NOT_YET_ELAPSED, DayCellState.OUTSIDE_RECORD -> MaterialTheme.colorScheme.surface
}
