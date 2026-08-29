# Implementation Plan: Leaderboards & Honor Board

**Branch**: `spec/008-leaderboard-honor-board` | **Date**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-leaderboard-honor-board/spec.md`

## Summary

The first increment where one account's data becomes visible to another. Regional rankings over
daily, weekly and monthly periods, an Honor Board that recognises days engaged rather than points,
and an opt-in that is genuinely revocable — all as a read-only remote surface inside Progress that
the core loop never depends on.

Seven decisions carry the increment.

1. **The row-level policies do not widen. Not one of them.** Today every policy on `completions` and
   `day_records` is `_select_own`, and Phase 7's `RLS OK` verification exists to keep it that way. A
   leaderboard needs to show other people's figures, and the tempting move — a policy letting
   participants read each other's completions — would hand every participant the entire recording
   history of everyone in their region. Instead the client reads a **separate, purpose-built
   aggregate**: `leaderboard_entries`, holding only `(period, region, user_id, display_name,
   points, days_engaged)` for opted-in participants. Raw completions remain unreadable across
   accounts forever, and Phase 7's verification script is extended rather than relaxed (research R1).
2. **Aggregates are computed server-side, on a schedule, never by a client.** A scheduled job folds
   synced completions into `leaderboard_entries` per region and period. `points` is `sum(points_awarded)`
   over non-reversed completions; `days_engaged` is `count(distinct credited_date)` over the same. No
   client write path touches the table, and no client-reported total is trusted — FR-018, FR-019 and
   SC-006 fall out of the table simply having no client-writable policy (research R2, R4).
3. **Region is derived from a reported timezone, never from a claimed region.** The client sends its
   IANA zone id — which it already has, from `TimeProvider.zone()` — and the server maps zone → region
   through an administrator-defined table. A client that lies about its zone gets moved to a
   different region and ranked against people on its claimed clock; it cannot select a weak pool,
   because it does not name the pool (FR-014, research R3).
4. **The region's calendar date must equal the participant's own.** This is the correctness
   requirement behind FR-012 and the reason regions exist at all: the catalogue schedules Friday
   tasks, so a participant ranked inside a leaderboard Friday while their device says Thursday is
   compared against a denominator they never had. Regions are therefore *timezone bands whose local
   date agrees*, and `credited_date` — already stored per completion by Phase 7 and already computed
   on the device against `DayBoundary` — is what the aggregation groups by. The server never
   re-derives anyone's date (research R3, R5).
5. **Ranking is a cache, not a source of truth.** A new local table stores the last retrieved page
   per (period, region) with the instant it was fetched. The UI observes Room as everywhere else;
   the network writes into Room and never reaches a ViewModel. With the service down the cache
   renders, stamped with its age; with no cache the surface says unavailable. Nothing on the record,
   undo, view or score path gains a network call, so Principle IV holds by construction (research R6).
6. **A closed period never changes. No exception, no carve-out.** Clearing consent removes the
   participant from every period still open and keeps them out of every period that opens after,
   while closed periods stand exactly as they were — rankings and Honor Board alike. This makes
   FR-025 and FR-031 structural rather than conditional: a closed period is in no mutating code
   path at all, neither the aggregation job's working set nor the withdrawal delete. The cost is
   that a participant cannot erase past standings, which is why **FR-002a** requires that to be
   disclosed before they join (research R7).
7. **Principle IX is enforced in the read model, not only in the view.** The Honor Board endpoint
   returns qualifying members and nothing else — no non-qualifier count, no threshold distance, no
   "you were N days short" field for a client to render by accident. SC-012 is verified against what
   the client can *retrieve*, not against what it displays, so the guarantee cannot be undone by a
   later UI change (research R8).

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (unchanged)

**Primary Dependencies**: Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01, coroutines 1.10.2,
supabase-kt (`auth-kt`, `postgrest-kt`), `ktor-client-okhttp`, `androidx.work:work-runtime-ktx` —
all already present from Phase 7. **No new dependency is introduced.** The leaderboard is additional
tables and additional calls through the `RemoteDataSource` seam Phase 7 built.

**Storage**: Room remains the single local source of truth. **Migration 3 → 4, purely additive**:
two new tables (`leaderboard_cache`, `participation_state`). No column dropped, renamed or
rewritten. Schema exported to `data/schemas/…/4.json` and committed. Remote storage gains four
tables and one scheduled job, described in
[contracts/remote-schema-008.sql](./contracts/remote-schema-008.sql).

**Testing**: JUnit 4. `:domain` and `:app` on the JVM; `:data` instrumented for the migration, the
cache, region assignment and consent behaviour, all against an extended `FakeRemoteDataSource`.
Compose UI tests for the leaderboard surface, the opt-in flow and the Honor Board. Two guarantees a
fake cannot prove — that RLS still forbids cross-account raw reads, and that the aggregate exposes
nothing about non-qualifiers — are verified by SQL against the real project
([contracts/rls-verification-008.sql](./contracts/rls-verification-008.sql)), extending Phase 7's
script rather than replacing it.

**Target Platform**: Android, `minSdk 24`, `compileSdk 36`, `targetSdk 36`

**Performance Goals**: SC-014 — a first ranking readable within 3 s on a working connection, served
from a precomputed table rather than aggregated on request; an unavailable ranking resolves within
10 s rather than hanging. SC-009 — own row reachable in a 10 000-participant region without
scrolling, via a direct own-rank lookup rather than paging to find it. Ranking pages are bounded at
50 entries and extend on demand (FR-024).

**Constraints**: no network call on the record, undo, view or score path (Principle IV). `:domain`
keeps zero Android, zero Ktor, zero Supabase on its classpath (Principle II). `TimeProvider` remains
the only clock, and supplies the zone the client reports (Principle VII). No red, no failure
framing, no blame, and no last-place emphasis in any leaderboard string or colour (Principle IX).
**No row-level policy on an existing table is widened** (research R1).

**Scale/Scope**: 1 new screen area inside Progress (ranking + Honor Board + opt-in), 1 Room
migration, 2 new local tables, 4 new remote tables, 1 scheduled aggregation job, 3 new domain
interfaces, 4 new pure domain functions, 40 functional requirements, 14 success criteria.

## What `007` already provides

Verified against the merged code on `develop-v1` (`b235673`), not against its documents.

| Needed by this feature | Status in `develop-v1` |
|---|---|
| Accounts, sessions, passwordless sign-in | ✅ `AccountRepository`, `SupabaseAccountRepository` |
| A display name to rank under | ✅ `UpdateDisplayName`, `profiles.display_name` |
| Completions synced to the account with points frozen at write time | ✅ `completions.points_awarded`, never recomputed |
| `credited_date` computed on-device against `DayBoundary` | ✅ stored per completion; the server never re-derives it |
| Tombstones, so reversed completions can be excluded from a sum | ✅ `reversed_at`, set-only |
| A remote seam with a fake for tests | ✅ `RemoteDataSource` + `FakeRemoteDataSource` |
| Background execution | ✅ `SyncWorker`, `SyncScheduler`, WorkManager wired through Koin |
| RLS proven to forbid cross-account reads | ✅ `contracts/rls-verification.sql`, prints `RLS OK` |
| A device zone from the single clock seam | ✅ `TimeProvider.zone()` |
| A days-engaged notion to reuse for the Honor Board | ✅ `buildStreakSummary`'s consistency dates |
| Saturday-to-Friday week rule | ✅ `WeekBoundary` |
| Any cross-account read of any kind | ❌ every policy is `_select_own` — R1 |
| Any server-side aggregation | ❌ new — R2 |
| Any notion of region, consent, or ranking | ❌ new |
| A scheduled server job | ❌ new — R4 |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.1.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | Order per layer, as `002`–`007` established: `:domain` pure tests (period boundary derivation per region, tie-break ordering, days-engaged qualification, consent state) → domain code → domain interfaces and use-case tests → use cases → `:data` migration test → migration → cache/consent/region-assignment tests against the extended `FakeRemoteDataSource` → implementation → `SupabaseRemoteDataSource` contract tests → implementation → ViewModel tests → ViewModels → Compose UI tests → the Progress leaderboard surface, opt-in flow and Honor Board. Only Koin wiring and `@Preview` claim the exemption. |
| **II — Domain purity** | Yes | Three new interfaces (`LeaderboardRepository`, `ParticipationRepository`, `HonorBoardRepository`) declared in `:domain`, implemented in `:data`. Period-boundary derivation for a region, tie-break ordering and days-engaged qualification are pure functions over domain types. No Supabase, Ktor, WorkManager or DTO type enters `:domain`; `ModuleBoundaryTest` continues to enforce it. |
| **III — Immutable history (NON-NEGOTIABLE)** | Yes | Three mechanisms. (a) The aggregation **reads** completions and writes only to `leaderboard_entries`; it has no path that alters a completion, a day record or a points figure, and no client write path to the aggregate exists at all. (b) **A closed period is in no mutating code path.** The job's working set is open periods only, and the withdrawal delete is scoped to open periods only, so a completed period cannot be re-scored, re-ranked or partially erased by anything — not a late completion, not a catalogue change, not an opt-out (FR-025, FR-031, FR-004a). This is a single rule with no exception, which is what makes it hold: the earlier draft split withdrawal between closed rankings and closed Honor Boards, and a rule with a carve-out is one refactor away from being wrong. (c) Points stay frozen on the completion from Phase 7, so no aggregation can move an earned figure. The Principle III test this increment owes is `ClosedPeriodImmutabilityTest`: close a period, then change catalogue points, reverse a completion, add a late completion for that date, and opt a member out — and assert the closed period's standings and Honor Board membership are byte-identical throughout. |
| **IV — Offline-first** | Yes | The leaderboard is a read-only remote surface with a local cache. Record, undo, view and score gain no network call and no new suspend point. The UI observes Room; the network writes into Room and never reaches a ViewModel. FR-034 and SC-008 require the whole Phase 2–7 product to behave identically with the leaderboard service unreachable, and the surface is reached only from inside Progress, so a failure cannot block a navigation path anyone needs. |
| **V — Backend independence** | Yes | The three existing repository interfaces are untouched. The leaderboard adds new interfaces rather than modifying any, and its local cache is disposable — deleting every cached row loses nothing but a render. Turning the leaderboard off entirely returns the app to the Phase 7 product with no crash and no missing history. |
| **VI — Fixed content** | Yes | No surface creates, edits, deletes, reorders, reprices or reschedules a task. Regions and the Honor Board threshold are administrator-defined and shipped as configuration; FR-017 and FR-028 forbid user configuration of either, and there is no admin surface in the app. Participation consent is a user's choice about *their own visibility*, not authorship of content. |
| **VII — Deterministic time** | Yes | `TimeProvider` stays the only clock and is the source of the zone the client reports. `WeekBoundary` remains the single Saturday-to-Friday rule; regional periods apply it in the region's zone rather than reimplementing it. Crucially, **the server never derives anyone's date**: it groups by the `credited_date` the device already computed against `DayBoundary`, so there is exactly one place a date is decided, as there is today. |
| **VIII — Vertical slices** | Yes | One coherent capability: opt in, see where you stand in your region, be recognised for consistency, leave. Regions are a timezone grouping required by FR-012 today — not a social primitive built ahead of friends or cohorts, which the spec's Out of Scope section refuses explicitly. No realtime subscription, no push, no admin console, no moderation tooling, no cross-region view. |
| **IX — Encouragement** | Yes | The burden of this increment, and the reason several guarantees live in the read model rather than the view. The Honor Board endpoint cannot return a non-qualifier count or a threshold distance, so no client can render one by accident (SC-012). A last-place row uses the same container, the same colour and the same emphasis as every other row (FR-038). No rank-drop notification exists, and FR-039 makes that a requirement rather than an omission Phase 9 might fill. Copy is audited against the `CLAUDE.md` list before the increment is done (SC-013). |

**Technology constraints**: Kotlin + Compose ✓. MVVM, one immutable state per screen as `StateFlow`
✓. Module direction `:app` → `:data` → `:domain` unchanged ✓. Koin remains the sole DI framework; no
new DI, no Hilt, no KSP-based DI ✓. Room migration 3 → 4 is additive and its schema is exported ✓.
Arabic content handling untouched ✓. **No new network surface** — the leaderboard reuses the
Supabase client and the `RemoteDataSource` seam Phase 7 justified and shipped, so the constitution's
"a new network surface needs explicit justification" clause is not engaged.

**The one thing worth stating plainly**: this is the first increment in which one user's data is
visible to another. Every design choice above that looks conservative — the separate aggregate, the
unwidened policies, the server-side region assignment, the read-model-level Principle IX guarantees
— is there because the failure mode is not a wrong number on a screen. It is a participant's
recording history leaking to a stranger.

**Gate result: PASS.** No principle violation. Complexity Tracking records one constraint relaxation
that the constitution requires to be justified explicitly.

### Post-design re-check (after Phase 1)

Re-evaluated against the artifacts actually produced, not against the intentions above.

| Principle | Held in the design? | Evidence |
|---|---|---|
| **I** | Yes | Test ordering per layer is unchanged; `quickstart.md` §4 names the automated counterpart for every criterion, so `/speckit-tasks` has a test to write before each implementation task. |
| **II** | Yes | [contracts/repositories.md](./contracts/repositories.md) declares three interfaces and four pure functions with no Android, Room, Ktor or Supabase type in any signature. |
| **III** | Yes, and strengthened | The Q3 revision removed the last carve-out. `leaderboard_periods` makes "a closed period never changes" a **join condition** in both mutating paths — the job's working set and the withdrawal delete — rather than a conditional either could get wrong. `ClosedPeriodImmutabilityTest` is the owed test. |
| **IV** | Yes | The read model is cache-backed; [contracts/repositories.md](./contracts/repositories.md) has no method that blocks a record or view path, and `RankingState` has no `Empty` a view could render as "nobody is ahead of you". |
| **V** | Yes | No existing interface modified. Both new local tables are disposable and joined to `LocalRecordWipe`. |
| **VI** | Yes | `regions`, `region_zone_map` and the threshold have no client write policy; the client reports a zone and cannot name a region. |
| **VII** | Yes | `periodFor` delegates `WEEKLY` to the existing `WeekBoundary`; the aggregation groups by the device-computed `credited_date` and the server never derives a date. |
| **VIII** | Yes | Regions carry seven requirements that FR-012 needs *now*; no follower, group or challenge entity exists in the data model. |
| **IX** | Yes, and moved into the schema | The absences in [contracts/leaderboard-read-model.md](./contracts/leaderboard-read-model.md) are the enforcement: no `isLast`, no trend, no threshold, no gap figure exists for any view to render. `rls-verification-008.sql` §7 fails the build if such a column is ever added. |

**One design decision changed after the gate**, and it strengthened the result rather than weakening
it: the user revised Q3 from "withdraw from closed rankings, keep closed Honor Board recognition" to
"closed periods stand". That removed an exception from a Principle III rule. The tradeoff it
introduces — a participant cannot erase past standings — is a consent question, not a constitutional
one, and is handled by **FR-002a** requiring disclosure before opt-in.

**Post-design gate result: PASS.**

## Project Structure

### Documentation (this feature)

```text
specs/008-leaderboard-honor-board/
├── plan.md                        # This file
├── research.md                    # Phase 0 output — R1–R10
├── data-model.md                  # Phase 1 output — local cache, remote aggregate, states
├── quickstart.md                  # Phase 1 output — provision, run, validate
├── checklists/
│   └── requirements.md            # Spec quality checklist (from /speckit-specify)
├── contracts/
│   ├── remote-schema-008.sql      # Regions, consent, aggregate, Honor Board, RLS, scheduled job
│   ├── rls-verification-008.sql   # SC-006, SC-007, SC-012 — extends 007's script
│   ├── leaderboard-read-model.md  # What the client may retrieve, and what it may never
│   ├── repositories.md            # New :domain interfaces and their guarantees
│   └── ui-state.md                # LeaderboardUiState, opt-in flow, Honor Board, placement
└── tasks.md                       # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
domain/src/main/kotlin/com/giraffe/mizanapp/domain/
├── leaderboard/
│   ├── LeaderboardPeriod.kt        # Period kind + boundary derivation in a region's zone
│   ├── Ranking.kt                  # Ranking, RankingEntry, own-rank marker
│   ├── TieBreak.kt                 # Pure, stable ordering for equal totals
│   ├── HonorBoard.kt               # Membership; qualification by days engaged
│   └── Participation.kt            # Consent state
├── repository/
│   ├── LeaderboardRepository.kt    # New
│   ├── ParticipationRepository.kt  # New
│   └── HonorBoardRepository.kt     # New
└── usecase/
    ├── GetRanking.kt
    ├── GetOwnRank.kt
    ├── GetHonorBoard.kt
    ├── SetParticipation.kt
    └── GetParticipationState.kt

