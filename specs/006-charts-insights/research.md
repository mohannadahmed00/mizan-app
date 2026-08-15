# Phase 0 Research: Charts & Insights

All items below were unknowns raised by the Technical Context, resolved against the merged code on
`develop-v1` (through `005`) rather than assumed. No `NEEDS CLARIFICATION` remains.

## R1 — Weekly trend reuses `GetHistoryPage`, does not reimplement it — including its paging

**Decision**: `GetWeeklyTrend` takes a `GetHistoryPage` instance and calls
`invoke(before = before, weeksPerPage = weeks)`, then reverses the returned `HistoryPage.weeks` to
oldest-first and passes `hasMore` straight through. The initial call (`before = null`) loads the most
recent `weeks` (8, per Clarification Q3). Scrolling further back — required by User Story 1
Acceptance Scenario 3 ("navigating the trend further back than any recorded history... stops at the
install week") — is `InsightsViewModel` calling it again with `before` set to the oldest currently
loaded week's key and prepending the result, the same load-more-and-prepend shape `HistoryViewModel`
already uses for `HistoryEvent.LoadMore`.

**Rationale**: `GetHistoryPage` already produces exactly what a trend needs — backfill-free
`WeekSummary`s, floored at `earliestPlanDate()`, with no write on read (`005` FR-standard), *and* its
own `before`-cursor paging with a `hasMore` boundary flag. The only differences a trend chart needs
are (a) a bounded initial window instead of `GetHistoryPage`'s open-ended default and (b)
chronological instead of newest-first order — both are presentation reorderings of the same data and
the same paging contract, not a new aggregation or a new paging rule. Principle VII forbids a second
week-boundary implementation; reusing `GetHistoryPage`'s paging as well as its aggregation is what
keeps this to one. (An earlier draft of this plan gave `GetWeeklyTrend` only a fixed `weeks`
parameter with no cursor, which left AS3 and FR-005's "move between periods (previous/next)"
unimplementable — caught in `/speckit-analyze` and corrected here.)

**Alternatives considered**:
- A dedicated `buildWeeklyTrend` pure function repeating `GetHistoryPage`'s backfill-avoidance and
  projection logic — rejected as exactly the duplicate-implementation risk Principle VII calls out.
- Extending `GetHistoryPage` itself with an `order` parameter — rejected; it already has a clean,
  tested contract (`005`) and callers outside this feature (`HistoryViewModel`) should not need to
  reason about an order flag they never use.

## R2 — `buildDayCells` is extracted from `buildWeekSummary`, not duplicated

**Decision**: Pull the per-date loop inside `buildWeekSummary` (plan lookup, earned sum, state,
available) into a standalone pure function:

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

`buildWeekSummary` becomes `buildDayCells(week.dates, ...)` plus the `WeeklyScore` roll-up it already
computes from the resulting `days` list. Its signature, its behavior, and every existing test's
expectations are unchanged.

**Rationale**: `buildMonthOverview` needs the identical per-date derivation — same four-plus-one
`DayCellState` rule, same "a stored plan's own total always wins over a projection" guarantee — over
a list of dates that is not seven and does not start on a Saturday. Copying the loop would create a
second place the state rule could drift (the exact failure mode Principle VII's "no second opinion"
line warns about); extracting it creates one. This is the plan's only refactor, and Principle VIII
permits it because it unlocks a named capability (User Story 2) being built in this same increment,
not a hypothetical future one.

**Alternatives considered**:
- Have `buildMonthOverview` call `buildWeekSummary` seven dates at a time and concatenate — rejected;
  a month is not a whole number of weeks, and stitching partial weeks together is more code than the
  extraction, with edge cases at both ends of the month.
- Leave `buildWeekSummary` untouched and hand-write the same state rule again for months — rejected
  as the duplicate-logic risk above.

## R3 — The monthly overview's four bands are `DayCellState`, not a new type

**Decision**: `MonthOverview.days` is `List<DayCell>` — the same type `WeekSummary.days` already
uses. The Q1 clarification's four bands (no-data / untouched / partial / complete) map directly onto
`DayCellState.OUTSIDE_RECORD` / `NOTHING_RECORDED` / `PARTLY_RECORDED` / `FULLY_RECORDED`, with
`NOT_YET_ELAPSED` already covering days later in the month than today. The color mapping
(`WeekScreen.containerColorFor`) moves to a small shared file, `ui/DayCellColors.kt`, so
`WeekScreen` and the new `InsightsScreen` read one definition instead of two.

**Rationale**: The clarification's discrete-band answer was chosen without knowing the app already
has this exact enum, color-mapped and non-red, shipped since `003`. Building a second enum or a
second color table for the same five states would be the kind of duplication Principle VII and
Principle IX's own design-audit list exist to prevent — and it would risk the two screens disagreeing
about what "partial" looks like.

**Alternatives considered**: A month-specific `MonthDayState` with only four values (folding
`NOT_YET_ELAPSED` into `OUTSIDE_RECORD` or `NOTHING_RECORDED`) — rejected; collapsing "upcoming this
month" into "nothing recorded" is exactly the misreading FR-008/Q3 (in-progress period must not read
as low-consistency) was written to prevent, and `WeekScreen` already proves five values render fine.

