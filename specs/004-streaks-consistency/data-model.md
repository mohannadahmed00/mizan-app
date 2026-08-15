# Phase 1 Data Model: Streaks & Consistency

**Feature**: 004-streaks-consistency | **Date**: 2026-08-15

Only what is added. Everything from `002` and `003` that is not named here is unchanged and
untouched.

**The headline is what is missing.** There is no Part for storage, because this increment adds no
table, no column, no index and no migration. The database stays at version 2 and `data/schemas/`
gains no file. Every model below is derived, lives for the length of one emission, and can be deleted
without touching a single stored byte.

---

## Part 1 — Domain models (`:domain`, pure Kotlin)

### New: ConsistencyDay

Not a stored type — a rule and a predicate over dates.

```kotlin
/** FR-001. A date counts when at least one live completion is credited to it. */
fun isConsistencyDay(date: LocalDate, consistencyDates: Set<LocalDate>): Boolean
```

The set arrives already filtered of reversed and tombstoned records by
`CompletionRepository.observeConsistencyDates()` — see [contracts/repositories.md](./contracts/repositories.md).
Nothing downstream re-checks liveness, so there is one place the filter can be wrong.

**It has a production caller, deliberately.** `buildStreakSummary` computes `todayCounted` through it
rather than by an inline membership test. A predicate that exists only to carry documentation is an
abstraction with no user, which Principle VIII rules out — so either the rule has a caller or it has
no reason to be a function. It has one.

**Why a set of dates and not a list of completions**: FR-002 makes consistency a yes or no. A date
with forty completions and a date with one are the same fact, and a set is the shape that cannot
represent the difference. It also removes any temptation to weight a day by how much was done, which
is the product thesis expressed as a type.

**What is deliberately not consulted**: `PlanOrigin`, and the existence of a `DayPlan` at all
(FR-004). A completion cannot exist without a plan, and `DayWritePolicy` admits completions only on
the current date, so a live completion is already proof the app was open that day. `003` stored the
origin for this phase; this phase finds it corroborating rather than necessary. **Phase 5 changes
this** — retroactive completion breaks the premise, and that is where `PlanOrigin` earns its keep.

### New: StreakSummary

Everything the screen needs, computed together in one pass.

| Field | Type | Rules |
|---|---|---|
| `current` | `Int` | ≥ 0. The run ending on today or yesterday (FR-006); 0 when neither counts |
| `longest` | `Int` | ≥ `current`. The longest run anywhere in the record (FR-007) |
| `lastActiveDate` | `LocalDate?` | the most recent Consistency Day; null when none exists (FR-008) |
| `todayCounted` | `Boolean` | whether today is already inside `current` (FR-006a) |
| `isAtRisk` | `Boolean` | FR-025 — `current ≥ 1`, `!todayCounted`, and local time in `[20:00, midnight)` |
| `showBreakNotice` | `Boolean` | FR-021a — `current == 0`, `longest > 0`, `lastActiveDate` within the 7 days ending today |
| `recentActivity` | `List<ActivityDay>` | exactly 7, oldest first, today last (FR-020) |

**Invariants**, asserted in the type:

- `current ≤ longest` always. A current run *is* a run, so it cannot exceed the longest.
- `todayCounted` implies `current ≥ 1`.
- `isAtRisk` and `todayCounted` are never both true.
- `isAtRisk` and `showBreakNotice` are never both true — the first requires `current ≥ 1`, the second
  requires `current == 0`.
- `recentActivity.size == 7`, unconditionally, including for an empty record.

**Not stored, and not storable.** There is no repository for this type, no DAO, and no entity. The
only way to obtain one is to fold the record (FR-012).

### New: ActivityDay

One position in the seven-day indicator.

| Field | Type | Rules |
|---|---|---|
| `date` | `LocalDate` | within the 7 days ending today |
| `state` | `ActivityState` | one of four |

```kotlin
enum class ActivityState {
    COUNTED,          // a live completion is credited to this date
    NOT_RECORDED,     // elapsed, inside the record, nothing credited
    TODAY_PENDING,    // today, nothing credited yet — not a missed day
    OUTSIDE_RECORD,   // earlier than the record start; never tracked at all
}
```

**Why four and not two**: FR-020a. Collapsing `TODAY_PENDING` into `NOT_RECORDED` shows the user a
day they have not had yet as a day they missed, and collapsing `OUTSIDE_RECORD` into it invents a
history the user never had — a fresh install would open showing six failures. Both are Principle IX
violations that a two-state model makes structurally unavoidable, which is why the states live in the
domain rather than in the composable.

**None of the four is a failure state**, and none may acquire a colour or glyph that reads as one
(FR-021). `003`'s `DayCellState` set the same posture for the week sheet.

### New: StreakClock

