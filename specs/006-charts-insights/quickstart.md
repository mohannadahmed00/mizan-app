# Quickstart: Validating Charts & Insights

How to prove this increment works. Models and signatures are in
[data-model.md](./data-model.md) and [contracts/](./contracts/); this file is the run guide.

## Prerequisites

- The repo on `spec/006-charts-insights`, branched from `origin/develop-v1`.
- A device or emulator for `:data` and `:app` instrumented tests. `:domain` needs only the JVM.
- No network at any point. If any test here needs connectivity, that is the bug.

## Commands

```bash
# Domain — buildDayCells extraction, month/section/personal-best pure functions, use cases
./gradlew :domain:test

# Data — no-write, mid-period catalogue change, full-year/full-record performance
./gradlew :data:connectedAndroidTest

# App — ViewModel on the JVM, screen on device
./gradlew :app:test
./gradlew :app:connectedAndroidTest

# Everything
./gradlew test connectedAndroidTest
```

`003`'s `BuildWeekSummaryTest` and `GetWeekSummaryBackfillTest`, and `005`'s `GetHistoryPageTest` and
`HistoryScreenTest`, must all pass **unmodified** throughout — they are the regression net for the
`buildDayCells` extraction (research R2) and for reusing `GetHistoryPage` unchanged (research R1). A
change to any of them is a signal this feature reached further than it should have.

---

## The defining scenario — SC-003, mid-period catalogue change

The Phase 6 equivalent of `005`'s catalogue-change suite, scoped to aggregation instead of a single
day.

1. Seed a record under catalogue **v1** spanning at least six weeks, including one full calendar
   month, with known per-section completion patterns (e.g., Adhkar always complete, Qiyam roughly
   half).
2. Capture: the weekly trend's per-week percentages, the monthly overview's per-day states, the
   section breakdown's per-section rates, and the personal-best day and week — all for that month.
3. Introduce catalogue **v2** effective today, changing at least one point value and one schedule
   rule.
4. Re-open Insights for that same past month/weeks under v2.

**Expected:** every figure captured in step 2 is bit-for-bit identical. Today's own week/month
figures follow v2. If any past figure moved, Insights is reading the live catalogue somewhere and
Principle III is violated — stop and fix before continuing.

---

## Manual walkthrough

Fresh install, airplane mode on, throughout.

| # | Do | Expect |
|---|---|---|
| 1 | Open the app with no records, reach Insights | An explicit "nothing recorded yet" state, not an empty or zeroed chart (Edge Case) |
| 2 | Record one task today, open Insights | Trend shows one in-progress week point, distinct from a completed week; month shows one non-outside-record day; personal bests show that one day (SC-005) |
| 3 | Seed a full year of varied history, open Insights | All three views render within 1 second (SC-002) |
| 4 | Switch Trend → Month → Sections repeatedly | Each switch is immediate; no chart ever shows stale data from the previous view |
| 5 | Navigate the trend and the month view past the record start | Navigation stops there and says so, same as History's own boundary (FR-005) |
| 6 | Navigate the month view across a December→January boundary | The new month's data is correct, no day miscounted into the wrong month (Edge Case) |
| 7 | Open a month that starts before install | Pre-install days read as "no data," never as 0% (Edge Case, FR-007) |
| 8 | Read the section breakdown for a period with one rarely-completed section | Every section listed in catalogue order, plain rate shown, no sorting by rate, no "lowest" badge, no red anywhere (Clarification Q2, FR-010, SC-006) |
| 9 | Read the personal-bests card | Only a best day and a best week are shown — no "worst" surface anywhere in the feature (FR-004, Assumptions) |
| 10 | Try to find any way to complete, undo, edit, export, or share from Insights | None exists — the screen has no such affordance anywhere (FR-009) |
| 11 | Inspect stored rows after steps 1–10 | **Identical to before opening Insights at all.** This is SC-001/FR-009 |

## Failure paths

| Do | Expect |
|---|---|
| Make reads fail, open Insights | A notice attributing the failure to the app, with a retry — never a blank or zeroed chart |
| Make the catalogue unavailable for a version a projected date needs | That date/section/period is named as unavailable; everything already loaded from stored plans still renders |

## Time paths

Use the fake clock; never the device clock.

| Do | Expect |
|---|---|
| View the current, unfinished week/month | Visually distinct from a completed period in every chart that shows it (FR-008, Clarification Q3) |
| Cross local midnight while Insights is open | The current period's in-progress marker updates on next load; nothing already rendered silently changes underneath the user |
| Move the clock backwards past dates carrying completions | Those dates still render from what is stored; nothing recorded is lost or hidden |

## Performance — SC-002

Seed a full year (~365 plans) and, separately, a 3-year fixture (~1,095 plans, tens of thousands of
completions) for `GetPersonalBests`, mirroring `005`'s `HistoryPerformanceTest` scale.

- Weekly trend: **within 1 second**.
- Monthly overview: **within 1 second**.
- Section breakdown: **within 1 second**.
- Personal bests (full-record scan): **within 1 second** even at 3 years.
- Recording or undoing a task on the Today screen: no slower than without this feature present
  (SC-004).

Measure from request to figures available, on a mid-range device, not an emulator on a workstation.

## Done

- All four Gradle commands green; `003`'s two week tests and `005`'s history tests unmodified.
- The SC-003 mid-period catalogue-change scenario passes and was written before the code it covers.
- The manual walkthrough completed in airplane mode on a fresh install.
- The SC-006 no-red audit passed against `CLAUDE.md`'s design checklist.
