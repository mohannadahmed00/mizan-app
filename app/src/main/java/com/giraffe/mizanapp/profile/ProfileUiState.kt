package com.giraffe.mizanapp.profile

import com.giraffe.mizanapp.domain.sync.SyncStatus

/**
 * One immutable state for the profile screen, per `contracts/ui-state.md`.
 *
 * [conflictPolicy] is always populated — the plain-language statement FR-019a
 * requires, rendered beside the sync status on this screen and nowhere else.
 * [confirming] is non-null only between a sign-out attempt and the user's
 * decision; cancelling clears it and leaves the account untouched.
 */
data class ProfileUiState(
    val email: String = "",
    val displayName: String? = null,
    val editingDisplayName: Boolean = false,
    val draftDisplayName: String = "",
    val syncStatus: SyncStatus = SyncStatus.NotSignedIn,
    val pendingCount: Int = 0,
    val confirming: SignOutConfirmation? = null,
    val conflictPolicy: String = CONFLICT_POLICY_STATEMENT,
)

const val CONFLICT_POLICY_STATEMENT =
    "If you record on two devices at once, both records are kept. " +
        "If you undo something on one device, it stays undone on the other."

sealed interface SignOutConfirmation {
    data class Plain(val pendingCount: Int) : SignOutConfirmation
    data class Removing(val pendingCount: Int, val recordedDays: Int, val completions: Int) : SignOutConfirmation
}

sealed interface ProfileEvent {
    data class DisplayNameChanged(val value: String) : ProfileEvent
    data object SaveDisplayName : ProfileEvent
    data object ClearDisplayName : ProfileEvent
    data object SignOut : ProfileEvent
    data object SignOutAndRemoveData : ProfileEvent
    data object ConfirmSignOut : ProfileEvent
    data object CancelSignOut : ProfileEvent
}
