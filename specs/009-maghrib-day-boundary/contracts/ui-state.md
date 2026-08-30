# Contract: UI State

**Feature**: `specs/009-maghrib-day-boundary`

Two surfaces: a first-launch prompt on Today, and a location section on the existing profile screen.
Both follow the project's standing rule — one immutable state per screen as `StateFlow`, no mutable
state exposed from a ViewModel, every transition driven by an event.

No new top-level destination is added. Navigation stays three tabs.

---

## 1. First-launch location prompt (Today)

Added to the existing `TodayUiState` rather than as a screen of its own — FR-007a requires the app to
be usable immediately, so this cannot be anything that renders in place of Today.

```kotlin
data class LocationPrompt(
    val visible: Boolean,
    val explanation: String,
)

sealed interface TodayEvent {
    data object EnableLocation : TodayEvent
    data object DismissLocationPrompt : TodayEvent
}
```

| Requirement | How the state satisfies it |
|---|---|
| App renders and records with no location, no dialog | FR-007a — the prompt is a field on an already-populated state, never a gate |
| Explanation shown before asking | FR-007b — `explanation` is rendered; the dialog is not raised until `EnableLocation` |
| System dialog only on explicit choice | FR-007c — only `EnableLocation` calls `requestLocation()` |
| Declining leaves the app fully usable | FR-007d — `DismissLocationPrompt` sets `visible = false` and nothing else |
| Reachable again later | FR-007d — the profile section below |

**Copy rules (FR-007e, Principle IX)**: states what location enables — accurate local prayer times and
the Maghrib-based Islamic day boundary. No warning framing, no consequence framing, no "your record
will be less accurate", no repeat nagging after dismissal. Declining is a supported way to use the
app, and the copy must read that way. SC-018 reviews every string here.

---

## 2. Location section (Profile)

Extends `ProfileUiState`, which already carries the sync status and conflict policy.

```kotlin
data class LocationSettings(
    val regime: BoundaryRegimeLabel,
    val statement: String,
    val locationHeld: Boolean,
    val obtainedAt: Instant?,
    val canEnable: Boolean,
)

sealed interface ProfileEvent {
    data object EnableLocation : ProfileEvent
    data object EraseLocation : ProfileEvent
    data object ConfirmEraseLocation : ProfileEvent
    data object CancelEraseLocation : ProfileEvent
}
```

### `statement` — always populated

The plain-language line FR-016, FR-012d and FR-017b require. Which one shows follows the regime:

| Regime | What the statement must convey |
|---|---|
| `Maghrib` | The day boundary follows the calculated Maghrib; a last known location is held and is being used for it (FR-017b) |
| `Fallback(NEVER_HAD_LOCATION)` | The Islamic day boundary is unavailable; the day currently runs local midnight to midnight; enabling location resolves it (FR-016) |
| `Fallback(ERASED)` | Same, naming that the location was erased and can be enabled again |
| `Fallback(ZONE_CHANGED_AWAITING_FIX)` | The device moved to a new time zone, the previous location is no longer used, and the boundary is on the fallback until a new location is obtained (FR-012d) |

The last row is the one that must never be silent. FR-012d exists because a day boundary that moves
without explanation is worse than one that is merely approximate.

### Erasing

`EraseLocation` opens a confirmation; `ConfirmEraseLocation` performs it. Confirmation is required
because the transition changes the day boundary — the same standard the existing sign-out flow
applies before a consequential change.

The confirmation must state what erasing does — the boundary returns to local midnight — and must
**not** state or imply that any recorded history changes, because none does (FR-017d).

---

## Forbidden

- No screen offers a choice of calculation method, calculation authority, or Asr madhab (FR-003a).
- No screen lets a person type or pick a location. Coordinates come from the device or not at all.
- No red, no warning iconography, and no failure framing on either surface — the fallback is a
  supported state, not an error (Principle IX).
- Neither surface blocks or gates any other part of the app on a location, a permission decision, or
  a boundary resolution (FR-007a, Principle IV).
