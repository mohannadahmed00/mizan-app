# Phase 1 Data Model: Maghrib-Anchored Day and Week Boundary

**Feature**: `specs/009-maghrib-day-boundary` | **Date**: 2026-08-30

Three layers: pure domain types in `:domain`, one device-local Room table, and one versioned seed.
Nothing here is synchronisable and nothing here is a record of a day — the recorded history tables
are untouched by this feature, which is the point of FR-021.

---

## 1. Domain types (`:domain` — no Android, no Room, no Adhan, no `kotlinx-datetime`)

### `Coordinates`

| Field | Type | Notes |
|---|---|---|
| `latitude` | `Double` | Degrees, −90..90 |
| `longitude` | `Double` | Degrees, −180..180 |

Validated in `init`. Carries no accuracy, timestamp or provider — the calculation needs none of them,
and FR-006 forbids retaining more than the calculation requires.

### `CalculationConvention`

```
MUSLIM_WORLD_LEAGUE | UMM_AL_QURA | EGYPTIAN | ISNA | KARACHI | ...
```

Plus `asr: AsrMadhab` (`STANDARD | HANAFI`). Which authorities are populated is a seed question, not
a type question; the enum names authorities the mapping actually references.

`MUSLIM_WORLD_LEAGUE` with `STANDARD` Asr is the documented default of FR-003c.

### `PrayerTimesOutcome`

```
Calculated(times: PrayerTimes)
| NoLocation
| CalculationFailed(reason)
```

Exactly the three outcomes the spec's Key Entities name. `NoLocation` and `CalculationFailed` are
values, not exceptions — FR-015 forbids a crash and a thrown exception would put the choice of
fallback in a `catch` block somewhere rather than in the one place that decides it.

`PrayerTimes` carries the five prayer instants for one date at one location. This feature reads only
`maghrib`; the rest exist because the provider is the single one Principle VII requires and spec 010
consumes the same type.

### `BoundaryRegime`

| Value | Meaning |
|---|---|
| `Maghrib` | Coordinates held and trusted; the boundary is calculated (FR-012) |
| `Fallback(reason)` | Local midnight and Saturday-to-Friday are in force (FR-013) |

`reason` is `NEVER_HAD_LOCATION`, `ERASED`, or `ZONE_CHANGED_AWAITING_FIX` — the three and only three
routes to the fallback (FR-013, FR-017c, FR-012b). It exists so FR-016 and FR-012d can tell the
person *which* happened rather than only that something did.

### `BoundaryState`

The resolved state `today()` reads (research R3). Held in memory, persisted across process death.

| Field | Type | Notes |
|---|---|---|
| `regime` | `BoundaryRegime` | Which rule is in force right now |
| `coordinates` | `Coordinates?` | Null exactly when the regime is `Fallback` |
| `zoneIdWhenObtained` | `String?` | The trust test of FR-012a/FR-012b |
| `resolvedDate` | `LocalDate` | The current accountability date |
| `expiresAt` | `Instant` | When `resolvedDate` next changes — the day's end. Under `Maghrib` this is the **next** Maghrib, which is tomorrow's whenever the current instant is already past today's; under `Fallback` it is the next local midnight |
| `lastResolvedDate` | `LocalDate?` | The clamp's memory (FR-022, FR-023) |
| `lastResolvedRegime` | `BoundaryRegime?` | Which regime `lastResolvedDate` was resolved under. This, not the date, is what identifies a seam (FR-023a) |

**Invariant**: `resolvedDate` is always populated. There is no "unknown day" state, which is what
makes FR-015 and SC-010 hold. `expiresAt` is likewise always populated and, immediately after a
successful resolution, always in the future — FR-026's in-app rollover fires at it and `StreakClock`
takes it as `dayEndsAt`, so an absent or stale value would silently disable both.

### `DayBoundary` (changed)

```
dateAt(instant: Instant, zone: ZoneId, maghribOnCivilDate: Instant?): LocalDate
```

Take the civil date of `instant` in `zone`. If `maghribOnCivilDate` is null, return it unchanged —
the fallback regime. Otherwise return the next day when `instant` is at or after that Maghrib, and
that civil date otherwise.

Still an `object` with no state and no clock, still the only place this rule exists (FR-010).

### `WeekBoundary` — **unchanged**

Kept in this document only to record that it is deliberately untouched. Maghrib on Friday starts
accountability-Saturday, so "the Saturday on or before" remains correct in accountability-date space
(research R1). Changing it would create the second week rule Principle VII forbids.

### `ResolveBoundaryDate` (new, pure)

```
resolveBoundaryDate(
    computed: LocalDate,
    lastResolved: LocalDate?,
    regimeChanged: Boolean,
): LocalDate
```

The clamp, **armed only across a regime change**. It returns `computed` when there is no previous
date, and returns `computed` unchanged whenever `regimeChanged` is false. At a seam it returns a date
never less than `lastResolved` and never more than `lastResolved.plusDays(1)`, which is what makes a
skipped or duplicated accountability date unrepresentable across a transition (FR-022, FR-023,
research R7).

Clamping *every* resolution would be a defect rather than extra safety: after a five-day absence it
would hand back `lastResolved + 1` and credit that evening's completions to a date that closed four
days ago, and it would make the answer depend on how often the app was opened — which FR-017 forbids
and FR-023a now states outright.

### `ConventionForRegion` (new, pure)

```
conventionFor(zoneId: String, mapping: RegionConventionMapping): SelectedConvention
```

