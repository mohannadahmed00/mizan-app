package com.giraffe.mizanapp.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.sync.SyncStatusBar

/**
 * The profile screen, rendering [ProfileUiState] and nothing else — every
 * transition is driven by [onEvent] per `contracts/ui-state.md`.
 *
 * The conflict-policy line (FR-019a) is always visible here, and the removing
 * confirmation always names the day count and the completion count before
 * [ProfileEvent.ConfirmSignOut] is reachable (FR-007b).
 */
@Composable
fun ProfileScreen(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val confirming = state.confirming
        if (confirming != null) {
            ConfirmationContent(confirming, onEvent)
            return@Column
        }

        Text(state.displayName ?: state.email)
        OutlinedTextField(
            value = state.draftDisplayName,
            onValueChange = { onEvent(ProfileEvent.DisplayNameChanged(it)) },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth().testTag("profile-display-name-field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEvent(ProfileEvent.SaveDisplayName) },
                modifier = Modifier.testTag("profile-save-name"),
            ) { Text("Save") }
            TextButton(
                onClick = { onEvent(ProfileEvent.ClearDisplayName) },
                modifier = Modifier.testTag("profile-clear-name"),
            ) { Text("Clear") }
        }

        SyncStatusBar(state.syncStatus)

        Text(state.conflictPolicy, modifier = Modifier.testTag("profile-conflict-policy"))

        Button(
            onClick = { onEvent(ProfileEvent.SignOut) },
            modifier = Modifier.testTag("profile-sign-out"),
        ) { Text("Sign out") }
        TextButton(
            onClick = { onEvent(ProfileEvent.SignOutAndRemoveData) },
            modifier = Modifier.testTag("profile-sign-out-remove"),
        ) { Text("Sign out and remove data from this device") }
    }
}

@Composable
private fun ConfirmationContent(confirming: SignOutConfirmation, onEvent: (ProfileEvent) -> Unit) {
    when (confirming) {
        is SignOutConfirmation.Plain -> {
            Text("Sign out of this device?")
            if (confirming.pendingCount > 0) {
                Text("${confirming.pendingCount} changes are still waiting to be sent.")
            }
        }
        is SignOutConfirmation.Removing -> {
            Text(
                "This removes ${confirming.recordedDays} recorded days and " +
                    "${confirming.completions} completions from this device. " +
                    "Your account keeps everything.",
            )
            if (confirming.pendingCount > 0) {
                Text("${confirming.pendingCount} changes are still waiting to be sent.")
            }
        }
    }
    Button(
        onClick = { onEvent(ProfileEvent.ConfirmSignOut) },
        modifier = Modifier.testTag("profile-confirm-sign-out"),
    ) { Text("Continue") }
    TextButton(
        onClick = { onEvent(ProfileEvent.CancelSignOut) },
        modifier = Modifier.testTag("profile-cancel-sign-out"),
    ) { Text("Cancel") }
}
