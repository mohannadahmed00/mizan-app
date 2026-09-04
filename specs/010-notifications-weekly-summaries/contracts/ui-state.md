# Contract: UI State

**Feature**: `specs/010-notifications-weekly-summaries`

Two surfaces: a settings section added to the existing profile screen, and one new destination. One
immutable state each, exposed as `StateFlow`, per the constitution's technology constraints.

---

## 1. `NotificationSettings` — inside `ProfileUiState`

`ProfileUiState` gains one field. The profile screen is already where Phase 9 put the location
section, and FR-004 forbids a new settings destination.

```kotlin
data class NotificationSettings(
    val prayerWindowEnabled: Boolean,
    val streakAtRiskEnabled: Boolean,
    val weeklySummaryEnabled: Boolean,
    val allSilenced: Boolean,
    val quietHours: QuietHours?,            // the domain type; null = off
    val systemPermission: PermissionState,
    val deliveryMode: DeliveryMode,         // the domain enum from NotificationScheduler.kt
    val statements: List<String>,           // always populated; see below
)

enum class PermissionState { GRANTED, DENIED, NOT_YET_ASKED }
```

**No display-only wrappers.** `QuietHours` and `DeliveryMode` are the domain types, used directly.
An earlier draft of this contract named `QuietHoursDisplay` and `DeliveryModeDisplay`; both were
removed because neither carried a single field the domain type did not already have, and a parallel
type that only ever mirrors another is a second place for the same value to drift. Formatting a
`LocalTime` for display is the composable's job, not a type's.

`PermissionState` has no domain counterpart — it describes a platform grant, which `:domain` knows
nothing about — so it is declared here.

### `statements` — what the surface must say out loud

Never empty. Each condition below contributes exactly one plain-language line, in this order. The
pattern is Phase 9's `LocationSettings.statement`, which is always populated for the same reason: a
notification system that silently does nothing is worse than one that explains itself.

| Condition | Must state | Source |
|---|---|---|
| `systemPermission != GRANTED` | that the system permission is off, that nothing will be delivered, and how to reach system settings | FR-007 |
| `deliveryMode == DeliveryMode.RELAXED` | that timing may drift, and that a notification arriving outside its window is discarded rather than shown late | FR-036b |
| Prayer nudges on, boundary on the fallback regime | that nudges need location, and that none are scheduled meanwhile | FR-016 |
| Weekly summary on but dormant | that the weekly notification is paused after two quiet weeks, that the summary itself is still on its screen, and that recording anything resumes it | FR-030c |
| `allSilenced` | that everything is silenced and the categories are remembered | FR-002 |

**`allSilenced` never clears the three category booleans.** The switches keep their positions while
silenced so turning silence off restores exactly what the person chose (data-model.md).

**Dormancy is never shown as a failure.** The line says what resumes it, not what stopped it
(Principle IX).

### Entry point

The notifications section sits on the existing profile screen, beside the location section Phase 9
added (FR-004). There is no notification settings destination, and none may be added.

### Events

```kotlin
sealed interface NotificationSettingsEvent {
    data class SetCategory(val category: NotificationCategory, val enabled: Boolean) : NotificationSettingsEvent
    data class SetAllSilenced(val silenced: Boolean) : NotificationSettingsEvent
    data class SetQuietHours(val start: LocalTime, val end: LocalTime) : NotificationSettingsEvent
    data object ClearQuietHours : NotificationSettingsEvent
    data object RequestPermission : NotificationSettingsEvent
    data object OpenSystemSettings : NotificationSettingsEvent
}
```

Every event that changes preferences must, in the same operation, persist and then call
`replaceAll`/`cancelAll` — FR-005 requires withdrawal *at the moment the switch flips*, not at the
next refresh. There is no event for editing a notification, a time, or a per-task reminder, and none
may be added (FR-006, Principle VI).

---

## 2. `WeeklySummaryUiState` — the new destination

```kotlin
data class WeeklySummaryUiState(
    val content: WeeklySummaryContent,
    val canGoEarlier: Boolean,
    val canGoLater: Boolean,
)

sealed interface WeeklySummaryContent {
    /** No week has closed yet — FR-027b. */
    data class Waiting(val firstSummaryAt: LocalDate) : WeeklySummaryContent

    data class Closed(
        val weekKey: WeekKey,
        val range: String,
        val daysEngaged: Int,
        val daysInWeek: Int,
        val tasksRecorded: Int,
        val pointsEarned: Int,
        val pointsAvailable: Int,
        val streakAtClose: Int,
        val coverage: CoverageNote?,     // non-null only for a partly-covered week — FR-029
        val quiet: Boolean,              // no recorded activity — FR-028
    ) : WeeklySummaryContent

    data class Unavailable(val reason: String) : WeeklySummaryContent
}
```

### Rules

| Rule | Source |
|---|---|
| Only closed weeks are ever rendered; the week in progress belongs to the weekly sheet | FR-027a |
| Before any week has closed, `Waiting` — and it names when the first summary arrives and offers a route to the sheet | FR-027b, SC-015 |
| A `quiet` week still renders in full, in encouraging terms, with a route back into the current day | FR-028 |
| `coverage` describes a partly-recorded week as coverage, never as a shortfall | FR-029 |
| The screen renders identically whether or not permission was granted or the category is on | FR-027 |
| No field counts anything not done; no field compares to another person; no field is a target the person fell short of | FR-025, Principle IX |
| `Unavailable` is a first-class state — a read failure must never render as a zero | Phase 5 precedent (FR-021b there) |

**There is no "current week" variant, deliberately.** Adding one later would put a second live view of
the same numbers in the same tab, which FR-027a forbids and which the clarification session settled.

### Reaching the screen

`MainActivity` gains `Destination.WeeklySummary(val week: WeekKey?)`. The existing hand-rolled
`encode`/`decode` pair is extended, not replaced:

| Destination | Encoded |
|---|---|
| `WeeklySummary(null)` — most recent closed week | `WEEKLYSUMMARY` |
| `WeeklySummary(WeekKey("2026-08-29"))` | `WEEKLYSUMMARY:2026-08-29` |
| `Today` at a named section | `TODAY:asr` |

**The week key is part of the destination because FR-030 requires it.** A summary notification must
open the week *it describes*, not merely the most recent one — a summary held across quiet hours and
tapped the following evening would otherwise open the wrong week. A null key means "whatever is most
recent", which is what the in-app entry point uses.

The in-app entry point is a row on `WeekScreen`, beside the existing link to Insights. The product
design groups the weekly sheet, insights and this screen as "Progress", but that is a design-document
grouping and not a destination in the code — `MainActivity` has no `Progress` route, and this feature
does not add one.

**`pointsEarned` against `pointsAvailable` is a ratio, not a deficit.** The screen shows what was
completed. It must not render the difference between them as its own figure.

---

## 3. First-launch and permission prompts

No new destination and no blocking dialog. The permission prompt is dismissible and non-blocking,
matching Phase 9's location prompt.

| Rule | Source |
|---|---|
| Nothing is asked during the app's first week | FR-007a |
| The first ask comes at the first week close, framed as the summary that is ready | FR-007a |
| Or earlier, if the person switches a category on themselves | FR-007a |
| The exact-delivery permission is never asked before notification permission is granted | FR-007b |
| Declining leaves the app fully usable, and the summary screen fully populated | FR-007, FR-027 |
| Asked once — never re-prompted on each launch | FR-007 |

`permissionAskedAt` in `notification_preferences` is what enforces "asked once". It records that the
app asked, not what the answer was; the live platform state is always read from the presenter, never
cached.
