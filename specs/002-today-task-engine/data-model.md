# Phase 1 Data Model: Today Screen — Local Task Engine

**Feature**: 002-today-task-engine | **Date**: 2026-08-09

Two layers. Domain models are pure Kotlin in `:domain`; Room entities are their persisted form in
`:data`, connected by mappers that are written test-first like anything else.

---

## Part 1 — Domain models (`:domain`, pure Kotlin)

### Carried over from `001`, unchanged

`Catalogue`, `CatalogueVersion`, `Section`, `TaskDefinition`, `TaskVersion`, `ScheduleRule`,
`CatalogueDefect`, `CatalogueValidator`. These move from `:app`'s test source set into
`:domain/src/main` as a file move — no behaviour change, and the `001` test suite moves with them.

### DayPlan

The frozen record of one date. Written once.

| Field | Type | Rules |
|---|---|---|
| `id` | `String` (UUID) | client-generated, stable |
| `date` | `LocalDate` | unique; the accountability date |
| `catalogueVersion` | `Int` | the version in effect when created |
| `hijriLabel` | `String` | non-null; computed from `date` at creation, never revised |
| `availablePoints` | `Int` | sum over planned tasks of `points × maxOccurrences` |
| `plannedTasks` | `List<PlannedTask>` | non-empty |

**Invariant**: after creation, **no field may change — without exception**. The label is computed
locally (research.md R4), so it is present from the moment the plan exists and there is no
fill-later path to keep open. No repository method, DAO method, or use case may update a day plan.

### PlannedTask

One task as it stood on one date. Carries everything needed to render and score the day without
consulting the live catalogue.

| Field | Type | Rules |
|---|---|---|
| `id` | `String` (UUID) | client-generated |
| `dayPlanId` | `String` | resolves to a `DayPlan` |
| `taskSlug` | `String` | the catalogue slug, snapshotted |
| `sectionId`, `sectionLabel`, `sectionOrder` | `String`, `String`, `Int` | snapshotted |
| `displayPosition` | `Int` | unique within its section on that day |
| `label` | `String` | snapshotted — the day must render without the catalogue |
| `points` | `Int` | > 0; what one occurrence was worth that day |
| `maxOccurrencesPerDay` | `Int` | ≥ 1 |

Snapshotting the label as well as the numbers is deliberate. FR-017 already requires available
points to come from the plan rather than the catalogue; a day that could render its numbers but not
its text would still depend on live content to be readable.

### Completion

One recorded occurrence. Append-only; undo writes a tombstone.

| Field | Type | Rules |
|---|---|---|
| `id` | `String` (UUID) | client-generated |
| `dayPlanId` | `String` | resolves to a `DayPlan` |
| `taskSlug` | `String` | resolves to a `PlannedTask` on that plan |
| `creditedDate` | `LocalDate` | the accountability date; equals the plan's date |
| `pointsAwarded` | `Int` | denormalised at write time; never recomputed |
| `recordedAt` | `Instant` | when the user acted, from `TimeProvider` |
| `reversedAt` | `Instant?` | null means live; non-null is a tombstone |

**Counting rule (research.md R5)**: occurrences, scores and limit checks count only rows where
`reversedAt` is null. Applied at every read path without exception.

### DailyScore

Derived, never stored.

| Field | Type | Rules |
|---|---|---|
| `earned` | `Int` | sum of `pointsAwarded` over live completions |
| `available` | `Int` | from the plan |
| `fraction` | `Float` | `earned / available`; 0 when available is 0 |

**Invariants**: `earned ≥ 0` and `earned ≤ available` (FR-018). The second holds because a task
cannot exceed its occurrence limit and each occurrence awards exactly the planned points.

### Supporting domain types

| Type | Purpose |
|---|---|
| `TimeProvider` | The only source of `now(): Instant`, `today(): LocalDate`, `zone(): ZoneId`. Faked in tests. |
| `DayBoundary` | Local midnight to local midnight. The single place this rule exists (FR-030). |
| `HijriLabel` | Civil date → Hijri label, computed locally. No I/O. |
| `DayWritePolicy` | Decides whether a date accepts writes. Today only, this increment. Phase 5 widens it here and nowhere else. |
| `LandingSection` | Earliest section containing an incomplete task, else the first (FR-020b). Pure; derived on every open, never stored. |

---

## Part 2 — Room entities (`:data`)

Schemas exported to `data/schemas/` and committed. No destructive migration in any build a user has
installed.

### Sync-ready columns

Per Principle V and `docs/PLAN.md` Decision 6, every **synchronisable** row carries:

| Column | Type | Purpose |
|---|---|---|
| `id` | `TEXT` PK | client-generated UUID, never auto-increment |
| `updatedAt` | `INTEGER` | last-modified, epoch millis |
| `deletedAt` | `INTEGER?` | tombstone marker; null means live |
| `userId` | `TEXT?` | nullable until Phase 7 |

Synchronisable: `day_plans`, `planned_tasks`, `completions`, `task_versions`.
Not synchronisable: `sections`, `task_definitions`, `catalogue_versions` — administrator content
that arrives from the catalogue, replaced wholesale by version rather than merged.

### Tables

| Table | Primary key | Notes |
|---|---|---|
| `sections` | `id` TEXT | from the seed |
| `task_definitions` | `slug` TEXT | from the seed; slug is the natural key (Decision 5) |
| `catalogue_versions` | `version` INTEGER | with `effectiveFrom` |
| `task_versions` | `id` TEXT | `taskSlug` + `catalogueVersion` unique together |
| `day_plans` | `id` TEXT | `date` unique |
| `planned_tasks` | `id` TEXT | FK `dayPlanId`; unique (`dayPlanId`, `taskSlug`) |
| `completions` | `id` TEXT | FK `dayPlanId`; indexed on (`creditedDate`, `taskSlug`, `deletedAt`) |

**Schedule rule storage**: `task_versions` stores the rule as a discriminator plus a nullable
day-set — `scheduleType` TEXT (`everyDay` / `daysOfWeek`) and `scheduleDays` TEXT holding a
comma-separated `DayOfWeek` name list. A sealed type does not map to a column, and a reserved
`dateAnchored` variant must be addable later without a destructive migration (Decision 7).

**Indices**: `day_plans(date)`, `completions(creditedDate)`, `completions(dayPlanId, taskSlug)`.
Justified by the queries in contracts/repositories.md, not added speculatively.

### What is deliberately absent

No `isCompleted` column anywhere (Decision 4). No cached score, streak, or day-summary table —
those are derivable, and `docs/PLAN.md` defers caching until measurement demands it. No outbox, no
sync state beyond the four columns above.

---

## Part 3 — State transitions

**DayPlan**

```text
(absent) --app start or rollover, no plan for date--> created (frozen)
created --any write whatsoever--> FORBIDDEN
```

**Completion**

```text
(absent) --user completes, under limit--> live
live --user undoes most recent--> reversed (tombstone)
reversed --> terminal; never revived, never counted, never shown
```

A reversed completion frees exactly one occurrence slot (FR-013a) because slots count live rows
only, not because the row is restored.

**Catalogue**

```text
(empty) --seed on first launch--> version N present
version N --seed again, same version--> unchanged (idempotent, FR-001)
version N --new version arrives--> version N+1 added; N retained for past days
```

Older versions are never deleted. A day plan created under version N must remain explicable after
N+1 arrives, which is the whole of Principle III.