data/src/main/kotlin/com/giraffe/mizanapp/data/
├── db/
│   ├── entity/LeaderboardCacheEntity.kt
│   ├── entity/ParticipationStateEntity.kt
│   ├── dao/LeaderboardCacheDao.kt
│   └── dao/ParticipationStateDao.kt
├── repository/
│   ├── RoomLeaderboardRepository.kt      # Cache-backed; network writes into Room
│   ├── RoomParticipationRepository.kt
│   └── RoomHonorBoardRepository.kt
└── sync/
    ├── RemoteDataSource.kt               # Extended: rankings, own rank, honor board, consent, zone
    ├── SupabaseRemoteDataSource.kt       # Extended
    ├── NoOpRemoteDataSource.kt           # Extended
    └── LeaderboardRefresh.kt             # Bounded fetch → Room; never called from a ViewModel

app/src/main/java/com/giraffe/mizanapp/
└── leaderboard/
    ├── LeaderboardUiState.kt
    ├── LeaderboardViewModel.kt
    ├── LeaderboardSection.kt             # Hosted inside Progress — not a navigation destination
    ├── OptInPanel.kt
    └── HonorBoardPanel.kt

supabase/
├── migrations/0002_leaderboard_honor_board.sql
└── seed/regions.sql                      # Administrator-defined regions and zone mapping
```

**Structure Decision**: Android multi-module (`:app` → `:data` → `:domain`) plus a Supabase Postgres
backend, exactly as Phase 7 established. The leaderboard adds one package per module and four remote
tables; it introduces no new module, no new DI framework and no new network client. The Compose
surface is a **section hosted inside the existing Progress area**, deliberately not a navigation
destination — FR-032 and the `CLAUDE.md` three-tab decision both forbid a fourth tab.

## Complexity Tracking

> Filled only for constraint relaxations the constitution requires to be justified.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A scheduled server-side job (first in the project) | FR-018 requires rankings computed by the service from synced completions, and SC-014 requires a first ranking within 3 s. Aggregating on request over a region's completions cannot meet that at 10 000 participants, and computing on the client is forbidden outright by FR-018/FR-019. | **Aggregate on read via a Postgres view**: correct, and rejected only on latency — a view over `completions` for a large region re-scans on every request. **Compute on the client**: rejected by FR-019 and SC-006; a client that computes the ranking is a client that can forge it. **Materialised view refreshed on write**: rejected because a refresh on every completion upsert puts leaderboard cost on the sync path, which Principle IV forbids touching. |

Two things that look like violations and are not, recorded so a reviewer does not have to re-derive
them:

- **Regions resemble cohorts, which PLAN.md puts out of scope.** They are administrator-defined
  timezone bands, not social groups: a user cannot create, choose, join, name, browse or invite
  anyone into one, and the spec's Out of Scope section says so. They exist because FR-012 needs
  them now — not to make friends cheaper later, which Principle VIII forbids.
- **Cross-account visibility resembles a Principle V or RLS relaxation.** No existing policy is
  widened. A new table with its own policy exposes a purpose-built aggregate over opted-in
  participants; raw completions remain `_select_own`, and `rls-verification-008.sql` re-proves that
  alongside the new guarantees.
