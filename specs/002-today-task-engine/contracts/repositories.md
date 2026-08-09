# Contract: Repository Interfaces

**Feature**: 002-today-task-engine | **Date**: 2026-08-09

Declared in `:domain`, implemented in `:data`. These are the seam Phase 7 swaps: adding a backend
must change implementations, never these signatures (Principle V).

Every method is suspending or returns a `Flow`. None throws for expected conditions — absent data is
modelled in the return type.

---

## CatalogueRepository

```kotlin
interface CatalogueRepository {
    suspend fun seedIfNeeded(): SeedOutcome
    suspend fun currentVersion(): Int?
    suspend fun versionEffectiveOn(date: LocalDate): Int?
    suspend fun catalogueAt(version: Int): Catalogue?
}

sealed interface SeedOutcome {
    data class Seeded(val version: Int, val taskCount: Int) : SeedOutcome
    data class AlreadyPresent(val version: Int) : SeedOutcome
    data class Failed(val defects: List<CatalogueDefect>) : SeedOutcome
}
```

**Guarantees**

1. `seedIfNeeded` is **idempotent** (FR-001). Calling it twice changes nothing — no duplicate rows,
   no altered plans, no altered completions.
2. It validates before writing, using the `001` contract. A catalogue with defects is **not
   partially written**; it returns `Failed` and leaves storage untouched.
3. `Failed` is a first-class outcome the UI must surface (FR-003). An empty day is never shown as if
   it were a valid one.
4. `versionEffectiveOn` returns the version with the greatest `effectiveFrom` ≤ date, or null if the
   date precedes every version. Never guesses.

---

## DayPlanRepository

```kotlin
interface DayPlanRepository {
    suspend fun planFor(date: LocalDate): DayPlan?
    suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome
    fun observePlan(date: LocalDate): Flow<DayPlan?>
}

sealed interface EnsureOutcome {
    data class Created(val plan: DayPlan) : EnsureOutcome
    data class AlreadyExists(val plan: DayPlan) : EnsureOutcome
    data class NoCatalogue(val date: LocalDate) : EnsureOutcome
}
```

**Guarantees**

1. `ensurePlanFor` creates a plan **only if none exists** for that date. When one exists it is
   returned untouched — never rebuilt, never reconciled against the current catalogue (FR-007).
2. A created plan is **immutable** thereafter. There is deliberately no `update` method; the
   interface offers no way to express the forbidden operation.
3. A created plan carries its Hijri label already, computed from the civil date (FR-009). There is
   **no method to set or change it** — the interface offers no way to express a revision, and none
   is needed.
4. `ensurePlanFor` accepts any date and is not itself restricted, because roadmap Phase 3 must
   backfill past dates through this same method. The restriction to the current date lives in
   `DayWritePolicy`, consulted by `CompletionRepository` — see its guarantee 7.

---

## CompletionRepository

```kotlin
interface CompletionRepository {
    suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome
    suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome
    fun observeCompletions(date: LocalDate): Flow<List<Completion>>
    suspend fun liveCount(date: LocalDate, taskSlug: String): Int
}

sealed interface RecordOutcome {
    data class Recorded(val completion: Completion, val liveCount: Int) : RecordOutcome
    data class AtLimit(val limit: Int) : RecordOutcome
    data class NotWritable(val reason: String) : RecordOutcome
}

sealed interface UndoOutcome {
    data class Reversed(val completion: Completion, val liveCount: Int) : UndoOutcome
    data object NothingToUndo : UndoOutcome
    data class NotWritable(val reason: String) : UndoOutcome
}
```

**Guarantees**

1. `record` awards the points from the **planned task**, not the live catalogue, and stores them on
   the row (FR-011). Those points never change afterwards.
2. `record` refuses beyond the occurrence limit and returns `AtLimit`. It does not throw — being at
   the limit is an ordinary state, not an error (Principle IX).
3. `undoLast` reverses the most recent **live** completion by `recordedAt`, writing a tombstone
   rather than deleting (FR-014).
4. `NothingToUndo` when no live completion exists. Silent and harmless — never an error state.
5. Every read — `observeCompletions`, `liveCount`, and everything derived from them — counts only
   rows with a null tombstone (research.md R5). No method returns tombstoned rows; nothing outside a
   future sync path has any reason to see them.
6. `record` followed by `undoLast` leaves `liveCount` exactly as it began, however many times it is
   repeated (SC-012).
7. **Both `record` and `undoLast` consult `DayWritePolicy` before writing anything.** When the date
   is not writable they return `NotWritable` and touch no storage. This is the only place FR-015 is
   enforced; without it the policy is decoration and `NotWritable` is an unreachable branch. Phase 5
   widens the policy and nothing else changes.

---

## Cross-cutting

**No repository reads a clock.** Dates and instants are passed in from `TimeProvider` at the call
site (Principle VII). A repository that could ask what day it is would be a second opinion about the
day boundary.

**No repository performs I/O outside its own store.** No network exists in this feature at all
(research.md R4).

**Nothing throws for an expected condition.** Absent catalogue, absent plan, at-limit, and
nothing-to-undo are all modelled in return types. Exceptions are reserved for genuine faults —
corrupt storage, failed migration.

**Suspending, not blocking.** Every call is safe from the main thread and dispatches internally, so
no caller has to know where the work runs.
