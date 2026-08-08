# Implementation Plan: Today Screen — Local Task Engine

**Branch**: `spec/002-today-task-engine` | **Date**: 2026-08-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-today-task-engine/spec.md`

## Summary

The core loop, and the increment where the project's module structure finally exists. Three modules
are created: `:domain` as a **pure Kotlin JVM library**, `:data` as an Android library holding Room,
and `:app` keeping Compose and the ViewModel.

Making `:domain` a JVM module rather than an Android library is the central technical decision here.
It turns Principle II from a rule someone has to remember into something the compiler enforces:
there is no Android SDK on that module's classpath, so `import android.*` cannot compile.

Two consequences worth stating up front. The catalogue model built in `001` moves from `:app`'s test
source set into `:domain/src/main`, which means it now runs on a device — and because it uses
`java.time` on `minSdk 24`, **core library desugaring becomes mandatory**. And the Hijri label is
computed locally rather than fetched, which removes the only network surface this feature would
have had. See research.md R4; it narrows the roadmap's Phase 2 scope and deserves a decision.

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11

**Primary Dependencies**: Room (persistence, exported schemas), Koin (DI, sole framework), Jetpack
Compose (UI), kotlinx.serialization (seed parsing, already present), coroutines + Flow. **No
Retrofit** — see research.md R4.

**Storage**: Room, offline-only, single source of truth. Exported schemas committed. No destructive
migration.

**Testing**: JUnit 4 throughout. `:domain` and `:app` on the JVM; `:data` Room DAO tests as
instrumented tests on a device or emulator.

**Target Platform**: Android, `minSdk 24`, `compileSdk 37`, `targetSdk 36`

**Project Type**: Android application, multi-module by layer

**Performance Goals**: A day holds ~32 planned tasks and at most ~50 completions. Every read is a
single-table query over tens of rows. No performance work is warranted; the only requirement is that
recording a completion never blocks on I/O the user can perceive (SC-008).

**Constraints**: `:domain` must have zero Android on its classpath, enforced structurally. Nothing
may sit on the path of viewing, recording, undoing, or scoring (Principle IV). Core library
desugaring is required for `java.time` below API 26.

**Scale/Scope**: One screen, three modules, ~10 Room entities and DAOs, 37 functional requirements.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.1.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | Order per layer: domain test → domain code → data test → data code → UI. Every scoring, applicability, occurrence and boundary rule gets a failing unit test first. Only Koin module wiring and `@Preview` composables claim the exemption; mappers do not. |
| **II — Domain purity** | Yes | `:domain` is a `kotlin("jvm")` module. No Android SDK on its classpath, so a framework import is a compile error rather than a review miss. Repository interfaces declared there, implemented in `:data`. |
| **III — Immutable history** | Yes | Day plans and planned tasks are written once and never updated. Completions carry `pointsAwarded` denormalised. The only permitted post-creation write to a day plan is filling a null Hijri label exactly once (FR-009a), which touches no figure. A DAO-level test asserts a catalogue change leaves recorded days untouched. |
| **IV — Offline-first** | Yes | No network at all in this feature. Room is the only source of truth. The app is fully functional on a fresh install in airplane mode. |
| **V — Backend independence** | Yes | Synchronisable rows (`day_plans`, `completions`, `task_versions`) carry client-generated UUIDs, `updatedAt`, a soft-delete marker and a nullable `userId`. Undo writes a tombstone. No backend type anywhere. |
| **VI — Fixed content** | Yes | The catalogue is seeded from a versioned resource, idempotently. No create/edit/delete/reorder surface exists in the UI or the DAOs. |
| **VII — Deterministic time** | Yes | A single `TimeProvider` in `:domain` is the only source of now, today, and zone. Day and week boundary rules live in one place each. Rollover is tested by advancing a fake clock. |
| **VIII — Vertical slices** | Yes | Four stories, all shipping together as one usable screen. `:domain` and `:data` are created because this increment needs them, not in anticipation. No table, interface, or configuration surface exists for anything beyond US1–US4. |
| **IX — Encouragement** | Yes | No negative figures possible (FR-018). Zero-progress states are neutral. Undo is always non-destructive (FR-013a). Copy and states audited against the design's audit list in `CLAUDE.md`. |

**Technology constraints**: Kotlin + Compose ✓. MVVM with one immutable state per screen as
`StateFlow`, no mutable state exposed ✓. Module direction `:app` → `:data` → `:domain`, and
`:domain` depends on nothing ✓. Koin sole DI, no Hilt/KSP introduced ✓. Room with exported schemas
and no destructive migration ✓. Arabic content as data, correct bidirectional rendering ✓ (v1.1.1
wording). **Retrofit: not introduced** — the constraint permits it for Hijri sync; this plan needs
no network at all, which is strictly less. No new network surface requires justification because
there is no new network surface.

**Gate result: PASS.** One deviation from `docs/PLAN.md` (not from the constitution) is recorded in
Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-today-task-engine/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── repositories.md
│   └── ui-state.md
├── checklists/requirements.md
├── spec.md
└── tasks.md             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

```text
settings.gradle.kts                      # include(":app", ":data", ":domain")

