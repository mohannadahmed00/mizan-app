# Phase 1 Data Model: Weekly Accountability Sheet

**Feature**: 003-weekly-accountability-sheet | **Date**: 2026-08-11

Only what changes or is added. Everything from `002` that is not named here is unchanged and
untouched.

---

## Part 1 — Domain models (`:domain`, pure Kotlin)

### Changed: DayPlan

One field added. Every other field, and the write-once invariant, are unchanged.

| Field | Type | Rules |
|---|---|---|
| *(existing fields)* | | unchanged — `id`, `date`, `catalogueVersion`, `hijriLabel`, `availablePoints`, `plannedTasks` |
| `origin` | `PlanOrigin` | **new**; set at creation, never revised |

```kotlin
enum class PlanOrigin { OPENED, BACKFILLED }
```

`OPENED` — the app was running on that date and created the plan for it.
`BACKFILLED` — the plan was created afterwards, for a date the user never saw.

**Invariant unchanged**: after creation no field may change, without exception. `origin` is set by
whichever call created the plan and joins the rest of the record in being frozen. There is still no
update method anywhere in the stack.

**Why this is stored and not derived**: the system knows it at write time. Reconstructing it later
from completion timestamps would be a heuristic about the user standing in for a fact — and it would
be wrong for a day genuinely opened and left empty, which the spec requires to be recordable
(`002` spec, final edge case).

**Who reads it**: nothing in this feature's UI. It exists for Phase 4's streak rule, which must
count days the user was present for and must not be handed a backfilled plan as evidence of
presence (FR-011, SC-008). The sheet renders a backfilled day and an opened-but-empty day
identically.

### New: Week

Seven consecutive dates, Saturday through Friday.

| Field | Type | Rules |
|---|---|---|
| `key` | `WeekKey` | identity; derived from `start` |
| `start` | `LocalDate` | always a Saturday |
| `dates` | `List<LocalDate>` | exactly 7, consecutive, `start` first |

```kotlin
@JvmInline value class WeekKey(val value: String)   // the ISO date of the Saturday, e.g. "2026-08-08"
```

**Why the Saturday's date is the key**: it is stable, sorts correctly, reads correctly in a log, and
cannot drift from the week it names — unlike an ISO week number, which is defined Monday-first and
would need a second rule to translate. Principle VII allows exactly one week rule; a key derived
directly from the boundary keeps it that way.

### New: WeekBoundary

The single place the Saturday-to-Friday rule exists (FR-001), alongside `002`'s `DayBoundary`.

| Function | Returns |
|---|---|
| `weekContaining(date)` | the `Week` that `date` falls in |
| `startOfWeek(date)` | the Saturday on or before `date` |

No other code may compute a week start, a week key, or a week's member dates. Month, year, and
daylight-saving boundaries need no special handling — the week is seven `LocalDate`s stepped one day
at a time from a Saturday, and a `LocalDate` has no offset to shift (FR-003).

### New: DaySummary

One date's read-only projection. Derived on every read, stored nowhere.

| Field | Type | Rules |
|---|---|---|
| `date` | `LocalDate` | |
| `hijriLabel` | `String` | from the stored plan |
| `score` | `DailyScore` | `002`'s type, unchanged |
| `state` | `DayCellState` | derived from `score` |
| `tasks` | `List<PlannedTaskRecord>` | plan's tasks each with their live occurrence count |

```kotlin
enum class DayCellState { OUTSIDE_RECORD, NOT_YET_ELAPSED, NOTHING_RECORDED, PARTLY_RECORDED, FULLY_RECORDED }
```

State derivation (FR-015, FR-016, FR-017a):

| Condition | State |
|---|---|
| date < record start | `OUTSIDE_RECORD` |
| date > today | `NOT_YET_ELAPSED` |
| `earned == 0` | `NOTHING_RECORDED` |
| `0 < earned < available` | `PARTLY_RECORDED` |
| `earned == available` | `FULLY_RECORDED` |

The first two are the states Principle IX makes load-bearing: neither is a day the user missed, and
each must be visually distinct from `NOTHING_RECORDED`, which is also not a failure.

### New: WeeklyScore

Derived, never stored. Three figures kept separate so no caller can divide by the wrong one.

| Field | Type | Rules |
|---|---|---|
| `earned` | `Int` | sum of the week's days' earned points |
| `elapsedAvailable` | `Int` | sum of available over dates ≤ today that are in the record |
| `weekTarget` | `Int` | `elapsedAvailable` + projected available for dates > today |

