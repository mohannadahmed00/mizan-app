# Quickstart: Streaks & Consistency

**Feature**: 004-streaks-consistency | **Date**: 2026-08-15

How to run this increment's checks and how to satisfy yourself that each acceptance criterion in
[spec.md](./spec.md) actually holds. Shapes live in [data-model.md](./data-model.md) and
[contracts/](./contracts/); this file is the run guide.

## Prerequisites

- JDK 17+ on `PATH`, Android SDK with `compileSdk 37`.
- A device or emulator for `:data` and `:app` instrumented tests.
- Branch `spec/004-streaks-consistency`, cut from `origin/develop-v1`.
- `002` and `003` merged — this increment extends running code.

```bash
./gradlew --version          # sanity check
```

**One build change**: `:domain`'s `build.gradle.kts` gains
`testImplementation(libs.kotlinx.coroutines.test)`. The library is already in
`gradle/libs.versions.toml` (used by `:app`), so no version is added. Nothing else in any build file
changes — see [research R2](./research.md#r2--how-does-the-state-change-while-the-app-sits-open).

## Running the checks

```bash
# Domain — the fold, the seven-day window, the 20:00 rule, boundary re-emission. Fast.
./gradlew :domain:test

# App — ViewModel state transitions, including the panel surviving CatalogueUnavailable
./gradlew :app:testDebugUnitTest

# Data — the DISTINCT query and the catalogue-change immutability check. Needs a device.
./gradlew :data:connectedDebugAndroidTest

# App instrumented — the streak element's four states and its persistence across blocks
./gradlew :app:connectedDebugAndroidTest

# Everything
./gradlew test connectedAndroidTest
```

**No migration test in this increment**, because there is no migration. The database stays at
version 2 and `data/schemas/` gains no file. If you find yourself writing one, something has gone
wrong — check [data-model.md Part 2](./data-model.md#part-2--storage-data).

## Test-first order (Principle I)

Strictly per layer, tests before the code they require:

1. `:domain` — `BuildStreakSummaryTest`, `RecentActivityTest`, `StreakClockTest` → then
   `ConsistencyDay.kt`, `StreakSummary.kt`, `BuildStreakSummary.kt`, `RecentActivity.kt`,
   `StreakClock.kt`.
2. `:domain` — `GetStreakSummaryTest` (virtual time) → then `GetStreakSummary.kt` and the
   `CompletionRepository` addition.
3. `:data` — `ConsistencyDatesQueryTest`, `StreakImmutabilityTest` → then
   `CompletionDao.observeLiveDates()` and the repository implementation.
4. `:app` — `TodayStreakTest` → then the `TodayUiState` field and the `TodayViewModel` restructure.
   The existing `TodayViewModelTest` is not modified; it is the regression net and must stay green.
5. `:app` — `StreakElementTest` and `TodayScreenStreakTest` → then `StreakElement.kt` and its
   placement on `TodayScreen`.

Only Koin wiring in `di/Modules.kt` and `@Preview` composables claim the exemption. The composable
does not.

**The restructure in step 4 is the risky one.** It changes shipped behaviour, so keep the existing
`TodayViewModelTest` green at every commit — those cases are the regression net for the day's tasks,
scoring, section position, and rollover, none of which this increment intends to touch.

## Validating the acceptance scenarios

### The fold (SC-001, SC-004, SC-006, SC-007)

Drive `buildStreakSummary` directly with hand-built date lists; no database, no coroutines.

| Seeded dates | `current` | `longest` |
|---|---|---|
| five consecutive ending today | 5 | 5 |
| five consecutive ending yesterday, nothing today | 5 | 5 |
| run ending the day before yesterday | 0 | (run length) |
| empty | 0 | 0 |
| 12-day run, one gap day, 3-day run ending today | 3 | 12 |
| unbroken run reaching `recordStart` | full length | full length |

Gaps of one, two and seven days must all break the run identically — a gap is a gap (FR-009).

### A day counts once (SC-002, SC-003, SC-005)

- Forty completions on one date and one completion on one date produce identical summaries. The fold
  never sees the difference, because it takes dates.
- Undo the only live completion for a date → the date leaves the list → `current` drops. Re-record →
  it returns to exactly the previous value. Assert both directions; a summary that only recovers on
  restart has failed FR-023.
- A week of `BACKFILLED` plans with no completions contributes nothing. Seed via `003`'s backfill
  path so the plans are real, not fabricated.

### Time (SC-010, SC-011, SC-012)

All with `FakeTimeProvider` and `runTest`:

- Advance the zone so today moves forward two days → the run ends, and every stored `creditedDate` is
  byte-identical afterwards.
- Move the clock backwards past dates carrying completions → `current` reads lower, nothing stored
  changes, and restoring the clock restores the original figure exactly. This is FR-011 doing its job.
- 19:59 → not at risk. 20:00 → at risk. The boundary is inclusive.
- With the flow collected, advance virtual time across 20:00 and then across midnight, and assert the
  summary re-emits at each without any user action. This is the check that
  [research R2](./research.md#r2--how-does-the-state-change-while-the-app-sits-open) exists for; a
  resume-only implementation passes every other test in this section and fails this one.

### The break notice window (SC-018)

Last active date 7 days ago → notice shown. 8 days ago → not shown, and the panel reads as a plain
start state with the longest streak still present. Compare stored records before and after showing
it: nothing may change, because nothing records that it was shown (FR-021a).

### Nothing is written (SC-009)

The load-bearing check of the whole increment. Snapshot every row in `day_plans`, `planned_tasks` and
`completions`; open Today, let the streak resolve, record, undo, cross midnight, retry after a
failure; snapshot again. The only differences may be the completion rows the user's own taps created.
No plan may be created — in particular, no **production** path in this feature may call
`ensurePlanFor`, which is where `003` deliberately differs. Tests may call it freely to seed history;
the prohibition is on `src/main`, not on fixtures.

### Immutability across a catalogue change (SC-009a)

Seed history, then bump the catalogue's point values and schedule rules, then re-read every streak
figure. All identical. This is `StreakImmutabilityTest`, and it is how Principle III is discharged
by an increment that changes no schema — see
[research R7](./research.md#r7--how-is-principle-iii-satisfied-by-an-increment-that-changes-no-schema).
A figure that moves means the streak is reading the catalogue, which FR-005 forbids and which nothing
else would catch, because the wrong number would still look plausible.

### The panel's states (SC-016, SC-017, SC-019)

Compose tests drive `StreakPanelUi` directly — no database needed, because `Ready` is a flat
snapshot:

- Step forward and back through every prayer block: the element stays in place and reads the same.
- Set the screen to `Status.CatalogueUnavailable` with `StreakPanelUi.Ready`: the figures are still
  shown.
- `Resolving`: the element occupies its space and displays no number. Assert the absence of "0"
  specifically — that is the failure mode FR-018c names.
- `Unavailable`: a notice and a retry appear; tasks remain recordable in the same test.
- Capture the panel from first paint until the figures settle; no intermediate number may appear.

### Performance (SC-014)

Seed three years of daily completions (~1,095 consistency dates, ~65,000 rows). Measure from
subscription to first emission: under 100 ms on a mid-range device. Then measure recording and
undoing with the panel collected and without it — the two must not differ. `docs/PLAN.md`'s
definition of done for this phase is "requires no new writes on the completion path", and this is
the measurement of it.

### No-shame audit (SC-013)

Read every string, colour and state the element can produce, in all four `ActivityState` values and
all three `StreakPanelUi` cases, including the ended-run, never-started, and read-failure copy.
Nothing red, no cross, no broken-chain imagery, no negative figure, no countdown framed as a penalty,
and nothing attributing fault — including the failure notice, which blames the app. Audit against the
design list in `CLAUDE.md`.

## Manual smoke check

```bash
./gradlew :app:installDebug
```

On a **fresh install in airplane mode**:

1. Open the app. The tasks appear; the streak element appears beside them reading an unstarted
   record — an invitation, not zero failures.
2. Complete one task. The run reads 1 immediately, without leaving the screen.
3. Undo it. The run returns to 0 immediately, with nothing implying a mistake.
4. Complete one task again, kill the app, relaunch. The run reads 1, recomputed from the record.
5. Step through the prayer blocks. The element does not move and does not change.

## Merge gate reminders

Before opening the PR into `develop-v1`:

- The Constitution Check in [plan.md](./plan.md) passes and names each principle touched.
- Test tasks precede their implementation tasks **in the PR's commit history**. Squash merges mean
  the pull request is the only place that evidence exists.
- Persistence and the catalogue are untouched, so no migration and no schema export are involved.
  `StreakImmutabilityTest` is what stands in for Principle III's test obligation here, and it is not
  optional — see research R7.
- `docs/GLOSSARY.md` carries Longest Streak and Streak Break. The glossary is a deliverable of this
  increment, not tidying (research R6).
- MVP boundary: this is the last increment of Phases 1–4. `docs/PLAN.md` asks for two full weeks of
  real use before Stage 5 begins.
