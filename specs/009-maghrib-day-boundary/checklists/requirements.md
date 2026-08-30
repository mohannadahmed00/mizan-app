# Specification Quality Checklist: Maghrib-Anchored Day and Week Boundary

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-30
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
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

## Notes

- **Both constitutionally deferred decisions are settled in the spec, not left to planning**, as
  constitution v2.0.0 requires:
  - The unavailable-location fallback (Principle VII's explicit deferral to "the spec that
    introduces this provider") is FR-012 through FR-017. Maghrib from last known coordinates
    whenever any coordinates have ever been obtained; local midnight / Saturday-to-Friday before the
    first fix ever; never a guessed location; never a block or a crash.
  - The already-closed-history question (the Principle III tension the amendment's sync-impact
    report recorded) is FR-021 through FR-025. Closed days and weeks stand exactly as recorded and
    are never recomputed, with FR-025 requiring the immutability test the constitution mandates for
    any change touching persistence.

- **Principle IV is addressed at spec level, not deferred to the plan.** The constitution requires
  any plan introducing this provider to address offline-first directly rather than pass it
  routinely. User Story 3, FR-012 through FR-017, SC-004, SC-005 and SC-010 are what that check will
  be evaluated against.

- **Two requirements reach into already-merged features and were included deliberately**: FR-032
  (leaderboard region membership must still guarantee a shared accountability date) and FR-029 (the
  streak's fixed 20:00 at-risk time cannot survive a variable-length day). Both are consequences of
  the boundary change rather than new scope, and leaving either out would ship a known
  inconsistency.

- **Sequencing**: this spec is a prerequisite of spec 010 (Notifications & Weekly Summaries), whose
  own checklist blocked planning until this existed. 009 replans on top of this one and consumes the
  provider introduced here rather than building a second.

- The constitution v2.0.0 amendment commit was cherry-picked onto this branch, because it had been
  committed only on `spec/010-notifications-weekly-summaries` and had never reached `develop-v1`.
  This branch's pull request is therefore what lands the amendment on the integration branch —
  correct ordering, since the governance change arrives with the increment that implements it. When
  009's branch is later rebased, its duplicate copy of that commit is expected to drop out as
  already applied.

- **Clarify session 2026-08-30 — five questions asked and integrated** (see the spec's Clarifications
  section): leaderboard scope narrowed to period timing only; retained coordinates survive permission
  revocation but are disclosed and erasable; calculation convention is region-derived rather than
  globally fixed; coordinates are invalidated by a time-zone change rather than by age; the location
  request is non-blocking on first launch behind an explicit opt-in.

- **FR-003d gate — CLEARED 2026-08-30.** The region-derived calculation convention contradicted the
  literal wording of constitution v2.0.0 Principle VII ("a **single**, administrator-fixed calculation
  convention"), which would have failed the plan's Constitution Check on its face. Principle VII was
  amended in **constitution v2.0.1** to fix the convention per region, keeping every other constraint
  in that bullet intact and adding two the old wording left unstated: on-device region resolution, and
  a documented default where no mapping entry matches. Nothing was invalidated — no merged spec or
  shipped code calculates prayer times at all.

- All checklist items pass and no gate remains outstanding. Ready for `/speckit-plan`.
