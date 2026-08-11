# Quickstart: Weekly Accountability Sheet

**Feature**: 003-weekly-accountability-sheet | **Date**: 2026-08-11

How to run and validate this increment. Types and rules live in [data-model.md](./data-model.md) and
[contracts/](./contracts/); this file is the run guide.

## Prerequisites

Nothing new. `002` is merged on `develop-v1` and the toolchain is unchanged — no dependency is added
by this feature (research.md R3, R5).

- JDK 17+, Android SDK with `compileSdk 37`
- A device or emulator for `:data` instrumented tests — the Room migration test cannot run on the JVM

```bash
git switch spec/003-weekly-accountability-sheet
./gradlew --version          # sanity check
```

## Running the checks

```bash
# Domain — week boundary, aggregate, projection. Fast, run these constantly.
./gradlew :domain:test

# App — ViewModel state transitions
./gradlew :app:testDebugUnitTest

# Data — Room migration, range queries, immutability. Needs a device.
./gradlew :data:connectedDebugAndroidTest

# Everything
./gradlew test connectedAndroidTest
```

## Test-first order (Principle I)

The commit history must show this order — it is checked at the PR merge gate, and a PR whose first
commit is production code has already failed it.

| Step | Write first | Then |
|---|---|---|
| 1 | `WeekBoundaryTest` — Saturday/Friday edges, month and year crossings | `WeekBoundary`, `Week`, `WeekKey` |
| 2 | `BuildWeekSummaryTest` — aggregation, elapsed vs target, the 500 fixture | `WeeklyScore`, `BuildWeekSummary` |
| 3 | `GetWeekSummaryTest` with fake repositories — backfill policy, idempotency, failure | `GetWeekSummary` |
| 4 | `MizanDatabaseMigrationTest` — v1 data survives 1 → 2 | `MIGRATION_1_2`, `origin` column, schema 2 |
| 5 | `DayPlanRangeQueryTest`, `CatalogueVersionResolutionTest` | new DAO queries, R1 change |
| 6 | `HistoricalImmutabilityTest` — catalogue bump vs stored days | (proves 4 and 5; no new production code) |
| 7 | `WeekViewModelTest`, `DaySummaryViewModelTest` | the two ViewModels |
| 8 | — | `WeekScreen`, `DaySummaryScreen`, Koin wiring (exempt) |

Koin module wiring and `@Preview` composables are the only exemptions. Mappers are not exempt.

## Validating the acceptance scenarios

### The 500 fixture (SC-001)

The single most important check, and the one that fails loudest if anything is wrong.

```text
Seed a fully elapsed Saturday–Friday week, every applicable task at its occurrence limit.
Expect: earned 500, elapsedAvailable 500, weekTarget 500, fraction 1.0.
Per-day available: 69, 69, 74, 69, 69, 74, 76.
```

If this reads anything but 500, stop — `docs/PLAN.md` locks the arithmetic and `001` validates the
catalogue against it. The catalogue is not the thing to adjust.

### Mid-week denominators (SC-001a)

With a fake clock walking Saturday → Friday through a week with no catalogue change:

```text
elapsedAvailable: 69, 138, 212, 281, 350, 424, 500
weekTarget:       500 on every one of those days
```

Also assert that displaying a mid-week week creates **no** plan for any date after today (SC-001b) —
compare `countPlans()` before and after.

### Backfill (SC-005, SC-007, SC-008)

```text
1. Fake clock at a Saturday. Launch, complete one task.
2. Advance the clock to Thursday without launching.
3. Launch, open the week.
Expect: Sunday–Wednesday each have a plan reading 0 out of 69/69/74/69,
        every one marked BACKFILLED,
        Saturday still marked OPENED,
        the week's elapsedAvailable identical to a week opened every day.
4. Assert no plan exists for Friday, and none for any date before the record start.
```

### Immutability across a catalogue change (SC-009, SC-009a)

Mandatory — the constitution requires it for any increment touching persistence or the catalogue,
and this one touches both.

```text
1. Seed catalogue v1. Create an opened plan and a backfilled plan.
2. Introduce catalogue v2 with different points and a changed schedule.
3. Assert both stored plans report their original tasks, points, and totals —
   on the sheet and in the day summary.
4. Assert a newly created plan reflects v2.
```

### Migration (research.md R2)

```text
1. MigrationTestHelper creates a v1 database.
2. Insert a day plan, its planned tasks, and completions.
3. Run MIGRATION_1_2.
4. Assert every original column value is unchanged and origin reads 'OPENED'.
```

`data/schemas/2.json` must be committed. A missing exported schema blocks the `develop-v1` → `main`
release gate.

### Read-only (SC-010)

Exercise every control on both screens and compare the stored rows before and after. Nothing may
change. `WeekEvent` has four cases and `DaySummaryUiState` has no event type at all, so this is a
finite check rather than a hope.

### Performance (SC-013)

```text
Week needing all seven days backfilled → final figures within 300 ms, mid-range device.
Week needing none, against a year of seeded records → same budget.
Capture the rendered week at first paint and after all writes settle: identical (SC-013a).
```

A miss here is a finding about `002`'s storage design, not a reason to loosen the number or add a
cache — `docs/PLAN.md` defers caching until a measurement demands it, and this *is* the measurement.

### No-shame audit (SC-011)

Walk every state named in [contracts/ui-state.md](./contracts/ui-state.md) — five `DayCellState`,
four `WeekUiState.Status`, three `DaySummaryUiState.Status` — and check each for a negative
quantity, a penalty, red, a cross, or language implying fault. Pay particular attention to the three
states that read alike but mean different things: a day outside the record, a day not yet elapsed,
and a day with nothing recorded. None is a failure, and each must be visually distinct from the
others (FR-016, FR-017a).

## Manual smoke check

```bash
./gradlew :app:installDebug
```

Airplane mode, fresh install. Complete a task on Today, open the week, confirm today's cell shows
what you recorded and the other six read sensibly. Tap a day, confirm the summary is read-only and
offers nothing to tap that changes anything. Step back a week, confirm it stops at the record start
without an error. Leave and reopen — the current week is shown again.

## Merge gate reminders

Before merging to `develop-v1`:

- Constitution Check in [plan.md](./plan.md) passes and names each principle touched.
- Tests green, including `connectedAndroidTest`.
- Test commits precede their implementation commits **in the PR's** history — merges are squash-only,
  so the PR is the only place this evidence exists.
- The historical-immutability test is present (Principle III) — this increment touches persistence
  and the catalogue, so it is not optional.
- `data/schemas/2.json` is committed and the migration is non-destructive.
