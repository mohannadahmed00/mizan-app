package com.giraffe.mizanapp.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.ui.containerColorFor

/**
 * The Week screen.
 *
 * Same two rules as `TodayScreen`: **Principle IX** — no state here reads as
 * a failure, including [DayCellState.OUTSIDE_RECORD] and
 * [DayCellState.NOT_YET_ELAPSED], neither of which is a day the user missed.
 * **Principle VI** — this screen is read-only; [WeekEvent] carries no case
 * that could record, undo, add, remove, or reorder anything.
 */
@Composable
fun WeekScreen(
    state: WeekUiState,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val status = state.status) {
        is WeekUiState.Status.Loading -> LoadingState(modifier)
        is WeekUiState.Status.CatalogueUnavailable -> MessageState(
            "The task list could not be loaded.", status.detail, modifier,
        )
        is WeekUiState.Status.CouldNotLoad -> CouldNotLoadState(status, onEvent, modifier)
        is WeekUiState.Status.Ready -> ReadyState(state, onEvent, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * No day cell and no total is shown here — a week rendered with the
 * unfillable days omitted would understate its own denominator (FR-014b).
 * The message attributes the failure to the app, never the user (FR-014c).
 */
@Composable
private fun CouldNotLoadState(
    status: WeekUiState.Status.CouldNotLoad,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This week couldn't load right now.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(status.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = { onEvent(WeekEvent.Retry) },
            modifier = Modifier.testTag("retry-button"),
        ) { Text("Retry") }
    }
}

@Composable
private fun ReadyState(
    state: WeekUiState,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        WeekNavigationRow(state, onEvent)
        Spacer(Modifier.width(8.dp))
        WeekHeader(state)
        Spacer(Modifier.width(16.dp))

        state.days.forEach { day ->
            DayRow(day, onEvent)
            Spacer(Modifier.width(8.dp))
        }

        Spacer(Modifier.width(16.dp))
        TextButton(
            onClick = { onEvent(WeekEvent.OpenHistory) },
            modifier = Modifier.testTag("open-history-button"),
        ) { Text("View history") }
        TextButton(
            onClick = { onEvent(WeekEvent.OpenInsights) },
            modifier = Modifier.testTag("open-insights-button"),
        ) { Text("Insights") }
    }
}

/**
 * At a bound the affordance is simply unavailable — no error, no message,
 * no explanation (FR-018).
 */
@Composable
private fun WeekNavigationRow(state: WeekUiState, onEvent: (WeekEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            onClick = { onEvent(WeekEvent.PreviousWeek) },
            enabled = state.canGoPrevious,
            modifier = Modifier.testTag("previous-week-button"),
        ) { Text("Previous") }

        TextButton(
            onClick = { onEvent(WeekEvent.NextWeek) },
            enabled = state.canGoNext,
            modifier = Modifier.testTag("next-week-button"),
        ) { Text("Next") }
    }
}

/**
 * Two figures, never combined: the headline is earned against what has
 * elapsed so far; the week's full target is shown alongside as context, not
 * as the denominator (FR-009a, FR-009b).
 */
@Composable
private fun WeekHeader(state: WeekUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "${state.earnedPoints} of ${state.elapsedAvailablePoints} points",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Week target: ${state.weekTargetPoints}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayRow(day: DayCellUi, onEvent: (WeekEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day-cell-${day.date}")
            .clickable(enabled = day.isOpenable) { onEvent(WeekEvent.OpenDay(day.date)) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColorFor(day.state)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.dayLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(48.dp),
            )
            day.hijriLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } ?: Spacer(Modifier.weight(1f))

            Text(
                text = pointsText(day),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A day still ahead of the user is not a day they missed, and a day outside
 * the record never existed — neither may read like a zero the user earned
 * (FR-017a). All three read distinctly from each other and from a completed
 * day, and none of the three is expressed as a failure.
 */
private fun pointsText(day: DayCellUi): String = when (day.state) {
    DayCellState.OUTSIDE_RECORD -> "Not recorded"
    DayCellState.NOT_YET_ELAPSED -> "Upcoming"
    else -> "${day.earnedPoints} of ${day.availablePoints}"
}
