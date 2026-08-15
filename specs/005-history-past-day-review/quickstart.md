# Quickstart: Validating History & Past-Day Review

How to prove this increment works. Models and signatures are in
[data-model.md](./data-model.md) and [contracts/](./contracts/); this file is the run guide.

## Prerequisites

- The repo on `spec/005-history-past-day-review`, branched from `origin/develop-v1`.
- A device or emulator for `:data` and `:app` instrumented tests. `:domain` needs only the JVM.
- No network at any point. If any test here needs connectivity, that is the bug.

## Commands

```bash
# Domain — the fold, the widened aggregate, the page assembly, derived-equals-stored
./gradlew :domain:test

# Data — the catalogue-change suite, no-writes-on-scroll, best-effort store, performance
./gradlew :data:connectedAndroidTest

# App — ViewModels on the JVM, screens on device
./gradlew :app:test
./gradlew :app:connectedAndroidTest

# Everything
./gradlew test connectedAndroidTest
```

`003`'s `BuildWeekSummaryTest` and `GetWeekSummaryBackfillTest` must pass **unmodified** throughout —
they are the regression net for the widened `buildWeekSummary` precondition (research R2). A change
to either is a signal that the widening went further than intended.

---

## The defining scenario — SC-005, User Story 3

This is the one this whole phase exists for. `docs/PLAN.md` asks for it as a first-class deliverable,
not a test chore, and Principle I requires it to exist before the code it covers.

1. Seed a record under catalogue **v1**: several weeks of completions across weekdays that exercise
   the 69 / 74 / 76 daily totals, with at least one fully recorded week reading 500/500.
2. Capture, for every recorded date: its task list, each task's point value, its available points,
   its earned points, its containing week's totals, and both streak figures.
3. Introduce catalogue **v2** with an effective-from date of today, changing at least one point value
   and at least one schedule rule (a task gains a weekday, another loses one).
4. Reopen and re-capture.

**Expected:** every pre-change figure is identical. Today follows v2. If any past figure moved, the
feature is reading the live catalogue and Principle III is violated — stop and fix before continuing.

Then, still under v2, open an elapsed date that never had a plan and confirm the plan it materialises
is built from **v1**, not v2 (FR-018, SC-007).

---

## Manual walkthrough

Fresh install, airplane mode on, throughout.

| # | Do | Expect |
|---|---|---|
| 1 | Open the app with no records, reach history | "The record has not started" and a way to start — not an empty list (FR-007) |
| 2 | Record something today, return to history | One week row, today's position recorded, the six other positions in that week outside the record or not yet elapsed (SC-002) |
| 3 | Seed a record with a twelve-week gap, scroll through it | All twelve weeks present, each 0 out of its available points, none skipped or merged (FR-001a, SC-002a) |
| 4 | Read those twelve rows carefully | No red, no cross, no "missed", nothing summarising the lapse (FR-029, FR-030, SC-014) |
| 5 | Scroll to the record start | The list ends there and says so; scrolling further does nothing (FR-004) |
| 6 | Scroll the whole record, then inspect stored plans | **Identical to before scrolling.** This is SC-008 and the point of clarification Q2 |
| 7 | Open an elapsed day from that gap | Its full plan, all zeros, its available points from the version effective then; exactly one plan now stored, marked backfilled (SC-008a) |
| 8 | Reopen the same day | Figures identical; nothing new stored (SC-009) |
| 9 | Compare step 7's figures against what its week row showed before opening | Identical (FR-020b, SC-009b) |
| 10 | Look for any way to record or undo on that day | None. Plain copy saying recording happens on the current day (FR-024, SC-012) |
| 11 | Tap **today's** cell, from history and from the weekly sheet | The recording surface both times, not a read-only copy (FR-015a, SC-013) |
| 12 | Record something there, press back | Returns to whichever list you came from, with the change reflected (FR-015, SC-013) |
| 13 | Open a past day from the weekly sheet, press back | Returns to the weekly sheet, not to history (FR-015) |

## Failure paths

| Do | Expect |
|---|---|
| Make reads fail, open history | A notice attributing the failure to the app, with a retry — never an empty record (FR-031, SC-016) |
| Make the catalogue unavailable, open history | Every week whose plans exist still renders; what cannot be built is named; the current day stays recordable (FR-032) |
| Make plan storage fail, open an unopened past day | The day opens with the same figures its week row showed, no error shown, no plan stored. Restore storage, reopen — exactly one plan stored (FR-020c, SC-016a) |
| Make `versionEffectiveOn` return null for that date, open it | "No record" — never an empty day scoring zero (FR-032, SC-016a) |

## Time paths

Use the fake clock; never the device clock.

| Do | Expect |
|---|---|
| Cross local midnight while on the recording surface opened from history | It stops accepting writes at the moment it stops being today (FR-028, FR-015b, SC-013) |
| Cross local midnight while on a past day | Nothing about that day changes — nothing on it depends on the current date |
| Move the clock backwards past dates carrying completions | Those dates are not recordable, nothing stored changes, restoring the clock restores the view (FR-027) |
| Open a record whose start falls mid-week | Days before the start read as outside the record, not as unrecorded; no plan is created for them (SC-002, SC-010) |

## Performance — SC-015

Seed three years of daily completions (~157 weeks, ~1,095 plans, ~65,000 completions).

- First screen of history: **within 500 ms**.
- Any day opens: **within 300 ms**.
- Recording a completion on the current day: no slower than without this feature present.

Measure from request to figures available, on a mid-range device, not an emulator on a workstation.

## Done

- All four Gradle commands green, `003`'s two week tests unmodified.
- The SC-005 scenario passes and was written before the code it covers.
- The manual walkthrough completed in airplane mode on a fresh install.
- `docs/GLOSSARY.md` carries Locked Day and Record Start (research R7).
