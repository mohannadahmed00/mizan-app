# Specification Quality Checklist: Leaderboards & Honor Board

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-29
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

## Constitution Alignment

Checked against `.specify/memory/constitution.md`, since this increment sits closer to Principle IX
than any before it.

- [x] **Principle III** — FR-005, FR-025, FR-031 and SC-003 hold recorded history immutable. FR-004a
      resolves the opt-out tension deliberately: rankings are withdrawn everywhere, closed Honor
      Board recognition is preserved.
- [x] **Principle IV** — FR-034 and SC-008 require the core loop to be unaffected by leaderboard
      failure; FR-036 forbids presenting a cached ranking as current.
- [x] **Principle VI** — FR-017 and FR-028 forbid user-configurable regions and thresholds; the Out
      of Scope section restates the boundary.
- [x] **Principle VII** — FR-010 through FR-013 pin period boundaries to the region's timezone and
      guarantee the leaderboard day matches the participant's own calendar day. FR-011 forbids a
      second definition of the week.
- [x] **Principle VIII** — regions are an administrator-defined timezone grouping, explicitly not a
      social primitive, so nothing is pre-built for friends or cohorts.
- [x] **Principle IX** — FR-030, FR-038, FR-039, SC-012 and SC-013 carry the burden: no red, no
      last-place emphasis, no shortfall disclosure, no rank-drop nudges. The Honor Board rewards
      consistency rather than volume (FR-027), which is the constitution's own framing.

## Resolved Clarifications

All three questions raised at specification time were answered by the user and folded in:

- **Period boundaries** → regional. Leaderboards are scoped to administrator-defined regions, each
  with its own timezone fixing its period boundaries, assigned so the region's calendar date matches
  the participant's own (FR-009 – FR-017). Driven by day-specific catalogue tasks: ranking someone
  inside a leaderboard Friday while their device says Thursday would compare them against a
  denominator they never had. SC-005 tests exactly this.
- **Honor Board qualification** → days-engaged consistency threshold, administrator-defined, points
  excluded from qualification (FR-027, FR-028, SC-011). The leaderboard already rewards points; the
  Honor Board deliberately does not duplicate it.
- **Opt-out reach** → **revised by the user after the first answer.** Leaving clears every period
  still open and keeps the participant out of periods opening afterwards; periods that have already
  closed stand exactly as they are, rankings and Honor Board alike (FR-004, FR-004a, FR-004b,
  SC-002). This removed the last exception from "a closed period never changes", which is why
  Principle III is stronger in the design than it was in the gate. The tradeoff — a participant
  cannot erase past standings — is handled by **FR-002a**, which requires that to be disclosed
  before anyone opts in. Consent a person would not have given had they understood it is not
  consent.

## Notes

No open items. Specification is ready for `/speckit-plan`.

The riskiest areas for planning, in order: region assignment and re-evaluation under timezone change
(FR-012, FR-013, SC-005, SC-007), server-side recomputation and tamper resistance (FR-018, FR-019,
SC-006), and the Principle IX surface audit (SC-013), which has to be done by reading strings and
colours rather than by test assertion alone.