domain/                                  # kotlin("jvm") — no Android on the classpath
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/com/giraffe/mizanapp/domain/
    │   │   ├── catalogue/               # moved from :app test source set
    │   │   │   ├── Catalogue.kt  CatalogueVersion.kt  Section.kt
    │   │   │   ├── TaskDefinition.kt  TaskVersion.kt  ScheduleRule.kt
    │   │   │   └── CatalogueDefect.kt  CatalogueValidator.kt  CatalogueJson.kt
    │   │   ├── day/
    │   │   │   ├── DayPlan.kt  PlannedTask.kt  Completion.kt  DailyScore.kt
    │   │   │   ├── ResolveApplicableTasks.kt
    │   │   │   ├── BuildDayPlan.kt
    │   │   │   ├── ScoreDay.kt
    │   │   │   └── LandingSection.kt
    │   │   ├── time/
    │   │   │   ├── TimeProvider.kt      # the only source of now/today/zone
    │   │   │   ├── DayBoundary.kt       # local midnight to local midnight, one place
    │   │   │   └── HijriLabel.kt        # local conversion, no network
    │   │   ├── policy/
    │   │   │   └── DayWritePolicy.kt    # only today is writable; Phase 5 widens here
    │   │   └── repository/
    │   │       ├── CatalogueRepository.kt
    │   │       ├── DayPlanRepository.kt
    │   │       └── CompletionRepository.kt
    │   └── resources/catalogue/
    │       └── catalogue-v1.json        # the seed, single source of truth
    └── test/kotlin/…                    # JVM tests, incl. the 001 validator + fixtures

data/                                    # Android library — Room lives here
├── build.gradle.kts
├── schemas/                             # exported Room schemas, committed
└── src/
    ├── main/kotlin/com/giraffe/mizanapp/data/
    │   ├── db/  MizanDatabase.kt  entities/  daos/
    │   ├── seed/ CatalogueSeeder.kt
    │   ├── mapper/                      # entity <-> domain, test-first
    │   ├── time/ SystemTimeProvider.kt
    │   └── repository/                  # implementations of the three interfaces
    └── androidTest/kotlin/…             # Room DAO + immutability tests

app/
└── src/main/java/com/giraffe/mizanapp/
    ├── MizanApplication.kt              # Koin start
    ├── di/                              # Koin modules per layer (test-first exempt)
    └── today/
        ├── TodayViewModel.kt            # one immutable state, StateFlow
        ├── TodayUiState.kt
        └── TodayScreen.kt               # stepped flow, Arabic content
```

**Structure Decision**: three modules, `:domain` as a pure Kotlin JVM library.

The alternative — `:domain` as an Android library with a lint rule or a text-scanning test — was
rejected because it enforces Principle II by convention. A JVM module enforces it by construction:
the Android SDK is not on the classpath, so the violation cannot compile. The `DomainPurityTest`
written in `001` becomes redundant and is replaced by a one-line assertion that `:domain`'s build
file applies `kotlin("jvm")` and not `com.android.library` — guarding the guarantee itself rather
than its symptoms.

The cost is that `:domain` cannot hold Android resources or read assets. The seed therefore lives as
a **JVM classpath resource** in `:domain/src/main/resources/`, which works identically under JVM
tests and on a device, and is read by `:data`'s seeder rather than by `:domain` itself.

## Constitution Re-Check (post-Phase 1 design)

Design introduced four things absent at the first gate. Each re-checked:

| Introduced | Principle at risk | Verdict |
|---|---|---|
| Core library desugaring | Technology constraints | **Pass.** A build flag, not a dependency or a network surface. Mandatory rather than optional: `java.time` on `minSdk 24` would otherwise crash on API 24–25 (research.md R2). |
| `:domain` as a JVM module | II | **Pass, and strengthens it.** Purity moves from convention to compile error. `001`'s text-scanning `DomainPurityTest` is replaced by an assertion on the module's build file — guarding the guarantee rather than its symptoms. |
| Label snapshotting on `PlannedTask` | VIII (premature denormalisation) | **Pass.** FR-017 already requires available points to come from the plan; a day able to render its numbers but not its text would still depend on live content to be readable. Consistent with Principle III rather than extra. |
| `attachHijriLabel` as the one mutating method on an immutable aggregate | III | **Pass, conditionally.** Permitted by FR-009a, writes only when null, touches no figure. **Under research.md R4 it becomes dead code** — a locally computed label is never null. It survives only until the author rules on R4's flag; if R4 is accepted the method and the nullable column should go. |

**Gate result: PASS.** No new violations. Complexity Tracking carries one roadmap deviation, not a
constitution violation.

## Complexity Tracking

> One deviation. Not a constitution violation — a departure from `docs/PLAN.md`.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `docs/PLAN.md` Phase 2 states "Retrofit is introduced here for Hijri sync and is the only network surface in the app." This plan introduces **no network surface** and computes the Hijri date locally. | `java.time.chrono.HijrahChronology` is in the standard library and available on all supported API levels once core library desugaring is enabled — which this feature already requires for `java.time` on `minSdk 24`. Local conversion means the label is always available, so US4's offline-degradation scenarios cannot arise, Principle IV is satisfied by construction, and an entire failure mode plus a dependency disappear. | The network alternative was rejected as *more* complex, not less: it adds Retrofit, a DTO layer, a caching policy, a retry policy, and the fill-once machinery of FR-009a — all to obtain a value the platform already computes offline. It would also be the only thing in the increment that can fail. The one thing it buys is agreement with a specific observational authority, which nothing in the spec or constitution requires. **If that agreement is wanted, it is additive later**: the stored label is already a snapshot, so a future increment can reconcile it without touching this design. |
