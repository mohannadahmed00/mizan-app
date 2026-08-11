# Contract: Repository and Use-Case Surface

**Feature**: 003-weekly-accountability-sheet | **Date**: 2026-08-11

Additions and changes only. `002`'s `contracts/repositories.md` remains the record of what `002`
shipped; where the two disagree, this file is current and says so explicitly.

Declared in `:domain`, implemented in `:data`. Every method suspends or returns a `Flow`. Nothing
throws for an expected condition.

---

## DayPlanRepository *(changed — two additions)*

```kotlin
interface DayPlanRepository {
    suspend fun planFor(date: LocalDate): DayPlan?
    suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome              // unchanged signature
    fun observePlan(date: LocalDate): Flow<DayPlan?>

    // new
    suspend fun plansBetween(start: LocalDate, end: LocalDate): List<DayPlan>
    suspend fun earliestPlanDate(): LocalDate?
}
```

**Guarantees** — `002`'s 1–4 stand unchanged. Added:

5. `ensurePlanFor` now records **how** the plan came into being. A plan created for the current date
   is `OPENED`; one created for an elapsed date is `BACKFILLED`. The caller does not choose — the
   repository decides from the date and the injected clock, so two callers cannot label the same
   situation differently.
6. `ensurePlanFor` remains the **only** way a plan is created. Backfill adds no second creation path,
   which is what keeps a backfilled plan and an opened plan identical in every field but one.
7. `plansBetween` is inclusive at both ends, returns plans in date order, and returns only the dates
   that have plans — it never fabricates an entry for a missing date. Deciding what a missing date
   means belongs to `GetWeekSummary`, not to a query.
8. `earliestPlanDate` returns null when no plan exists. This is the record start, and after
   research.md R1 it is the only floor on backfill.
9. There is still **no update method of any kind**, and none may be added. `origin` is written at
   insert and is frozen with the rest of the row.

---

## CatalogueRepository *(changed — one semantic change)*

```kotlin
suspend fun versionEffectiveOn(date: LocalDate): Int?   // signature unchanged, behaviour changed
```

**Supersedes `002`'s guarantee 4.** The old wording was "returns the version with the greatest
`effectiveFrom` ≤ date, or null if the date precedes every version. Never guesses."

**Current rule:**

| Case | Returns |
|---|---|
| One or more versions with `effectiveFrom` ≤ date | the greatest such version |
| Versions exist, but all start after date | the **lowest** version |
| No versions at all | `null` |

The middle row is the change (FR-013b). It is not a guess: a catalogue applies until superseded, and
the earliest one has nothing before it to defer to. Null is reserved for a genuine absence — no
catalogue has ever been loaded — which remains a first-class outcome the UI surfaces.

**This cannot re-score anything.** It affects only whether a plan can be *created* for a date that
previously resolved to nothing. An existing plan stores its own `catalogueVersion` and is never
re-resolved, because `ensurePlanFor` returns early when a plan exists.

---

## CompletionRepository *(changed — one addition)*

```kotlin
// new
suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion>
```

**Guarantees** — `002`'s 1–7 stand unchanged, including that `DayWritePolicy` gates every write.
Added:

8. `liveBetween` is inclusive at both ends and returns only live records — `reversedAt` null and
   `deletedAt` null — like every other read in this repository. A range read that returned tombstones
   would inflate a past week's earned total, and it would be visible to the user only as a number
   they cannot reconcile with what they remember doing.
9. This feature adds **no write method**. The sheet and the day summary read; recording stays on the
   Today screen, and `DayWritePolicy` is not widened (FR-025).

---

## GetWeekSummary *(new — `:domain/usecase/`)*

The only orchestrator in the feature. Backfills, then aggregates, then returns.

```kotlin
class GetWeekSummary(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(week: Week): WeekOutcome
}

sealed interface WeekOutcome {
    data class Ready(val summary: WeekSummary) : WeekOutcome
    data class BackfillFailed(val week: Week) : WeekOutcome
    data class NoCatalogue(val week: Week) : WeekOutcome
}
```

**Guarantees**

1. **Backfill precedes aggregation.** For every date in `week` that has elapsed, has no plan, and is
   at or after the record start, `ensurePlanFor` is called and must succeed before any figure is
   computed (FR-010, FR-014a).
2. **Never writes at or after today.** The current date's plan is `002`'s responsibility, created at
   launch; later dates get nothing (FR-010c). A date after today is projected, never persisted
   (FR-009d).
3. **Never writes a completion** (FR-011a).
4. **Idempotent.** Re-invoking for the same week creates no additional plan and returns identical
   figures. A concurrent creation of the same date is absorbed: the unique index on `day_plans.date`
   rejects the second insert, and that is read as "it already exists", not as a failure (FR-010d).
5. **All-or-nothing rendering.** If any required backfill cannot be written, it returns
   `BackfillFailed` and no figures. Plans written before the failure remain valid and are reused on
   the next attempt (FR-014b, FR-014d).
6. **Two denominators, never mixed.** Elapsed dates contribute their **stored plan's**
   `availablePoints`; dates after today contribute a **projection** against the current catalogue.
   `WeeklyScore.fraction` divides by `elapsedAvailable` alone (FR-009a).
7. **Reads no clock of its own.** `today` comes from the injected `TimeProvider` (Principle VII).
8. **Returns `NoCatalogue` only when no catalogue has ever been loaded** — after research.md R1, a
   date can no longer fail to resolve a version for any other reason.

---

## GetDaySummary *(new — `:domain/usecase/`)*

```kotlin
class GetDaySummary(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
) {
    suspend operator fun invoke(date: LocalDate): DaySummary?
}
```

**Guarantees**

1. Built entirely from that date's **stored plan** and its completions. The live catalogue is not
   consulted, so a catalogue change cannot alter what a past day reports (FR-023, Principle III).
2. Task order, labels, points, and occurrence limits come from the plan's `PlannedTask` rows, which
   `002` already snapshots — the day renders correctly even if the task no longer exists in the
   catalogue.
3. Returns null when no plan exists for the date. It never creates one; backfill is `GetWeekSummary`'s
   job and happens before a day is reachable.
4. **Read-only by construction.** There is no write method here, and the day-summary UI state carries
   no event that could express one (FR-024, Principle VI).

---

## Cross-cutting

**No repository reads a clock.** Unchanged from `002`. `GetWeekSummary` takes `TimeProvider` because
it must know which dates have elapsed; the repositories still do not.

**Week rules live in `WeekBoundary` only.** No repository, query, or use case computes a week start
or a week key (FR-001, Principle VII).

**No network.** Unchanged — the app still has none.

**Nothing throws for an expected condition.** Absent plan, absent catalogue, failed backfill, and a
date outside the record are all modelled in return types.
