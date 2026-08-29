---

description: "Task list for spec 008 — Leaderboards & Honor Board"
---

# Tasks: Leaderboards & Honor Board

**Input**: Design documents from `/specs/008-leaderboard-honor-board/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: MANDATORY, not optional. The project constitution (Principle I, NON-NEGOTIABLE) forbids
production code before a failing test that requires it, and `CLAUDE.md` makes a pull request whose
first commit is production code an automatic merge-gate failure. Every test task below must be
committed **before** the implementation task that follows it.

**Organization**: grouped by user story so each can be implemented, tested and merged independently.

**Written to be executed literally.** Every task names its exact file path, its exact verification
command, and what "done" looks like. Where a choice exists, this file makes it for you. If you find
yourself deciding something, you have misread a task — re-read it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel — different files, no dependency on an incomplete task
- **[Story]**: US1–US4, mapping to spec.md's user stories
- Every task names its exact file path

---

## Conventions for the implementer — read this entire section before T001

These rules remove every judgement call in the task list. Follow them literally.

### 1. The stub → red → green cycle

Kotlin is statically typed, so a test cannot compile against a function that does not exist. Each
unit therefore gets **two tasks**:

- **Task A (test task)** — create the production file containing only the type declarations and the
  function signature, with the body `TODO("T0XX")`, **and** write the full test file. Run the test.
  It MUST fail with `NotImplementedError` or an assertion failure. Commit. The stub is not an
  implementation; the failing test is the deliverable.
- **Task B (implementation task)** — replace the `TODO(...)` body with the real implementation.
  Re-run the same test. It MUST pass. **Change nothing in the test file.** Commit.

If a test passes in Task A, the test is wrong — fix the test, do not proceed.
If a test still fails in Task B, fix the implementation, never the test.

### 2. Commands

Run from the repository root, Windows PowerShell:

```powershell
.\gradlew :domain:test                                              # JVM, fast
.\gradlew :app:test                                                 # JVM, fast
.\gradlew :data:test                                                # JVM, fast
.\gradlew :data:connectedAndroidTest                                # needs a running emulator
.\gradlew :app:connectedAndroidTest                                 # needs a running emulator
.\gradlew :domain:test --tests "*PeriodForTest*"                    # one class
.\gradlew :data:connectedAndroidTest -Pandroid.testInstrumentationrunnerArguments.class=com.giraffe.mizanapp.data.LeaderboardCacheTest
```

Start an emulator with:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_36.1
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" wait-for-device
```

### 3. Files that MUST NOT be modified by any task in this list

Touching one of these means the task was misread. If a change seems necessary, stop and re-read.

```text
supabase/migrations/0001_identity_cloud_sync.sql
specs/007-identity-cloud-sync/contracts/remote-schema.sql
specs/007-identity-cloud-sync/contracts/rls-verification.sql
data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/1.json
data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/2.json
data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/3.json
domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CompletionRepository.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/DayPlanRepository.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CatalogueRepository.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/WeekBoundary.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/DayBoundary.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummary.kt
data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomCompletionRepository.kt
data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomDayPlanRepository.kt
```

`WeekBoundary` is reused, never reimplemented and never edited (FR-011). Existing schema JSON files
are historical records — a new `4.json` is generated, the older three stay byte-identical.

### 4. The four rules that outrank every other instruction here

**Rule A — no existing row-level policy is widened.** Every policy on `completions` and `day_records`
stays `_select_own`. If an implementation seems to need a participant to read another participant's
completions, the task has been misread. Cross-account reading happens **only** through
`leaderboard_entries` and `honor_board_closed`.

**Rule B — a CLOSED period is never mutated.** Not by the aggregation job, not by the withdrawal
delete, not by a late completion, not by a catalogue change, not by an opt-out. Both mutating paths
join `leaderboard_periods` and filter `state <> 'CLOSED'`. No task creates a code path that can
write to a closed period.

**Rule C — the client never computes a ranking.** No task adds a method that sums points, sorts
entries, assigns a position, or extends a ranking locally. The client renders what the service
returns. If you are writing arithmetic over points in `:domain`, `:data` or `:app`, stop.

**Rule D — no field that shames anyone may exist.** Not in a DTO, not in a UI model, not in a
database column, not filtered out later — it must not exist at all. Specifically forbidden anywhere:
`isLast`, `isBottom`, `trend`, `positionChange`, `lastPosition`, `pointsBehind`, `gapToNext`,
`threshold`, `thresholdDistance`, `daysShort`, `shortfall`, `nonQualifierCount`, `missedCount`.

### 5. Kotlin style, matching the existing code

- 4-space indent, no wildcard imports, imports sorted alphabetically, trailing commas in multi-line
  parameter lists.
