package com.giraffe.mizanapp.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.notifications.PermissionState
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
        if (state.confirmingEraseLocation) {
            EraseLocationConfirmationContent(onEvent)
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

        LocationSettingsSection(state.locationSettings, onEvent)

        HorizontalDivider()
        NotificationSettingsSection(state.notifications, onEvent)
        HorizontalDivider()

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

/**
 * The statement is always populated, whatever the regime (FR-016, FR-012d, FR-017b) -- there is
 * no state here that reads as an error, only a description of which rule is currently in force.
 */
@Composable
private fun LocationSettingsSection(settings: LocationSettings, onEvent: (ProfileEvent) -> Unit) {
    Text(settings.statement, modifier = Modifier.testTag("profile-location-statement"))
    if (settings.canEnable) {
        TextButton(
            onClick = { onEvent(ProfileEvent.EnableLocation) },
            modifier = Modifier.testTag("profile-enable-location"),
        ) { Text("Enable location") }
    }
    if (settings.locationHeld) {
        TextButton(
            onClick = { onEvent(ProfileEvent.EraseLocation) },
            modifier = Modifier.testTag("profile-erase-location"),
        ) { Text("Erase location") }
    }
}

/**
 * Per-category switches, the master silence, quiet hours and every disclosure line from
 * [NotificationSettings.statements] (contracts/ui-state.md §1). No red or warning iconography
 * anywhere here (Principle IX) — a category being off, or the summary dormant, is an ordinary
 * state described in plain language, never flagged.
 */
@Composable
private fun NotificationSettingsSection(settings: com.giraffe.mizanapp.notifications.NotificationSettings, onEvent: (ProfileEvent) -> Unit) {
    var editingQuietHours by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Text("Notifications", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

    settings.statements.forEach { line -> Text(line, modifier = Modifier.testTag("notifications-statement")) }

    NotificationCategoryRow(
        label = "Prayer window nudges",
        checked = settings.prayerWindowEnabled,
        tag = "notifications-prayer-window",
        onCheckedChange = { onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetCategory(NotificationCategory.PRAYER_WINDOW, it))) },
    )
    NotificationCategoryRow(
        label = "Streak reminder",
        checked = settings.streakAtRiskEnabled,
        tag = "notifications-streak-at-risk",
        onCheckedChange = { onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetCategory(NotificationCategory.STREAK_AT_RISK, it))) },
    )
    NotificationCategoryRow(
        label = "Weekly summary",
        checked = settings.weeklySummaryEnabled,
        tag = "notifications-weekly-summary",
        onCheckedChange = { onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetCategory(NotificationCategory.WEEKLY_SUMMARY, it))) },
    )
    NotificationCategoryRow(
        label = "Silence everything",
        checked = settings.allSilenced,
        tag = "notifications-all-silenced",
        onCheckedChange = { onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetAllSilenced(it))) },
    )

    Row(
        modifier = Modifier.fillMaxWidth().testTag("notifications-quiet-hours-row"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val quietHours = settings.quietHours
        Text(
            if (quietHours != null) "Quiet hours: ${quietHours.start} to ${quietHours.end}" else "Quiet hours: off",
        )
        Row {
            TextButton(onClick = { editingQuietHours = true }, modifier = Modifier.testTag("notifications-edit-quiet-hours")) { Text("Set") }
            if (quietHours != null) {
                TextButton(
                    onClick = { onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.ClearQuietHours)) },
                    modifier = Modifier.testTag("notifications-clear-quiet-hours"),
                ) { Text("Clear") }
            }
        }
    }

    if (editingQuietHours) {
        QuietHoursEditor(
            initialStart = settings.quietHours?.start ?: java.time.LocalTime.of(22, 0),
            initialEnd = settings.quietHours?.end ?: java.time.LocalTime.of(6, 0),
            onSet = { start, end ->
                editingQuietHours = false
                onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetQuietHours(start, end)))
            },
            onDismiss = { editingQuietHours = false },
        )
    }

    if (settings.systemPermission != PermissionState.GRANTED) {
        TextButton(
            onClick = { onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.OpenSystemSettings)) },
            modifier = Modifier.testTag("notifications-open-system-settings"),
        ) { Text("Open system settings") }
    }
}

@Composable
private fun NotificationCategoryRow(label: String, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}

/** A minimal HH:mm editor for the quiet-hours window — interpreted in device-local time. */
@Composable
private fun QuietHoursEditor(
    initialStart: java.time.LocalTime,
    initialEnd: java.time.LocalTime,
    onSet: (java.time.LocalTime, java.time.LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var startText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialStart.toString()) }
    var endText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialEnd.toString()) }

    Column(modifier = Modifier.testTag("notifications-quiet-hours-editor")) {
        OutlinedTextField(
            value = startText,
            onValueChange = { startText = it },
            label = { Text("Start (HH:mm)") },
            modifier = Modifier.testTag("notifications-quiet-hours-start"),
        )
        OutlinedTextField(
            value = endText,
            onValueChange = { endText = it },
            label = { Text("End (HH:mm)") },
            modifier = Modifier.testTag("notifications-quiet-hours-end"),
        )
        Row {
            Button(
                onClick = {
                    val start = runCatching { java.time.LocalTime.parse(startText) }.getOrNull()
                    val end = runCatching { java.time.LocalTime.parse(endText) }.getOrNull()
                    if (start != null && end != null) onSet(start, end)
                },
                modifier = Modifier.testTag("notifications-quiet-hours-confirm"),
            ) { Text("Save") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

/**
 * States what erasing does -- the boundary returns to local midnight -- and never suggests any
 * recorded history changes, because none does (FR-017d).
 */
@Composable
private fun EraseLocationConfirmationContent(onEvent: (ProfileEvent) -> Unit) {
    Text(
        "Erase the location held on this device? The Islamic day boundary returns to local " +
            "midnight until location is enabled again. Nothing you have already recorded changes.",
    )
    Button(
        onClick = { onEvent(ProfileEvent.ConfirmEraseLocation) },
        modifier = Modifier.testTag("profile-confirm-erase-location"),
    ) { Text("Erase") }
    TextButton(
        onClick = { onEvent(ProfileEvent.CancelEraseLocation) },
        modifier = Modifier.testTag("profile-cancel-erase-location"),
    ) { Text("Cancel") }
}
