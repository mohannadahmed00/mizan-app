# Phase 1 Data Model: Leaderboards & Honor Board

**Feature**: `specs/008-leaderboard-honor-board` | **Date**: 2026-08-29

Three layers: pure domain types in `:domain`, a disposable local cache in Room, and the remote
aggregate that is the only authority on any position. The direction of authority is one-way — the
server computes, the client renders.

---

## 1. Domain types (`:domain`, no Android, no Room, no Supabase)

### `PeriodKind`

```
DAILY | WEEKLY | MONTHLY
```

The three periods FR-020 requires. Rankings exist for all three; the Honor Board for `WEEKLY` and
`MONTHLY` only (FR-027a).

### `LeaderboardPeriod`

| Field | Type | Notes |
|---|---|---|
| `kind` | `PeriodKind` | |
| `start` | `LocalDate` | Inclusive |
| `endInclusive` | `LocalDate` | |
| `regionId` | `RegionId` | The period only means anything within a region |

**Derivation is a pure function.** `periodFor(kind, date, zone)` returns the period containing
`date` when evaluated in `zone`:

- `DAILY` — `start == endInclusive == date`.
- `WEEKLY` — delegates to the existing `WeekBoundary`. Saturday to Friday, **not reimplemented
  here**; FR-011 forbids a second definition of the week.
- `MONTHLY` — calendar month containing `date`.

The zone comes from the region, never from the device (FR-010). The function takes a zone rather
than reading one, so it stays pure and testable, as `buildStreakSummary` does.

### `RegionId`

Opaque identifier for an administrator-defined region. The domain never constructs one — it is
assigned by the account service (FR-014) and arrives with the ranking.

### `Region`

| Field | Type | Notes |
|---|---|---|
| `id` | `RegionId` | |
| `displayName` | `String` | Shown on the ranking surface (FR-016) |
| `zone` | `ZoneId` | Fixes every period boundary in this region (FR-010) |

### `RankingEntry`

| Field | Type | Notes |
|---|---|---|
| `userId` | `String` | Present so the client can mark its own row; never displayed |
| `displayName` | `String` | Neutral placeholder when unset (FR-007) |
| `points` | `Int` | Server-computed. Never summed or adjusted locally |
| `position` | `Int` | Server-assigned, 1-based |
| `isViewer` | `Boolean` | Derived client-side by matching the session's user id |

**Deliberately absent**: no `isLast`, no `isBottom`, no `trend`, no `positionChange`, no
`pointsBehindLeader`. FR-038 forbids distinguishing a low position, and FR-039 forbids rank-drop
signalling; the way to guarantee neither happens is for the data not to exist (research R8).

### `Ranking`

| Field | Type | Notes |
|---|---|---|
| `period` | `LeaderboardPeriod` | Carries its own boundaries, so the surface can state exactly which span the total covers (FR-026) |
| `region` | `Region` | |
| `entries` | `List<RankingEntry>` | A bounded page, position-ordered |
| `hasMore` | `Boolean` | Drives extend-on-demand (FR-024) |
| `retrievedAt` | `Instant` | Stamped so age can be shown (FR-036) |
| `isProvisional` | `Boolean` | True while the viewer has unsynced completions (FR-037) |

### `OwnRank`

| Field | Type | Notes |
|---|---|---|
| `entry` | `RankingEntry` | The viewer's own row |
| `neighbours` | `List<RankingEntry>` | Entries immediately around it |
| `totalParticipants` | `Int` | Region size, for context — never framed as "you beat N" |

Returned by a dedicated lookup, which is what satisfies FR-023 — the viewer reaches their own row
without scrolling an unbounded list — and lets SC-009 hold at 10 000 participants without paging
(research R9).

### `TieBreak`

A pure, total ordering for equal totals (FR-022). Ordering is by `points` descending, then by
**who reached the total earliest** — the recorded time of the last completion contributing to it.
The function is pure, so the same inputs always produce the same order, and no comparison is
expressed as one participant beating another.

The recorded time is device-reported, so a manipulated client could reorder a tie. FR-022a bounds
that exposure explicitly: it cannot change any total, days-engaged figure or region, and cannot put
a participant above anyone with a higher total. Ordering *within* an identical total is all it
reaches.

### `HonorBoardMember`

| Field | Type | Notes |
|---|---|---|
| `displayName` | `String` | |
| `isViewer` | `Boolean` | |

No points, no days count, no position — FR-029 forbids ordering or a points figure distinguishing
members.

### `HonorBoard`

| Field | Type | Notes |
|---|---|---|
| `period` | `LeaderboardPeriod` | |
| `region` | `Region` | |
| `members` | `List<HonorBoardMember>` | Unordered by achievement; presentation order is stable but not competitive |
| `viewerQualified` | `Boolean` | |
| `retrievedAt` | `Instant` | |

