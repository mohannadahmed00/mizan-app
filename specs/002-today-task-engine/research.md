# Phase 0 Research: Today Screen — Local Task Engine

**Feature**: 002-today-task-engine | **Date**: 2026-08-09

Seven unknowns. All resolved. One (R4) narrows the roadmap's stated scope and is recorded in
plan.md's Complexity Tracking.

---

## R1 — What kind of module is `:domain`?

**Decision**: a pure Kotlin JVM library — `kotlin("jvm")`, not `com.android.library`.

**Rationale**: Principle II says `:domain` must have zero dependency on Android, Room, Retrofit,
Compose, or Koin annotations. As an Android library that is a rule someone has to keep remembering.
As a JVM module it is a compile error: the Android SDK is not on the classpath, so `import android.*`
cannot resolve. The strongest available enforcement, and it costs nothing — Android modules consume
JVM Kotlin libraries normally.

It also retires a test. `001`'s `DomainPurityTest` scanned source text for `import android.`; that
becomes redundant. It is replaced by a single assertion that `:domain`'s build file applies
`kotlin("jvm")` and not `com.android.library`, which guards the guarantee rather than its symptoms.

**Alternatives considered**:

- *`:domain` as an Android library, purity enforced by the text-scanning test from `001`.* Rejected:
  keeps a convention where a constraint is available.
- *No `:domain` module; keep everything in `:app` with package discipline.* Rejected: Principle II
  and `docs/PLAN.md` Decision 9 both require the boundary, and Phase 7's backend swap depends on it.

**Cost**: `:domain` cannot hold Android resources or read assets. Resolved by R3.

---

## R2 — `java.time` on `minSdk 24`

**Decision**: enable core library desugaring in every module that compiles for Android.

**Rationale**: this is not optional and is easy to miss. `java.time.LocalDate` and
`java.time.DayOfWeek` require API 26. The catalogue model written in `001` uses both, and until now
it has only ever run under JVM unit tests where that is irrelevant. Moving it into `:domain/src/main`
puts it on a device for the first time, where `minSdk 24` means API 24 and 25 devices exist.

Verified against the current build: `minSdk = 24`, `sourceCompatibility = VERSION_11`, and no
desugaring configured. Without it, this feature crashes on two API levels it claims to support.

**Alternatives considered**:

- *Raise `minSdk` to 26.* Rejected: drops real devices to avoid a two-line build change.
- *Use `kotlinx-datetime` instead.* Rejected: adds a dependency, and the `001` model plus its
  validator and fixtures are already written against `java.time`. Rewriting working, tested code to
  dodge a build flag is the wrong trade.
- *Use `Long` epoch days and hand-roll weekday maths.* Rejected outright: date arithmetic by hand is
  precisely the class of bug Principle VII exists to prevent.

---

## R3 — Where does the catalogue seed live?

**Decision**: `:domain/src/main/resources/catalogue/catalogue-v1.json`, read by `:data`'s seeder via
the classloader.

**Rationale**: it must be one file, reachable from both JVM tests and the Android runtime. A JVM
classpath resource is exactly that — `:domain`'s own tests validate it with the `001` contract, and
`:data` reads the identical bytes at seed time on device. Android assets would have satisfied only
the second, forcing either a duplicate copy or a device test to validate content.

`:domain` contains no code that reads it. The resource is declared there because that is where the
catalogue is *defined* and validated; reading is I/O and belongs to `:data`.

**Alternatives considered**:

- *`:app/src/main/assets/`.* Rejected: unreadable from `:domain`'s JVM tests, so the `001` contract
  could no longer validate the shipped seed.
- *`:data/src/main/assets/`.* Same objection.
- *Kotlin source constant in `:domain`.* Rejected: not diffable as data, and it would bypass the
  parse path that the contract exists to exercise.

---

## R4 — Hijri date: computed or fetched?

**Decision**: computed locally with `java.time.chrono.HijrahChronology`. **No Retrofit. No network
surface in this feature at all.**

**Rationale**: `docs/PLAN.md` Phase 2 assumes an API-synced conversion and states Retrofit is
introduced here. That assumption predates this analysis, and following it would make the increment
strictly more complex:

| | Local conversion | Network fetch |
|---|---|---|
| New dependencies | none | Retrofit, converter, DTOs |
| Failure modes | none | timeout, offline, malformed, rate limit |
| Principle IV | satisfied by construction | needs explicit guarding |
| FR-009a fill-once machinery | unnecessary — a value always exists | required |
| US4 offline scenarios | cannot arise | must be built and tested |

Desugaring is already mandatory for R2, and it covers `java.time.chrono` including
`HijrahChronology`. The label is therefore always available, offline, on first launch, with no
waiting.

