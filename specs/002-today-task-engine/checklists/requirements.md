# Specification Quality Checklist: Today Screen — Local Task Engine

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-09
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

**16/16 pass.** Validation iterations: 2 (initial draft, then clarification).

**The one question asked, and its consequence.** `docs/PLAN.md` promises occurrence counters on the
Today screen, but the placeholder catalogue built in `001` has every occurrence limit set to 1 —
either the counters had nothing to display, or the catalogue was wrong. Answered: the Adhkar section
is **one task completed nine times**, not nine tasks.

That makes multi-occurrence central to User Story 1 rather than incidental, and it means the
catalogue must be corrected before anything is built on it — 9 tasks at limit 1 become 1 task at
limit 9. Every total is unchanged (2 × 9 = 18, base day 69, week 500) because available points were
already defined as points × occurrence limit. `001`'s validation contract passes the corrected
catalogue unmodified, which is precisely the property it was built to have.

Two count assertions in `001`'s parse test move from 40 to 32. That is test data catching up with
content, not a contract change.

### Clarification pass 2026-08-09

Four further questions asked and answered. No checklist item changed state — 16/16 before and
after. These removed latent ambiguity rather than fixing defects:

| # | Resolved | Effect |
|---|---|---|
| 1 | An absent Hijri label may be filled once; a set label never changes | FR-009a/b; US4 scenarios 3–5 |
| 2 | Stepped flow lands on the earliest incomplete section, derived not stored | FR-020b; US3 scenarios 4–7 |
| 3 | App launch and rollover create the plan, not screen navigation | FR-006a/b; new edge case |
| 4 | Undo frees exactly one occurrence slot; reversed records are invisible | FR-012, FR-013a, FR-014; SC-012 |

**Q1 and Q4 were the load-bearing ones.**

Q1 sat on the seam between Principle III and Principle IV. Read strictly, a user who opens the app
offline loses the Hijri date for that day permanently — and for every day they ever open offline.
Resolved by distinguishing missing data from a recorded value: the label may be written once, never
revised.

Q4 was worse. Because undo is a tombstone rather than an erasure, a naive count over all records
would lock the Adhkar task at nine of nine after one mistaken tap, with no way back and no visible
reason. That is a destructive accident in a record the user is asked to trust, and hostile under
Principle IX. FR-014 now states plainly that tombstones are storage bookkeeping and must be
invisible in every count, score, and limit.

Deliberately *not* asked, because a defensible default exists and is recorded in Assumptions:

| Question | Default taken | Authority |
|---|---|---|
| Hard delete or tombstone on undo? | Tombstone | Principle V |
| Can past days be edited? | No, current date only | Phase 5 scope; policy object lives here |
| What if a day is never opened? | No plan created; Phase 3 backfills | `docs/PLAN.md` Phase 3 |
| Does Hijri block the UI? | Never | Principle IV |
| Placeholder or real catalogue? | Placeholder; swapped later | Principle VI |

**Constitution alignment** (v1.1.1), checked while drafting:

| Principle | Bearing on this spec |
|---|---|
| I — Test-first | Every acceptance scenario is written as an observable assertion; no FR states a mechanism |
| II — Domain purity | Spec names no framework; scoring and applicability are stated as rules over data |
| III — Immutable history | US2 exists solely for this. FR-006 through FR-011 and SC-004/006 enforce it |
| IV — Offline-first | FR-026, FR-027, SC-001, SC-010. The Hijri label is the only network surface and cannot block |
| V — Backend independence | FR-014 (reversal not erasure), FR-028 (stable ids, timestamp, tombstone, nullable user) |
| VI — Fixed content | Out of Scope forbids task creation or editing; catalogue is loaded, never authored |
| VII — Deterministic time | FR-029, FR-030, SC-006. Rollover testable by fake clock; boundary rule in one place |
| VIII — Vertical slices | Four stories, each independently shippable. Overview argues why Phase 2 is one spec, not five |
| IX — No shame | FR-024, SC-009, and US1 scenario 9 — a zero day must not read as failure |

**Scope note**: this increment creates the `:domain` and `:data` modules. `001` deliberately did not,
because a module with no capability behind it is the speculative layering Principle VIII forbids.
Phase 2 is where they earn themselves.
