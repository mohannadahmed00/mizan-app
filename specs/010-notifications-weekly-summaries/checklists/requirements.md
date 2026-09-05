# Specification Quality Checklist: Notifications and Weekly Summaries

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-04
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

## Constitution Pre-Read

Not part of the standard checklist; recorded because this feature is the first that speaks to the
person unprompted, and three principles bite unusually hard.

- [x] **Principle IX** — every category has an explicit copy constraint (FR-015, FR-021, FR-025), the
      summary reports no count of anything not done, the app is silenceable in one control (FR-002),
      and a fresh install is quiet by default (FR-003). SC-009 is the gating review.
- [x] **Principle VII** — no second time, location or prayer-time provider (FR-039); the at-risk offset
      and the week-close instant are read from their existing single homes (FR-017, FR-023).
- [x] **Principle III** — the feature writes no history (FR-044), and a closed week's summary is frozen
      against catalogue change (FR-024, SC-006, SC-012).
- [x] **Principle IV** — no network on any notification path (FR-036); fresh install in airplane mode
      with no location is fully usable (SC-001), and the fallback regime has an explicit, non-guessing
      behaviour (FR-016).
- [x] **Principle VI** — control is per category only; no user-authored or per-task reminders (FR-006).

## Notes

Eight clarifications are recorded in the spec's Clarifications section, all from 2026-09-04. Three were
resolved before the spec was written (nudge cadence, fresh-install defaults, quiet-hours treatment of
the weekly summary). Five more came from `/speckit-clarify`: summaries are recalculated rather than
stored, delivery is exact with a stated degradation path, the summary goes dormant after two empty
weeks, the summary screen shows only closed weeks, and no permission is requested in the first week. No
open markers remain.

`/speckit-clarify` resolved one internal contradiction: FR-024 described a recalculated summary while the
Assumptions section described a stored one. Recalculation won, and FR-024a now forbids storing weekly
figures outright.

One item is deliberately left to `/speckit-plan` rather than fixed here: where the FR-045 delivery
bookkeeping lives. It changes no observable behaviour any requirement above asserts. The nudge offset
constant is likewise a planning value, but the spec fixes what matters about it — that it is a constant
and never a setting (FR-009).