The one home for this feature's time rules (Principle VII).

```kotlin
object StreakClock {
    val AT_RISK_FROM: LocalTime = LocalTime.of(20, 0)          // FR-025, inclusive

    fun isAtRiskWindow(now: Instant, zone: ZoneId): Boolean
    fun nextBoundaryAfter(now: Instant, zone: ZoneId): Instant  // next 20:00, else next midnight
}
```

`nextBoundaryAfter` is what makes FR-017 and FR-026 hold while the app sits open — see
[research R2](./research.md#r2--how-does-the-state-change-while-the-app-sits-open). It reads no
clock itself; the instant and zone are passed in from `TimeProvider`, exactly as `DayBoundary.dateAt`
takes them.

**The threshold is a constant, not a setting.** Settings are outside the MVP, and a configurable
nudge opens a preference surface this increment has no reason to open (spec Assumptions).

### New: BuildStreakSummary

The pure fold. `docs/PLAN.md` specifies it as "a pure fold over consistency days"; this is that,
split from the flow plumbing so it stays synchronously testable.

```kotlin
fun buildStreakSummary(
    consistencyDates: List<LocalDate>,   // ascending, distinct, already filtered of reversals
    today: LocalDate,
    now: Instant,
    zone: ZoneId,
    recordStart: LocalDate?,
): StreakSummary
```

**Rules it implements**, each traceable to one requirement:

| Rule | Requirement |
|---|---|
| Dates after `today` are dropped before anything else | FR-011 |
| `current` counts back from `today` if counted, else from `yesterday`, else 0 | FR-006, FR-009 |
| A run ending at `recordStart` is complete, not broken | FR-010 |
| Dates before `recordStart` are never evaluated | FR-010 |
| `longest` is the maximum run in the whole retained set | FR-007 |
| The catalogue is not a parameter, so it cannot be consulted | FR-005 |

**Why `now` and `zone` rather than a `Boolean atRisk` flag**: the at-risk decision is part of the
same fact and belongs to the same test. Passing a pre-computed flag would put the 20:00 rule in the
caller and give Principle VII two homes.

**Complexity**: one pass, O(n) over the retained dates, no allocation per date beyond the indicator.
See [research R5](./research.md#r5--is-one-pass-over-the-dates-fast-enough-and-does-anything-need-caching).

---

## Part 2 — Storage (`:data`)

**No change.** Recorded explicitly because it is the most load-bearing fact in this document.

| | |
|---|---|
| Database version | **2**, unchanged. No migration written, none needed |
| Schema exports | `data/schemas/1.json`, `2.json` — unchanged, no new file |
| Tables | unchanged |
| Columns | unchanged |
| Indices | unchanged — `completions.creditedDate` from `002` already covers the new read |
| Write methods | unchanged. No DAO gains one, and this feature calls none |

The one addition is a read:

```kotlin
@Query(
    "SELECT DISTINCT creditedDate FROM completions " +
        "WHERE reversedAt IS NULL AND deletedAt IS NULL ORDER BY creditedDate"
)
fun observeLiveDates(): Flow<List<String>>
```

Mapped to `List<LocalDate>` by the repository, using `002`'s existing date conversion. The
`reversedAt IS NULL AND deletedAt IS NULL` filter is not optional and is not a detail — it is FR-003,
and a query that omitted it would keep a date counted after its only completion was undone.

---

## Part 3 — UI models (`:app`)

Full shapes in [contracts/ui-state.md](./contracts/ui-state.md). In summary:

- `TodayUiState` gains one field, `streak: StreakPanelUi`, carrying its own three-way status so the
  element can be resolving while the screen is ready, and ready while the screen has no catalogue
  (FR-018b, FR-018c).
- `TodayEvent` gains one case, `RetryStreak`. It re-subscribes to a read and can author nothing
  (Principle VI).
- `TodayViewModel` stops replacing its whole state on the `CatalogueUnavailable` path. This is the
  only existing behaviour the increment changes; see
  [research R3](./research.md#r3--where-does-the-streak-live-in-the-today-state).

---

## What this model deliberately does not contain

| Absent | Why |
|---|---|
| A `streaks` table or cached row | `docs/PLAN.md` permits one only after a measurement shows the derived query is slow; none exists, and Principle VIII forbids it until one does |
| A "break notice already shown" flag | FR-021a derives the window from `lastActiveDate`. A flag would be the only writable state in the feature, existing purely to suppress a message |
| A freeze, repair, or grace record | Out of scope by the spec, and FR-016 forbids leniency of any kind |
| An achievement or milestone entity | Phase 10 |
| A `StreakBreak` record | Streak Break is a boundary read out of the record, not an event that happens to it |
| Anything keyed by user | `userId` already exists on every synchronisable row from `002`; this feature adds no row to key |
