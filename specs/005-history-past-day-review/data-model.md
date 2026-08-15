# Phase 1 Data Model: History & Past-Day Review

**No table, no column, no migration.** The Room schema stays at version 2 and no schema export
changes. Everything below is either an in-memory domain model or a clarified rule over models that
already exist.

The one persistence-adjacent fact worth stating plainly: the only row this feature can cause to exist
is a `day_plans` row (and its `planned_tasks` children) inserted through the existing
`DayPlanRepository.ensurePlanFor`, only when a user opens an elapsed date at or after the record
start. Nothing is ever updated and nothing is ever deleted.

---

## New domain models

### `HistoryPage` — `domain/history/HistoryPage.kt`

A loaded stretch of the record. Held in memory, never stored.

| Field | Type | Meaning |
|---|---|---|
| `weeks` | `List<WeekSummary>` | Descending by date, newest first. Reuses `003`'s aggregate unchanged. |
| `oldestLoaded` | `WeekKey` | The key of the last entry in `weeks`, used to request the next page. |
| `hasMore` | `Boolean` | Whether any week older than `oldestLoaded` is still within the record. |
| `recordStart` | `LocalDate?` | The floor. Null before any plan exists, in which case `weeks` is empty. |

**Rules**

- `weeks` is **continuous** — for every adjacent pair, the older week starts exactly seven days
  before the newer one. No week within the span may be absent (FR-001a). A test asserts the invariant
  directly rather than trusting assembly order.
- The first page's newest week is the week containing today (FR-006 — no week later than the current
  one).
- `hasMore` is false exactly when `weeks` reaches the week containing `recordStart` (FR-004).
- When `recordStart` is null, `weeks` is empty and `hasMore` is false (FR-007).

### Derived `DayPlan` — `domain/history/DeriveDayPlan.kt`

Not a new type. A `DayPlan` produced by `buildDayPlan` and never inserted, for an elapsed date at or
after the record start that has no stored plan (R1).

| Field | Value when derived |
|---|---|
| `id` | A fixed non-identifying constant. It is never persisted, never returned by a repository, and never reaches the sync surface. |
| `date`, `catalogueVersion`, `hijriLabel`, `availablePoints`, `plannedTasks` | Exactly what `buildDayPlan` produces for `versionEffectiveOn(date)` — the same values a stored plan for that date would carry. |
| `origin` | `BACKFILLED`. A derived plan is never evidence the app was open (FR-021, FR-014). |

**The invariant this exists to guarantee (FR-020b)**: for any elapsed date at or after the record
start, the derived plan and the plan `ensurePlanFor` would store are equal on every field except
`id`. Because both come from `buildDayPlan` with the same arguments, this holds by construction;
`DeriveDayPlanTest` asserts it field by field anyway, since it is the property FR-020b names.

---

## Changed rules over existing models

### `buildWeekSummary` — widened precondition (R2)

`projectedAvailable` currently must hold an entry for every date **after today**. It now must hold an
entry for every date in the week with **no stored plan** that is at or after the record start.

`available` per day cell becomes:

| Condition | `available` |
|---|---|
| `OUTSIDE_RECORD` | `0` |
| A stored plan exists | that plan's `availablePoints` — **always wins** |
| No stored plan, in record | `projectedAvailable[date] ?: 0` |

`DayCellState` is unchanged. All five values already exist and `plan == null` on an in-record elapsed
date already yields `NOTHING_RECORDED` — only the denominator was wrong.

The precedence line is the one that matters for Principle III: a stored plan always wins, so a
catalogue change can never move a date that has one.

### `DaySummary` — unchanged shape, widened source

`003`'s `DaySummary`, `PlannedTaskRecord` and `DayCellState` are used exactly as they are. The only
change is that the `DayPlan` behind a summary may now be derived rather than stored. Nothing in the
model records which, and nothing downstream may branch on it (FR-014).

---

## Entity glossary alignment

| Spec term | Where it lives in the model |
|---|---|
| History Page | `HistoryPage` |
| Week Row | `WeekSummary` — `003`'s, unchanged |
| Record Start | `DayPlanRepository.earliestPlanDate()`; surfaced as `HistoryPage.recordStart`. **Added to `docs/GLOSSARY.md`** (R7) |
| Locked Day | Not a type. The rule is `DayWritePolicy.isWritable(date) == false`, which is every date except today. **Added to `docs/GLOSSARY.md`** (R7) |
| Retro-Completion Window | Not a type and not a glossary entry — permanently empty in shipped behaviour (R7). The vocabulary lives in `spec.md`. |
| Day Detail | `DaySummary` — `003`'s, unchanged |

---

## What is deliberately absent

- **No stored week rollup or day-summary cache.** `docs/PLAN.md` permits one only when a measurement
  says the derived query is slow. No measurement exists (Principle VIII).
- **No stored "history last viewed" position.** Scroll position is UI state, not a record.
- **No stored install date.** The record start is the earliest plan, which is the same statement for
  a fresh install and a restored one.
- **No new identifier of any kind**, so nothing new enters the Phase 7 sync surface (Principle V).
