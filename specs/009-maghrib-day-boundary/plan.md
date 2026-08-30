# Implementation Plan: Maghrib-Anchored Day and Week Boundary

**Branch**: `spec/009-maghrib-day-boundary` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-maghrib-day-boundary/spec.md`

## Summary

The increment that makes constitution v2.0.0's redefined day real: the accountability day begins at
a calculated Maghrib rather than at local midnight, computed on-device from the person's location
through the single provider Principle VII requires.

Seven decisions carry it, and the first two shrink it by more than anything else in the plan.

1. **The week rule does not change at all.** This is the finding that reshapes the increment. Maghrib
   on Friday is the start of accountability-Saturday, so once `DayBoundary` maps instants to
   accountability dates correctly, "the Saturday on or before" is still exactly right in
   accountability-date space. `WeekBoundary` keeps its current implementation, and every consumer
   that works in dates rather than instants — `WeekViewModel`, `GetHistoryPage`, `BuildPersonalBests`,
   `LeaderboardPeriod.periodFor` — needs **no change whatsoever**. FR-018, FR-019, FR-020 and FR-032a
   are satisfied by the day mapping alone (research R1).
2. **There is exactly one production call site to change.** `DayBoundary.dateAt` is called in
   production from precisely one place: `SystemTimeProvider.today()`. Everything else in the app —
   every ViewModel, every use case — reaches the date through `TimeProvider.today()`. Principle VII's
   single-home discipline actually held across eight increments, so the blast radius of redefining
   the day is one function and its provider (research R2).
3. **`today()` stays synchronous, backed by resolved state held in memory.** This is the crux. The
   Maghrib boundary needs coordinates, which live in Room, which makes resolution asynchronous — but
   `today()` is called synchronously from more than thirty places and FR-015 forbids it ever blocking.
   So the boundary provider holds its resolved state (coordinates, the regime in force, the current
   accountability date and the instant it next changes) in memory, refreshed off the UI path, and
   `today()` is a field read. No call site changes signature, and a boundary that cannot be resolved
   returns the fallback immediately rather than waiting (research R3).
4. **The calculation lives in `:data`; the rule lives in `:domain`.** A third-party calculation engine
   is exactly what `:domain` may not depend on (Principle II). So `:domain` declares the provider
   interface and holds `DayBoundary` as a pure function that takes the Maghrib instant as a
   *parameter*, and `:data` supplies it. The boundary rule is therefore testable by passing a literal
   instant, with no location service and no astronomical calculation anywhere near a domain test —
   which is what FR-004 asks for (research R4, R5).
5. **The region that selects a calculation convention is derived from the IANA time zone id.** It is
   already in hand via `TimeProvider.zone()`, costs nothing, needs no permission, and requires no
   network — which FR-003b demands and which a reverse-geocoding call would violate outright. It is
   also the *same* signal FR-012b uses to invalidate stale coordinates, so one input serves both rules
   rather than two independent mechanisms disagreeing (research R6).
6. **The seam is handled by a monotonic clamp, not by a migration — and the clamp is armed only at
   the seam.** No stored day is rewritten (FR-021). Instead the boundary provider remembers the last
   accountability date it resolved *and the regime it resolved it under*; when the regime changes it
   refuses to move backwards or to jump more than one day forward. That makes FR-022 and FR-023
   structural — a skipped or duplicated date is unrepresentable across a transition — and it covers
   all four uniformly: the initial changeover, the first location fix, an erase, and a zone-change
   invalidation. Within an unchanged regime the computed date is adopted as-is, because an always-on
   clamp would hand a returning person a date days behind, credit their completions to a day that
   has already closed, and make the answer depend on launch frequency — which FR-017 forbids
   (FR-023a, research R7).
7. **The streak's 20:00 rule is replaced, not adjusted.** A fixed wall-clock at-risk time cannot
   survive a day whose length and endpoints move with the seasons: in winter a day beginning at 17:00
   Maghrib would be "at risk" three hours in. It becomes a fixed offset before the day's *end*, which
   is what FR-029 requires and what keeps `StreakClock` a single home rather than a special case
   (research R8).

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (unchanged)

**Primary Dependencies**: Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01, coroutines 1.10.2,
`androidx.work:work-runtime-ktx`, supabase-kt — all already present. **One new dependency**:
`com.batoulapps.adhan:adhan` 1.2.1 (MIT), the Java Adhan port — a pure prayer-time calculation
library — added to `:data` only. The Kotlin rewrite `adhan2` was tried first and rejected: its Kotlin
2.4.0 metadata is unreadable by this project's Kotlin 2.2.10 and `:data` would not compile (R5).
It is not a network surface, so the constitution's new-network-surface justification does not apply;
the dependency justification is in [research.md](./research.md) R5. Device location uses the platform
`LocationManager`, not Play Services — no Google Play Services dependency is introduced.

**Storage**: Room remains the single local source of truth. **Migration 4 → 5, purely additive**: one
new single-row table, `boundary_state`, holding the last known coordinates, the zone id they were
obtained under, the last resolved accountability date and the regime it was resolved under, and the
first-launch prompt state. No column is dropped, renamed or rewritten; no existing row is touched.
Schema exported to `data/schemas/…/5.json` and committed. **No remote schema change** (FR-032b).

**One pre-existing defect is repaired here, because this feature cannot ship over it.**
`MIGRATION_3_4` is declared in `MizanDatabase.kt` and passed to nothing:
`MizanDatabaseFactory.createMizanDatabase` registers `MIGRATION_1_2` and `MIGRATION_2_3` only, so an
installed database at version 3 has no registered path forward and Room throws on open. Room runs
only the migrations handed to `addMigrations`, so `MIGRATION_4_5` would be dead code beside it. This
increment registers both. It is a one-line change to an existing call, introduces no schema change of
its own, and is the smallest fix that makes this feature's own migration reachable — leaving it
would mean shipping a migration that never runs.

**Testing**: JUnit 4. `:domain` and `:app` on the JVM; `:data` instrumented for the migration, the
boundary state store and the changeover clamp. Domain boundary tests take the Maghrib instant as a
literal parameter — no fake location service is needed to test the rule itself, only to test the
provider that feeds it. Compose UI tests for the first-launch prompt and the settings surface.

The test doubles live **once per consuming source set**, not in a shared module. `:domain`'s test
sources are not on `:data`'s androidTest classpath or `:app`'s test classpath —
`data/build.gradle.kts` takes `api(project(":domain"))`, which is main only — and this project has no
`java-test-fixtures` setup. `DbTestBase` already duplicates its own `TestTimeProvider` for exactly
this reason, so `FakePrayerTimes` and `FakeLocationSource` follow that precedent rather than
introducing a test-fixtures build change this increment does not otherwise need.

**Target Platform**: Android, `minSdk 24`, `compileSdk 36`, `targetSdk 36`

**Performance Goals**: SC-010 — `today()` returns in constant time whether or not a location is
available, because it is a field read over resolved state and never touches Room, the location
service, or the calculation. SC-004 — a fresh install in airplane mode reaches a usable Today screen
with no location wait at all.

**Constraints**: no network call on any path in this feature (FR-002, FR-003b, Principle IV).
`:domain` keeps zero Android, zero `kotlinx-datetime`, zero Adhan on its classpath (Principle II).
Exactly one production call site resolves an instant to a date, and exactly one reads location or
computes a prayer time (Principle VII, SC-013). No already-closed day, week or leaderboard period is
recomputed (Principle III, FR-021). No pressure or guilt in the location prompt or the declined state
(Principle IX, FR-007e).

**Scale/Scope**: 1 Room migration, 1 new local table, 1 migration-registration fix, 3 new domain
interfaces (`PrayerTimesProvider`, `LocationSource`, `BoundaryStatus`), 2 pure domain functions
changed, 1 replaced (`StreakClock`), 1 new first-launch surface, 1 settings section, 58 functional
requirements, 19 success criteria. **Zero changes** to `WeekBoundary`, `WeekViewModel`,
`GetHistoryPage`, `BuildPersonalBests`, `LeaderboardPeriod`, or any remote artifact.

**Five decisions settled by `/speckit-clarify` on 2026-08-30**, each folded through the design rather
than left in the spec alone:

1. The leaderboard is narrowed to period timing only — and R1 then showed even that needs no code
   change, because periods are computed in accountability-date space already.
2. Retained coordinates survive permission revocation, disclosed and erasable (FR-017a–d).
3. The calculation convention is region-derived, not global — which required constitution **v2.0.1**
   before this plan could pass its own Constitution Check (FR-003d, now satisfied).
4. Coordinates are invalidated by a time-zone change, never by age (FR-012a–e).
5. The location request is non-blocking on first launch behind an explicit opt-in (FR-007a–e).

## What the codebase already provides

Verified against the working tree on this branch, not against documents.

| Needed by this feature | Status |
|---|---|
| Single clock seam | `TimeProvider` / `SystemTimeProvider`; only `Instant.now()` and `ZoneId.systemDefault()` in the app |
| Single instant→date rule | `DayBoundary.dateAt`, one production caller |
| Week rule in date space | `WeekBoundary`, already correct under the new day mapping (R1) |
| Fake clock for tests | `FakeTimeProvider` (`:domain` test), `DbTestBase` (`:data` androidTest), `FakeRepositories` (`:app` test) |
| Zone-change reaction | `ReconcileZone` use case — existing precedent for acting on a zone change |
| Device-local singleton table | `account_scope` (`id INTEGER PRIMARY KEY`), a non-synchronised settings row |
| Additive-migration discipline | `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, all purely additive, schemas exported |
| Settings surface | `ProfileScreen` / `ProfileUiState` / `ProfileViewModel` |
| Immutable day record | `DayPlanRepository` has no update method by construction |

