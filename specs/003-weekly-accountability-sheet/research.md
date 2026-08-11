# Phase 0 Research: Weekly Accountability Sheet

**Feature**: 003-weekly-accountability-sheet | **Date**: 2026-08-11

Seven questions. Each was resolved against the merged code on `develop-v1`, not against `002`'s
documents — two of `002`'s documented positions turned out not to match what shipped, and one of
those matters (R1).

---

## R1 — How does the earliest catalogue version apply to dates before its effective-from?

**Decision**: The earliest catalogue version applies **open-ended backwards**. `versionEffectiveOn`
returns the greatest version whose `effectiveFrom` ≤ date, and when no version qualifies it returns
the **lowest** version rather than null.

**Rationale**: This is the clarified decision from 2026-08-11, and the current implementation does
not satisfy it. The shipped DAO query is:

```sql
SELECT MAX(version) FROM catalogue_versions WHERE effectiveFrom <= :date
```

which returns null for any date before the earliest version. The seed ships version 1 with
`effectiveFrom = 2026-01-01`. So today the behaviour is: a skipped date before 2026-01-01 resolves
to no catalogue and `ensurePlanFor` returns `NoCatalogue`, silently refusing the backfill.

That produces exactly the failure the clarification rejected — two independent floors on backfill
(record start, and version effectivity) that can disagree. Making the earliest version open-ended
leaves the record start as the only floor, structurally.

The change is one query plus a fallback:

```sql
SELECT COALESCE(
  (SELECT MAX(version) FROM catalogue_versions WHERE effectiveFrom <= :date),
  (SELECT MIN(version) FROM catalogue_versions)
)
```

with null still returned when the table is empty — no catalogue at all is a real absence and must
stay distinguishable.

**This changes a documented contract.** `002`'s `contracts/repositories.md` guarantee 4 reads "or
null if the date precedes every version. Never guesses." That wording is superseded here: returning
the earliest version for an earlier date is not a guess, it is the stated rule that a catalogue
applies until superseded. `002`'s contract file is left as the historical record of what `002`
shipped; this feature's `contracts/repositories.md` carries the new wording.

**Cannot re-score anything.** The change affects only whether a plan can be *created* for a date
that previously resolved to nothing. Existing plans store their `catalogueVersion` and are never
re-resolved (`ensurePlanFor` returns early on an existing plan). Principle III is untouched.

**Alternatives considered**:

- *Leave it null and rely on record start.* Rejected — this is the "two floors" arrangement the
  clarification explicitly chose against. It works only as long as the first install postdates the
  seed's `effectiveFrom`, which is an accident, not a guarantee.
- *Backdate the seed's `effectiveFrom` to a sentinel like `0001-01-01`.* Rejected — `effectiveFrom`
  is catalogue-authored content validated by `001`, not a local marker. Editing content to
  compensate for a resolution rule puts the fix in the wrong layer, and a future server-provided
  catalogue would not carry the sentinel.

---

## R2 — Does the Day Plan origin field need a migration, and can it be non-destructive?

**Decision**: Yes, a migration; `origin TEXT NOT NULL DEFAULT 'OPENED'` on `day_plans`. Schema goes
1 → 2, both files exported and committed.

**Rationale**: `DayPlanEntity` has no such field and the database is at version 1 with one exported
schema. FR-011 requires it, and Phase 4's streak rule depends on it — after backfill, "a plan
exists" no longer means "the user was there".

The default is not a guess. `002`'s only plan-creation path is `ensurePlanFor` called for the
current date at launch or rollover, so every plan that can exist in a v1 database was created while
the app was open on that date (FR-013e). The migration is a pure `ALTER TABLE … ADD COLUMN` with a
constant default: no row is rewritten, no figure moves, and it is non-destructive by construction —
which is the release gate for `develop-v1` → `main`.

Stored as `TEXT` rather than an integer so the exported schema is readable in a diff and an
unrecognised value fails loudly instead of silently meaning something.

**Alternatives considered**:

