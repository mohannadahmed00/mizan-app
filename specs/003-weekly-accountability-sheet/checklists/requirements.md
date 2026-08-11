# Specification Quality Checklist: Weekly Accountability Sheet

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-11
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — 2 raised, both resolved in Clarifications 2026-08-11
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Constitution Alignment

- [x] **III — Historical records immutable**: FR-006, FR-010a, FR-023, SC-009 keep every figure
      sourced from the stored plan, never the live catalogue. FR-010b forbids replacing a plan.
- [x] **IV — Offline-first**: FR-026, SC-012.
- [x] **VI — No user authoring**: FR-024, SC-010 — the whole feature is a read.
- [x] **VII — Deterministic time**: FR-001 (one week rule), FR-004 (injected clock), SC-004.
- [x] **VIII — Vertical slices**: three PLAN specs merged into one shippable increment; the day
      summary cache is deliberately not built (Assumptions).
- [x] **IX — Encouragement, never shame**: FR-016, SC-011; skipped days read as 0 of N, and
      pre-record dates are visually distinct from zero days.

## Notes

All items pass. Five clarifications raised across two sessions, all resolved:

1. **Mid-week denominator** → both figures, shown separately. Headline is earned against elapsed
   available; the 500 week target is context. FR-009, FR-009a–d, FR-017, FR-017a, SC-001a, SC-001b.
2. **Backfill catalogue version** → the version in effect on the date being backfilled. FR-013,
   FR-013a, SC-009a.
3. **Pre-existing catalogue version's effective date** → none; the earliest version is open-ended
   backwards, so the record start is the only floor on backfill. FR-013b–e, SC-009b.
4. **Backfill cost** → blocking, 300 ms budget for a week needing all seven days filled; no
   provisional figures, no cell changing under the user. FR-014a, SC-013, SC-013a.
5. **Backfill write failure** → week-level notice with retry, no figures, no fourth cell state.
   FR-014b–d, SC-013b.

**Carried into planning — updated 2026-08-11 against the merged `002` code.** Planning found that
`002` is built and merged, and that two claims here were wrong: catalogue-version effective dates
already exist (`001` defined `effectiveFrom`, `002` persists it and resolves it), so FR-013a is a
resolution rule and not new storage. The spec is corrected. What remains:

- **One new stored fact** — the Day Plan origin (FR-011), requiring Room migration 1 → 2 with a
  constant `'OPENED'` default. No guessed values: `002` can only create a plan by opening.
- **One changed rule** — `versionEffectiveOn` must treat the earliest version as open-ended
  backwards (FR-013b). It currently returns null before the seed's `effectiveFrom` of `2026-01-01`,
  which would leave FR-013b unimplemented. See plan.md research R1.

Persistence and the catalogue are both touched, so Principle III's historical-immutability test is
mandatory and the migration must be non-destructive.

**Also carried** — FR-014a makes displaying a week a blocking write path with a 300 ms budget. That
budget is the first real measurement of whether `002`'s storage design is queryable, which is one of
this increment's stated purposes; if it is missed, that is a Phase 2 finding, not a reason to loosen
the number.