| Property | Meaning |
|---|---|
| `fraction` | `earned / elapsedAvailable`; 0 when `elapsedAvailable` is 0 |

**Invariants**: `earned ≥ 0`; `earned ≤ elapsedAvailable`; `elapsedAvailable ≤ weekTarget`; and for
a fully elapsed week inside the record, `elapsedAvailable == weekTarget` (FR-009c).

`fraction` divides by `elapsedAvailable`, never `weekTarget` (FR-009a) — that single choice is what
stops a Sunday morning reading as 10% of a week.

### New: WeekSummary

What the screen renders. A `Week`, its `WeeklyScore`, and seven day cells.

| Field | Type |
|---|---|
| `week` | `Week` |
| `score` | `WeeklyScore` |
| `days` | `List<DayCell>` — exactly 7, in week order |

`DayCell` carries `date`, `hijriLabel?`, `earned`, `available`, `state`. The label is null only for
`OUTSIDE_RECORD` and `NOT_YET_ELAPSED`, which have no stored plan to take one from.

### New: RecordStart

Not a type — a derived `LocalDate?` from `DayPlanRepository.earliestPlanDate()`. Null before any
plan exists. It is the floor for backfill (FR-012) and for week navigation (FR-018), and after R1 it
is the **only** floor.

---

## Part 2 — Room entities (`:data`)

### Changed: `day_plans`

One column added. Schema 1 → 2.

| Column | Type | Notes |
|---|---|---|
| *(existing)* | | unchanged |
| `origin` | `TEXT NOT NULL DEFAULT 'OPENED'` | **new**; `'OPENED'` or `'BACKFILLED'` |

`TEXT` rather than an integer so the exported schema reads plainly in a diff and an unrecognised
value fails loudly rather than silently meaning something.

### Migration 1 → 2

```sql
ALTER TABLE day_plans ADD COLUMN origin TEXT NOT NULL DEFAULT 'OPENED'
```

Additive. No row rewritten, no figure touched, nothing dropped — non-destructive by construction,
which is the `develop-v1` → `main` release gate.

**The default is a fact, not a guess.** `002`'s only creation path is `ensurePlanFor` called for the
current date at launch or rollover, so no plan in a v1 database can be a backfill (FR-013e).

### New DAO queries

No new table. Every query below is covered by an index `002` already created (research.md R6).

**`DayPlanDao`**

| Query | Purpose | Index used |
|---|---|---|
| `plansBetween(start, end)` | the week's stored plans, one round trip | `day_plans.date` (unique) |
| `earliestPlanDate()` | `MIN(date)` — the record start | same |

**`CompletionDao`**

| Query | Purpose | Index used |
|---|---|---|
| `liveBetween(start, end)` | the week's live completions, one round trip | `completions.creditedDate` |

`liveBetween` filters `reversedAt IS NULL AND deletedAt IS NULL`, like every other read in that DAO.
A range query that forgot the tombstone filter would inflate a past week's earned total — visible
only as a number the user cannot reconcile with what they remember doing.

**`CatalogueDao`** — `versionEffectiveOn` changes (research.md R1): falls back to the lowest version
when no version's `effectiveFrom` is on or before the date. Still null when the table is empty.

### What is deliberately still absent

No `week_summaries` or `day_summaries` cache table — aggregates are computed (`docs/PLAN.md`,
Principle VIII). No `isCompleted` column. No streak table. No stored "week being viewed". No update
method on `DayPlanDao`, and none may be added.

---

## Part 3 — State transitions

**DayPlan** — one new entry path, same terminal state.

```text
(absent) --app open on that date (002)--------> created, origin = OPENED    (frozen)
(absent) --week viewed, date elapsed, in record--> created, origin = BACKFILLED (frozen)
created  --any write whatsoever----------------> FORBIDDEN
```

Both paths run through the same `ensurePlanFor`, so the two plans differ in exactly one field. A
date at or after today is never reachable by the second path (FR-010c).

**Week view**

```text
requested --elapsed dates all have plans--> rendered with final figures
requested --dates missing---------------> backfill --success--> rendered with final figures
                                                    --write fails--> not-loaded notice + retry
not-loaded --retry--> requested
```

There is no partial render (FR-014a) and no third outcome. Plans written before a failure survive
and are reused on retry (FR-014d).

**Catalogue version resolution** *(changed, R1)*

```text
date ≥ some version's effectiveFrom --> greatest such version
date < every version's effectiveFrom --> lowest version        (was: null)
no versions at all                   --> null                  (unchanged — a real absence)
```
