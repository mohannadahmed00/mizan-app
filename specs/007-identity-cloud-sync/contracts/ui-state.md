# Contract: UI state and navigation

Two new screens, one status surface reused on two existing screens, two new destinations. Every
ViewModel exposes one immutable state as `StateFlow` and no mutable state — unchanged from `002`–`006`.

---

## `SignInUiState`

```kotlin
data class SignInUiState(
    val step: SignInStep = SignInStep.EnteringEmail(""),
    val configured: Boolean = true,        // false when no Supabase config is present
)

sealed interface SignInEvent {
    data class EmailChanged(val value: String) : SignInEvent
    data object SubmitEmail : SignInEvent
    data class CodeChanged(val value: String) : SignInEvent
    data object SubmitCode : SignInEvent
    data object ResendCode : SignInEvent
    data object UseDifferentAddress : SignInEvent
    data object ConfirmReplaceLocalRecords : SignInEvent
    data object Dismiss : SignInEvent
}
```

Rules the screen tests pin:

- Every transition preserves the entered email (FR-002a).
- `ResendCode` is inert before `resendAvailableAt` and the button states when it becomes available.
- `SignInStep.NeedsConnection` renders as a statement, in the ordinary text colour, with the app
  still reachable behind it — not as an error dialog and not in red.
- `ConfirmReplaceLocalRecords` is reachable only from `CodeConfirmation.WouldReplaceLocalRecords`,
  and re-issues `confirmCode` with `replaceLocalRecords = true`. The confirmation preceding it names
  the account being replaced, the recorded-day count, the completion count, and the number of changes
  not yet backed up (FR-013a). Declining leaves the device untouched and the email intact.
- `configured = false` shows "signing in isn't available in this build" and nothing else. It never
  crashes and never blocks the rest of the app.

---

## `ProfileUiState`

```kotlin
data class ProfileUiState(
    val email: String,
    val displayName: String?,              // null = not set; the email is shown instead (FR-007e)
    val editingDisplayName: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.NotSignedIn,
    val pendingCount: Int = 0,
    val confirming: SignOutConfirmation? = null,
)

// Rendered beside the status, always visible on this screen — the user-reachable
// statement of the conflict policy FR-019a requires:
//   "If you record on two devices at once, both records are kept.
//    If you undo something on one device, it stays undone on the other."

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
```

Rules:

- `Removing` names what is about to be removed — the day count and the completion count — before the
  second confirmation (FR-007b).
- Both confirmations state pending changes plainly when `pendingCount > 0` (FR-007c), and neither
  says anything about the account's contents being at risk, because they are not (FR-007d).
- The display name is optional everywhere: empty is a valid saved state, and no prompt asks for one.

---

## `SyncStatusBar`

One composable, one line, hosted by `TodayScreen` and `WeekScreen`. It renders `SyncStatus` and
nothing else — it holds no state, makes no decision, and has no failure branch.

| Status | Copy | Treatment |
|---|---|---|
| `NotSignedIn` | *(nothing rendered)* | The signed-out product shows no sync surface and no account prompt (FR-004) |
| `UpToDate` | "Backed up" | Muted text, no icon emphasis |
| `Pending(n)` | "n changes waiting to be sent" | Muted text — a count, not a warning |
| `NotSyncing` | "Not syncing right now" | Muted text |
| `LoadingEarlierDays` | "Still loading earlier days" | Muted text with the existing progress treatment |

Forbidden here, and audited by `SyncStatusBarTest` and `SyncStatusCopyTest` (SC-011, Principle IX):
the words *failed*, *error*, *lost*, *missing*, *problem*, *you didn't*; any red or warning colour;
any badge; any modal; any control that blocks recording. The bar is never tappable into a retry
prompt — retry is automatic, and asking the user to fix it would make an infrastructure fact their
responsibility.

---

## Changes to existing screens

| Screen | Change |
|---|---|
| `TodayScreen` | Hosts `SyncStatusBar`; adds a profile entry point (which opens sign-in when signed out). Recording is untouched — no new blocking state, no new spinner. |
| `WeekScreen` | Hosts `SyncStatusBar`. Day cells handle the new `NOT_YET_KNOWN` state. |
| `HistoryScreen`, `InsightsScreen` | Render `NOT_YET_KNOWN` dates as still loading, never as 0% or absent (FR-023b); show insight and streak figures as provisional while coverage over their range is incomplete (FR-023d). |
| `ui/DayCellColors.kt` | Adds a neutral container for `NOT_YET_KNOWN`, visually distinct from `NOTHING_RECORDED` and from `OUTSIDE_RECORD`. No red enters the file. |

---

## Navigation

```kotlin
sealed interface Destination {
    data object Today : Destination
    data object Week : Destination
    data object History : Destination
    data object Insights : Destination
    data class DaySummary(val date: LocalDate) : Destination
    data object SignIn : Destination     // NEW
    data object Profile : Destination    // NEW
}
```

Encoded as `"SIGNIN"` and `"PROFILE"` in the existing `StackSaver`. Both are pushed from the Today
entry point: signed out it opens `SignIn`, signed in it opens `Profile`.

**Neither is ever a start destination, an interstitial, or a gate.** `NoAccountGateTest` asserts that
the whole Phase 2–6 product is reachable and complete with no account and no network, and that no
screen prompts for one more than the single, dismissible entry point above (FR-004, SC-007).
