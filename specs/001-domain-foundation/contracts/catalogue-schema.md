# Contract: Catalogue File Schema

**Feature**: 001-domain-foundation | **Date**: 2026-08-08

The catalogue file is this project's only content interface. `002` authors a file against this
schema; Phase 2 seeds from it; a future server serves it (Principle VI: "changes the source and not
the shape"). Freezing the shape here is what makes that swap a source change.

Format: **JSON**. Encoding UTF-8. See [research.md](../research.md) R2 for why not YAML.

## Shape

```json
{
  "versions": [
    { "version": 1, "effectiveFrom": "2026-01-01" }
  ],
  "sections": [
    { "id": "fajr", "label": "<Arabic>", "order": 1 }
  ],
  "tasks": [
    {
      "slug": "fajr-sunnah-before",
      "sectionId": "fajr",
      "displayPosition": 1,
      "label": "<Arabic>"
    }
  ],
  "taskVersions": [
    {
      "taskSlug": "fajr-sunnah-before",
      "catalogueVersion": 1,
      "points": 2,
      "maxOccurrencesPerDay": 1,
      "scheduleRule": { "type": "everyDay" }
    }
  ]
}
```

## Field rules

| Path | Type | Required | Rule |
|---|---|---|---|
| `versions[].version` | integer | yes | ≥ 1, unique, monotonically increasing |
| `versions[].effectiveFrom` | string | yes | ISO-8601 date `YYYY-MM-DD`; unique; order agrees with `version` |
| `sections[].id` | string | yes | slug form; unique |
| `sections[].label` | string | yes | non-blank |
| `sections[].order` | integer | yes | unique |
| `tasks[].slug` | string | yes | `^[a-z0-9]+(-[a-z0-9]+)*$`; unique; never reused |
| `tasks[].sectionId` | string | yes | must resolve to a `sections[].id` |
| `tasks[].displayPosition` | integer | yes | ≥ 1; unique **within its section** |
| `tasks[].label` | string | yes | non-blank |
| `taskVersions[].taskSlug` | string | yes | must resolve to a `tasks[].slug` |
| `taskVersions[].catalogueVersion` | integer | yes | must resolve to a `versions[].version` |
| `taskVersions[].points` | integer | yes | **> 0** |
| `taskVersions[].maxOccurrencesPerDay` | integer | yes | ≥ 1 |
| `taskVersions[].scheduleRule` | object | yes | see below |

## Schedule rule

Discriminated on `type`.

```json
{ "type": "everyDay" }
{ "type": "daysOfWeek", "days": ["MONDAY", "THURSDAY"] }
```

`days` uses `java.time.DayOfWeek` names, non-empty. A rule matching no weekday is a defect
(`UnreachableSchedule`).

`{"type": "dateAnchored"}` is **reserved and rejected** this increment. It is named so that adding
Ramadan and Ashura rules later extends the discriminator rather than redefining `everyDay` or
`daysOfWeek`.

## Forbidden by contract

Any field implying user authorship — `editable`, `userCreated`, `custom`, `deletable`, `sortable`,
or an `ownerId` — is a `UserAuthoringAffordance` defect (FR-019, Principle VI). The catalogue is
administrator content; a field that admits otherwise is rejected at the door rather than policed in
the UI later.

## Unknown fields

Rejected, not ignored. A typo'd key in a hand-authored 40-record file is a likely defect and silent
tolerance would let `pointz: 2` default a task to zero points.

## Stability guarantee

Additive changes only. A slug, once published, is permanent. A field, once required, stays required.
Anything else is a new catalogue version with a new `effectiveFrom`, never an edit in place
(Principle III).