**Deliberately absent**: no `nonQualifierCount`, no `thresholdDistance`, no `daysShort`, no
`threshold` itself. FR-030 and SC-012 require that none of this be *retrievable*, not merely
unrendered (research R8).

### `qualifiesForHonorBoard`

```
qualifiesForHonorBoard(daysEngaged: Int, threshold: Int): Boolean  =  daysEngaged >= threshold
```

Pure. Points are not a parameter — the function cannot consult them even by accident, the same
device `buildStreakSummary` uses to keep the catalogue out of streak logic.

**Defined for `WEEKLY` and `MONTHLY` only** (FR-027a). The daily period has a ranking and no Honor
Board: a days-engaged threshold over one day can only be 0 or 1, so a daily board would recognise
everyone active and devalue the weekly board beside it. `HonorBoardState` for `DAILY` is therefore
not a thing the read model can express.

### `Participation`

| Field | Type | Notes |
|---|---|---|
| `optedIn` | `Boolean` | Off by default for every account (FR-001) |
| `region` | `Region?` | Null until assigned |

---

## 2. Local storage (Room, migration 3 → 4, additive only)

Both tables are **disposable**. Deleting every row loses nothing but a render — no history, no
points, no streak. This is what keeps the leaderboard clear of Principle V and lets FR-034 hold.

### `leaderboard_cache`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT, PK | `"$periodKind:$periodStart:$regionId"` — deterministic, so a refresh replaces rather than accumulates |
| `periodKind` | TEXT | |
| `periodStart` | TEXT | ISO date |
| `regionId` | TEXT | |
| `regionDisplayName` | TEXT | |
| `payload` | TEXT | The retrieved page, serialised |
| `retrievedAt` | INTEGER | Epoch millis — rendered as age, never as "now" |

Deterministic ids reuse the outbox convention Phase 7 established, for the same reason: writing the
same refresh twice is a no-op rather than a duplicate.

### `participation_state`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER, PK | Always `1` — one account's state per device, matching `account_scope` |
| `optedIn` | INTEGER | 0/1, default 0 (FR-001) |
| `regionId` | TEXT, nullable | Assigned by the service |
| `regionDisplayName` | TEXT, nullable | |
| `updatedAt` | INTEGER | |

**Cleared by `LocalRecordWipe`.** Phase 7's wipe already clears `outbox`, `sync_cursors` and
`account_scope`; both new tables join that list, so signing out does not leave one account's cached
ranking or consent visible to the next (FR-008).

### Migration 3 → 4

Two `CREATE TABLE` statements. No column dropped, renamed, retyped or rewritten. Schema exported to
`data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/4.json` and committed; `1.json`, `2.json`
and `3.json` must remain byte-identical.

---

## 3. Remote storage (Postgres, Supabase)

Full DDL in [contracts/remote-schema-008.sql](./contracts/remote-schema-008.sql). Shape and
guarantees here.

### `regions`

Administrator-defined. `(id, display_name, zone, is_fallback)`. Readable by any signed-in client
(needed to label a ranking); **writable by none** — FR-017.

### `region_zone_map`

`(zone, region_id)`. Maps a reported IANA zone to a region. Readable by no client at all — the
client reports a zone and is told its region; it never sees the mapping, so it cannot reverse it to
find a favourable zone to claim (FR-014, research R3).

### `leaderboard_participation`

| Column | Notes |
|---|---|
| `user_id` | PK, references the account |
| `opted_in` | Default **false** (FR-001) |
| `region_id` | Assigned server-side from the reported zone |
| `reported_zone` | The client's claim, stored for re-evaluation on change (FR-013) |
| `updated_at` | |

Policies: a participant may select and update **their own row only**. Setting `opted_in = false`
triggers withdrawal (below).

### `leaderboard_entries` — the aggregate

| Column | Notes |
|---|---|
| `period_kind`, `period_start`, `region_id` | Period identity |
| `user_id` | |
| `display_name` | Denormalised at aggregation time |
| `points` | `sum(points_awarded)` over non-reversed completions |
| `days_engaged` | `count(distinct credited_date)` over the same |
| `position` | Assigned by the job, tie-broken deterministically |

**This is the only table any participant reads about anyone else, and it holds only what FR-002
says opting in publishes.**

Policies:

- **Select**: permitted only for rows in the reader's own region, and only for opted-in
  participants. A participant cannot read another region (SC-007).
- **Insert / update / delete**: **no policy exists for any client.** Written solely by the scheduled
  job under elevated privilege. This is what makes SC-006 structural — there is no client write path
  to defend (research R1).

