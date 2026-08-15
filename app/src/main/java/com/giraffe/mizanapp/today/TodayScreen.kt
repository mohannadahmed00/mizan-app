package com.giraffe.mizanapp.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Today screen.
 *
 * Two rules govern everything here:
 *
 * **Principle IX** — nothing may present incompleteness as failure. No red, no
 * ✗, no "missed", no negative number. An untouched day reads as a day not yet
 * begun.
 *
 * **Principle VI** — there is no add, edit, delete, reorder or reprice
 * affordance anywhere, and no event exists that could express one.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onEvent: (TodayEvent) -> Unit,
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val status = state.status) {
        is TodayUiState.Status.Loading -> LoadingState(modifier)
        is TodayUiState.Status.CatalogueUnavailable ->
            CatalogueUnavailableState(status.detail, state.streak, onEvent, modifier)
        is TodayUiState.Status.Ready -> ReadyState(state, onEvent, onOpenWeek, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Distinct from an empty day (FR-003): a day the app could not load and a day
 * with nothing recorded must never look alike.
 */
@Composable
private fun CatalogueUnavailableState(
    detail: String,
    streak: StreakPanelUi,
    onEvent: (TodayEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(24.dp)) {
        // The streak survives a missing catalogue (FR-018b): it reads
        // completions, not the catalogue, so it is still true here.
        StreakElement(panel = streak, onRetry = { onEvent(TodayEvent.RetryStreak) })
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("The task list could not be loaded.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyState(
    state: TodayUiState,
    onEvent: (TodayEvent) -> Unit,
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        // Fixed position, outside the stepped flow (FR-018a): stepping
        // between blocks below must never move or change this.
        StreakElement(panel = state.streak, onRetry = { onEvent(TodayEvent.RetryStreak) })
        Spacer(Modifier.width(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayHeader(state)
            TextButton(onClick = onOpenWeek) { Text("Week") }
        }
        Spacer(Modifier.width(16.dp))
        PointsHeader(state)
        Spacer(Modifier.width(16.dp))

        val section = state.currentSection
        if (section != null) {
            Text(
                text = "${state.currentSectionIndex + 1} of ${state.sections.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ArabicText(
                text = section.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(section.tasks, key = { it.slug }) { task ->
                    TaskRow(task, onEvent)
                }
            }

            SectionNavigation(state, onEvent)
        }
    }
}

@Composable
private fun DayHeader(state: TodayUiState) {
    Column {
        Text(
            text = state.civilDate?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
        state.hijriLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Progress is expressed as what was completed. Never as what was missed. */
@Composable
private fun PointsHeader(state: TodayUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "${state.earnedPoints} of ${state.availablePoints} points",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TaskRow(task: TaskRowUi, onEvent: (TodayEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !task.isAtLimit) { onEvent(TodayEvent.CompleteTask(task.slug)) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            // Completion is signalled by emphasis, never by an error colour.
            containerColor = if (task.isAtLimit) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArabicText(
                text = task.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )

            // Without this the Arabic label sits flush against the figures and
            // the two read as one string.
            Spacer(Modifier.width(16.dp))

            if (task.isMultiOccurrence) {
                Text(
                    text = "${task.recordedCount}/${task.maxOccurrences}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = "${task.points} pts",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (task.canUndo) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onEvent(TodayEvent.UndoTask(task.slug)) }) {
                    Text("Undo")
                }
            }
        }
    }
}

@Composable
private fun SectionNavigation(state: TodayUiState, onEvent: (TodayEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Button(
            onClick = { onEvent(TodayEvent.PreviousSection) },
            enabled = state.hasPreviousSection,
        ) { Text("Back") }

        Button(
            onClick = { onEvent(TodayEvent.NextSection) },
            enabled = state.hasNextSection,
        ) { Text("Next") }
    }
}

/**
 * Renders Arabic **content** (FR-025, constitution v1.1.1).
 *
 * The layout direction is flipped for this text alone, so a mixed Arabic/Latin
 * row never reflows the surrounding layout. The interface shell stays English
 * and left-to-right.
 *
 * The typeface comes from [MizanTypography.arabic] — one swap point for the
 * whole app.
 */
@Composable
private fun ArabicText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            style = style.copy(
                fontFamily = MizanTypography.arabic,
                lineHeight = style.fontSize * ARABIC_LINE_HEIGHT,
            ),
            textAlign = TextAlign.Right,
            modifier = modifier,
        )
    }
}

private const val ARABIC_LINE_HEIGHT = 1.75f
