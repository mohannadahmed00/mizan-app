# Phase 1 Data Model: Charts & Insights

No new table, column, migration, or stored identity — every type below is a read-only projection
built in `:domain` from data `002`–`005` already persist. This document covers only what is new or
reused; unchanged types (`DayPlan`, `Completion`, `WeekSummary`, `DayCell`, `DayCellState`,
`WeeklyScore`) are described by their existing modules and are not repeated here.

## New domain types (`domain/insights/`)

### `SectionPerformance`

```kotlin
data class SectionPerformance(
    val sectionId: String,
    val sectionLabel: String,
    val sectionOrder: Int,
    val completed: Int,
    val available: Int,
) {
    val rate: Float get() = if (available == 0) 0f else completed.toFloat() / available
}
```

- One row per section that had at least one applicable task on at least one elapsed date in the
  period. A section absent from every day in the period (schedule rule excluded it entirely) does not
  appear — there is nothing to report, not a zero to show.
- `completed` and `available` are both counted in **occurrences**, per FR-003's literal wording
  ("occurrences completed vs. occurrences available") — not points, so `rate` is a true fraction.
  `completed` sums live completions per planned-task instance, capped at that task's
  `maxOccurrencesPerDay`; `available` sums each instance's `maxOccurrencesPerDay` directly.
- `sectionLabel` and `sectionOrder` are taken from the most recent day in the period carrying that
  section (a mid-period catalogue relabel resolves to how the section reads *now*, consistent with
  FR-006's "past figures never change" applying to *points*, not to a display label — see
  `contracts/use-cases.md` for the boundary this draws).
- List order: `sectionOrder` ascending, per Clarification Q2 — never sorted by `rate`.

### `MonthOverview`

```kotlin
data class MonthOverview(
    val month: YearMonth,
    val days: List<DayCell>,
)
```

- `days` has one entry per calendar date in `month`, in date order — reuses `domain.week.DayCell`
  and `DayCellState` exactly as `WeekSummary.days` does (research R3). No new per-day type.
- `month` is a display grouping (`java.time.YearMonth`), never an accountability boundary — the day
  and week boundaries remain the only two boundary rules in the app (Principle VII).

### `PersonalBests`

```kotlin
data class PersonalBests(
    val bestDay: DayCell?,
    val bestWeek: BestWeek?,
)

data class BestWeek(val week: Week, val earned: Int, val available: Int) {
    val fraction: Float get() = if (available == 0) 0f else earned.toFloat() / available
}
```

- Both null only when the record is empty (no plan ever materialized).
- `bestDay` excludes cells in `OUTSIDE_RECORD` or `NOT_YET_ELAPSED` — a best day must have actually
  happened. Ties resolve to the earliest occurrence (research.md).
- `bestWeek` is a small bespoke type, **not** the full `WeekSummary`: `GetPersonalBests` scans only
  `recordStart..today`, so a week at either edge of that range can hold fewer than seven `DayCell`s —
  `WeekSummary`'s constructor requires exactly seven and would throw. `BestWeek.available` sums only
  the cells actually present for that week (dates before `recordStart` are correctly absent, not
  zero-padded). Weeks with `available == 0` are excluded (a week that has not started yet cannot be a
  best week), and comparison uses `fraction`, never a padded denominator.

## Extracted pure function

### `buildDayCells`

```kotlin
fun buildDayCells(
    dates: List<LocalDate>,
    today: LocalDate,
    recordStart: LocalDate?,
    plans: List<DayPlan>,
    completions: List<Completion>,
    projectedAvailable: Map<LocalDate, Int>,
): List<DayCell>
```

- Extracted verbatim from `buildWeekSummary`'s existing per-date loop (research R2). No new state
  rule — the five-value `DayCellState` decision tree is copied unchanged, not reinvented.
- `buildWeekSummary(week, ...)` becomes: call this with `week.dates`, then roll the resulting
  `List<DayCell>` up into a `WeeklyScore` exactly as it does today. Existing `buildWeekSummary` tests
  assert the combined behavior is unchanged.
- `buildMonthOverview(month, today, recordStart, plans, completions, projectedAvailable)` calls this
  with `month.atDay(1)..month.atEndOfMonth()` and wraps the result in `MonthOverview`.

## Use case inputs/outputs

See `contracts/use-cases.md` for full signatures, outcomes, and the read/no-write contract each one
holds. Summary of what each reads and returns:

| Use case | Reads | Returns |
|---|---|---|
| `GetWeeklyTrend` | Delegates to `GetHistoryPage` (8 weeks, then further pages via `before` as the user scrolls back) | `List<WeekSummary>`, oldest-first, plus `hasMore` |
| `GetMonthOverview` | `plansBetween`, `liveBetween` for the month; `versionEffectiveOn`/`catalogueAt` per distinct version needed for unplanned elapsed dates; current catalogue for future dates in the month | `MonthOverview` |
| `GetSectionBreakdown` | Same shape as `GetMonthOverview`, scoped to a `Week` or a `YearMonth` | `List<SectionPerformance>` |
| `GetPersonalBests` | `earliestPlanDate`, then one `plansBetween`/`liveBetween` over the whole record | `PersonalBests` |

## Period selection

```kotlin
sealed interface InsightsPeriod {
    data class ForWeek(val week: Week) : InsightsPeriod
    data class ForMonth(val month: YearMonth) : InsightsPeriod
}
```

Used only by `GetSectionBreakdown`, which is the one chart the spec (FR-003) lets the user scope to
either granularity — but always the *current* week or month (spec.md Assumptions); there is no past
`ForWeek`/`ForMonth` ever constructed. `GetMonthOverview` always takes a `YearMonth` directly (and
does support past months, via the ViewModel's Previous/Next navigation); `GetWeeklyTrend` takes a
`before: WeekKey?` cursor for its own, separate backward-scroll paging (research R1); `GetPersonalBests`
takes no period at all — it is always "the whole record."
