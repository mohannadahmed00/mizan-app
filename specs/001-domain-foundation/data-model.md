# Phase 1 Data Model: Domain Foundation

**Feature**: 001-domain-foundation | **Date**: 2026-08-08

Model types backing the validation contract. Pure Kotlin — stdlib and `java.time` only, zero
Android, zero framework. Lives in `app/src/test/kotlin/.../catalogue/model/` this increment; moves
to `:domain` in Phase 2.

Only entities the contract needs are modelled here. Day Plan, Planned Task, Completion, Daily/Weekly
Score, Consistency Day and Streak are **glossary terms only** in this feature — defined in
`docs/GLOSSARY.md`, given no type until the phase that records them.

---

## Catalogue

Root aggregate. What a catalogue file parses into.

| Field | Type | Rules |
|---|---|---|
| `versions` | `List<CatalogueVersion>` | non-empty; strictly increasing by `version`; see FR-004a/b |
| `sections` | `List<Section>` | non-empty; unique `id`; unique `order` |
| `tasks` | `List<TaskDefinition>` | non-empty; unique `slug` |

---

## CatalogueVersion

| Field | Type | Rules |
|---|---|---|
| `version` | `Int` | ≥ 1; unique; monotonically increasing (FR-004) |
| `effectiveFrom` | `LocalDate` | unique across versions; order must agree with `version` order (FR-004a/b) |

**Resolution rule**: the version current for date *d* is the one with the greatest `effectiveFrom`
that is ≤ *d*. If no `effectiveFrom` is ≤ *d*, **no version is current** — the contract reports this
rather than falling back to the earliest (edge case, spec).

This pairing is what makes Phase 3's backfill possible for a day the user never opened.

---

## Section

| Field | Type | Rules |
|---|---|---|
| `id` | `String` | slug form; unique |
| `label` | `String` | non-blank; Arabic content, treated as data |
| `order` | `Int` | unique across sections; defines section display order |

Sections carry their own ordering. Task display positions are scoped inside a section
(FR-003a) — two tasks in different sections may share a position.

---

## TaskDefinition

Identity and placement. Stable across point and schedule changes.

| Field | Type | Rules |
|---|---|---|
| `slug` | `String` | `^[a-z0-9]+(-[a-z0-9]+)*$`; unique across catalogue; never reused, never changed (FR-002) |
| `sectionId` | `String` | must resolve to a `Section.id` |
| `displayPosition` | `Int` | ≥ 1; unique **within its section** (FR-003a) |
| `label` | `String` | non-blank; Arabic content, treated as data |

---

## TaskVersion

What a Task Definition was worth under a given catalogue version. The thing a past day is scored
against.

| Field | Type | Rules |
|---|---|---|
| `taskSlug` | `String` | must resolve to a `TaskDefinition.slug` |
| `catalogueVersion` | `Int` | must resolve to a `CatalogueVersion.version` |
| `points` | `Int` | **> 0** — zero and negative rejected (FR-003, Principle IX) |
| `scheduleRule` | `ScheduleRule` | exactly one; must match ≥ 1 weekday |
| `maxOccurrencesPerDay` | `Int` | ≥ 1 (FR-003) |

---

## ScheduleRule

Sealed. Closed for now, open to extension without redefining existing variants (FR-005).

| Variant | Fields | Meaning |
|---|---|---|
| `EveryDay` | — | applies to every date |
| `DaysOfWeek` | `days: Set<DayOfWeek>` | non-empty; applies on those weekdays |
| *`DateAnchored`* | *reserved* | Ramadan, Ashura — **not implemented this increment** |

`DateAnchored` is named in the glossary and left unimplemented. Adding it later must not change
`EveryDay` or `DaysOfWeek`. Not building it now is Principle VIII; naming it now is FR-005.

---

## CatalogueDefect

The validator's output vocabulary. Sealed, one variant per rule, each carrying enough context to fix
the file without re-running.

| Variant | Carries | Rule |
|---|---|---|
| `DuplicateTaskSlug` | slug, count | FR-002 |
| `MalformedSlug` | slug | FR-002 |
| `NonPositivePoints` | slug, points | FR-003 |
| `InvalidOccurrenceLimit` | slug, value | FR-003 |
| `UnknownSection` | slug, sectionId | FR-003 |
| `DuplicateDisplayPosition` | sectionId, position, slugs | FR-003a |
| `DuplicateSectionOrder` | order, sectionIds | FR-003a |
| `UnreachableSchedule` | slug | FR-005 |
| `VersionOrderMismatch` | version, effectiveFrom | FR-004a |
| `DuplicateEffectiveFrom` | date, versions | FR-004b |
| `WeekdayTotalMismatch` | dayOfWeek, expected, actual | FR-006 |
| `WeekTotalMismatch` | expected, actual | FR-006 |
| `SectionCompositionMismatch` | sectionId, expected, actual | FR-007 |
| `UserAuthoringAffordance` | field | FR-019 |
| `MalformedCatalogue` | message | FR-011 (parse failure) |
| `NoCatalogue` | path | FR-011 (absent file) |

Fifteen defect variants, fifteen bad fixtures, one per rule. `NoCatalogue` needs no fixture file —
its fixture is the absence of one.

---

## Expected arithmetic (the fixture's target)

Contract asserts these against the known-good fixture. Derived from `docs/PLAN.md`.

```text
Fajr      6 tasks x 2 = 12
Dhuhr     4 tasks x 2 =  8
Asr       3 tasks x 2 =  6
Maghrib   3 tasks x 2 =  6
Isha      3 tasks x 2 =  6      -> prayer subtotal 38
Qiyam/Witr                 9    -> 47
Quran (memorisation+reading) 4  -> 51
Adhkar    9 tasks x 2 = 18      -> 69   BASE DAY

Sat, Sun, Tue, Wed            = 69
Mon, Thu   = 69 + fast 5      = 74
Fri        = 69 + 7 x 1       = 76

Week = 69x4 + 74x2 + 76 = 276 + 148 + 76 = 500
```

The known-good fixture uses **placeholder Latin labels**. Task text is irrelevant to every rule the
contract enforces, and the real Arabic content arrives in `002`.

---

## What is deliberately absent

No persistence types, no DAOs, no entities, no DTOs. No `Instant`, no clock, no timezone — the
validator reads no time (Principle VII); `effectiveFrom` is data passed in.
