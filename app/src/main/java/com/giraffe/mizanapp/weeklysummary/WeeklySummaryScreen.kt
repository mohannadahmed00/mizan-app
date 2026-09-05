package com.giraffe.mizanapp.weeklysummary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The Weekly Summary screen. Only ever shows a closed week (FR-027a) — there
 * is no "current week" branch, deliberately.
 *
 * **Principle IX**: no field here counts anything not done, no figure is
 * framed as a shortfall, and a quiet week still renders in full, in
 * encouraging terms (FR-028). **Principle VI**: read-only — the only events
 * this screen can send move which closed week is shown, or leave for the
 * weekly sheet.
 */
@Composable
fun WeeklySummaryScreen(
    state: WeeklySummaryUiState,
    onEarlier: () -> Unit = {},
    onLater: () -> Unit = {},
    onOpenWeekSheet: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        is WeeklySummaryContent.Waiting -> WaitingState(content, onOpenWeekSheet, modifier)
        is WeeklySummaryContent.Closed -> ClosedState(state, content, onEarlier, onLater, modifier)
        is WeeklySummaryContent.Unavailable -> UnavailableState(content, modifier)
    }
}

@Composable
private fun WaitingState(content: WeeklySummaryContent.Waiting, onOpenWeekSheet: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp).testTag("weekly-summary-waiting"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your first weekly summary arrives ${content.firstSummaryAt}.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            "Until then, this week's progress is on the weekly sheet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Button(onClick = onOpenWeekSheet, modifier = Modifier.testTag("open-weekly-sheet-button")) {
            Text("Go to this week")
        }
    }
}

@Composable
private fun UnavailableState(content: WeeklySummaryContent.Unavailable, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(content.reason, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ClosedState(
    state: WeeklySummaryUiState,
    content: WeeklySummaryContent.Closed,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onEarlier, enabled = state.canGoEarlier, modifier = Modifier.testTag("weekly-summary-earlier")) {
                Text("Earlier")
            }
            TextButton(onClick = onLater, enabled = state.canGoLater, modifier = Modifier.testTag("weekly-summary-later")) {
                Text("Later")
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(content.range, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(16.dp))

        Text(
            text = if (content.quiet) "This week is quiet so far — nothing recorded yet." else "${content.daysEngaged} of ${content.daysInWeek} days engaged",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(4.dp))
        Text("${content.tasksRecorded} tasks recorded", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${content.pointsEarned} of ${content.pointsAvailable} points earned",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.width(4.dp))
        Text("Streak at close: ${content.streakAtClose}", style = MaterialTheme.typography.bodyMedium)

        content.coverage?.let {
            Spacer(Modifier.width(16.dp))
            Text(
                "This device's record covers from ${it.coveredFrom}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
