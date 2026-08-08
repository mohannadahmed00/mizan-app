<!--
SYNC IMPACT REPORT
==================
Version change: 1.1.0 → 1.1.1 (2026-08-09)
Bump rationale: PATCH. Clarifies the wording of one technology constraint. No principle is added,
removed, or redefined; no requirement is loosened or tightened in substance.

Amendment rationale: "Layouts MUST be RTL-correct" was read as mandating a right-to-left interface
shell. The product design settles on an English LTR shell carrying Arabic task content, with each
Arabic string rendered in a dedicated face and its own direction so mixed rows do not reflow the
layout. That is a stricter bidirectional discipline than a naive full-RTL flip, but the old wording
appeared to forbid it. The constraint now states the actual requirement — correct bidirectional
rendering of Arabic content — and names shell language and direction as a product decision.

Modified sections:
  - Technology Constraints, "Content and localisation" bullet (reworded)

Invalidates: nothing. No spec or code depended on a full-RTL shell; increment 001 ships no UI.

---- PRIOR VERSIONS ----

Version change: 1.0.0 → 1.1.0 (2026-08-08)
Bump rationale: MINOR. Materially expands the existing "Development Workflow and Quality
Gates" section with branch-protection and merge-gate requirements. No principle was added,
removed, or redefined, so no existing work is invalidated.

Amendment rationale: v1.0.0 asserted that an increment violating Principle I or III "is not
complete", but named no point at which that is verified. Anchoring the Constitution Check to
a pull-request merge gate turns that assertion into an enforceable gate.

Modified sections:
  - Development Workflow and Quality Gates (expanded; 4 bullets added)

Invalidates: nothing. No existing spec or code depends on the previous wording.

Deliberately excluded from this document: concrete branch names, branch-protection settings,
and CI configuration. These live in CLAUDE.md so they can change without an amendment.

---- PRIOR VERSIONS ----

Version change: (uninitialized template) → 1.0.0 (2026-08-08)
Bump rationale: Initial ratification. The file previously contained only unfilled
placeholder tokens, so this is the first substantive constitution rather than an amendment.

Principles defined (all new, none renamed or removed):
  - I.   Test-First Development (NON-NEGOTIABLE)
  - II.  Domain Purity
  - III. Historical Records Are Immutable (NON-NEGOTIABLE)
  - IV.  Offline-First, Local Source of Truth
  - V.   Backend Independence and Sync Readiness
  - VI.  Fixed Content, No User Authoring
  - VII. Deterministic Time
  - VIII.Vertical Slices, No Speculative Abstraction
  - IX.  Encouragement, Never Shame

Sections added:
  - Core Principles (9 principles; template shipped slots for 5)
  - Technology Constraints (fills template slot [SECTION_2_NAME])
  - Development Workflow and Quality Gates (fills template slot [SECTION_3_NAME])
  - Governance

Sections removed: none.

Deferred items / TODOs: none. Ratification date supplied by the author as today's date.
-->

# Mizan Constitution

Mizan is an offline-first Android application for recording a fixed, administrator-defined set of
Islamic daily practices. It is built by a solo developer, incrementally. This constitution defines
the rules that no feature, plan, or increment may violate.

## Core Principles

### I. Test-First Development (NON-NEGOTIABLE)

No production code may be written before a failing test that requires it. The cycle is: write the
test, observe it fail for the right reason, write the minimum code to pass, then refactor.

Within a feature, the order is fixed: domain tests and domain code first, then data-layer tests and
data code, then UI.

Every scoring rule, applicability rule, date-boundary rule, and occurrence rule MUST have a unit
test written before its implementation. Persistence behaviour MUST have a test before the schema
that satisfies it.

Exempt from test-first: dependency-injection module wiring, `@Preview` composables, and generated
code. Nothing else is exempt, including data mappers.

**Rationale**: This project's value is entirely in the correctness of numbers the user is asked to
trust. A wrong total is worse than a missing feature, and a solo developer has no reviewer to catch
it.

**Check that can fail**: Any plan or task list that produces implementation tasks before their
corresponding test tasks violates this principle.

### II. Domain Purity

The `:domain` module MUST have zero dependencies on Android, Room, Retrofit, Compose, Koin
annotations, or any other framework. It contains only Kotlin, the standard library, and coroutines.