### `leaderboard_periods` — the period lifecycle, made explicit

`(period_kind, period_start, region_id, state, closed_at)` where `state` is `OPEN` or
`CLOSED`. There is no intermediate state: a period freezes at its boundary (FR-025).

This table exists so that "a closed period never changes" is a **join condition rather than a
convention**. Both mutating paths — the aggregation job and the withdrawal delete — are scoped to
periods whose state is not `CLOSED`, so neither can reach a closed period even by mistake.

Readable by any signed-in client, so a ranking can say whether it is final. Writable by none.

### `honor_board_closed`

Frozen membership for periods that have closed. `(period_kind, period_start, region_id, user_id,
display_name)`.

Written once, when a period settles. No client has an insert, update or delete policy, and the
withdrawal function is scoped to open periods, so nothing alters it afterwards (FR-004a, FR-031,
research R7).

### The aggregation job

Runs on a schedule. For each region and each **open** period:

1. Select completions where `reversed_at is null`, joined to opted-in participants in that region,
   grouped by `(user_id, period)` over the stored `credited_date`.
2. Compute `points` and `days_engaged`; assign `position` with the tie-break.
3. Replace that period's rows for that region.
4. When the period's boundary passes in the region's timezone, write qualifying members into
   `honor_board_closed` — for `WEEKLY` and `MONTHLY` only (FR-027a) — set the period's state to
   `CLOSED`, and stop recomputing it. There is no settlement window: the freeze is immediate
   (FR-025).

The job's working set is `leaderboard_periods` where `state <> 'CLOSED'`, so a closed period is not
merely skipped by convention — it is not selected. The job **reads** completions and **writes** only
the aggregate, the closed Honor Board and the period state. It has no path that alters a completion,
a day record or a points figure — Principle III.

---

## 4. State transitions

### Participation

```
NOT_OPTED_IN  ──opt in──▶  OPTED_IN(region assigned)
     ▲                            │
     └──────── opt out ───────────┘
```

Opting out: delete the user's `leaderboard_entries` rows **only for periods whose state is not
`CLOSED`**, and remove open-period Honor Board membership. Closed periods — rankings and
`honor_board_closed` alike — are untouched, because the delete is scoped by a join on
`leaderboard_periods` and closed periods are not in it (FR-004, FR-004a). While opted out, the
aggregation job does not enter the participant into any newly opening period (FR-004b). Local
`participation_state` and `leaderboard_cache` are cleared. Recorded history, points, streak and
insights are not touched by any of this (FR-005, SC-003).

The asymmetry is worth stating once: leaving is immediate and total *going forward*, and changes
nothing *backwards*. FR-002a requires a participant to be told this before they opt in, because it
is the one commitment they cannot take back.

Signing out ends participation for the session (FR-008); `LocalRecordWipe` clears both local tables.

### Period

```
OPEN ──region-local boundary passes──▶ CLOSED (immutable)
```

No intermediate state and no settlement window: the freeze is immediate (FR-025). Only `OPEN`
periods are in the job's working set, and only they are in the withdrawal delete's. **A `CLOSED`
period admits no mutation whatsoever** — not a late completion, not a catalogue change, not an
opt-in, not an opt-out (FR-025, FR-031, FR-004a, research R5).

The cost of an immediate freeze is that a participant who records offline and syncs after the
boundary scores nothing for those days here. FR-025a keeps that confined to the leaderboard — their
own records, points, streak and insights count the days in full — and FR-025b requires it to be
disclosed before they opt in.

There is no exception to that sentence, and that is deliberate. The earlier draft carved out one —
withdrawal could delete closed ranking rows but not closed Honor Board rows — and a rule with a
carve-out is a rule that regresses the first time someone refactors the delete.

### Region assignment

```
zone reported ──▶ mapped to region ──▶ stored on the account
      ▲                                        │
      └──── device zone changes (FR-013) ──────┘
```

Re-evaluation on zone change keeps FR-012 true as a participant travels. Rankings already written
for closed periods in the previous region are not rewritten.

---

## 5. What is deliberately not modelled

Recorded so a reviewer does not have to infer intent from an absence:

- **No cross-region ranking**, no global position, no country/region comparison. Out of scope.
- **No rank history**, no `positionLastPeriod`, no trend. FR-039 forbids rank-drop signalling, and
  storing the data invites it.
- **No threshold or shortfall in any client-visible shape.** Research R8.
- **No follower, friend, group or challenge entity.** Principle VIII, and the spec's Out of Scope.
- **No local aggregation of any kind.** The client never sums points for a ranking, so no local type
  can disagree with the server's.
