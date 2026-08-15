package com.giraffe.mizanapp.daysummary

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.today.MizanTypography

/**
 * The Day Summary screen. Read-only by construction: it takes only [state]
 * — there is no `onEvent` parameter, because there is no event this screen
 * could ever need to send (FR-024, Principle VI).
 */
@Composable
fun DaySummaryScreen(state: DaySummaryUiState, modifier: Modifier = Modifier) {
    when (val status = state.status) {
        is DaySummaryUiState.Status.Loading -> LoadingState(modifier)
        is DaySummaryUiState.Status.NoRecord -> NoRecordState(modifier)
        is DaySummaryUiState.Status.CatalogueUnavailable -> CatalogueUnavailableState(status, modifier)
        is DaySummaryUiState.Status.Ready -> ReadyState(state, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A plain statement that nothing was recorded — never an error (Principle IX). */
@Composable
private fun NoRecordState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Nothing recorded for this date.", style = MaterialTheme.typography.titleMedium)
    }
}

/** What applied on this date cannot be determined right now — the app's failure, not the user's (FR-032). */
@Composable
private fun CatalogueUnavailableState(status: DaySummaryUiState.Status.CatalogueUnavailable, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This day couldn't load right now.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(status.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyState(state: DaySummaryUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = state.civilDate?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
        state.hijriLabel?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = "${state.earnedPoints} of ${state.availablePoints} points",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "This day is a record. Recording happens on the current day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("locked-day-notice"),
        )
        Spacer(Modifier.width(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.sections.forEach { section ->
                item(key = "header-${section.id}") {
                    ArabicText(
                        text = section.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                items(section.tasks, key = { "${section.id}-${it.slug}" }) { task ->
                    SummaryTaskRow(task)
                }
            }
        }
    }
}

@Composable
private fun SummaryTaskRow(task: SummaryTaskUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isComplete) {
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
        }
    }
}

/** Renders Arabic content in its own face and direction (FR-021). No write affordance anywhere. */
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