All scoring, applicability resolution, occurrence rules, and streak logic MUST be pure functions
over data passed into them. All repositories MUST be interfaces declared in `:domain` and
implemented in `:data`.

**Rationale**: The domain is the part that must survive the arrival of Supabase, a change of
database, and any UI rewrite. Keeping it frameworkless is what makes those changes an implementation
swap instead of a rewrite, and it is what makes Principle I cheap rather than painful.

**Check that can fail**: Adding any framework dependency to `:domain`'s build file, or declaring a
repository implementation there.

### III. Historical Records Are Immutable (NON-NEGOTIABLE)

Once a day has been recorded, nothing may retroactively change what that day reports.

- A recorded completion MUST carry the points it was awarded.
- A recorded day MUST carry the set of tasks that applied to it and the total points that were
  available on it.
- Changes to the task catalogue affect future days only.

No feature may compute a past day's figures by reading the current task catalogue. Any change
touching persistence or the catalogue MUST be accompanied by a test that changes task points and
schedules and asserts that previously recorded days are unaffected while the current day follows the
new definitions.

**Rationale**: The app is an accountability record. A record that silently rewrites itself when an
administrator edits a task is not a record, and this is the one category of bug that cannot be fixed
after the fact — the original truth is gone.

**Check that can fail**: Any plan that derives historical scores from the live catalogue, or that
stores a day's available-points total nowhere.

### IV. Offline-First, Local Source of Truth

The local database is the single source of truth for task recording and scoring. No network call may
sit on the path of viewing tasks, recording a completion, undoing one, or computing a score.

The app MUST be fully usable in airplane mode on a fresh install. Any remote data is a cache or a
peer to reconcile with, never something the UI reads from directly.

**Rationale**: The practices being tracked happen at fixed times regardless of connectivity, often
in a mosque with no signal. A spinner at that moment loses the record and the user.

**Check that can fail**: Any plan where a UI state depends on a network result to display or record
tasks.

### V. Backend Independence and Sync Readiness

The app is built local-only first, but records created today MUST be synchronisable tomorrow without
migrating existing user data. Therefore:

- Synchronisable rows MUST use client-generated stable identifiers, never auto-incrementing keys.
- Synchronisable rows MUST carry a last-modified timestamp, a soft-delete marker, and a nullable
  user reference.
- Deletion of a synchronisable record MUST be a tombstone, not a hard delete.
- No backend type, DTO, or SDK type may appear in `:domain`.
- Adding a backend later MUST change implementations of existing repository interfaces, not the
  interfaces themselves.
- Turning the backend off MUST degrade the app to a fully working offline app, with no crashes and
  no missing history.

**Rationale**: These constraints cost hours now and prevent a data migration over real user history
later. They are the cheapest insurance in the project.

### VI. Fixed Content, No User Authoring

Tasks, their point values, their sections, and their schedules are administrator-defined. No feature
may allow a user to create, edit, delete, reorder, reprice, or reschedule a task.

The catalogue ships as a versioned seed loaded idempotently, so that replacing it with a
server-provided catalogue later changes the source and not the shape.

**Rationale**: Mizan is a defined accountability sheet, not a habit tracker. Every feature request
that begins "let the user customise" is out of scope by construction, and settling that here
prevents the data model drifting toward user-owned content.

### VII. Deterministic Time

No code outside a single injected time provider may read the system clock, current date, or default
timezone. Day boundaries, week boundaries, rollover, and streak logic MUST be testable by advancing
a fake clock.

- The accountability day runs local midnight to local midnight.
- The Hijri date is a label attached to a day, never the thing that defines the day's boundaries.
- The week runs Saturday to Friday.

Each of these rules MUST live in exactly one place in the codebase; there may be no second opinion
about when a day or week begins.

**Rationale**: Every hard bug in an app like this is a date bug, and date bugs are untestable
without an injectable clock. Duplicated boundary logic guarantees two screens will eventually
disagree about the same day.

**Check that can fail**: Any direct use of the system clock or "now" outside the provider; any
second implementation of a boundary rule.

### VIII. Vertical Slices, No Speculative Abstraction

Every increment MUST deliver a coherent, usable, testable capability. No feature may introduce an
abstraction, a layer, a configuration surface, or a table for a capability that is not being built
in that increment.

