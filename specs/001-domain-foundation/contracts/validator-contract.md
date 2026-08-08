# Contract: Catalogue Validator

**Feature**: 001-domain-foundation | **Date**: 2026-08-08

## Surface

```kotlin
fun interface CatalogueValidator {
    fun validate(catalogue: Catalogue): List<CatalogueDefect>
}

// Two-stage. Stage 1 scans raw keys for forbidden authoring fields; stage 2 parses strictly.
fun scanForAuthoringAffordances(json: String): List<CatalogueDefect>
fun parseCatalogue(json: String): Result<Catalogue>
```

Pure. No clock, no I/O, no Android, no logging side effects.

### Why parsing is two-stage

`parseCatalogue` uses `ignoreUnknownKeys = false`, so **any** unexpected key fails the parse. That
is deliberate — `pointz: 2` in a hand-authored 40-record file would otherwise silently default a
task to zero points.

But it means a forbidden authoring field like `"editable": true` would be reported as a generic
`MalformedCatalogue`, and FR-019 — a Principle VI obligation — would lose its name in the noise of
ordinary typos. "Rejected for some reason" is not the same as "rejected because it lets a user edit
tasks."

So a raw-key scan runs **first**. If it finds a forbidden field it returns
`UserAuthoringAffordance` naming that field, and the strict parse is not consulted. Everything else
falls through to the parse, where an unknown key is a typo and becomes `MalformedCatalogue`.

Forbidden field names (exact match, any nesting level): `editable`, `userCreated`, `custom`,
`deletable`, `sortable`, `reorderable`, `ownerId`, `userId`.

## Behavioural guarantees

1. **Never throws on invalid input.** Every defect is a returned value. A parse failure becomes
   `MalformedCatalogue`; an absent file becomes `NoCatalogue`. An exception escaping `validate` is
   itself a bug.
2. **Never stops at the first defect.** All rules run against the whole catalogue. Authoring 40
   records must not require 40 runs (research.md R3).
3. **One variant per rule.** `emptyList()` means admissible — the only success signal (FR-008).
4. **Deterministic and order-independent.** Same catalogue in, same defect set out. Reordering
   `tasks` in the file changes nothing. Defects are sorted before return so assertions are stable.
5. **Absence is not validity.** `NoCatalogue` is distinct from `emptyList()` (FR-011). A run that
   found no file must never report success.

## Rule → defect → fixture

Each row is one bad fixture and one test. This table *is* the SC-003 obligation.

| # | Rule | Defect | Fixture |
|---|---|---|---|
| 1 | Slugs unique | `DuplicateTaskSlug` | `duplicate-slug.json` |
| 2 | Slug well-formed | `MalformedSlug` | `malformed-slug.json` |
| 3 | Points > 0 | `NonPositivePoints` | `zero-points.json`, `negative-points.json` |
| 4 | Occurrences ≥ 1 | `InvalidOccurrenceLimit` | `zero-occurrences.json` |
| 5 | Section resolves | `UnknownSection` | `missing-section.json` |
| 6 | Position unique in section | `DuplicateDisplayPosition` | `duplicate-position-in-section.json` |
| 7 | Section order unique | `DuplicateSectionOrder` | `duplicate-section-order.json` |
| 8 | Schedule matches ≥ 1 weekday | `UnreachableSchedule` | `unreachable-schedule.json` |
| 9 | Version numbers unique | `DuplicateVersionNumber` | `duplicate-version-number.json` |
| 10 | Version/date order agree | `VersionOrderMismatch` | `version-order-mismatch.json` |
| 11 | Effective-from unique | `DuplicateEffectiveFrom` | `duplicate-effective-from.json` |
| 12 | Weekday totals 69/74/76 | `WeekdayTotalMismatch` | `wrong-weekday-total.json` |
| 13 | Week total 500 | `WeekTotalMismatch` | `wrong-week-total.json` |
| 14 | Section composition | `SectionCompositionMismatch` | `wrong-section-composition.json` |
| 15 | No authoring affordance | `UserAuthoringAffordance` | `user-editable-flag.json` |
| 16 | Parse succeeds | `MalformedCatalogue` | `malformed.json` |
| 17 | File present | `NoCatalogue` | *(absence — no file)* |

17 rules, 17 defect variants, 16 fixture files. `NoCatalogue`'s fixture is the absence of one.

**Positive control**: `good/valid-catalogue.json` returns `emptyList()`.

**Negative control** (SC-002): mutate any single point value in the good fixture and the run must
fail. A contract that passes a corrupted catalogue is not a contract.

## Cross-cutting requirement

Every bad fixture in the table above differs from the good one in **exactly one way**. A fixture
with two defects cannot prove which rule caught it. This is the difference between a suite that
demonstrates coverage and one that merely appears to.

**One deliberate exception**: `bad/two-defects.json` carries two defects at once and is *not* in the
table. It exists to prove a different obligation — that `validate` reports every defect rather than
stopping at the first (guarantee 2). It is never used to prove a rule.

## Non-goals

Not a linter — says nothing about label wording or Arabic correctness. Not a migration tool. Does
not decide *which* version applies to a date at runtime; it only guarantees that question has
exactly one answer.
