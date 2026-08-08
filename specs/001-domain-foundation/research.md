# Phase 0 Research: Domain Foundation

**Feature**: 001-domain-foundation | **Date**: 2026-08-08

Five unknowns carried in from the spec. All resolved; none escalate to `/speckit-clarify`.

---

## R1 — Where does the validation contract live?

**Decision**: `app/src/test/kotlin/com/giraffe/mizanapp/catalogue/`. No new Gradle module.

**Rationale**: Three independent constraints agree.

- Principle VIII forbids introducing a layer for a capability not built in this increment.
  `:domain` exists to hold scoring, applicability, and streak logic — Phase 2 work, absent here.
- `docs/PLAN.md` Phase 1: "Module boundary decision only… Decide the shape, do not build empty
  modules you have no code for yet."
- FR-020: nothing may ship in the running application. A unit test source set is not packaged.

The validator is still written as pure Kotlin with zero Android imports, so relocating it to
`:domain` in Phase 2 is a file move plus a package rename.

**Alternatives considered**:

- *Create `:domain` now, put model types in `main` and the validator in `test`.* Rejected: the
  module's own justification is deferred work, and the types would ship in the APK while nothing
  calls them — the exact speculative layering VIII names.
- *Create `:domain` as a pure JVM module with everything in `main`.* Rejected for the same reason,
  and it additionally breaks FR-020 outright.
- *Standalone Gradle `buildSrc` or a script.* Rejected: harder to run, harder to move to `:domain`
  later, and puts the project's most correctness-critical logic outside the normal test report.

**Accepted cost**: a ~10-file move in Phase 2, guarded by `DomainPurityTest`.

---

## R2 — What format is the catalogue, and how do fixtures represent it?

**Decision**: JSON files on the test classpath, parsed with `kotlinx.serialization-json` added as
`testImplementation` only.

**Rationale**: FR-001 requires the catalogue be machine-readable data; FR-011 requires the contract
to distinguish "no catalogue present" from "catalogue valid". Both are statements about a *file*. A
contract that only ever sees in-memory Kotlin objects cannot reject a malformed catalogue, a
duplicated JSON key, or a missing file — which is a large share of the defects that will actually
occur when a human hand-authors 40 records.

JSON over YAML: YAML needs a third-party parser, and its implicit typing (`no` parsing as boolean,
unquoted numerics) is a liability in a file whose whole purpose is exact point values. JSON is
strict, diffable, and is what Phase 2 will read at seed time anyway.

**Alternatives considered**:

- *Fixtures as Kotlin objects, format deferred to 002.* Rejected: leaves FR-011 untestable and
  defers the parse-failure path to the increment that can least afford surprises.
- *`org.json`.* Rejected: it is an Android framework class, stubbed to throw under JVM unit tests
  without Robolectric.
- *Gson / Moshi.* Workable, but kotlinx.serialization is Kotlin-native, needs no reflection, and
  matches the project's Kotlin-first constraint.

**Note**: this adds the `kotlin-serialization` Gradle plugin. It is not speculative — the fixtures
under test are JSON, so the parser is part of the artifact being validated.

---

## R3 — How is "one distinct failure per defect" (FR-008) achieved?

**Decision**: `CatalogueValidator` returns `List<CatalogueDefect>`, where `CatalogueDefect` is a
sealed type with one variant per rule. It never throws on invalid input, and never stops at the
first defect.

**Rationale**: Making distinctness a property of the *return value* rather than of test-runner
granularity keeps FR-008 satisfied regardless of test framework, and gives the same detail to a
future CI check or a `002` authoring loop. Throwing on the first defect would force an author to
fix 40 records one run at a time.

JUnit 4 (already in the project) has no dynamic-test facility, which would otherwise make
per-defect reporting awkward. This design sidesteps that entirely.

**Alternatives considered**:

- *Throw on first defect.* Rejected: hostile to the authoring loop `002` depends on.
- *Adopt JUnit 5 for `@TestFactory`.* Rejected: adds a Gradle plugin and a second test framework to
  solve a problem better solved in the return type.
- *Return `Boolean` plus logging.* Rejected: unassertable, and FR-008 becomes untestable.

---

## R4 — Where does the glossary live?

**Decision**: a new `docs/GLOSSARY.md`.

**Rationale**: A glossary is looked up, not read linearly. `docs/PLAN.md` is a narrative roadmap and
is already absorbing the decision record, so adding twelve definitions would bury both. A separate
file is also what Phase 2's specs will link to per-term.

**Alternatives considered**:

- *Inside `docs/PLAN.md`.* Rejected: two unrelated reference structures in one narrative document.
- *Inside the constitution.* Rejected: the constitution governs; it does not define vocabulary, and
  every glossary edit would then demand a version bump.
- *Inside `specs/001-domain-foundation/`.* Rejected: the glossary outlives this feature and is read
  by every later one.

---

## R5 — How is the spelling correction applied without collateral damage?

**Decision**: mechanical replacement of `catalog` → `catalogue` in `docs/PLAN.md` only, using a
negative lookahead (`catalog(?!ue)`) so already-correct occurrences are untouched. Verified by
recount afterwards.

**Rationale**: measured baseline at clarification time — `docs/PLAN.md` 32 occurrences of `catalog`,
constitution 7 of `catalogue`, `CLAUDE.md` 2 of `catalogue`. Only `PLAN.md` diverges from the
constitution, so it is the only file to change. The constitution is the authority and stays put,
avoiding a PATCH amendment for a spelling change.

**Alternatives considered**:

- *Rename everything down to `catalog`.* Rejected: requires amending the constitution, a governing
  document, for cosmetic reasons.
- *Leave both spellings.* Rejected by clarification Q5; this feature ships the glossary, and one
  term with two spellings is precisely what a glossary exists to prevent.

**Watch**: the replacement must not touch the spec's own quoted references to the wrong spelling
(SC-005 already carves those out).

---

## Resolved unknowns summary

| # | Unknown | Resolution |
|---|---|---|
| R1 | Contract location | `:app` unit test source set; no new module |
| R2 | Catalogue format | JSON + kotlinx.serialization, `testImplementation` |
| R3 | Distinct failures | Validator returns `List<CatalogueDefect>`, never throws |
| R4 | Glossary location | New `docs/GLOSSARY.md` |
| R5 | Spelling fix | `catalog(?!ue)` → `catalogue`, `docs/PLAN.md` only, 32 occurrences |

No `NEEDS CLARIFICATION` markers remain.