## R4 — One range read per period change, not one per chart

**Decision**: `InsightsViewModel` loads a period's `plansBetween`/`liveBetween` once per period
selection (week or month) and derives the section breakdown from that payload; `GetMonthOverview`
does its own read for the month grid (a different, calendar-shaped range); `GetWeeklyTrend` and
`GetPersonalBests` each do their own bounded reads (8 weeks; full record) since neither shares the
current period's range.

**Rationale**: Four independent per-chart repository calls covering overlapping ranges would cost
more Room round-trips than the data volume justifies and risk the four charts disagreeing after a
completion changes mid-session, since each would be reading at a slightly different moment. A single
payload per period keeps the section breakdown and the month grid consistent with each other when
both are viewing the same month, without introducing a cache or a new stored aggregate (the roadmap's
explicit preference — "no new writes on the completion path," "pre-computed rollups only if measured
necessary").

**Alternatives considered**: A shared in-memory cache keyed by date range across all four use cases —
rejected as unneeded complexity (Principle VIII) until a real performance measurement shows the
straightforward reads are insufficient; SC-002's 1-second budget is generous against the row counts
involved (at most one month or 8 weeks per view, a bounded full-record scan for personal bests).

## Full-record scan bound for `GetPersonalBests`

**Decision**: `GetPersonalBests` reads `plansBetween(recordStart, today)` and
`liveBetween(recordStart, today)` once, builds `DayCell`s via `buildDayCells`, groups them into
`WeekBoundary` weeks locally (no further repository calls), and scans both for the maximum
`fraction`. Ties keep the earliest-occurring maximum, matching how a user would expect "my best day"
to resolve for a repeated 100%.

**Rationale**: This is the one view in the feature that must see the whole record, not a page of it —
FR-004 says "within their recorded history," not "within the last N weeks." `005`'s
`HistoryPerformanceTest` already establishes that a range read over a multi-year record (up to
~1,095 dates) stays within budget; `GetPersonalBests` is a single instance of that same read shape,
exercised once when Insights opens, not on every scroll frame.

**Alternatives considered**: Maintaining a running best-day/best-week cache updated on every
completion — rejected; it is a new stored fact for a value cheaply recomputed on the one screen that
needs it, and the roadmap explicitly defers caching until measurement says otherwise.

## R5 — SC-002's "1 second" is measured at the use-case boundary, not end-to-end through Compose

**Decision**: `InsightsPerformanceTest` (T043) times `GetWeeklyTrend`/`GetMonthOverview`/
`GetSectionBreakdown`/`GetPersonalBests` directly — request to domain result — not the full
ViewModel-mapping-plus-Compose-recomposition path a stopwatch-on-the-user would measure.

**Rationale**: This is the same convention `005`'s `HistoryPerformanceTest` already established for
an identical SC ("first screen of history within 500ms") — read-path latency is overwhelmingly
dominated by the Room queries these use cases perform, and the mapping/render step downstream of
them is the same lightweight, already-measured-elsewhere Compose machinery every other screen in the
app uses. Re-measuring Compose recomposition per-feature would test the framework, not this feature.
Flagged during `/speckit-analyze` (C2) as worth recording explicitly rather than leaving implicit.

**Alternatives considered**: An instrumented end-to-end test driving `InsightsScreen` and measuring
frame time — rejected as disproportionate for a first cut; revisit only if a real user report ties a
perceived delay to Insights specifically.
