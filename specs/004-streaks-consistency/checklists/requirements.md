# Specification Quality Checklist: Streaks & Consistency

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-15
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — 8 raised, all resolved in Clarifications 2026-08-15
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

- [x] **II — Domain purity**: the streak is a fold over Consistency Days; FR-005 and FR-013 keep it
      free of the catalogue and of any write path.
- [x] **III — Historical records immutable**: FR-013 forbids this feature writing anything; FR-015
      forbids re-crediting a recorded date; SC-009, SC-009a, SC-010.
- [x] **IV — Offline-first**: FR-028, SC-015.
- [x] **VI — No user authoring**: nothing in this feature writes; the streak element carries no
      affordance of any kind.
- [x] **VII — Deterministic time**: FR-014 (injected clock for both date and time-of-day), FR-011,
      FR-017, SC-010, SC-011, SC-012.
- [x] **VIII — Vertical slices**: PLAN's three specs merged into one shippable increment; the cached
      streak row, the streak detail sheet, and the at-risk notification are all deliberately not
      built (Assumptions, Out of Scope).
- [x] **IX — Encouragement, never shame**: FR-021, FR-021a, FR-021b, FR-022, FR-027, SC-013. This is
      the principle most at risk in this increment and it is load-bearing in four separate places —
      the ended run, the zero streak, the at-risk nudge, and a figure that fails to load.

## Notes

All items pass. Eight clarifications raised across the 2026-08-15 session, all resolved:

1. **Current streak before today is earned** → the run ending today *or yesterday*, with today marked
   pending. FR-006, FR-006a, FR-009, FR-019, FR-020a, SC-001.
2. **Timezone travel and clock changes** → stored facts fixed, only the present moves; no detection,
   no leniency, nothing new stored. FR-011, FR-015, FR-016, SC-010.
3. **At-risk trigger** → fixed local time, 20:00 to local midnight, live run only. FR-025, FR-026,
   SC-012.
4. **Placement and visibility** → one fixed position, unchanged across the stepped flow, shown when
   the catalogue is unavailable, omitted only while Today is loading. FR-018a, FR-018b, SC-016.
5. **First paint** → Today does not wait; the element reserves its place and resolves once, never
   showing a provisional or zero figure. FR-018c, SC-017.
6. **Break-notice lifetime** → shown while the last active date is within the past seven days, then
   the plain start state. Derived, nothing stored. FR-021a, SC-018.
7. **Record read failure** → notice with retry in the element's place, core loop unaffected, never a
   zero and never a silent disappearance. FR-021b, SC-019.
8. **Terminology** → *Longest Streak* is canonical in spec, tests and types; "best" is on-screen copy
   only. `docs/GLOSSARY.md` gains Longest Streak and Streak Break. FR-007.

**Carried into planning:**

- **This increment adds no persistence.** No table changes, no migration, no new stored fact. That
  makes it the first increment since `001` that does not trigger Principle III's mandatory
  historical-immutability test on schema grounds — but SC-009a still requires proving a catalogue
  change moves no streak figure, because the only way it could is if the streak were reading the
  catalogue.
- **One coupling handed forward.** FR-001 treats a live completion as sufficient evidence the app was
  open on that date, which is true only because `002`'s write rule admits completions on the current
  date alone. Phase 5's retro-completion spec breaks that premise and must decide whether a
  retroactive completion makes a date a Consistency Day. `003`'s Plan Origin exists for exactly this
  and is deliberately not consulted yet.
- **SC-014 is the honest measurement of PLAN's definition of done** for this phase — "requires no new
  writes on the completion path". A streak that slows recording has failed the phase regardless of
  whether its numbers are right.
- **Two documentation deliverables.** `docs/GLOSSARY.md` gains Longest Streak and Streak Break
  (clarification 8). Both are named in `docs/PLAN.md` as concepts this phase introduces and neither
  is yet defined, so the glossary is incomplete until this increment lands.
- **This feature fails differently from `003`.** The week screen blocks and reports a whole-screen
  failure because the figures *are* the screen. Here the figures sit beside the core loop, so
  FR-018c and FR-021b deliberately let the streak be late or absent without the user losing the
  ability to record. Planning should not "harmonise" the two postures.
