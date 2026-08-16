package com.giraffe.mizanapp.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.giraffe.mizanapp.domain.week.DayCellState

/**
 * The one color mapping for [DayCellState], shared by `WeekScreen` and
 * `InsightsScreen`'s month grid (`006` research.md R3) — extracted so the
 * SC-006 "zero red anywhere" audit has exactly one table to check, not two.
 *
 * Takes [scheme] explicitly rather than reading `MaterialTheme.colorScheme`
 * itself, so the mapping is a plain function `DayCellColorsTest` can call
 * directly from a JVM test, with no composition needed.
 */
fun containerColorFor(state: DayCellState, scheme: ColorScheme): Color = when (state) {
    DayCellState.FULLY_RECORDED -> scheme.secondaryContainer
    DayCellState.PARTLY_RECORDED -> scheme.tertiaryContainer
    DayCellState.NOTHING_RECORDED -> scheme.surfaceVariant
    DayCellState.NOT_YET_ELAPSED, DayCellState.OUTSIDE_RECORD -> scheme.surface
    // Placeholder — T106 picks a container visually distinct from both
    // NOTHING_RECORDED and OUTSIDE_RECORD. Reusing surface here is what
    // DayCellColorsTest (T096) exists to catch as still-wrong.
    DayCellState.NOT_YET_KNOWN -> scheme.surface
}

@Composable
fun containerColorFor(state: DayCellState): Color = containerColorFor(state, MaterialTheme.colorScheme)