## Constitution Check

*GATE: evaluated against constitution v2.0.1. Re-checked after Phase 1 design — result unchanged.*

| Principle | Touched | How this plan complies |
|---|---|---|
| **I. Test-First** (non-negotiable) | Yes | Every boundary rule, the clamp, the regime selection, the convention mapping and the at-risk offset is a pure function with its test written first. `tasks.md` must order every test task before its implementation task. `DayBoundary` taking the Maghrib instant as a parameter (R4) is what makes this cheap — the rule is testable with literals. |
| **II. Domain Purity** | Yes | Adhan goes in `:data` only. `:domain` gains an interface and pure functions over `java.time`, which it already uses. `:domain`'s build file gains nothing. The Java port brings no `kotlinx-datetime`, so that dependency never enters the project at all. |
| **III. Immutable History** (non-negotiable) | Yes | FR-021 forbids recomputation and the design has no code path that could: `DayPlanRepository` still exposes no update method, and the seam is handled by clamping *future* resolution rather than by rewriting stored rows. FR-025's immutability test is task-listed and gates completion. |
| **IV. Offline-First** | **Yes — see below** | Addressed directly, not routinely. |
| **V. Backend Independence** | Yes | `boundary_state` is device-local and deliberately **not** synchronisable — coordinates must not leave the device (FR-006), and multi-device boundaries are per-device by design. It therefore falls outside Principle V's synchronisable-row rules, on the same footing as `account_scope`. No repository interface changes shape. |
| **VI. Fixed Content** | Yes | The region-to-convention mapping is administrator-defined seed content, versioned like the catalogue, with no user authoring and no per-person setting (FR-003a). Erasing coordinates is a privacy control, not content authoring. |
| **VII. Deterministic Time** | Yes | This feature *is* Principle VII. One provider reads location and computes prayer times; one function maps instant→date; `WeekBoundary` stays the one week rule. Fallback is explicit and specified (FR-012–FR-017), never undefined. |
| **VIII. Vertical Slices** | Yes | Ships a working, user-visible capability: the day turns over at Maghrib, with a prompt, a settings surface, and a stated regime. The provider is built because *this* increment needs it, not because 009 will. |
| **IX. No Shame** | Yes | FR-007e governs the prompt and the declined state; SC-018 reviews every string introduced. Declining is a supported way to use the app. |