Interfaces exist because something needs to be substituted now, or because Principle V requires the
shape — not because something might need substituting someday.

Pure refactors are not permitted as standalone increments unless the refactor unlocks a specific
capability that is being built next, and that capability is named in the plan.

**Rationale**: A solo developer's scarcest resource is finished work. Over-engineering an MVP is the
most common way this project fails.

### IX. Encouragement, Never Shame

The product measures consistency, not perfection. There are no penalties for missed tasks, no
negative scores, no failure states, and no guilt-inducing language or imagery anywhere in the UI.

Progress is expressed as what was completed. The consistency streak is maintained by engaging with
the app and completing at least one applicable task in a day.

**Rationale**: The domain is personal worship. A design that shames users into compliance is both
counterproductive and inappropriate to the subject matter, and this constraint has to hold at the
copy-and-visuals level, which is where it is easiest to violate by accident.

## Technology Constraints

These are fixed and are not renegotiated per feature.

- **Language and UI**: Kotlin and Jetpack Compose.
- **Architecture**: Clean Architecture with MVVM. One immutable UI state per screen, exposed as
  `StateFlow`. No mutable state may be exposed from a ViewModel.
- **Modules**: Multi-module by layer — `:domain`, `:data`, `:app`. The dependency direction is
  `:app` → `:data` → `:domain`, and `:domain` depends on nothing. `:domain` MUST never depend on
  `:data` or `:app`.
- **Dependency injection**: Koin is the only DI framework. Hilt and KSP-based DI MUST be removed
  from this project. Two DI frameworks may never coexist.
- **Persistence**: Room, with exported schemas and migrations. No destructive migration in any build
  that a user has installed.
- **Networking**: Retrofit and coroutines, for the existing Hijri date synchronisation only. New
  network surfaces require an explicit justification in the plan.
- **Content and localisation**: Task content is Arabic and is treated as data, not as UI strings.
  Arabic content MUST render with correct bidirectional handling and an Arabic-appropriate
  typeface, and MUST NOT reflow or reorder the layout around it. The language and reading direction
  of the interface shell are a product decision, not a constitutional one — an English
  left-to-right shell carrying right-to-left Arabic content satisfies this constraint, as does a
  fully right-to-left interface.

## Development Workflow and Quality Gates

- Every `/plan` MUST include a Constitution Check that names each principle the plan touches and
  states how the plan complies with it.
- Any violation MUST be recorded in the plan's Complexity Tracking section, naming the specific
  simpler approach that was rejected and why it was insufficient. "It was faster" is not a
  justification.
- Principle I and Principle III admit no exceptions and may not appear in Complexity Tracking as
  accepted violations.
- Task lists MUST order test tasks before their corresponding implementation tasks, per Principle I.
- Any increment touching persistence or the task catalogue MUST include the historical-immutability
  test required by Principle III before it is considered complete.
- Work happens on short-lived branches, one per increment. The integration branch and the release
  branch are protected: neither accepts direct commits, only merges from a pull request.
- A pull request MUST NOT be merged into the integration branch unless its Constitution Check
  passes, its tests are green, and its test tasks demonstrably preceded their implementation tasks
  (Principle I).
- Release pull requests from the integration branch to the release branch additionally require that
  every Room migration in the release is non-destructive and its schema is exported.
- Branch names, protection settings, and CI configuration are recorded outside this document and may
  change without amendment.

## Governance

This constitution supersedes all other practices. Where any other document, habit, or convenience
conflicts with it, this constitution wins.

**Amendments** require:

1. A documented rationale for the change.
2. A version bump according to the versioning policy below.
3. A note of which existing specs or code the amendment invalidates.

**Versioning policy** is semantic:

- **MAJOR**: Removing or redefining a principle in a way that invalidates existing work.
- **MINOR**: Adding a principle, or materially expanding an existing one.
- **PATCH**: Wording, clarification, and non-semantic refinement.

**Compliance review**: Every plan is reviewed against this document at planning time via its
Constitution Check, and again before an increment is considered complete. An increment that violates
Principle I or Principle III is not complete, regardless of whether it works.

**Version**: 1.1.1 | **Ratified**: 2026-08-08 | **Last Amended**: 2026-08-09