**What this costs**: the Umm al-Qura calendar that `HijrahChronology` implements can differ by a day
from a particular local observational authority. Nothing in the spec or constitution requires
agreement with one — the constitution is explicit that the Hijri date "is a label attached to a day,
never the thing that defines the day's boundaries", and no score or boundary depends on it.

**If agreement is later wanted, it is additive.** The label is already stored as a per-day snapshot,
so a future increment can reconcile or override it without touching this design.

**Effect on the spec — resolved 2026-08-09.** The author accepted the simplification. US4's
offline-degradation scenarios and the fill-once machinery of FR-009a were removed rather than left
dead: FR-009 now states the label is computed at creation, FR-009a that it needs no network, and
FR-009b that it is written exactly once and never revised. `attachHijriLabel` and the nullable
column are gone, which makes `DayPlan` immutable with no exception at all.

**Alternatives considered**:

- *Retrofit against a Hijri conversion API.* Rejected on the table above.
- *Bundle a conversion table.* Rejected: reimplements the standard library.
- *Omit the Hijri label entirely this increment.* Rejected: it is a stated user-facing feature and
  now costs almost nothing.

---

## R5 — Occurrence counting with tombstones

**Decision**: the recorded occurrence count for a task on a date is the number of completion rows
for that task and date **where the soft-delete marker is null**. Every read path — the counter, the
score, the limit check — applies the same filter.

**Rationale**: clarification Q4 requires undo to free exactly one slot. Undo writes a tombstone
rather than deleting, so a count over all rows would lock a nine-occurrence task at 9/9 after one
mistaken tap. Filtering at every read keeps the tombstone invisible to the user, which FR-014 now
states explicitly.

Enforced at the DAO level rather than by convention: the DAO exposes no query returning tombstoned
completions to anything outside the sync path, which does not exist yet.

**Alternatives considered**:

- *Hard delete on undo.* Rejected: Principle V requires a tombstone for synchronisable rows.
- *A running counter column on the planned task.* Rejected: a second source of truth for something
  derivable, and it would drift.

---

## R6 — Where do day plans come from, and when?

**Decision**: `BuildDayPlan` is a pure function in `:domain` taking a date, a catalogue, and a Hijri
label, returning a `DayPlan` with its `PlannedTask`s. `:data` persists it. The trigger is
application start and date rollover (clarification Q3), owned by a single coordinator so no screen
can create a plan as a side effect of being shown.

**Rationale**: keeping construction pure makes the whole of Principle III testable without a
database — an entire class of "does a catalogue change alter a recorded day" test runs as a JVM unit
test in milliseconds. Persistence then only has to be shown to store and return what it was given.

**Alternatives considered**:

- *Build the plan inside the repository.* Rejected: puts scoring-adjacent logic in `:data`, where
  Principle II says it may not live.
- *Build lazily on first read of the day.* Rejected by clarification Q3.

---

## R7 — Testing the Room layer

**Decision**: `:data` DAO tests are instrumented tests running on a device or emulator against real
SQLite. `:domain` and `:app` ViewModel tests are JVM unit tests.

**Rationale**: `docs/PLAN.md` calls for Room instrumentation tests, and the properties being checked
— that a day plan cannot be updated, that migrations are non-destructive, that a tombstone survives
a round trip — are properties of the real database engine. Testing them against a substitute proves
less than it appears to.

**Cost, stated plainly**: this is the first part of the project that cannot be verified by
`./gradlew test` alone. A device or emulator is required, and CI will eventually need one.

**Alternatives considered**:

- *Robolectric for JVM Room tests.* Faster and device-free, but adds a dependency and tests against
  a reimplementation of the engine whose exact behaviour is the thing under test.
- *Skip DAO tests; cover through the ViewModel.* Rejected: Principle I requires persistence
  behaviour to have a test before the schema that satisfies it.

---

## Resolved unknowns summary

| # | Unknown | Resolution |
|---|---|---|
| R1 | `:domain` module type | `kotlin("jvm")` — purity enforced by the compiler |
| R2 | `java.time` on minSdk 24 | Core library desugaring, mandatory |
| R3 | Seed location | `:domain/src/main/resources/`, read by `:data` |
| R4 | Hijri source | Computed locally; **no network in this feature** |
| R5 | Occurrence counting | Count rows with null soft-delete marker, at every read |
| R6 | Day plan construction | Pure function in `:domain`; trigger on start and rollover |
| R7 | Room testing | Instrumented, on device; JVM elsewhere |

No `NEEDS CLARIFICATION` markers remain. R4's effect on US4 was flagged for the author and
**resolved on 2026-08-09**: the simplification was accepted and the dead machinery removed from the
spec rather than left in place.
