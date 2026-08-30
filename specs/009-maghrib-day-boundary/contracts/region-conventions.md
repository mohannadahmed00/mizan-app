# Contract: Region-to-Convention Mapping

**Feature**: `specs/009-maghrib-day-boundary`

Administrator-defined seed content naming which calculation authority applies where. Fixed content in
the sense Principle VI already uses for the task catalogue: versioned, loaded idempotently, never
authored by a user, never exposed as a setting.

Required by constitution v2.0.1 Principle VII and by FR-003, FR-003a, FR-003c and FR-003e.

---

## Format

`domain/src/main/resources/prayer/region-conventions.json`

```json
{
  "version": 1,
  "default": { "convention": "MUSLIM_WORLD_LEAGUE", "asr": "STANDARD" },
  "regions": [
    { "zoneIds": ["Africa/Cairo"],  "convention": "EGYPTIAN",     "asr": "STANDARD" },
    { "zoneIds": ["Asia/Riyadh"],   "convention": "UMM_AL_QURA",  "asr": "STANDARD" }
  ]
}
```

| Field | Rule |
|---|---|
| `version` | Increments on any change. A change affects future days only (FR-003e). |
| `default` | Mandatory. The documented default of FR-003c — MUSLIM_WORLD_LEAGUE / STANDARD. |
| `regions[].zoneIds` | IANA zone ids, matched exactly. Non-empty. |
| `regions[].convention` | Must name a `CalculationConvention` the code knows. |
| `regions[].asr` | `STANDARD` or `HANAFI`. |

The mapping is keyed by IANA zone id because that is the only region signal available on-device with
no network call and no permission (FR-003b, research R6). It is the same signal FR-012b uses to
invalidate stale coordinates.

---

## Validation

Enforced by a test over the shipped resource, in the spirit of the existing `CatalogueValidator`:

1. `default` is present and names a known convention and madhab.
2. Every `convention` and `asr` value resolves to a known enum constant.
3. No zone id appears in two entries — **exactly one convention applies in any given region**
   (constitution v2.0.1 Principle VII).
4. Every `zoneIds` array is non-empty and every id parses as a valid `ZoneId`.
5. `version` is a positive integer.

Rule 3 is the one that matters most: a duplicate zone id is the "second opinion within a region" the
constitution forbids, and it would make the convention depend on iteration order.

---

## Lookup

`ConventionForRegion.conventionFor(zoneId, mapping)` — a pure function, total by construction. An
unmatched zone returns `default`; it never fails, never asks, and never guesses (FR-003c).

It returns a `SelectedConvention(convention: CalculationConvention, asr: AsrMadhab)` — the same shape
the mapping's `default` entry deserialises to, so the matched and unmatched paths hand back an
identical type and no caller can assemble a half-default by taking the convention from one place and
the madhab from another.

Populating the mapping beyond the regions the product actually serves is out of scope. Principle VIII
forbids adding entries for regions no one is in yet; the default already covers them correctly.

---

## What this mapping is not

- **Not a user setting.** No screen exposes it, and there is no per-person override of the
  convention, the authority or the Asr madhab anywhere in the product (FR-003a).
- **Not a source of history.** It is consulted when resolving a boundary, never when reading a stored
  day. A day already closed keeps the convention it closed under (FR-003e, FR-021).
- **Not remotely fetched.** It ships in the APK. Fetching it would put the day boundary behind
  connectivity (FR-002, Principle IV).
