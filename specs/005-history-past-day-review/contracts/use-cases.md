# Contract: Domain Use Cases

Two new use cases and one widened pure function. **No repository interface changes and no new DAO
methods** — every read this feature needs already exists.

---

## `GetHistoryPage` — `domain/usecase/GetHistoryPage.kt`

```
class GetHistoryPage(
    plans: DayPlanRepository,
    completions: CompletionRepository,
    catalogue: CatalogueRepository,
    time: TimeProvider,
)

suspend operator fun invoke(before: WeekKey? = null, weeks: Int = 8): HistoryOutcome

sealed interface HistoryOutcome {
    data class Ready(val page: HistoryPage) : HistoryOutcome
    data class CouldNotLoad(val detail: String) : HistoryOutcome
    data class CatalogueUnavailable(val detail: String) : HistoryOutcome
}
```

`before = null` requests the first page, whose newest week is the week containing today. A non-null
`before` requests the page immediately older than that key.

### Guarantees

1. **Writes nothing.** There is no call to `ensurePlanFor` on this path, directly or transitively
   (FR-020, SC-008). This is the guarantee the whole clarification Q2 rests on.
2. **Continuous.** Every week between the newest returned and the oldest returned is present, in
   descending order, exactly seven days apart (FR-001a).
3. **Floored at the record start.** No week older than the one containing `earliestPlanDate()` is
   returned, and `hasMore` is false once it is reached (FR-004).
4. **Capped at the current week.** No week later than the one containing `time.today()` is ever
   returned, whatever `before` is passed (FR-006).
5. **Empty record is not an error.** `earliestPlanDate() == null` yields `Ready` with no weeks and
   `hasMore = false` (FR-007), never `CouldNotLoad`.
6. **Honest denominators.** Elapsed dates with no plan carry the availability the version effective
   on that date gives, not zero and not the current version's (FR-020a, R2).
7. **Uses one week rule.** Page boundaries come from `WeekBoundary` and nowhere else (FR-009).
8. **Partial catalogue is not total failure.** `CatalogueUnavailable` is returned only when no
   version can be resolved at all; a page whose stored plans render fine is returned even if some
   date's projection cannot be computed (FR-032).

### Cost

One `plansBetween` and one `liveBetween` over the page's span, plus one `catalogueAt` per distinct
version in the range, memoised within the call (R3).

---

## `GetDayDetail` — `domain/usecase/GetDayDetail.kt`

```
class GetDayDetail(
    plans: DayPlanRepository,
    completions: CompletionRepository,
    catalogue: CatalogueRepository,
    time: TimeProvider,
)

suspend operator fun invoke(date: LocalDate): DayDetailOutcome

sealed interface DayDetailOutcome {
    data class Ready(val summary: DaySummary) : DayDetailOutcome
    data object NoRecord : DayDetailOutcome
    data class CatalogueUnavailable(val detail: String) : DayDetailOutcome
}
```

### Order of operations (R4)

| Step | Condition | Action |
|---|---|---|
| 1 | `date` after `today`, or before `earliestPlanDate()`, or no record start exists | `NoRecord`. Nothing read, nothing written. |
| 2 | A stored plan exists | Summarise from it. **No write.** |
| 3 | Otherwise | Attempt `ensurePlanFor(date)`. **Any failure is swallowed.** |
| 4 | Re-read | A plan now exists → summarise from it. |
| 5 | Still no plan | Derive (R1) and summarise from that. |
| 6 | `versionEffectiveOn(date)` is null | `CatalogueUnavailable`. |

### Guarantees

1. **Never alters or removes anything.** The only write reachable is `ensurePlanFor`, which returns
   `AlreadyExists` untouched for a date that has a plan and has no update path (Principle III).
2. **At most one plan per call, and only for an eligible date** (FR-020, SC-008a).
3. **Best-effort storing.** A failed store never changes what the user sees and never surfaces
   (FR-020c). Step 4's re-read, rather than trusting the return value, is what makes this correct
   when an exception left no outcome at all.
4. **Identical figures either way.** Steps 4 and 5 produce equal summaries for the same date
   (FR-020b, SC-009b).
5. **Never fabricates.** An unresolvable catalogue version yields `CatalogueUnavailable`, never an
   empty day scoring zero (FR-032).
6. **Never consults the current catalogue for a past date.** Every version comes from
   `versionEffectiveOn(date)` (FR-010, FR-018).

### Not this use case's job

The **current date**. FR-015a routes today to the recording surface, so `GetDayDetail` is only ever
called with an elapsed date. It still refuses a future date rather than assuming callers behave.

---

## `buildWeekSummary` — widened precondition

```
fun buildWeekSummary(
    week: Week,
    today: LocalDate,
    recordStart: LocalDate?,
    plans: List<DayPlan>,
    completions: List<Completion>,
    projectedAvailable: Map<LocalDate, Int>,   // precondition widened
): WeekSummary
```

`projectedAvailable` must now hold an entry for **every date in `week` with no stored plan that is at
or after `recordStart`** — previously only dates after `today`.

**Unchanged:** the signature, `DayCellState` and all five of its values, the two-denominator weekly
score, and the rule that a stored plan's `availablePoints` always wins over the projection.

**Regression net:** `003`'s `BuildWeekSummaryTest` and `GetWeekSummaryBackfillTest` must pass
unmodified. They only exercise weeks whose elapsed dates all have plans, so the widened branch is
unreachable for them.

---

## Repository interfaces — unchanged

Stated explicitly because it is a design claim, not an omission:

| Interface | Change |
|---|---|
| `DayPlanRepository` | None. `planFor`, `ensurePlanFor`, `plansBetween`, `earliestPlanDate` all suffice. |
| `CompletionRepository` | None. `liveBetween` takes an arbitrary range. |
| `CatalogueRepository` | None. `versionEffectiveOn` and `catalogueAt` are exactly what derivation needs. |

Phase 7 therefore inherits no new seam from this increment (Principle V).
