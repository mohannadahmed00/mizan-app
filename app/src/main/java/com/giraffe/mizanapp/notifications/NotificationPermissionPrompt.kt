package com.giraffe.mizanapp.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.Instant
import java.time.LocalDate

/**
 * Asked once, at the first week close, framed as the summary that is ready -- never during the
 * app's first week, and never a second time regardless of the answer (FR-007a).
 *
 * [recordStart] is the earliest planned date on this device; `null` means no week has opened
 * yet, which also means the prompt cannot show.
 */
fun shouldShowNotificationPermissionPrompt(recordStart: LocalDate?, today: LocalDate, permissionAskedAt: Instant?): Boolean {
    if (permissionAskedAt != null) return false
    val start = recordStart ?: return false
    val firstWeek = WeekBoundary.weekContaining(start)
    val currentWeek = WeekBoundary.weekContaining(today)
    return currentWeek.start.isAfter(firstWeek.start)
}

/**
 * Dismissible and non-blocking, matching Phase 9's location prompt (`LocationPromptCard`): no
 * red, no warning icon, no repeat nagging after dismissal -- declining is a supported way to use
 * the app.
 */
@Composable
fun NotificationPermissionPrompt(visible: Boolean, onEnable: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (!visible) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Your first weekly summary is ready. Turn on notifications to be told when it arrives.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                Button(onClick = onEnable) { Text("Enable notifications") }
            }
        }
    }
}
