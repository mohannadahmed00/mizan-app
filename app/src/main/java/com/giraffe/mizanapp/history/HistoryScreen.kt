package com.giraffe.mizanapp.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.week.DayCellUi

/**
 * The History screen: a scrolling list of weeks, newest first, back to the
 * record start.
 *
 * **Principle IX** — no state here reads as a failure, including a week
 * totalling zero after a long gap (FR-029, FR-030). **Principle VI** — this
 * screen is read-only; [HistoryEvent] carries no case that could record,
 * undo, add, remove, or reorder anything.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onEvent: (HistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val status = state.status) {
        is HistoryUiState.Status.Loading -> LoadingState(modifier)
        is HistoryUiState.Status.RecordNotStarted -> RecordNotStartedState(modifier)
        is HistoryUiState.Status.CouldNotLoad -> CouldNotLoadState(status, onEvent, modifier)
        is HistoryUiState.Status.CatalogueUnavailable -> ReadyState(state, onEvent, modifier, partialNotice = status.detail)
        is HistoryUiState.Status.Ready -> ReadyState(state, onEvent, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A beginning, not an empty list (FR-007). */
@Composable
private fun RecordNotStartedState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your record hasn't started yet.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            "Complete a task on Today to begin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CouldNotLoadState(
    status: HistoryUiState.Status.CouldNotLoad,
    onEvent: (HistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("History couldn't load right now.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(status.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = { onEvent(HistoryEvent.Retry) },
            modifier = Modifier.testTag("retry-button"),
        ) { Text("Retry") }
    }
}

@Composable
private fun ReadyState(
    state: HistoryUiState,
    onEvent: (HistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
    partialNotice: String? = null,
) {
    Column(modifier.fillMaxSize()) {
        partialNotice?.let {
            Text(
                text = "Some weeks can't be built yet: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("partial-catalogue-notice"),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("history-list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.weeks, key = { it.weekKey.value }) { week ->
                WeekRow(week, onEvent)
            }
            item {
                if (state.hasMore) {
                    LoadMoreTrigger(state.isLoadingMore, onEvent)
                } else if (state.weeks.isNotEmpty()) {
                    Text(
                        "This is where your record begins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("record-start-notice"),
                    )
                }
            }
        }
    }
}

/**
 * Triggers [HistoryEvent.LoadMore] the moment it enters composition — this is
 * the last item in the list, so it becomes visible exactly when the user
 * scrolls to the end (FR-005).
 */
@Composable
private fun LoadMoreTrigger(isLoadingMore: Boolean, onEvent: (HistoryEvent) -> Unit) {
    LaunchedEffect(Unit) { onEvent(HistoryEvent.LoadMore) }
    if (isLoadingMore) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun WeekRow(week: WeekRowUi, onEvent: (HistoryEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("week-row-${week.weekKey.value}"),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "${week.startDate} - ${week.endDate}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${week.earnedPoints} of ${week.availablePoints} points",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.days.forEach { day -> DayDot(day, onEvent) }
            }
        }
    }
}

/**
 * One day position within a week row. Four visually and semantically
 * distinct states (FR-020a): recorded, elapsed-not-recorded, today-pending,
 * and outside-the-record. None of the four reads as a failure (Principle IX).
 */
@Composable
private fun DayDot(day: DayCellUi, onEvent: (HistoryEvent) -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .testTag("day-cell-${day.date}")
            .clickable(enabled = day.isOpenable) { onEvent(HistoryEvent.OpenDay(day.date)) }
            .semantics { contentDescription = descriptionFor(day.state) },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = containerColorFor(day.state)),
        ) {}
    }
}

/**
 * A distinct label per state - this is what proves the four states actually
 * reach the screen rather than only existing in the domain model.
 */
private fun descriptionFor(state: DayCellState): String = when (state) {
    DayCellState.FULLY_RECORDED -> "Recorded"
    DayCellState.PARTLY_RECORDED -> "Recorded"
    DayCellState.NOTHING_RECORDED -> "Not recorded"
    DayCellState.NOT_YET_ELAPSED -> "Upcoming"
    DayCellState.OUTSIDE_RECORD -> "Outside the record"
}

@Composable
private fun containerColorFor(state: DayCellState) = when (state) {
    DayCellState.FULLY_RECORDED -> MaterialTheme.colorScheme.secondaryContainer
    DayCellState.PARTLY_RECORDED -> MaterialTheme.colorScheme.tertiaryContainer
    DayCellState.NOTHING_RECORDED -> MaterialTheme.colorScheme.surfaceVariant
    DayCellState.NOT_YET_ELAPSED, DayCellState.OUTSIDE_RECORD -> MaterialTheme.colorScheme.surface
}
