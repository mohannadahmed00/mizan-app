# Implementation Plan: Domain Foundation — Validation Contract, Glossary, Decisions

**Branch**: `spec/001-domain-foundation` | **Date**: 2026-08-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-domain-foundation/spec.md`

## Summary

Build the executable contract that decides whether a task catalogue is admissible, before any
catalogue exists. Ship the domain glossary and record the twelve architectural decisions.

Technical approach: a pure-Kotlin validator plus JSON fixtures, living entirely in `:app`'s unit
test source set. No new Gradle module, no production code, nothing in the APK. The validator returns
a list of typed defects rather than throwing, so "one distinct failure per defect" (FR-008) is a
property of the return value rather than of test-runner granularity.

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11

**Primary Dependencies**: JUnit 4.13.2 (already present); kotlinx.serialization-json (new,
`testImplementation` only)

**Storage**: N/A — no persistence in this feature. Fixtures are JSON files on the test classpath.

**Testing**: JUnit 4, JVM unit tests via `./gradlew :app:testDebugUnitTest`

**Target Platform**: JVM (unit test runtime). Nothing reaches Android.

**Project Type**: Android application, single module `:app` at present

**Performance Goals**: N/A — the catalogue is ~40 static records. Validation completes in the noise
of test startup.

**Constraints**: No new Gradle module (spec Assumption). Nothing may ship in the APK (FR-020).
Validator must contain zero Android imports so the Phase 2 move into `:domain` is mechanical.

**Scale/Scope**: ~40 task records, 6–8 sections, 1 catalogue version at first. Roughly 12 defect
types, one known-good fixture, and one known-bad fixture per rule.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.0. Every principle this feature touches, and how it complies.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | The deliverable *is* the test. Order is fixed: known-bad fixture → failing assertion → defect type → validator rule. FR-010 and SC-003 require every rule to have a fixture proving it can fail. No exemption claimed — the validator is not DI wiring, not a `@Preview`, not generated. |
| **II — Domain purity** | Yes | Validator and model types are pure Kotlin: stdlib only, zero Android, zero framework. Enforced by a test asserting no `android.*` import appears in the validator sources, so the Phase 2 move into `:domain` cannot silently rot. |
| **III — Immutable history** | Yes | No persistence here, so nothing can rewrite a past day. The contract *protects* III downstream: FR-004a/b force a catalogue version to carry an effective-from date, which is what makes "which version applied on this date" answerable for a day never opened. |
| **IV — Offline-first** | No | No network, no UI, no storage. |
| **V — Backend independence** | Partly | No backend types anywhere. Task Definition identity is a slug, deliberately *not* the sync-ready UUID rule — V governs synchronisable rows (completions, day plans, task versions), and a catalogue definition is administrator content. Recorded in the decision record. |
| **VI — Fixed content** | Yes | FR-019: the contract rejects any catalogue carrying a user-authoring affordance. The catalogue is admin content by construction. |
| **VII — Deterministic time** | Yes | The validator reads no clock. Effective-from dates are data passed in, never `now()`. Boundary rules are recorded, not implemented here. |
| **VIII — Vertical slices** | Yes | **No `:domain` module is created.** See Structure Decision. No abstraction, layer, or table is introduced for a capability not built in this increment. The contract-before-catalogue ordering is argued in the spec's closing note. |
| **IX — Encouragement** | Yes | FR-003 requires positive point values; zero and negative are rejected defects. No UI, so no copy to police. |

**Technology constraints**: Kotlin ✓. No Compose/MVVM surface (no UI) ✓. Module direction
unaffected — no new module ✓. Koin untouched, and no second DI framework introduced ✓. No Room ✓.
No new network surface ✓. Task content is data, not UI strings — the whole point of the catalogue ✓.

**Gate result: PASS.** No violations. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-domain-foundation/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── catalogue-schema.md
│   └── validator-contract.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

```text
app/src/test/kotlin/com/giraffe/mizanapp/catalogue/
├── model/
│   ├── TaskDefinition.kt        # slug, section, displayPosition
│   ├── TaskVersion.kt           # points, scheduleRule, maxOccurrencesPerDay
│   ├── Section.kt               # id, label, order
│   ├── ScheduleRule.kt          # sealed: EveryDay | DaysOfWeek (DateAnchored reserved)
│   ├── CatalogueVersion.kt      # version: Int, effectiveFrom: LocalDate
│   └── Catalogue.kt             # versions + sections + tasks
├── CatalogueDefect.kt           # sealed defect type, one variant per rule
├── CatalogueValidator.kt        # Catalogue -> List<CatalogueDefect>
├── CatalogueJson.kt             # JSON -> Catalogue, parse failures as defects
└── ...