### Principle IV — addressed directly, as the constitution requires

Constitution v2.0.1's Principle VII carries a recorded tension: a device's own midnight is computable
from nothing, while a Maghrib boundary depends on a resolved location and a successful calculation.
It requires any plan introducing this provider to confront that rather than tick the box. Four things
make it hold here.

- **The dependency is on coordinates, not on connectivity or on a live fix.** Prayer times are an
  astronomical calculation, so a single obtained location makes Maghrib computable for every future
  date, forever, offline. Offline days do not reach the fallback at all — only three situations do:
  before the first fix ever, after the person erases the coordinates, and after a zone change with no
  fresh fix (FR-012, SC-005).
- **Nothing sits in front of the core loop.** FR-007a requires the app to render and record on first
  launch with no location, no permission decision and no dialog. The permission dialog is raised only
  on an explicit opt-in (FR-007c). A fresh install in airplane mode is fully usable — SC-004 tests
  exactly that.
- **The fallback is the boundary the app already shipped**, not a degraded new one. A person who
  never grants location keeps precisely today's behaviour, which is why FR-013 reuses local midnight
  and Saturday-to-Friday rather than inventing a third rule.
- **Unresolvability is a value, not an exception.** The provider returns an explicit outcome and
  `today()` reads resolved state, so an unavailable location produces the fallback immediately —
  never a block, a retry loop, a crash, or an undefined result (FR-015, R3).

