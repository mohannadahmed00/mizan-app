# Use Case Contracts: Charts & Insights

All four use cases live in `domain/usecase/`, depend only on repository interfaces and
`TimeProvider`, and **write nothing** — no call in this feature reaches `ensurePlanFor`, `record`, or
`undoLast`. This is the load-bearing guarantee `InsightsNoWriteTest` (SC-001, FR-009) checks.

## `GetWeeklyTrend`

```kotlin
class GetWeeklyTrend(private val historyPage: GetHistoryPage) {
    suspend operator fun invoke(before: WeekKey? = null, weeks: Int = 8): TrendOutcome
}

sealed interface TrendOutcome {
    data class Ready(val weeks: List<WeekSummary>, val hasMore: Boolean) : TrendOutcome // oldest-first
    data object NoHistory : TrendOutcome
    data class CouldNotLoad(val detail: String) : TrendOutcome
    data class CatalogueUnavailable(val detail: String) : TrendOutcome
}
```

`CouldNotLoad` passes through `HistoryOutcome.CouldNotLoad` unchanged — `GetHistoryPage` already
distinguishes a read failure from a missing catalogue version, and `TrendOutcome` preserves that
distinction rather than collapsing it.

- Calls `historyPage(before = before, weeksPerPage = weeks)`, maps `HistoryOutcome` to `TrendOutcome`,
  and reverses `page.weeks` to oldest-first (charts read left-to-right as time moving forward).
  `hasMore` is `page.hasMore`, passed straight through.
- `before = null` (the initial load) fetches the most recent `weeks` weeks, same as before. To scroll
  further back, the caller passes the `WeekKey` of the **oldest week currently loaded** as `before`
  and prepends the newly returned (oldest-first) weeks to the front of what it already has — the same
  load-more-and-prepend shape `HistoryViewModel` already uses for `HistoryEvent.LoadMore` (`005`),
  reused here instead of inventing a second paging idiom (US1 Acceptance Scenario 3, FR-005).
- `NoHistory` when `HistoryPage.weeks` is empty (no plan ever materialized) — distinct from an empty
  list of completions, matching FR-007's "no data" vs. "recorded zero" distinction at the use-case
  boundary, not just in the UI.
- `hasMore = false` is the boundary signal for US1 Acceptance Scenario 3 ("stop at the install week
  and communicate there is nothing earlier") — it is `GetHistoryPage`'s own bound, not a new rule.

## `GetMonthOverview`

```kotlin
class GetMonthOverview(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(month: YearMonth): MonthOverviewOutcome
}

sealed interface MonthOverviewOutcome {
    data class Ready(val overview: MonthOverview) : MonthOverviewOutcome
    data class CatalogueUnavailable(val detail: String) : MonthOverviewOutcome
}
```

- Reads `recordStart = plans.earliestPlanDate()`, `today = time.today()`.
- `storedPlans = plans.plansBetween(month.atDay(1), month.atEndOfMonth())`,
  `liveCompletions = completions.liveBetween(...)` — same two range reads `GetHistoryPage` already
  makes, over a calendar month instead of a page of weeks.
- For every date in the month with **no** stored plan:
  - If the date is after `today`: project with the *current* catalogue version (mirrors
    `GetHistoryPage`'s future-date handling).
  - If the date is on or before `today` and at/after `recordStart`: project with
    `catalogue.versionEffectiveOn(date)` (mirrors `GetHistoryPage`'s elapsed-unplanned handling,
    `005` research R2/R3) — **never** the current version, so a mid-month catalogue change cannot
    move an earlier date's figures (FR-006, SC-003).
  - If the date precedes `recordStart` (or there is no record yet): no projection entry —
    `buildDayCells` reports `OUTSIDE_RECORD` for it (FR-007).
- Returns `CatalogueUnavailable` only if a needed version's content cannot be loaded — matching
  `GetHistoryPage`'s and `GetWeekSummary`'s existing failure shape, not a new one.
- **Never calls `ensurePlanFor`.** Scrolling/paging months must cost zero writes, exactly like
  scrolling history (`005` FR-standard, this feature's SC-001).

## `GetSectionBreakdown`

```kotlin
class GetSectionBreakdown(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(period: InsightsPeriod): SectionBreakdownOutcome
}

sealed interface SectionBreakdownOutcome {
    data class Ready(val sections: List<SectionPerformance>) : SectionBreakdownOutcome
    data class CatalogueUnavailable(val detail: String) : SectionBreakdownOutcome
}
```

- **Always the current week or current month** — the caller never constructs an `InsightsPeriod` for
  a past week/month. Unlike Trend and Month, Sections has no historical previous/next navigation; see
  spec.md Assumptions for why this narrower scope is deliberate, not an oversight.
- Resolves `period` to a date range (`Week.start..Week.end`, or `month.atDay(1)..month.atEndOfMonth()`).
- Restricts the range to elapsed dates only (`date <= today`) before reading — a section's `available`
  must never include a future date's projected points, mirroring `WeeklyScore.elapsedAvailable`
  (research R4's stated convention). An entirely future period (e.g., viewing next month, if the UI
  ever allowed it — it does not, per FR-005's bound to recorded data) yields an empty list, not a
  crash.
- For each elapsed date: uses the stored plan if one exists, otherwise `deriveDayPlan(catalogue,
  versionEffectiveOn(date), date)` — same read-only derivation `005` introduced, reused rather than
  reimplemented. A derived plan is discarded after this call, never stored (Principle III/VI).
- Folds every date's `plannedTasks` into `SectionPerformance` per `data-model.md`'s aggregation rule.

## `GetPersonalBests`

```kotlin
class GetPersonalBests(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(): PersonalBestsOutcome
}

sealed interface PersonalBestsOutcome {
    data class Ready(val bests: PersonalBests) : PersonalBestsOutcome
    data object NoHistory : PersonalBestsOutcome
    data class CatalogueUnavailable(val detail: String) : PersonalBestsOutcome
}
```

- `recordStart = plans.earliestPlanDate()`; `NoHistory` immediately if null.
- Single `plansBetween(recordStart, today)` / `liveBetween(recordStart, today)` read, projected only
  for elapsed dates with no stored plan (never future dates — the whole record is at or before
  today), same `versionEffectiveOn` rule as `GetMonthOverview`.
- Builds `DayCell`s via `buildDayCells`, groups them by `WeekBoundary.weekContaining(date)` locally
  (no further repository calls), computes each group's `WeeklyScore` the same way
  `buildWeekSummary` does, and scans both collections per `data-model.md`'s tie rule.

## Failure and empty states shared by all four

Every outcome type distinguishes exactly the same three situations `GetHistoryPage`/`GetWeekSummary`
already do, so `InsightsViewModel` maps them the same way `HistoryViewModel` and `WeekViewModel` do:

1. **No history at all** (`recordStart == null`) → an explicit empty state, never a zeroed chart
   (Edge Case: zero recorded history).
2. **Catalogue unavailable for a needed version** → a retryable failure notice, never a silently wrong
   number.
3. **Ready with partial coverage** (e.g., one day of history) → renders with whatever data exists;
   none of the four use cases requires a minimum amount of history to return `Ready` (SC-005).
