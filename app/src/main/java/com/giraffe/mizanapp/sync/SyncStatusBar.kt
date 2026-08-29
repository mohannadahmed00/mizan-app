package com.giraffe.mizanapp.sync

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.giraffe.mizanapp.domain.sync.SyncStatus

/**
 * The one line of copy for each [SyncStatus], per `contracts/ui-state.md`.
 * `NotSignedIn` renders nothing — the offline product shows no sync surface
 * at all (FR-004). Every other line is a fact about the queue, never a
 * warning (Principle IX).
 */
fun syncStatusText(status: SyncStatus): String? = when (status) {
    SyncStatus.NotSignedIn -> null
    SyncStatus.UpToDate -> "Backed up"
    is SyncStatus.Pending -> "${status.count} changes waiting to be sent"
    SyncStatus.NotSyncing -> "Not syncing right now"
    is SyncStatus.LoadingEarlierDays -> "Still loading earlier days"
}

/**
 * Stateless: holds no state, makes no decision, has no failure branch, and is
 * never tappable into a retry prompt (Principle IX) — retry is automatic.
 */
@Composable
fun SyncStatusBar(status: SyncStatus, modifier: Modifier = Modifier) {
    val text = syncStatusText(status) ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag("sync-status-bar"),
    )
}