The residual cost is real and is accepted rather than solved: a person who never grants location
never gets the Islamic day boundary this increment exists to build. FR-016 requires the app to say so
plainly instead of hiding it.

### Complexity Tracking

No principle is violated, so this section records no accepted violations. Two items are noted because
a reviewer will otherwise ask.

| Item | Why it is not a violation |
|---|---|
| A new third-party dependency (`com.batoulapps.adhan:adhan`) | Not a network surface, so the constitution's networking constraint does not apply. Principle II is satisfied by confining it to `:data`. The alternative — hand-writing a solar-position calculation — is more risk, not less, and is recorded in R5. |
| `boundary_state` is not sync-ready | Principle V governs *synchronisable* rows. These are deliberately device-local: FR-006 forbids transmitting coordinates, and each device resolves its own boundary by design. `account_scope` is the existing precedent. |

## Project Structure

### Documentation (this feature)

```text
specs/009-maghrib-day-boundary/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── boundary-provider.md
│   ├── prayer-times-provider.md
│   ├── region-conventions.md
│   └── ui-state.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
domain/src/main/kotlin/com/giraffe/mizanapp/domain/
├── time/
│   ├── DayBoundary.kt          # CHANGED — takes the Maghrib instant, plus a fallback branch
│   ├── WeekBoundary.kt         # UNCHANGED (R1)
│   ├── TimeProvider.kt         # unchanged signature; today() still synchronous
│   ├── BoundaryRegime.kt       # NEW — MAGHRIB | FALLBACK, and why
│   ├── BoundaryState.kt        # NEW — resolved state, incl. last resolved date
│   ├── ResolveBoundaryDate.kt  # NEW — pure: regime + clamp (FR-022, FR-023)
│   └── BoundaryStatus.kt       # NEW — observable regime, for FR-016/FR-012d disclosure
├── prayer/
│   ├── PrayerTimesProvider.kt  # NEW — interface; three explicit outcomes
│   ├── LocationSource.kt       # NEW — interface for the single location reader
│   ├── Coordinates.kt          # NEW
│   ├── CalculationConvention.kt# NEW — plus SelectedConvention (convention + Asr madhab)
│   └── ConventionForRegion.kt  # NEW — pure: zone id → SelectedConvention, with default
└── streak/
    └── StreakClock.kt          # CHANGED — offset before day end, not 20:00 (FR-029)

data/src/main/kotlin/com/giraffe/mizanapp/data/
├── time/
│   ├── SystemTimeProvider.kt   # CHANGED — delegates to resolved boundary state
│   └── BoundaryStateStore.kt   # NEW — Room-backed, refreshed off the UI path
├── prayer/
│   ├── AdhanPrayerTimes.kt     # NEW — the only Adhan caller in the app
│   └── AndroidLocationSource.kt# NEW — the only location reader; COARSE only
└── db/
    ├── Migrations.kt           # MIGRATION_4_5, purely additive
    ├── MizanDatabase.kt        # version 5
    └── entities/BoundaryStateEntity.kt  # NEW

app/src/main/java/com/giraffe/mizanapp/
├── today/                      # first-launch location prompt (FR-007b)
└── profile/                    # location section: regime, retention, erase (FR-016, FR-017b/c)
```

**Structure Decision**: the existing three-module layout is unchanged. The feature adds one domain
package (`prayer`), extends another (`time`), adds two `:data` packages, and touches two existing
`:app` screens. No new Gradle module is introduced — nothing here needs substituting independently,
and Principle VIII forbids the abstraction otherwise.

## Phase 0 — Research

Complete. Nine questions settled in [research.md](./research.md): the week-rule finding (R1), the
single-call-site finding (R2), synchronous `today()` over resolved state (R3), the boundary function
shape (R4), the calculation library (R5), on-device region resolution (R6), the changeover clamp
(R7), the at-risk offset (R8), and the location API choice (R9).

No `NEEDS CLARIFICATION` markers remain in Technical Context.

## Phase 1 — Design & Contracts

Complete. [data-model.md](./data-model.md) covers the domain types, the one new Room table and the
additive migration. [contracts/](./contracts/) covers the two provider interfaces, the
region-to-convention mapping format, and the UI state for the prompt and settings section.
[quickstart.md](./quickstart.md) is the validation guide.

Constitution Check re-evaluated after design: unchanged, all nine principles pass.
