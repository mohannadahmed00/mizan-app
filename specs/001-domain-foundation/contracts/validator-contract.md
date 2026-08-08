# Contract: Catalogue Validator

**Feature**: 001-domain-foundation | **Date**: 2026-08-08

## Surface

```kotlin
fun interface CatalogueValidator {
    fun validate(catalogue: Catalogue): List<CatalogueDefect>
}

fun parseCatalogue(json: String): Result<Catalogue>
```

Pure. No clock, no I/O, no Android, no logging side effects.

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
| 9 | Version/date order agree | `VersionOrderMismatch` | `version-order-mismatch.json` |
| 10 | Effective-from unique | `DuplicateEffectiveFrom` | `duplicate-effective-from.json` |
| 11 | Weekday totals 69/74/76 | `WeekdayTotalMismatch` | `wrong-weekday-total.json` |
| 12 | Week total 500 | `WeekTotalMismatch` | `wrong-week-total.json` |
| 13 | Section composition | `SectionCompositionMismatch` | `wrong-section-composition.json` |
| 14 | No authoring affordance | `UserAuthoringAffordance` | `user-editable-flag.json` |
| 15 | Parse succeeds | `MalformedCatalogue` | `malformed.json` |
| 16 | File present | `NoCatalogue` | *(absence — no file)* |

**Positive control**: `good/valid-catalogue.json` returns `emptyList()`.

**Negative control** (SC-002): mutate any single point value in the good fixture and the run must
fail. A contract that passes a corrupted catalogue is not a contract.

## Cross-cutting requirement

Every bad fixture differs from the good one in **exactly one way**. A fixture with two defects
cannot prove which rule caught it. This is the difference between a suite that demonstrates coverage
and one that merely appears to.

## Non-goals

Not a linter — says nothing about label wording or Arabic correctness. Not a migration tool. Does
not decide *which* version applies to a date at runtime; it only guarantees that question has
exactly one answer.
