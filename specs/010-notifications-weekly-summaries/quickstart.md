# Quickstart: Notifications and Weekly Summaries

**Feature**: `specs/010-notifications-weekly-summaries` | **Date**: 2026-09-04

How to run and validate this increment. Automated coverage first, then the checks that genuinely need
a device and a wall clock — recorded here rather than pretended, following the precedent spec 007 set
with its deferred manual validations.

## Prerequisites

- The repo's existing toolchain: JDK 17, Android SDK 36, no new tooling.
- For instrumentation: a device or emulator on API 34+ (the exact-alarm behaviour under test only
  exists there) **and** one on API 24 to confirm the minSdk path still schedules.
- No network, no Supabase project, and no account are needed for any check in this document.

## Automated

```bash
# Domain rules — the bulk of this feature. Fast, no device.
./gradlew :domain:test

# UI state mapping and the summary screen's states.
./gradlew :app:test

# Migration 5 -> 6, the ledger, and scheduler mode selection. Needs a device.
./gradlew :data:connectedAndroidTest

# Deep-link routing and the Principle IX string fixture. Needs a device.
./gradlew :app:connectedAndroidTest
```

All four must be green before the pull request is opened. `:data:connectedAndroidTest` includes the
Principle III migration test — a seeded `day_plans` and `completions` row surviving `MIGRATION_5_6`
byte for byte — which the merge gate requires of any increment touching persistence.

## Validation scenarios

Each maps to a success criterion in [spec.md](./spec.md). The rules under test are pure functions
(see [contracts/notification-plan.md](./contracts/notification-plan.md)), so most of these run against
literals and a fake clock rather than a real device.

### Automated, in `:domain`

| # | Scenario | Asserts |
|---|---|---|
| 1 | Fully-recorded day, every category on, advance across all five prayer instants | Zero anchors survive; SC-002 |
| 2 | Master silence on, advance a simulated week | `buildNotificationPlan` returns empty on every call; SC-003 |
| 3 | Fallback regime, nudges on, advance a simulated month | Zero `PRAYER_WINDOW` anchors, no prayer instant invented; SC-008 |
| 4 | Maghrib regime, simulated year including both solstices | Every nudge lands after its own prayer instant and before the next; SC-007 |
| 5 | Quiet hours crossing midnight, one anchor of each category inside it | Nudge and streak discard with `QUIET_HOURS`; summary returns `Hold`; SC-005 |
| 6 | Quiet window covering all 24 hours, summary held | Delivered exactly once at the window's end; FR-035 |
| 7 | Streak zero / day already counted / live streak untouched | Zero, zero, one anchor respectively; SC-011 |
| 8 | Three months of empty closed weeks | Exactly two announced, then dormant; a completion reactivates at the next close; SC-014 |
| 9 | Closed week's figures vs. the weekly sheet's, before and after a catalogue points/schedule change | Identical both times; SC-006, SC-012 |
| 10 | Summary screen state with no closed week, then with one | `Waiting` then `Closed`; SC-015 |

Scenario 9 is the one to write first and the one most likely to fail: it is what proves
`GetClosedWeekSummary` and `GetWeekSummary` agree without the summary ever backfilling (research R4).

### Automated, in `:data`

| # | Scenario | Asserts |
|---|---|---|
| 11 | Seed history at schema 5, run `MIGRATION_5_6` | Every column of `day_plans` and `completions` unchanged; Principle III |
| 12 | Deliver an anchor, simulate reboot, re-derive schedule, fire again | Second fire discards `ALREADY_DELIVERED`; SC-013 |
| 13 | Deliver an anchor, move the clock backwards, fire again | Same; SC-013 |
| 14 | Timezone change with anchors pending | Whole schedule re-derived, nothing delivered twice; FR-038, SC-013 |
| 15 | `canScheduleExactAlarms()` false | `deliveryMode()` reports `RELAXED`; anchors still scheduled; FR-036b |
| 16 | Relaxed mode, anchor delivered a simulated hour late | Discarded `WINDOW_PASSED`, never posted in the next window; SC-007a |

### Automated, in `:app`

| # | Scenario | Asserts |
|---|---|---|
| 17 | Every string this feature adds, against the CLAUDE.md Principle IX list | No loss, failure, deficit, warning or comparative framing; no count of anything not done; SC-009 |
| 18 | Notification intent extra with each category's destination | Stack seeded once, not re-seeded on recomposition; FR-013, FR-030 |
| 19 | Permission denied, every category on | Settings surface states it, screen fully populated, nothing crashes; FR-007, FR-027 |

Scenario 17 is gating. It is a review with a fixture behind it, not a fixture alone — the fixture
catches the vocabulary, a person catches the tone.

## By hand, on a device, before merge

Four checks no test can honestly make. Record the result of each in the pull request.

1. **A notification actually appears, at the right minute.** Set a location, enable prayer nudges,
   grant both permissions, and wait out one real prayer window. Confirm the nudge appears roughly
   twenty minutes after the calculated time and names the right block. *(FR-008, FR-009)*
2. **Exact-alarm refusal degrades as specified.** Refuse `SCHEDULE_EXACT_ALARM` in system settings.
   Confirm the settings surface says timing may drift, that notifications still arrive, and that none
   arrives in the wrong window. *(FR-036b, SC-007a)*
3. **Reboot.** With anchors pending, restart the device and do not open the app. Confirm the next
   anchor still fires. *(FR-037, SC-004)*
4. **Fresh install, airplane mode, no account, no location.** Confirm the app is fully usable, no
   permission is requested at any point in the first week, and no nudge is ever scheduled.
   *(SC-001, Principle IV)*

Check 4 is the Principle IV gate for this increment and should be run last, on a genuinely clean
install, exactly as spec 007 ran its SC-007 equivalent.

## What this increment must not have done

Quick negative checks, cheap to run and easy to get wrong:

```bash
# :domain must have gained no Android, platform or framework dependency.
git diff origin/develop-v1 -- domain/build.gradle.kts

# No notification code may have reached the completion write path.
grep -rn "Notification" domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/

# No second time or location read.
grep -rn "System.currentTimeMillis\|Instant.now()\|LocalDate.now()\|ZoneId.systemDefault" \
  app/src/main data/src/main domain/src/main
```

The last one should return only the existing provider implementations. Anything new is a Principle VII
violation regardless of whether the tests pass.