app/src/test/resources/catalogue/
├── good/valid-catalogue.json
└── bad/
    ├── duplicate-slug.json
    ├── zero-points.json
    ├── negative-points.json
    ├── zero-occurrences.json
    ├── missing-section.json
    ├── unreachable-schedule.json
    ├── duplicate-position-in-section.json
    ├── version-order-mismatch.json
    ├── duplicate-effective-from.json
    ├── wrong-weekday-total.json
    ├── wrong-week-total.json
    └── malformed.json

app/src/test/kotlin/com/giraffe/mizanapp/catalogue/
├── CatalogueValidatorTest.kt        # one test per defect type
├── CatalogueArithmeticTest.kt       # 69 / 74 / 76 / 500 and section composition
├── CatalogueMutationTest.kt         # SC-002: mutate good fixture, assert failure
└── DomainPurityTest.kt              # Principle II: no android.* import in validator

docs/
├── GLOSSARY.md          # new — the twelve terms
└── PLAN.md              # edited in place — decisions recorded, spelling corrected
```

**Structure Decision**: Everything lives in the existing `:app` module's **unit test source set**.
No `:domain` or `:data` module is created by this feature.

Three reasons, all pointing the same way. Principle VIII forbids introducing a layer for a
capability not being built, and `:domain`'s justification is Phase 2's scoring and applicability
logic, which is not built here. `docs/PLAN.md` Phase 1 says decide the module shape but "do not
build empty modules you have no code for yet". FR-020 requires that nothing ship inside the running
application, and a test source set is not packaged into the APK.

The cost is a move in Phase 2: the `model/` and validator files relocate to `:domain`. That move is
mechanical and enforced cheap by `DomainPurityTest` — no Android import can creep in meanwhile. It
is paid in the increment that actually earns the layer.

## Constitution Re-Check (post-Phase 1 design)

Design introduced three things not present at the first gate. Each re-checked:

| Introduced | Principle at risk | Verdict |
|---|---|---|
| `kotlinx.serialization-json` dependency | VIII (speculative), tech constraints | **Pass.** `testImplementation` only — absent from the APK. Not a network surface, so the Retrofit-only constraint is untouched. Justified in research.md R2: FR-011 requires distinguishing a malformed file from a valid one, which is untestable without a parser. |
| `ScheduleRule.DateAnchored` named but unimplemented | VIII (abstraction for unbuilt capability) | **Pass.** FR-005 explicitly requires the vocabulary admit date-anchored rules later without redefining existing ones. Named in the schema and *rejected at runtime* this increment. Naming is the requirement; building it would be the violation. |
| `taskVersions` as a separate collection from `tasks` | VIII (premature normalisation) | **Pass.** Principle III requires a past day be scored against the version that applied then, and FR-004a/b make version resolution answerable. Collapsing points onto `TaskDefinition` would make a point change rewrite history — the exact failure III exists to prevent. |

**Gate result: PASS.** No new violations. Complexity Tracking stays empty.

## Complexity Tracking

> No Constitution Check violations at either gate. Table intentionally empty.