`SelectedConvention` is the pair the calculation actually needs — `convention: CalculationConvention`
and `asr: AsrMadhab`. One type rather than two return values, because the madhab is part of what an
authority defines: splitting them invites a caller to take the convention from the mapping and the
madhab from a default, which is a second opinion within a region by another route.

Total by construction: an unmatched zone returns the mapping's documented default (FR-003c), which is
itself a `SelectedConvention`, so the matched and unmatched paths return the identical shape. Reads
no clock, no location and no device state — the zone id is passed in.

### `StreakClock` (changed)

| Before | After |
|---|---|
| `AT_RISK_FROM: LocalTime = 20:00` | `AT_RISK_BEFORE_END: Duration` |
| `isAtRiskWindow(now, zone)` | `isAtRiskWindow(now, dayEndsAt)` |
| `nextBoundaryAfter(now, zone)` | `nextBoundaryAfter(now, dayEndsAt)` |

Still the single home of the at-risk rule; still reads no clock. `dayEndsAt` comes from
`BoundaryState.expiresAt`, so the at-risk point moves with the day rather than with the wall clock
(FR-029, research R8).

---

## 2. Local storage (`:data`, Room)

### `boundary_state` — new table, one row

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER` PK | Always `0`. Same single-row shape as `account_scope`. |
| `latitude` | `REAL` | Nullable — null while no coordinates are held |
| `longitude` | `REAL` | Nullable |
| `zoneIdWhenObtained` | `TEXT` | Nullable; the FR-012a/FR-012b trust test |
| `obtainedAt` | `INTEGER` | Nullable; disclosure only (FR-017b), never a trust input — FR-012a forbids age invalidating coordinates |
| `lastResolvedDate` | `TEXT` | Nullable ISO date; the clamp's memory |
| `lastResolvedRegime` | `TEXT` | Nullable; which regime that date was resolved under, so the seam survives process death (FR-023a) |
| `promptShown` | `INTEGER` | Non-null, `DEFAULT 0`. Whether the first-launch prompt has been answered (FR-007b). The entity **must** carry `@ColumnInfo(defaultValue = "0")` or the exported schema disagrees with the migrated database and `runMigrationsAndValidate` fails; `ParticipationStateEntity` is the precedent |

**Deliberately not synchronisable.** No client-generated UUID, no `updatedAt`, no soft-delete marker,
no `userId`. Principle V governs synchronisable rows; coordinates must never leave the device
(FR-006), and each device resolves its own boundary by design. `account_scope` is the existing
precedent for a device-local settings singleton.

**Erasing coordinates** (FR-017c) nulls `latitude`, `longitude`, `zoneIdWhenObtained` and
`obtainedAt` in place. It is a genuine erase, not a tombstone — Principle V's tombstone rule applies
to synchronisable records, and retaining a marker of a location the person asked to be rid of would
defeat the control.

### `MIGRATION_4_5` — purely additive

Creates `boundary_state`. No column dropped, renamed or rewritten; no existing row touched; nothing
backfilled. The absent row is the correct initial state and means "no coordinates ever held", which
is exactly FR-013's starting regime.

**It must be registered, and so must `MIGRATION_3_4`.** Room runs only the migrations passed to
`addMigrations`, and `MizanDatabaseFactory.createMizanDatabase` currently passes `MIGRATION_1_2` and
`MIGRATION_2_3` alone — `MIGRATION_3_4` is declared in `MizanDatabase.kt` and handed to nothing, so a
database at version 3 has no path forward at all and `MIGRATION_4_5` would sit unreachable behind it.
Both are registered in this increment.

Schema exported to `data/schemas/…/5.json` and committed — the `develop-v1` → `main` release gate.

### Tables **not** changed

`day_plans`, `planned_tasks`, `completions`, `catalogue_*`, `outbox`, `sync_cursors`,
`account_scope`, `leaderboard_cache`, `participation_state`. Listed explicitly because FR-021 is the
non-negotiable requirement in this feature and "which tables does it write" is the fastest way to
verify it. In particular `completions.creditedDate` is never rewritten — a completion keeps the
accountability date the recording device assigned it (FR-031).

---

## 3. Seed content

### `region-conventions.json` (`:domain` resources, versioned)

Administrator-defined, loaded idempotently, versioned like the task catalogue — fixed content in the
sense Principle VI already uses (FR-003, constitution v2.0.1 Principle VII).

Shape and rules in [contracts/region-conventions.md](./contracts/region-conventions.md). A change to
it affects future days only (FR-003e); it is consulted when resolving a boundary, never when reading
a stored day.

---

## State transitions

The regime moves between exactly two states, by four events. Every transition runs through the clamp
(FR-022–FR-024), so none of them can skip or duplicate a date — and a transition is the *only* time
the clamp is armed, because it is the only time two different rules meet at one instant (FR-023a).

| From | Event | To |
|---|---|---|
| `Fallback(NEVER_HAD_LOCATION)` | First fix obtained | `Maghrib` |
| `Maghrib` | Person erases coordinates (FR-017c) | `Fallback(ERASED)` |
| `Maghrib` | Zone id changes, no fresh fix yet (FR-012b) | `Fallback(ZONE_CHANGED_AWAITING_FIX)` |
| `Fallback(ZONE_CHANGED_AWAITING_FIX)` | Fresh fix obtained (FR-012c) | `Maghrib` |
| `Maghrib` | Permission revoked, coordinates retained | `Maghrib` — **no transition** (FR-017a) |
| `Maghrib` | 90 offline days, zone unchanged | `Maghrib` — **no transition** (FR-012a, SC-005) |

The last two rows are transitions that deliberately do **not** happen, and each has a success
criterion asserting it.
