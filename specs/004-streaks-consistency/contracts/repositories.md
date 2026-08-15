# Contract: Repository and Use-Case Surface

**Feature**: 004-streaks-consistency | **Date**: 2026-08-15

Additions only. `002`'s and `003`'s contract files remain the record of what they shipped; nothing
here contradicts either.

Declared in `:domain`, implemented in `:data`. Every method suspends or returns a `Flow`. Nothing
throws for an expected condition.

**This feature adds no write of any kind.** No repository gains a write method, no DAO gains one,
and the one use case below has no creation path. That is the property to check first in review.

---

## CompletionRepository *(changed — one addition)*

```kotlin
interface CompletionRepository {
    // unchanged from 002 / 003
    suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome
    suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome
    fun observeCompletions(date: LocalDate): Flow<List<Completion>>
    suspend fun liveCount(date: LocalDate, taskSlug: String): Int
    suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion>

    // new
    fun observeConsistencyDates(): Flow<List<LocalDate>>
}
```

**Guarantees** — `002`'s and `003`'s stand unchanged. Added:

1. `observeConsistencyDates` returns **every date carrying at least one live completion**, ascending,
   distinct, with no duplicates and no gaps filled in. A date appears once however many completions
   it holds (FR-002).
2. It filters reversed and tombstoned records exactly as every other read here does. A date whose
   only completion has been undone does not appear (FR-003).
3. It is unbounded. There is no date range parameter and none may be added — the longest streak is
   unbounded by definition (FR-007), so a windowed variant would be a wrong answer waiting for a
   long-running user.
4. It emits again whenever the completion log changes, including on undo. This is what makes FR-023
   hold without the caller wiring anything: recording and undoing are both writes to `completions`,
   and Room invalidates on both.
5. It returns dates, never completions. A caller cannot learn from it what was completed, how much
   was earned, or how many occurrences were recorded — and so cannot accidentally weight a day by
   how much was done.
6. It never fails for an expected condition. An empty record emits an empty list, which is a
   perfectly ordinary state and the one a fresh install is in.

**Implementation note**: backed by `CompletionDao.observeLiveDates()`, a `DISTINCT` query covered by
the existing `completions.creditedDate` index. No index is added — see
[research R1](../research.md#r1--where-do-consistency-days-come-from).

---

## DayPlanRepository *(unchanged)*

Listed because this feature reads from it and adds nothing.

```kotlin
suspend fun earliestPlanDate(): LocalDate?   // 003. The record start — read, never written here
```

Used for one purpose: the floor beneath which no date is evaluated (FR-010) and beyond which
indicator positions read as `OUTSIDE_RECORD` rather than as days worth nothing (FR-020a).

`ensurePlanFor` is **not** called anywhere in this feature. Displaying a streak must create nothing
(FR-013), which is the difference between this increment and `003`, where displaying a week
deliberately wrote.

---

## GetStreakSummary *(new use case)*

```kotlin
class GetStreakSummary(
    private val completions: CompletionRepository,
    private val dayPlans: DayPlanRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(): Flow<StreakSummary>
}
```

**What it does**, in order:

1. Collects `completions.observeConsistencyDates()`.
2. Reads `dayPlans.earliestPlanDate()` for the record start.
3. Folds with `buildStreakSummary(dates, time.today(), time.now(), time.zone(), recordStart)`.
4. Suspends until `StreakClock.nextBoundaryAfter(...)` and re-folds, so 20:00 and local midnight
   change the state with the app open (FR-017, FR-025, FR-026).

**Guarantees**:

1. **It never writes.** It holds no method that could, and the two repositories it depends on are
   used only through reads. `ensurePlanFor` is not in its reach by discipline; that it is reachable
   on the interface is why SC-009 verifies stored records are identical before and after.
2. **It reads no clock of its own.** Every instant, date and zone comes from `TimeProvider`
   (Principle VII). `StreakClock` receives them as parameters and holds no state.
3. **It never consults the catalogue.** `CatalogueRepository` is not a dependency, so FR-005 is
   enforced by the constructor rather than by review.
4. **It emits on every relevant change**: a completion recorded, a completion undone, a scheduled
   boundary reached. It does not emit on a timer.
5. **It surfaces failure as failure.** A read that throws propagates; the flow does not swallow it or
   substitute an empty result. Turning that into `StreakPanelUi.Unavailable` is the ViewModel's job
   (FR-021b), because a zero produced by a failed read is indistinguishable from a real zero, and
   FR-021b forbids exactly that.
6. **First emission is not gated on anything else.** It subscribes independently of the day's task
   collector, so the tasks paint without waiting (FR-018c).

**Why a `Flow` and not a `suspend fun`**: three different things change the answer — a record, an
undo, and the clock crossing a boundary. A suspend function would need all three to be polled by the
caller, which puts a time rule in `:app` and re-reads the log on a timer. `003`'s `GetWeekSummary`
is a suspend function because a week changes only when the user navigates.

**Why `dayPlans` is a dependency at all**: the record start. It is the one fact the streak needs that
the completion log cannot supply — a date before the first plan was never tracked, which is different
from a date on which nothing was done (FR-010, FR-020a).

---

## Nothing else changes

| Surface | Status |
|---|---|
| `CatalogueRepository` | untouched. Not a dependency of anything in this feature |
| `DayWritePolicy` | untouched, and not consulted — there is nothing here to write |
| `GetWeekSummary`, `GetDaySummary` | untouched |
| `TimeProvider` | untouched. `now()`, `today()` and `zone()` already provide everything needed |
| `DayPlanDao`, `CatalogueDao` | untouched |
| `CompletionDao` | one read-only query added |
