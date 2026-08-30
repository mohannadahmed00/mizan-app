package com.giraffe.mizanapp.profile

import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.FallbackReason
import java.time.Instant

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
    val locationSettings: LocationSettings = locationSettingsFor(
        BoundaryState(
            regime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION),
            coordinates = null,
            zoneIdWhenObtained = null,
            resolvedDate = java.time.LocalDate.MIN,
            expiresAt = Instant.MAX,
            lastResolvedDate = null,
            lastResolvedRegime = null,
        ),
    ),
    val confirmingEraseLocation: Boolean = false,
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

    data object EnableLocation : ProfileEvent
    data object EraseLocation : ProfileEvent
    data object ConfirmEraseLocation : ProfileEvent
    data object CancelEraseLocation : ProfileEvent
}

enum class BoundaryRegimeLabel { MAGHRIB, NEVER_HAD_LOCATION, ERASED, ZONE_CHANGED_AWAITING_FIX }

/** Per contracts/ui-state.md §2. `statement` is always populated -- FR-016, FR-012d, FR-017b. */
data class LocationSettings(
    val regime: BoundaryRegimeLabel,
    val statement: String,
    val locationHeld: Boolean,
    val obtainedAt: Instant?,
    val canEnable: Boolean,
)

/**
 * Which line shows follows the regime alone -- never age, retries, or timing (FR-017,
 * research.md). `ZONE_CHANGED_AWAITING_FIX` must never be silent (FR-012d): a day boundary that
 * moves without explanation is worse than one that is merely approximate.
 */
fun locationSettingsFor(state: BoundaryState): LocationSettings {
    val label = when (val regime = state.regime) {
        BoundaryRegime.Maghrib -> BoundaryRegimeLabel.MAGHRIB
        is BoundaryRegime.Fallback -> when (regime.reason) {
            FallbackReason.NEVER_HAD_LOCATION -> BoundaryRegimeLabel.NEVER_HAD_LOCATION
            FallbackReason.ERASED -> BoundaryRegimeLabel.ERASED
            FallbackReason.ZONE_CHANGED_AWAITING_FIX -> BoundaryRegimeLabel.ZONE_CHANGED_AWAITING_FIX
        }
    }
    val statement = when (label) {
        BoundaryRegimeLabel.MAGHRIB ->
            "The Islamic day boundary follows today's calculated Maghrib, using your last known location."
        BoundaryRegimeLabel.NEVER_HAD_LOCATION ->
            "The Islamic day boundary isn't set up yet. The day currently runs local midnight to " +
                "midnight. Enabling location resolves this."
        BoundaryRegimeLabel.ERASED ->
            "Location was erased, so the day currently runs local midnight to midnight. " +
                "Enabling location again resolves this."
        BoundaryRegimeLabel.ZONE_CHANGED_AWAITING_FIX ->
            "Your device moved to a new time zone. The previous location is no longer used, and " +
                "the boundary runs local midnight to midnight until a new location is obtained."
    }
    return LocationSettings(
        regime = label,
        statement = statement,
        locationHeld = state.coordinates != null,
        obtainedAt = state.obtainedAt,
        canEnable = label != BoundaryRegimeLabel.MAGHRIB,
    )
}