- *Derive origin instead of storing it* (e.g. "a plan whose date is before the earliest completion
  was probably backfilled"). Rejected — it is a heuristic about user behaviour standing in for a
  fact the system knows at write time, and it would be wrong for any day genuinely opened and left
  empty, which the spec explicitly requires to be recordable.
- *A separate `plan_origins` table.* Rejected — a one-to-one table for one enum, and a join on every
  read, to avoid a migration that is already trivial.

---

## R3 — How do two more screens arrive without a navigation library?

**Decision**: No navigation dependency. `MainActivity` holds a `Destination` sealed interface in
`rememberSaveable` state, with `Today`, `Week`, and `DaySummary(date)`. Back from a summary returns
to the week; back from the week returns to Today.

**Rationale**: `002` ships one screen and no navigation dependency; `MainActivity` calls
`TodayRoute` directly. This increment needs two destinations and one parameter. That is a few lines
of hoisted state, and Principle VIII forbids introducing an abstraction for a need that is not
present.

`androidx.navigation:navigation-compose` is not in the version catalogue, so adopting it means a new
dependency, a nav graph, typed argument plumbing, and a back-stack model — none of which two screens
and one `LocalDate` argument require.

**When this stops being true**: the design in `CLAUDE.md` specifies a three-tab shell (Today,
Progress, Settings), which is Phase 5 or later territory. A tab shell with independent back stacks
per tab is where a hand-rolled destination stops being cheaper than a library. That is the moment to
adopt one — with the tabs, not before them. Recorded here so the decision is a deliberate revisit
rather than a rediscovery.

**Alternatives considered**:

- *Add `navigation-compose` now.* Rejected as premature under VIII — and it would have to be
  re-designed at the tab shell anyway, so it buys nothing that lasts.
- *Make the week a bottom sheet on Today.* Rejected — it implies the week is subordinate to the day,
  and it makes a per-day drill-in a sheet within a sheet.

---

## R4 — Where does backfill run, and what stops it running twice?

**Decision**: `GetWeekSummary` in `:domain/usecase/` performs backfill before aggregating, in a
single coroutine, and returns only when every elapsed date in the week has a plan. Idempotency
comes from `ensurePlanFor`'s existing pre-check plus `OnConflictStrategy.ABORT` on the insert and
the `UNIQUE` index on `day_plans.date`.

**Rationale**: FR-014a requires final figures at first paint, so backfill cannot be a background
job whose results arrive later. Sequencing it inside the use case keeps the whole "display a week"
rule in one testable place.

The uniqueness guarantee is already in the schema — `@Entity(indices = [Index(value = ["date"],
unique = true)])` on `DayPlanEntity`. Two concurrent week opens racing on the same date means one
insert aborts; the correct response is to re-read that date's plan, not to fail the week (FR-010d).
The use case treats a constraint violation on insert as "someone else created it", which is exactly
the `AlreadyExists` outcome.

**Alternatives considered**:

- *Backfill in `WeekViewModel`.* Rejected — puts a Principle III-critical rule in `:app`, outside
  the layer where domain rules get test-first treatment, and invites a second screen to implement
  it differently.
- *Sweep all history at launch.* Rejected by the spec's own assumption: unbounded work on the launch
  path for no visible benefit, and it would make first launch after a long absence the slowest
  moment in the app.
- *A database transaction wrapping all seven inserts.* Rejected as unnecessary — each plan is
  independently valid, and a partial backfill is a correct intermediate state that the next open
  completes (FR-014d). Wrapping them would also hold a write transaction across seven Hijri
  computations.

---

## R5 — What proves the migration and the immutability promise?

**Decision**: `MigrationTestHelper` from `androidx-room-testing` (already in the version catalogue,
already used by `002`'s `:data` androidTest source set). Two tests, both written before the
migration:

1. **Migration integrity** — create a v1 database, insert a plan with planned tasks and completions,
   run `MIGRATION_1_2`, assert every original column value is unchanged and `origin` reads `OPENED`.
2. **Immutability across a catalogue change** — seed catalogue v1, create an opened plan and a
   backfilled plan, introduce catalogue v2 with different points and a changed schedule, then assert
   both stored plans report their original tasks and totals while a newly created plan reflects v2.

**Rationale**: The second test is mandatory, not optional — the constitution requires a
historical-immutability test for any increment touching persistence or the catalogue, and this
increment touches both. It is stronger than `002`'s equivalent because it must now hold for a plan
created by backfill under a *past* catalogue version, which is the case FR-013 introduces.

No new dependency: `androidx-room-testing` and `androidx-test-runner` are both present.

---

## R6 — Does a week aggregate need an index or a cache?

**Decision**: Neither. The existing indices cover it.

**Rationale**: The queries this feature adds are:

- plans where `date BETWEEN :start AND :end` — covered by the existing **unique index on
  `day_plans.date`**;
- live completions where `creditedDate BETWEEN :start AND :end` — covered by the existing index on
  `completions.creditedDate`;
- `MIN(date)` over `day_plans` — covered by the same unique index.

Both indices were added by `002` and justified there. A week is seven days; a year is 365 plans and
roughly 18,000 completions, and both range queries are index scans over a bounded slice.

`docs/PLAN.md` permits a materialised `DaySummary` cache "only if a real measurement shows the
aggregate query is slow". There is no measurement, so there is no cache — and Principle VIII forbids
building one on a guess. SC-013's 300 ms budget is the measurement that would justify revisiting
this, and it is a gate on `002`'s storage design rather than a licence to cache around it.

---

## R7 — How is the week's "elapsed available" computed without creating future plans?

**Decision**: Two different sources for two different halves of the week, never mixed:

- **Elapsed dates** (up to and including today) — available points come from the **stored plan**,
  which backfill guarantees exists. This is the headline denominator.
- **Not-yet-elapsed dates** — available points are **projected** by running the existing
  `resolveApplicableTasks` + point summation against the *current* catalogue version, in memory,
  writing nothing (FR-009d).

The week target is the sum of both. `WeeklyScore` carries `earned`, `elapsedAvailable` and
`weekTarget` as three separate fields so no caller can accidentally divide by the wrong one.

**Rationale**: FR-009d forbids persisting anything for a future date, and Principle III is the
reason — freezing a plan before its date arrives would record a day under a catalogue that might
change before the user gets there. Projection is safe precisely because it is not a record: it may
change if the catalogue changes, and nothing depends on it having been stable.

The projection reuses `002`'s pure `resolveApplicableTasks`, so a projected Monday and a materialised
Monday cannot disagree about which tasks apply — there is one applicability rule, not two.

**Alternatives considered**:

- *Show the week target only for fully elapsed weeks.* Rejected — the clarified decision is that the
  500 is context the user recognises and should be present throughout the week.
- *Materialise all seven plans on week open.* Rejected outright: it violates FR-010c and, worse,
  would freeze days that have not happened, which is the one Principle III failure that cannot be
  repaired afterwards.