- Every new public type and every non-obvious function gets a KDoc comment that says **why**, not
  what. Match the density of the surrounding files (see `CompletionDao.kt` for the house style).
- `:domain` may import only Kotlin stdlib, `kotlinx.coroutines`, `kotlinx.serialization` and
  `java.time`. **No** `android.*`, `androidx.*`, `io.ktor.*`, `io.github.jan.*`, `org.koin.*`.
- Never read `Instant.now()`, `LocalDate.now()`, `System.currentTimeMillis()` or
  `ZoneId.systemDefault()` anywhere. Inject `TimeProvider` (Principle VII). Tests use the existing
  `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/FakeTimeProvider.kt`.

### 6. Forbidden words in any user-visible string (Principle IX)

`failed`, `failure`, `error`, `lost`, `missing`, `problem`, `wrong`, `you didn't`, `you haven't`,
`retry now`, `behind`, `beat`, `beaten`, `overtake`, `climb`, `drop`, `fell`, `only`, `just`.

No red, orange or amber colour value anywhere in this increment. A last-place row uses the same
container, background and text colour as every other row.

Unavailable copy describes the system, never the person: "Standings aren't available right now" —
not "We couldn't load your rank", and never "You're offline".

### 7. Commit message per task

```text
<type>(008): <task id> <short description>

e.g.  test(008): T031 failing test for periodFor weekly boundary
      feat(008): T032 implement periodFor
```

### 8. If a task seems impossible

Do not improvise. The three most likely causes, in order:

1. A prerequisite task was skipped — check the Dependencies section at the end.
2. You are trying to widen an RLS policy — see Rule A. The answer is always the aggregate table.
3. You are trying to compute something on the client — see Rule C. The answer is always the server.

---

## Phase 1: Setup (Shared Infrastructure)

**Goal**: the remote schema exists, regions are seeded, and spec 007's guarantees still hold.

