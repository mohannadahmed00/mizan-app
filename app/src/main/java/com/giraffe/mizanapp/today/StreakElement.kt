package com.giraffe.mizanapp.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.streak.ActivityState

/**
 * The streak element: current run, longest run, and whether today is
 * already counted. Fixed position on `TodayScreen`, shown even when the
 * catalogue is unavailable (FR-018a, FR-018b).
 *
 * **Forbidden here, always**: red, a cross, "missed", "failed", "lost",
 * "broken", a negative number, or any exclamation of alarm (Principle IX).
 */
@Composable
fun StreakElement(
    panel: StreakPanelUi,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("streak-element"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when (panel) {
                StreakPanelUi.Resolving -> ResolvingContent()
                is StreakPanelUi.Ready -> ReadyContent(panel)
                is StreakPanelUi.Unavailable -> UnavailableContent(panel, onRetry)
            }
        }
    }
}

@Composable
private fun ResolvingContent() {
    Text(
        text = "Your streak",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun ReadyContent(panel: StreakPanelUi.Ready) {
    if (panel.current == 0 && panel.longest == 0) {
        Text(
            text = "Begin your streak today",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Complete one task to start",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${panel.current}", style = MaterialTheme.typography.headlineMedium)
                if (!panel.todayCounted) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("streak-today-pending"),
                    )
                }
            }
            Text(
                text = "current streak",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column {
            Text(text = "${panel.longest}", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "best streak",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }

    if (panel.showBreakNotice) {
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Your ${panel.longest}-day record still stands. One task today puts you back on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

    if (panel.isAtRisk) {
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Today is still open. One task keeps your ${panel.current}-day run going.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

    if (panel.recentActivity.isNotEmpty()) {
        Spacer(Modifier.width(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            panel.recentActivity.forEach { day -> ActivityDot(day.state) }
        }
    }
}

@Composable
private fun ActivityDot(state: ActivityState) {
    val color = when (state) {
        ActivityState.COUNTED -> MaterialTheme.colorScheme.primary
        ActivityState.NOT_RECORDED -> MaterialTheme.colorScheme.surfaceVariant
        ActivityState.TODAY_PENDING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ActivityState.OUTSIDE_RECORD -> Color.Transparent
    }
    Column(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .testTag("streak-activity-day"),
    ) {}
}

@Composable
private fun UnavailableContent(panel: StreakPanelUi.Unavailable, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Couldn't read your streak just now",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