- [ ] T001 Start an emulator, then run all four suites to confirm a green baseline before any change: `.\gradlew :domain:test :app:test :data:test :data:connectedAndroidTest :app:connectedAndroidTest`. Record the pass counts in the commit message. If anything is red, stop — fix `develop-v1` first, do not start this increment on a red tree.
- [ ] T002 Create `supabase/migrations/0002_leaderboard_honor_board.sql` as a **byte-identical copy** of `specs/008-leaderboard-honor-board/contracts/remote-schema-008.sql`. Do not edit either file. Verify with `git diff --no-index supabase\migrations\0002_leaderboard_honor_board.sql specs\008-leaderboard-honor-board\contracts\remote-schema-008.sql` — it must print nothing.
- [ ] T003 [P] Create `supabase/seed/regions.sql` inserting the administrator-defined regions into `public.regions` and their zone mappings into `public.region_zone_map`. Seed at least four regions spanning ≥12 hours of offset so SC-005 is testable, plus **exactly one** row with `is_fallback = true` (FR-015). Suggested: `Asia/Riyadh`, `Africa/Cairo`, `Europe/London`, `Asia/Karachi`, and a UTC fallback. Map every seeded zone in `region_zone_map`.
- [ ] T004 Apply the migration to the Supabase project: `supabase db execute --file supabase\migrations\0002_leaderboard_honor_board.sql`. Then apply the seed: `supabase db execute --file supabase\seed\regions.sql`. If the project is paused, resume it from the dashboard first.
- [ ] T005 Re-run spec 007's verification unchanged to prove this increment has not regressed it: `supabase db execute --file specs\007-identity-cloud-sync\contracts\rls-verification.sql`. It MUST still print `RLS OK`. If it does not, the migration widened something — revert T004 and re-read Rule A.
- [ ] T006 Schedule `public.recompute_open_periods()` in the Supabase project (cron extension or scheduled Edge Function; operator's choice of mechanism). Cadence: every 15 minutes is sufficient for SC-014, which reads a precomputed table. Record the chosen mechanism and cadence in `specs/008-leaderboard-honor-board/quickstart.md` §2 as a one-line note.

**Checkpoint**: remote schema live, regions seeded, 007's `RLS OK` still passing.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Goal**: local storage, domain vocabulary and the remote seam all four stories depend on.

**⚠️ No user story can start until this phase is complete.**

### Room migration 3 → 4

- [ ] T007 Add `LeaderboardCacheEntity` to `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entity/LeaderboardCacheEntity.kt` and `ParticipationStateEntity` to `.../entity/ParticipationStateEntity.kt`, with the exact columns in [data-model.md](./data-model.md) §2. Both are `@Entity`. Do not register them in the database yet.
- [ ] T008 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/Migration3To4Test.kt`: open a version-3 database with rows in `day_plans`, `planned_tasks`, `completions`, `outbox`, `sync_cursors` and `account_scope`; run migration 3→4; assert every pre-existing row is unchanged and the two new tables exist and are empty. Run it — MUST fail (the migration does not exist yet).
- [ ] T009 Add `MIGRATION_3_4` to `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabase.kt`: bump `version = 4`, register both entities, and add a migration containing **only two `CREATE TABLE` statements**. No `DROP`, no `ALTER`, no `UPDATE`, no data rewrite. Re-run T008 — MUST pass.
- [ ] T010 Build once to export the schema, then confirm `data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/4.json` exists and is committed. Verify `1.json`, `2.json` and `3.json` are untouched: `git diff develop-v1 -- data/schemas` must show only the new `4.json`.
- [ ] T011 [P] Create `LeaderboardCacheDao` in `data/src/main/kotlin/com/giraffe/mizanapp/data/db/dao/LeaderboardCacheDao.kt` with `upsert(entity)`, `observeById(id: String): Flow<LeaderboardCacheEntity?>` and `deleteAll()`. Create `ParticipationStateDao` in `.../dao/ParticipationStateDao.kt` with `observe(): Flow<ParticipationStateEntity?>`, `upsert(entity)` and `deleteAll()`. Register both on `MizanDatabase`.
- [ ] T012 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/LocalRecordWipeLeaderboardTest.kt`: seed both new tables, call `LocalRecordWipe.wipe()`, assert both are empty and the catalogue tables are still populated. Run it — MUST fail.
- [ ] T013 Add `leaderboard_cache` and `participation_state` to the single transaction in `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/LocalRecordWipe.kt`. Do not change anything else in that file. Re-run T012 — MUST pass.

### Domain vocabulary

- [ ] T014 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/leaderboard/PeriodKind.kt` containing `enum class PeriodKind { DAILY, WEEKLY, MONTHLY }` (FR-020). No other content.
- [ ] T015 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/leaderboard/Region.kt` containing `@JvmInline value class RegionId(val value: String)` and `data class Region(val id: RegionId, val displayName: String, val zone: ZoneId)`.
- [ ] T016 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/leaderboard/LeaderboardPeriod.kt` with `data class LeaderboardPeriod(val kind: PeriodKind, val start: LocalDate, val endInclusive: LocalDate, val regionId: RegionId)` and the signature `fun periodFor(kind: PeriodKind, date: LocalDate, zone: ZoneId, regionId: RegionId): LeaderboardPeriod` with body `TODO("T017")`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/leaderboard/PeriodForTest.kt` covering: DAILY start == end == date; **WEEKLY runs Saturday to Friday and matches `WeekBoundary`'s own output for the same date**; MONTHLY is the calendar month; and a date near midnight in a zone 12 hours from UTC lands in the expected day. Run it — MUST fail.
- [ ] T017 Implement `periodFor`. **WEEKLY MUST delegate to the existing `WeekBoundary`** — do not compute Saturday yourself, do not copy its logic (FR-011). Re-run T016 — MUST pass.
- [ ] T018 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/leaderboard/Ranking.kt` with `RankingEntry`, `Ranking`, `OwnRank` and `RankingState` exactly as specified in [data-model.md](./data-model.md) §1. **`RankingEntry` has exactly five fields**: `userId`, `displayName`, `points`, `position`, `isViewer`. Adding any field from Rule D fails this task.
- [ ] T019 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/leaderboard/Participation.kt` with `data class Participation(val optedIn: Boolean, val region: Region?)` and `sealed interface ParticipationResult { Applied; Unreachable; SessionExpired }`. There is no `Failed` case and no message field — see Rule D and Conventions §6.
- [ ] T020 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/leaderboard/MarkViewer.kt` with `fun markViewer(entries: List<RankingEntry>, viewerUserId: String?): List<RankingEntry>` body `TODO("T021")`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/leaderboard/MarkViewerTest.kt`: sets `isViewer` on the matching row only; sets nothing when `viewerUserId` is null; **asserts the last entry is not distinguished in any way** (Rule D). Run it — MUST fail.
- [ ] T021 Implement `markViewer`. Re-run T020 — MUST pass.

### Domain repository interfaces

- [ ] T022 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/ParticipationRepository.kt` exactly as declared in [contracts/repositories.md](./contracts/repositories.md), including the KDoc. Interface only — no implementation.
- [ ] T023 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/LeaderboardRepository.kt` exactly as declared in [contracts/repositories.md](./contracts/repositories.md). Interface only. **It must contain no method that sums, sorts or extends a ranking** (Rule C).
- [ ] T024 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/HonorBoardRepository.kt` exactly as declared in [contracts/repositories.md](./contracts/repositories.md), including the comment that `DAILY` is a programming error (FR-027a).
- [ ] T025 Run `.\gradlew :domain:test` and confirm `ModuleBoundaryTest` still passes — `:domain` must have gained no Android, Room, Ktor, Supabase or Koin import.

### Remote seam

- [ ] T026 Create the DTOs in `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/dto/LeaderboardDtos.kt`: `RemoteRankingPage`, `RemoteRankingEntry`, `RemoteOwnRank`, `RemoteHonorBoard`, `RemoteHonorBoardMember`, `RemoteParticipation`. Fields must mirror [contracts/leaderboard-read-model.md](./contracts/leaderboard-read-model.md). **No field from Rule D.**
- [ ] T027 Extend the `RemoteDataSource` interface in `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/RemoteDataSource.kt` with: `rankingPage(kind, cursor)`, `ownRank(kind)`, `honorBoard(kind)`, `setParticipation(optedIn)`, `reportZone(zoneId)`. All return `RemoteResult<…>`. **Do not modify any existing method on this interface.** Note `rankingPage` takes no region parameter — the service derives it (FR-009).
- [ ] T028 Add the five new methods to `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/NoOpRemoteDataSource.kt`, each returning `RemoteResult.Unreachable`. This is what keeps the app working with the leaderboard switched off.
- [ ] T029 Add the five new methods to the fake at `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/FakeRemoteDataSource.kt`, backed by in-memory maps. Give it settable knobs: `unreachable: Boolean`, and the ability to seed entries per (kind, region) and to mark a period closed. The fake MUST enforce Rule B — a write to a closed period throws, so a test that tries fails loudly.
- [ ] T030 Implement the five methods in `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SupabaseRemoteDataSource.kt` against the tables from T002. Reads go to `leaderboard_entries`, `honor_board_closed`, `leaderboard_periods`, `regions`; writes go **only** to `leaderboard_participation`. Any attempt to write `leaderboard_entries` from here is a misread of Rule C.

**Checkpoint**: foundation ready — all four stories can now start.

---

## Phase 3: User Story 1 — Opting in and seeing where I stand (Priority: P1) 🎯 MVP

**Goal**: a signed-in person can opt in and see their region's ranking with their own row marked.

**Independent test**: sign in, open Progress, opt in, confirm a ranking appears with the
participant's own position, total and region label. No Honor Board and no period switching needed.

### Tests for User Story 1

- [ ] T031 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/ParticipationOptInTest.kt`: participation is off by default for a fresh account (FR-001); `optIn(zone)` reports the zone and receives a region; the client never sends a region id (FR-014). Run it — MUST fail.
- [ ] T032 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/LeaderboardCacheTest.kt`: a fetched page is written to `leaderboard_cache` with `retrievedAt`; observing emits from the cache; a second fetch for the same (kind, region) **replaces** rather than duplicates (deterministic id); with `unreachable = true` and a cache present the state is `Cached`, and with no cache it is `Unavailable` — **never an empty list** (spec edge case). Run it — MUST fail.
- [ ] T033 [P] [US1] Write `app/src/test/java/com/giraffe/mizanapp/leaderboard/LeaderboardViewModelTest.kt`: signed out → `Visibility.Hidden`; signed in and not opted in → `Visibility.Invitation`; opted in → `Visibility.Participating` with the region label present; unsynced completions → `isProvisional` true (FR-037); no string in any state contains a word from Conventions §6. Run it — MUST fail.
- [ ] T034 [P] [US1] Write `app/src/androidTest/java/com/giraffe/mizanapp/leaderboard/OptInPanelTest.kt`: the invitation states what becomes visible, that it is limited to the region, that finished periods stay visible after leaving (FR-002a), that the current period includes what is already recorded (FR-002b), and that days synced after a period ends do not count toward it (FR-025b). All five must be present before opt-in can proceed. Run it — MUST fail.
- [ ] T035 [P] [US1] Write `app/src/androidTest/java/com/giraffe/mizanapp/leaderboard/NoOptInGateTest.kt` (SC-001): signed in, never opted in — walk Today, Week, History, Insights and Progress and assert no ranking, no other participant's name, and nothing beyond the single invitation inside Progress. Then signed out — assert not even the invitation appears (FR-033). Run it — MUST fail.

### Implementation for User Story 1

- [ ] T036 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomParticipationRepository.kt` implementing `ParticipationRepository`. `optIn(zone)` calls `RemoteDataSource.reportZone` then `setParticipation(true)`, stores the returned region in `participation_state`, and returns `ParticipationResult`. It MUST NOT send a region id. Re-run T031 — MUST pass.
- [ ] T037 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/LeaderboardRefresh.kt` with `suspend fun refresh(kind: PeriodKind)`: fetch a bounded page (50), write it to `leaderboard_cache`, stamp `retrievedAt` from `TimeProvider`. It returns `Unit` and never throws on an unreachable service. **It must not be callable from a ViewModel** — document that in KDoc naming its only callers.
- [ ] T038 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomLeaderboardRepository.kt` implementing `LeaderboardRepository`. `observeRanking` maps the cached row to `RankingState`, applying `markViewer` with the session's user id. `refresh` delegates to T037. **No summing, no sorting, no position assignment** (Rule C). Re-run T032 — MUST pass.
- [ ] T039 [US1] Create the use cases `GetParticipationState.kt` and `SetParticipation.kt` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/`. `SetParticipation` takes the zone from the injected `TimeProvider.zone()` — the ViewModel must not read a clock.
- [ ] T040 [US1] Create `GetRanking.kt` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/`, a thin wrapper over `LeaderboardRepository.observeRanking`.
- [ ] T041 [US1] Create `app/src/main/java/com/giraffe/mizanapp/leaderboard/LeaderboardUiState.kt` exactly as specified in [contracts/ui-state.md](./contracts/ui-state.md), including `Visibility` with its three cases and `RankingRowUiModel` with exactly four fields. **No field from Rule D.**
- [ ] T042 [US1] Create `app/src/main/java/com/giraffe/mizanapp/leaderboard/LeaderboardViewModel.kt` exposing one immutable `StateFlow<LeaderboardUiState>`. No mutable state escapes. Re-run T033 — MUST pass.
- [ ] T043 [US1] Create `app/src/main/java/com/giraffe/mizanapp/leaderboard/OptInPanel.kt` rendering the five disclosures from T034. Use the suggested copy in [contracts/ui-state.md](./contracts/ui-state.md) verbatim unless it contains a forbidden word, in which case fix the copy and note it. No urgency, no social proof, no participant count. Re-run T034 — MUST pass.
- [ ] T044 [US1] Create `app/src/main/java/com/giraffe/mizanapp/leaderboard/LeaderboardSection.kt` rendering the ranking rows and the region label (FR-016, FR-026). Every row uses the same container, background and text colour; only `isViewer` may vary the container, and it must vary identically regardless of position (FR-038).
- [ ] T045 [US1] Host `LeaderboardSection` **inside the existing Progress area**. Do **not** add a `Destination` entry, do **not** add a navigation route, do **not** touch the three-tab navigation in `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` beyond what hosting requires (FR-032). Re-run T035 — MUST pass.
- [ ] T046 [US1] Register `RoomParticipationRepository`, `RoomLeaderboardRepository`, `LeaderboardRefresh`, the three use cases and `LeaderboardViewModel` in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`. Koin only — no other DI framework.
- [ ] T047 [US1] Run `.\gradlew :domain:test :app:test :data:connectedAndroidTest :app:connectedAndroidTest`. All green before proceeding.

**Checkpoint**: US1 is independently shippable — opt in, see your region's ranking, own row marked.

---

## Phase 4: User Story 2 — Leaving, and staying gone (Priority: P1)

**Goal**: leaving clears every open period and every period that opens after, while closed periods
stand exactly as they were.

**Independent test**: opt in, confirm the row is visible to a second participant, opt out, confirm
the row is gone from the open period and absent from the next period to open, that a closed period
is unchanged, and that the first person's own records and streak are untouched.

### Tests for User Story 2

- [ ] T048 [P] [US2] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/ParticipationWithdrawalTest.kt` — **the heart of this story.** Seed one OPEN and one CLOSED period, both containing the participant. Opt out. Assert **all four**: the open period no longer contains them (FR-004); the closed period's ranking is byte-identical (FR-004a); the closed period's Honor Board membership is byte-identical (FR-004a); a newly opened period does not contain them (FR-004b). Run it — MUST fail.
- [ ] T049 [P] [US2] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/OptOutPreservesRecordTest.kt` (SC-003): record several days, note every day's earned and available totals plus the streak; opt out; assert every figure is identical and recording still works. Run it — MUST fail.
- [ ] T050 [P] [US2] Write `app/src/androidTest/java/com/giraffe/mizanapp/leaderboard/LeaveControlTest.kt`: the leave control sits in the same place as the opt-in (FR-003); its confirmation states that leaving is immediate going forward and changes nothing backwards; **there is no retention plea** — assert no string contains "sure", "lose", "position" or any word from Conventions §6. Run it — MUST fail.
- [ ] T051 [P] [US2] Write `app/src/test/java/com/giraffe/mizanapp/leaderboard/OptedOutStateTest.kt`: after opting out the state returns to `Visibility.Invitation`, the cached ranking is cleared, and no other participant's name remains in state. Run it — MUST fail.

### Implementation for User Story 2

- [ ] T052 [US2] Implement `optOut()` in `RoomParticipationRepository`: call `RemoteDataSource.setParticipation(false)`, then clear `participation_state` and `leaderboard_cache` locally. It MUST NOT touch `day_plans`, `planned_tasks` or `completions` (FR-005). Re-run T049 — MUST pass.
- [ ] T053 [US2] Verify the server-side withdrawal trigger from T002 behaves as T048 expects. The delete joins `leaderboard_periods` and filters `state <> 'CLOSED'`; `honor_board_closed` holds only closed periods and is therefore unreachable from it. If T048 fails on the closed-period assertions, the trigger is wrong, not the test. Re-run T048 — MUST pass.
- [ ] T054 [US2] Add the leave control to `app/src/main/java/com/giraffe/mizanapp/leaderboard/OptInPanel.kt` (same file, same place — FR-003), with a single confirmation stating the forward/backward asymmetry. Re-run T050 and T051 — MUST pass.
- [ ] T055 [US2] Run all four suites. All green before proceeding.

**Checkpoint**: consent is genuinely revocable; closed periods are provably immutable.

---

## Phase 5: User Story 3 — Comparing across periods, within a region (Priority: P2)

**Goal**: daily, weekly and monthly rankings, each evaluated in the region's timezone, with the
viewer's own row reachable without scrolling.

**Independent test**: with a fixed set of synced completions spanning a month, switch between the
three periods and confirm each total matches an independent hand-computation over that period's
boundaries in the region's timezone.

### Tests for User Story 3

- [ ] T056 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/leaderboard/RegionalPeriodBoundaryTest.kt` (SC-005 — **the criterion this increment exists for**): for regions at ≥12 hours of offset, assert the leaderboard day for a participant always falls on the same weekday as their own device date, including across a Friday, when the catalogue schedules day-specific tasks. Run it — MUST fail if `periodFor` is wrong; if it passes immediately, the test is too weak — add a zone pair that actually straddles midnight.
- [ ] T057 [P] [US3] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/RankingAggregationTest.kt` (SC-004): seed known completions; assert each period's total equals an independent hand-computed sum of `points_awarded` over non-reversed completions in the region's timezone. Assert a reversed completion is excluded and a catalogue points change does **not** alter a past total (Principle III). Run it — MUST fail.
- [ ] T058 [P] [US3] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/MidPeriodOptInTest.kt` (SC-015): record several days without opting in, then opt in on the last day of the period; assert the published total covers **every** day of the period, not only days after opting in (FR-021a). Run it — MUST fail.
- [ ] T059 [P] [US3] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/OwnRankLookupTest.kt` (SC-009, FR-023): seed a region with 10 000 entries; assert `ownRank` returns the viewer's row plus neighbours **without** fetching intervening pages, and that `totalParticipants` is present. Run it — MUST fail.
- [ ] T060 [P] [US3] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/RankingPaginationTest.kt` (FR-024): a page is bounded at 50; `loadMore` extends it; `hasMore` is false at the end. Run it — MUST fail.
- [ ] T061 [P] [US3] Write `app/src/androidTest/java/com/giraffe/mizanapp/leaderboard/PeriodSwitchTest.kt`: switching period re-renders the ranking and the period label; the weekly label states a Saturday-to-Friday span (FR-011, FR-026). Run it — MUST fail.

### Implementation for User Story 3

- [ ] T062 [US3] Implement the SQL body of `public.recompute_open_periods()` in `supabase/migrations/0002_leaderboard_honor_board.sql`, replacing the `null;` placeholder with the fold described in its own comment block. Working set is `leaderboard_periods where state <> 'CLOSED'` (Rule B). `points = sum(points_awarded)`, `days_engaged = count(distinct credited_date)`, both where `reversed_at is null`. `position = rank over (points desc, max(recorded_at) asc)` (FR-022). Join `leaderboard_participation on opted_in` so an opted-out account is never entered (FR-004b), and do **not** filter completions by consent date (FR-021a). **After editing, re-copy the file to the contract so the two stay byte-identical**, then re-verify with `git diff --no-index`.
- [ ] T063 [US3] Add period closing to the same function: when a period's boundary has passed in its region's timezone, set `state = 'CLOSED'` and `closed_at = now()`. **No settlement window — the freeze is immediate** (FR-025). Re-apply the migration with `supabase db execute`.
- [ ] T064 [US3] Implement `ownRank(kind)` in `SupabaseRemoteDataSource` as a direct lookup plus neighbours, not a page scan. Re-run T059 — MUST pass.
- [ ] T065 [US3] Implement `loadMore(kind)` in `RoomLeaderboardRepository`, fetching the next bounded page and appending it to the cached payload. Appending a page is not computing a ranking — positions come from the service (Rule C). Re-run T060 — MUST pass.
- [ ] T066 [US3] Create `GetOwnRank.kt` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/` and surface own-rank in `LeaderboardUiState`. Render it so the viewer's row is reachable without scrolling.
- [ ] T067 [US3] Add the period selector to `LeaderboardSection.kt`. Three options, no default beyond `WEEKLY`. Re-run T061 — MUST pass.
- [ ] T068 [US3] Re-run T056, T057 and T058 — all MUST pass. Then run all four suites.

**Checkpoint**: rankings are correct in every period and every region.

---

## Phase 6: User Story 4 — Recognition on the Honor Board (Priority: P3)

**Goal**: weekly and monthly recognition by days engaged, with nothing revealed about anyone who did
not qualify.

**Independent test**: with participants above and below the threshold, confirm exactly those at or
above it appear, that the surface presents no ordering or position, and that those below it are not
listed, counted or alluded to.

### Tests for User Story 4

- [ ] T069 [P] [US4] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/leaderboard/QualifiesForHonorBoardTest.kt` (SC-011): qualification depends only on `daysEngaged` and `threshold`; two participants with equal days and very different points both qualify or both do not. Run it — MUST fail.
- [ ] T070 [P] [US4] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HonorBoardQualificationTest.kt`: exactly those at or above the threshold appear; a member who later opts out is removed from an **open** board and retained on a **closed** one (FR-004a). Run it — MUST fail.
- [ ] T071 [P] [US4] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HonorBoardLeakTest.kt` (SC-012): inspect **everything the client can retrieve**, not what it renders. Assert the response carries no threshold, no distance, no non-qualifier count, no per-person days figure, and no identity of anyone who did not qualify. Run it — MUST fail.
- [ ] T072 [P] [US4] Write `app/src/androidTest/java/com/giraffe/mizanapp/leaderboard/HonorBoardPanelTest.kt`: members render unordered with no points and no position; a non-qualifying viewer sees no statement about themselves; **selecting the daily period shows no Honor Board panel at all** — absent, not empty and not disabled (FR-027a). Run it — MUST fail.

### Implementation for User Story 4

- [ ] T073 [US4] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/leaderboard/HonorBoard.kt` with `HonorBoardMember` (exactly `displayName` and `isViewer`), `HonorBoard`, `HonorBoardState`, and `fun qualifiesForHonorBoard(daysEngaged: Int, threshold: Int): Boolean`. **Points must not be a parameter.** Re-run T069 — MUST pass.
- [ ] T074 [US4] Add Honor Board writing to `recompute_open_periods()`: on close, insert qualifying members into `honor_board_closed` for `WEEKLY` and `MONTHLY` only (FR-027a). The threshold is read from configuration, not hard-coded (FR-028). Re-copy to the contract and re-verify byte-identity.
- [ ] T075 [US4] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomHonorBoardRepository.kt` implementing `HonorBoardRepository`. Re-run T070 and T071 — MUST pass.
- [ ] T076 [US4] Create `GetHonorBoard.kt` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/` and register it in Koin.
- [ ] T077 [US4] Create `app/src/main/java/com/giraffe/mizanapp/leaderboard/HonorBoardPanel.kt`. Render for `WEEKLY` and `MONTHLY` only; for `DAILY` render nothing — no placeholder, no explanation, no "not available today" (FR-027a). Re-run T072 — MUST pass.
- [ ] T078 [US4] Run all four suites. All green.

**Checkpoint**: all four stories delivered.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T079 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/ClosedPeriodImmutabilityTest.kt` — **the Principle III test this increment owes** (SC-010). Close a period, then: change catalogue points; reverse a completion in that period; add a late completion for a date inside it; and opt a member out. Assert the closed period's standings and Honor Board membership are byte-identical after every one of those four. Run it — MUST fail if any mutating path can reach a closed period.
- [ ] T080 Make T079 pass. If it fails, the fault is in `recompute_open_periods()` or the withdrawal trigger, never in the test. Re-read Rule B.
- [ ] T081 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/LateSyncAfterFreezeTest.kt` (SC-016): record a full day offline, let the period boundary pass, reconnect. Assert **both**: the closed period's standings do not change, **and** Today, Week, Streak, History and Insights count the day in full (FR-025a). Both halves are required — the second is what keeps the tradeoff bounded.
- [ ] T082 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/DuplicateDisplayNameTest.kt` (SC-017): two participants in one region sharing a display name are both listed, neither name is altered or suffixed, and each can identify their own row (FR-007a).
- [ ] T083 [P] Write `app/src/androidTest/java/com/giraffe/mizanapp/leaderboard/LeaderboardDegradationTest.kt` (SC-008, FR-034): with the remote unreachable, assert recording, Today, Week, Streak, History and Insights behave identically to a run with it available, and the leaderboard panel renders an unavailable state without blaming the person.
- [ ] T084 Run `specs\008-leaderboard-honor-board\contracts\rls-verification-008.sql` against the project: `supabase db execute --file specs\008-leaderboard-honor-board\contracts\rls-verification-008.sql`. It MUST print `RLS OK 008`. This covers SC-006, SC-007 and part of SC-012. If §1 fails, this increment widened a 007 policy — re-read Rule A.
- [ ] T085 [P] Audit every string this increment adds against Conventions §6 and the `CLAUDE.md` Principle IX list (SC-013): both opt-in and leave confirmations, every unavailable and cached state, the period labels, the Honor Board panel. Record the audit as a comment block at the top of `app/src/test/java/com/giraffe/mizanapp/leaderboard/LeaderboardCopyTest.kt`, following the pattern in `SyncStatusCopyTest.kt`.
- [ ] T086 [P] Colour audit (SC-013): grep every file added by this increment for `Color(` and for red/orange/amber hex values. Confirm a last-place row is rendered with the same container, background and text colour as every other row. Record the result in the same comment block as T085.
- [ ] T087 [P] Boundary and prohibition audit. `Grep` for `io.github.jan`, `io.ktor`, `androidx.work` and `org.koin` across `domain/src` — must return nothing. Then `git grep -nE "isLast|isBottom|positionChange|lastPosition|pointsBehind|gapToNext|thresholdDistance|daysShort|shortfall|nonQualifierCount"` — must return nothing outside this tasks file and the design documents (Rule D). Confirm `ModuleBoundaryTest` passes.
- [ ] T088 [P] Remove the stale `tieBreak` row from the pure-functions table in `specs/008-leaderboard-honor-board/contracts/repositories.md`. The tie-break is implemented in SQL (T062) and asserted by `RankingAggregationTest`; a `:domain` copy would be dead code, which Principle VIII forbids. Add a one-line note saying where the rule actually lives.
- [ ] T089 [P] Confirm `data/schemas/…/4.json` is committed and that `1.json`, `2.json` and `3.json` are byte-identical to `develop-v1`: `git diff develop-v1 -- data/schemas`.
- [ ] T090 [P] Update `docs/PLAN.md` to mark Phase 8 as delivered, recording the regional model, the days-engaged Honor Board threshold, and the immediate-freeze tradeoff with its revisit trigger.
- [ ] T091 Run all four suites one final time and record the pass counts.
- [ ] T092 Run the full quickstart §4 validation, all 17 success criteria, and record the result in the pull request description. Anything that cannot be validated here — the OTP-gated criteria in particular — must be recorded as a follow-up issue the way spec 007's were (issue #15), never silently skipped.
- [ ] T093 Pre-merge gate check against `CLAUDE.md`: Constitution Check passes and names each principle touched; all four suites green; **the PR's commit history shows every test task committed before its implementation task** (Principle I); the Principle III test (T079) is present and passing; the Room migration is additive and its schema exported.

---

## Dependencies

```text
Phase 1 (Setup, T001–T006)
        │
        ▼
Phase 2 (Foundational, T007–T030)   ← BLOCKS EVERYTHING
        │
        ├──────────────┬──────────────┬──────────────┐
        ▼              ▼              ▼              ▼
   US1 (T031–T047)  US2 (T048–T055)  US3 (T056–T068)  US4 (T069–T078)
     P1, MVP          P1               P2               P3
        │              │                │                │
        └──────────────┴────────────────┴────────────────┘
                              │
                              ▼
                  Phase 7 (Polish, T079–T093)
```

**Hard ordering rules:**

- T007 → T008 → T009 → T010: the migration chain. Nothing in `:data` compiles until T009.
- T027 → T028, T029, T030: the interface before its three implementations.
- **US2 depends on US1** for the opt-in path it reverses. Do US1 first.
- **US4 depends on US3's T062** — the Honor Board is written by the same aggregation function.
- T062 must be re-copied to the contract every time it is edited, or T084's byte-identity gate fails.

## Parallel execution examples

Within Phase 2, after T010:

```text
T011, T014, T015, T018, T019, T022, T023, T024, T026   # all different files
```

Within US1, the five test tasks are all independent:

```text
T031, T032, T033, T034, T035
```

Within Phase 7:

```text
T081, T082, T083, T085, T086, T087, T088, T089, T090
```

## Implementation strategy

**MVP is US1 alone** (T001–T047). That delivers opt-in, a correct regional ranking, and the
no-account gate — a complete, shippable slice.

Recommended order: **US1 → US2 → US3 → US4**. US2 is also P1 and should not be deferred far: an
opt-in that cannot be reversed is not consent, and shipping US1 without it means publishing people's
figures with no way out.

Each phase ends with all four suites green. Do not start a phase with a red tree.

## Task count

93 tasks: 6 setup, 24 foundational, 17 US1, 8 US2, 13 US3, 10 US4, 15 polish.
