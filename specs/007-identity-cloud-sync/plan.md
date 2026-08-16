# Implementation Plan: Identity & Cloud Sync

**Branch**: `spec/007-identity-cloud-sync` | **Date**: 2026-08-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-identity-cloud-sync/spec.md`

## Summary

The first increment with a backend. Accounts (email one-time code), a durable local outbox that
drains to Supabase whenever it can, a progressive pull onto new devices, and a centrally published
catalogue that the built-in seed falls back to. Nothing on the recording path changes: record and
undo stay local writes with no network call anywhere near them.

Eight decisions carry the increment, and each one is chosen to keep the blast radius inside `:data`:

1. **Sync decorates the existing Room repositories; it does not replace them.**
   `SyncingCompletionRepository` and `SyncingDayPlanRepository` wrap `RoomCompletionRepository` /
   `RoomDayPlanRepository`, delegate the write, then enqueue an outbox entry in the same transaction.
   `CompletionRepository`, `DayPlanRepository` and `CatalogueRepository` are unchanged — the seam
   `002` built for exactly this moment (research R2).
2. **Completions sync; planned tasks never do; a day syncs as a version pointer.** A remote
   `day_records` row is `(user_id, date, catalogue_version)` and nothing else. Every device re-derives
   the applicable task set and the available-points total locally with `buildDayPlan(catalogue,
   version, date)`, which is already deterministic. This is the roadmap's "keep Day Plans local and
   rebuildable from the catalogue version" with the one field added that makes FR-024's "same
   applicable task set on every device" actually true (research R4).
3. **Both merges are monotone, so no clock is trusted and order does not matter.** A completion
   merges by "once reversed, always reversed", because the only mutable field on a completion is its
   tombstone and a re-record is a new row with a new id — FR-019's last-write-wins produces the
   identical outcome without either device's clock deciding anything. A date's catalogue version
   settles server-side on `min(catalogue_version)`, and **that settled value is only ever read by a
   device that has not yet materialised the date**. A day already recorded on a device is never
   re-derived, re-versioned, or rewritten by anything (FR-024a), and no repository, DAO, or merge
   in this increment is capable of it (research R5).
4. **The outbox is keyed deterministically, which is what makes migration and retry idempotent.**
   Outbox id is `"$entityType:$entityId:$operation"`, so enqueueing the same change twice is a
   no-op, and every remote write is an upsert on the client-generated UUID. Resuming an interrupted
   first-sign-in upload is therefore the same code path as an ordinary retry (research R6, R7).
5. **One account's records live on a device at a time.** Signing account V into a device holding
   account U's records goes through the same explicit, named confirmation as
   "sign out and remove data", and clears U's local rows before V's session opens. The alternative —
   an account filter on all thirty-odd existing queries — is one forgotten `WHERE` clause away from
   the worst failure this feature can produce (research R8).
6. **`RecordCoverage` generalises the record start that `003` already has.** Signed out, coverage is
   `complete` and its floor is `earliestPlanDate()`, which is exactly today's behaviour. During a
   backfill the floor is what has been fetched so far, and dates below it render as a fifth,
   neutral `DayCellState.NOT_YET_KNOWN` instead of as an empty day (FR-023b, research R9).
7. **The catalogue pull may add a version and may never alter one.** Versions already present are
   skipped, not overwritten; a publication whose `format_version` the app does not know is skipped
   in favour of the newest one it does. Principle III holds mechanically rather than by discipline
   (research R10).
8. **Every sync test runs against a fake remote, including two-device convergence.**
   `RemoteDataSource` is an interface in `:data` with a Supabase implementation and an in-memory
   fake; convergence tests drive two `MizanDatabase` instances against one fake. Row-level security
   is the one guarantee a fake cannot prove, so SC-008 is verified with SQL against the real project
   (research R13, R14).

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (unchanged)

**Primary Dependencies**: Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01, coroutines 1.10.2.
**Three new dependencies, all justified below and in Complexity Tracking**: supabase-kt (`auth-kt`,
`postgrest-kt`, via its BOM — 3.5.0 at time of writing, pinned in `libs.versions.toml` and
re-checked at implementation), its required Ktor engine (`ktor-client-okhttp`), and
`androidx.work:work-runtime-ktx` + `koin-androidx-workmanager`.

**Storage**: Room, still the single source of truth. **Migration 2 → 3, purely additive**: three new
tables (`outbox`, `sync_cursors`, `account_scope`) and one new nullable column (`syncedAt`) on
`day_plans` and `completions`. No column is dropped, renamed, or rewritten. Schema exported to
`data/schemas/…/3.json` and committed. Remote storage is Postgres on Supabase, described in
[contracts/remote-schema.sql](./contracts/remote-schema.sql).

**Testing**: JUnit 4. `:domain` and `:app` on the JVM; `:data` instrumented for the migration, the
outbox, the sync engine, two-device convergence, sign-in migration and the local-wipe suites, all
against `FakeRemoteDataSource`. Compose UI tests for sign-in, profile and the sync status surface.
Row-level security is verified by SQL run against the real project
([contracts/rls-verification.sql](./contracts/rls-verification.sql)), not through the app.

**Target Platform**: Android, `minSdk 24`, `compileSdk 36`, `targetSdk 36`

**Performance Goals**: SC-002 — record/undo latency signed-in is indistinguishable from signed-out;
the outbox insert rides the existing write transaction and adds one row. SC-003 — queued changes
reach the account within one minute of connectivity returning, with the app closed (WorkManager
expedited work on a `NetworkType.CONNECTED` constraint). SC-006 — today and the current week usable
within 10 s of sign-in, a year of history within two minutes, backfilled in date-descending pages of
90 days. SC-010 — a year of daily recording is roughly 25 000 outbox rows at ~200 bytes, ≈5 MB;
retained indefinitely and drained in bounded batches.

**Constraints**: no network call on the record, undo, view, or score path (Principle IV). `:domain`
keeps zero Android, zero Ktor, zero Supabase on its classpath (Principle II). `TimeProvider` remains
the only clock; server timestamps are merge inputs and pull cursors, never a source of "now"
(Principle VII). No red, no failure framing, no blame in any sync string (Principle IX).

**Scale/Scope**: 2 new screens (sign-in, profile) plus one status surface reused on Today and Week;
1 Room migration; 3 new local tables; 5 new remote tables; 4 new domain interfaces; 5 new pure
domain functions; 1 new `DayCellState` value; 43 functional requirements (FR-001–FR-028 plus the 15
lettered refinements).

## What `002`–`006` already provide

Verified against the merged code on `develop-v1`, not against their documents.

| Needed by this feature | Status in `develop-v1` |
|---|---|
| Client-generated UUID identity on every synchronisable row | ✅ `DayPlanEntity.id`, `CompletionEntity.id`, `PlannedTaskEntity.id`, `TaskVersionEntity.id` |
| `updatedAt`, `deletedAt`, nullable `userId` on those rows | ✅ all four entities carry the Principle V triple |
| Undo as a tombstone, never a delete | ✅ `CompletionDao.reverseById`, `reversedAt` filtered from every read |
| Points frozen at write time, never recomputed | ✅ `Completion.pointsAwarded` from the planned task |
| A day's available total stored, not derived | ✅ `DayPlanEntity.availablePoints` |
| Deterministic plan derivation from `(catalogue, version, date)` | ✅ `buildDayPlan` — pure, `newId` injected |
| The version that applied on a past date | ✅ `CatalogueRepository.versionEffectiveOn(date)` |
| A repository seam explicitly built for a server catalogue | ✅ `CatalogueRepository` KDoc names Phase 7 |
| Idempotent catalogue load | ✅ `CatalogueSeeder.seedIfNeeded` |
| A validator that rejects user-authoring fields in catalogue content | ✅ `scanForAuthoringAffordances`, `ignoreUnknownKeys = false` |
| A record-start floor the read models already respect | ✅ `DayPlanRepository.earliestPlanDate()` |
| Neutral, non-red day states | ✅ `DayCellState` + `ui/DayCellColors.kt` |
| Any networking at all | ❌ the project has none — no Retrofit, no Ktor, no OkHttp anywhere (R1) |
| Background execution | ❌ nothing schedules work today (R12) |
| A durable local queue | ❌ new |
| Any notion of an account, a session, or a device scope | ❌ new |
| A way to distinguish "not fetched yet" from "nothing recorded" | ❌ new — `RecordCoverage`, R9 |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.1.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | Order per layer, as `002`–`006` established: `:domain` pure tests (merge rules, backoff, status derivation, coverage) → domain code → domain interfaces and use-case tests → use cases → `:data` migration test → migration → outbox/sync-engine/convergence/migration-on-sign-in/wipe tests against `FakeRemoteDataSource` → sync engine → `SupabaseRemoteDataSource` contract tests → implementation → ViewModel tests → ViewModels → **Compose UI tests → sign-in, profile, status surface**. Only Koin/WorkManager wiring and `@Preview` claim the exemption. |
| **II — Domain purity** | Yes | The four new interfaces (`AccountRepository`, `SyncRepository`, `RecordCoverageRepository`, `CataloguePublicationRepository`) are declared in `:domain` and implemented in `:data`, like every existing repository. No Supabase, Ktor, WorkManager, or DTO type appears in `:domain`; the merge rules there are pure functions over domain types. `:domain`'s build file gains nothing — `ModuleBoundaryTest` continues to enforce this. |
| **III — Immutable history (NON-NEGOTIABLE)** | Yes | Four mechanisms, not four intentions. (a) A catalogue pull may **insert** a version and has no path that alters one already present, so a publication cannot rewrite what a recorded day was scored against. (b) `pointsAwarded` stays denormalised on the completion, so no merge, pull, or backfill can change an earned figure — ever, in any direction. (c) **A day already materialised on a device is never re-derived, re-versioned, or rewritten by sync (FR-024a), and `DayPlanDao` has no method capable of it** — the two writes it gains touch `userId` and `syncedAt` and nothing else. The settled catalogue version for a date (`min`, server-side, FR-024b) is read only by a device that has *not* yet materialised that date, so reconciliation cannot reach a recorded day at all. This is the resolution of the one place an earlier draft of this plan let a stored `availablePoints` move; the constitution supersedes the specification, so SC-004 was amended instead. (d) `HistoricalImmutabilityTest`'s successor for this increment, `RemoteCatalogueImmutabilityTest`, publishes a version with changed points and schedules and asserts every previously recorded day is unchanged while the current day follows the new version — the Principle III test this increment owes for touching persistence and the catalogue. |
| **IV — Offline-first** | Yes | `record`/`undoLast` stay local: the decorator delegates to Room, writes one outbox row in the same transaction, and returns. No network call, no suspend on a socket, no spinner (FR-014). Remote data is never read by a ViewModel — the pull writes into Room and the UI observes Room, as it does today. Fresh install in airplane mode with no account runs the whole Phase 2–6 product (FR-003, SC-007). |
| **V — Backend independence** | Yes | This is the increment Principle V was written for. The three existing repository interfaces are **not modified** — implementations are decorated. The Principle V columns (`id`, `updatedAt`, `deletedAt`, `userId`) are used exactly as intended and no data migration of user history is required (spec Assumption 4, verified against the entities above). Turning Supabase off returns the app to the MVP with no crash and no missing history. |
| **VI — Fixed content** | Yes | No surface in this increment creates, edits, deletes, reorders, reprices, or reschedules a task. The pulled catalogue runs through `scanForAuthoringAffordances` and `CatalogueValidator` before a row is written, so a server payload carrying `editable`/`userId`/`custom` is rejected wholesale — the same gate the local seed passes (FR-027). Publishing is an operator action outside the app; no admin surface ships here. |
| **VII — Deterministic time** | Yes | `TimeProvider` stays the only clock. Server `updated_at` is used as a pull cursor and as ordering data; it is never read as "now", never used to decide the current date, and never used to resolve a conflict (both merges are order-independent, R5 — which is precisely why a wrong device clock cannot win or lose a conflict). `DayBoundary` and `WeekBoundary` remain the single boundary rules; the backfill pages by date without re-implementing either. |
| **VIII — Vertical slices** | Yes | One coherent capability: sign in, keep your history, record on two devices, sign out. No leaderboard, no friends, no realtime subscription, no push, no admin console, no social profile, no account deletion or export surface (spec Assumptions — the last two are recorded as a follow-up obligation, not a silent omission). `RecordCoverage`, the outbox and the device scope each exist because a requirement in this increment needs them. |
| **IX — Encouragement** | Yes | Sync status is `Up to date` / `Changes waiting to be sent` / `Not syncing` / `Still loading earlier days`. No "failed", no "error", no red, no badge count styled as a warning, no nag to create an account, no interstitial. Sign-out warnings state facts ("these changes are not backed up yet") without blame. Audited against the `CLAUDE.md` list before the increment is done (SC-011). |

**Technology constraints**: Kotlin + Compose ✓. MVVM, one immutable state per screen as `StateFlow`
✓. Module direction `:app` → `:data` → `:domain` unchanged, `:domain` still depends on nothing ✓.
Koin remains the sole DI framework — `koin-androidx-workmanager` is a Koin integration, not a second
container, and no Hilt or KSP-based DI is introduced (Room's KSP processor is unchanged) ✓. Room
migration 2 → 3 is additive and its schema is exported ✓. Arabic content handling untouched ✓.

**New network surface** — the constitution requires this to be justified in the plan, and it is the
first network code in the repository (there is no Retrofit anywhere on `develop-v1`; the "existing
Hijri date sync" the constitution refers to lives only on abandoned branches, and `HijriLabel` is
computed locally with no I/O). The surface is Supabase's own client, `supabase-kt`, which brings
Ktor as its transport. Justification and the rejected Retrofit-by-hand alternative are in research
R1 and Complexity Tracking.

**Gate result: PASS.** No principle violation. Complexity Tracking records three constraint
relaxations that the constitution requires to be justified explicitly.

## Project Structure

### Documentation (this feature)

```text
specs/007-identity-cloud-sync/
├── plan.md                      # This file
├── research.md                  # Phase 0 output — R1–R14
├── data-model.md                # Phase 1 output — local + remote entities, states, merges
├── quickstart.md                # Phase 1 output — provision, run, validate
├── contracts/
│   ├── remote-schema.sql        # Postgres tables, constraints, upsert semantics, RLS policies
│   ├── rls-verification.sql     # SC-008 — two users, cross-account reads must return zero rows
│   ├── remote-data-source.md    # The :data interface the sync engine talks to, and its fake
│   ├── sync-engine.md           # Outbox, drain, pull, backfill, retry, status
│   ├── auth.md                  # Sign-in state machine, session lifecycle, sign-out modes
│   ├── repositories.md          # New :domain interfaces and their guarantees
│   └── ui-state.md              # SignInUiState, ProfileUiState, SyncStatus surface, navigation
├── checklists/requirements.md
├── spec.md
└── tasks.md                     # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

Only additions and the marked changes. Everything else is `002`–`006`'s, untouched.

```text
domain/src/
├── main/kotlin/com/giraffe/mizanapp/domain/
│   ├── identity/
│   │   ├── AccountSession.kt              # NEW — SignedOut | SignedIn(userId, email, displayName?)
│   │   ├── SignInStep.kt                  # NEW — the delivery round-trip states (FR-002a)
│   │   └── SignOutMode.kt                 # NEW — KeepLocalRecords | RemoveLocalRecords
│   ├── sync/
│   │   ├── SyncStatus.kt                  # NEW — UpToDate | Pending(n) | NotSyncing | LoadingEarlier
│   │   ├── RecordCoverage.kt              # NEW — knownFrom: LocalDate?, complete: Boolean
│   │   ├── MergeDayRecord.kt              # NEW — pure, min(catalogueVersion) (R5)
│   │   ├── MergeCompletion.kt             # NEW — pure, monotone tombstone (R5)
│   │   ├── RetrySchedule.kt               # NEW — pure backoff, unbounded, never expires (FR-021a)
│   │   └── DeriveSyncStatus.kt            # NEW — pure, (session, pending, reachable, coverage) -> status
│   ├── repository/
│   │   ├── AccountRepository.kt           # NEW — request code, confirm code, session, display name, sign out
│   │   ├── SyncRepository.kt              # NEW — observeStatus, syncNow
│   │   ├── RecordCoverageRepository.kt    # NEW — observeCoverage
│   │   └── CataloguePublicationRepository.kt # NEW — pullIfNewer
│   ├── usecase/
│   │   ├── RequestSignInCode.kt           # NEW
│   │   ├── ConfirmSignInCode.kt           # NEW — also drives first-sign-in migration via SyncRepository
│   │   ├── SignOut.kt                     # NEW — both modes, pending-change warning (FR-007a–d)
│   │   ├── UpdateDisplayName.kt           # NEW — optional, empty by default (FR-007e)
│   │   ├── GetWeekSummary.kt              # CHANGED — + RecordCoverageRepository; skips backfill below the floor
│   │   ├── GetHistoryPage.kt              # CHANGED — + coverage; unfetched dates are NOT_YET_KNOWN
│   │   ├── GetStreakSummary.kt            # CHANGED — + coverage; provisional while backfill incomplete (FR-023d)
│   │   ├── GetMonthOverview.kt            # CHANGED — + coverage
│   │   ├── GetSectionBreakdown.kt         # CHANGED — + coverage (provisional flag)
│   │   └── GetPersonalBests.kt            # CHANGED — + coverage (provisional flag)
│   └── week/
│       ├── DayCellState.kt                # CHANGED — + NOT_YET_KNOWN (neutral, fifth value)
│       └── BuildWeekSummary.kt            # CHANGED — buildDayCells takes RecordCoverage
└── test/kotlin/com/giraffe/mizanapp/domain/
    ├── sync/
    │   ├── MergeDayRecordTest.kt          # NEW — min-version, commutativity, idempotence
    │   ├── MergeCompletionTest.kt         # NEW — reversal monotonicity, both orders, re-record is a new row
    │   ├── RetryScheduleTest.kt           # NEW — growth, ceiling, never-expires, a year of entries
    │   └── DeriveSyncStatusTest.kt        # NEW — every combination, and the Principle IX copy audit
    ├── week/BuildDayCellsCoverageTest.kt  # NEW — NOT_YET_KNOWN vs NOTHING_RECORDED vs OUTSIDE_RECORD
    └── usecase/
        ├── GetWeekSummaryCoverageTest.kt  # NEW — no backfill below the coverage floor
        ├── GetHistoryPageCoverageTest.kt  # NEW — history is named explicitly by FR-023b
        ├── GetInsightsCoverageTest.kt     # NEW — month / section / bests, provisional (FR-023d)
        ├── GetStreakSummaryCoverageTest.kt# NEW — provisional while incomplete (FR-023d)
        ├── SignOutTest.kt                 # NEW — both modes; warning on pending; account untouched (FR-007d)
        └── ConfirmSignInCodeTest.kt       # NEW — expired/incorrect code keeps the email and the records

data/src/
├── main/kotlin/com/giraffe/mizanapp/data/
│   ├── db/
│   │   ├── MizanDatabase.kt               # CHANGED — version 3, + 3 entities
│   │   ├── Migrations.kt                  # CHANGED — + MIGRATION_2_3, additive only
│   │   ├── entities/SyncEntities.kt       # NEW — OutboxEntity, SyncCursorEntity, AccountScopeEntity
│   │   ├── entities/DayEntities.kt        # CHANGED — + syncedAt on DayPlanEntity, CompletionEntity
│   │   ├── daos/OutboxDao.kt              # NEW
│   │   ├── daos/SyncCursorDao.kt          # NEW
│   │   ├── daos/AccountScopeDao.kt        # NEW
│   │   ├── daos/CompletionDao.kt          # CHANGED — claimForUser, markSynced, upsertFromRemote
│   │   └── daos/DayPlanDao.kt             # CHANGED — claimForUser (plans + planned tasks), markSynced. No figure is writable (FR-024a)
│   ├── repository/
│   │   ├── SyncingCompletionRepository.kt # NEW — decorator: delegate, then enqueue (R2)
│   │   ├── SyncingDayPlanRepository.kt    # NEW — decorator
│   │   ├── SupabaseAccountRepository.kt   # NEW
│   │   ├── OutboxSyncRepository.kt        # NEW — status + syncNow
│   │   ├── RoomRecordCoverageRepository.kt# NEW — signed-out: complete at earliestPlanDate()
│   │   └── RemoteCataloguePublicationRepository.kt # NEW — pull, validate, insert-only (R10)
│   ├── sync/
│   │   ├── RemoteDataSource.kt            # NEW — interface; no Supabase type escapes this file's impl
│   │   ├── SupabaseRemoteDataSource.kt    # NEW
│   │   ├── NoOpRemoteDataSource.kt        # NEW — every call returns Unreachable; bound when no config is present
│   │   ├── SupabaseClientFactory.kt       # NEW — url/key from BuildConfig, session storage
│   │   ├── dto/RemoteDtos.kt              # NEW — RemoteCompletion, RemoteDayRecord, RemoteProfile, RemotePublication
│   │   ├── Outbox.kt                      # NEW — OutboxEntry + deterministic ids, enqueue-in-transaction (R6)
│   │   ├── SyncEngine.kt                  # NEW — drain, pull, backfill, claim-and-upload (R7)
│   │   ├── Backfill.kt                    # NEW — descending pages, resumable cursor (R11)
│   │   ├── SyncWorker.kt                  # NEW — WorkManager, network constraint (R12)
│   │   ├── SyncScheduler.kt               # NEW — enqueue on write, on connectivity, on foreground
│   │   └── LocalRecordWipe.kt             # NEW — the one destructive path, user-confirmed only (R8)
│   └── time/SystemTimeProvider.kt         # unchanged
├── androidTest/kotlin/com/giraffe/mizanapp/data/
│   ├── MizanDatabaseMigrationTest.kt      # CHANGED — + 2→3, and that a v2 database keeps every row
│   ├── OutboxDurabilityTest.kt            # NEW — survives process death; a year of entries; never evicted (FR-015, FR-021a, SC-010)
│   ├── OutboxIdempotencyTest.kt           # NEW — same change enqueued/submitted n times -> one row (FR-017, SC-005)
│   ├── SignInMigrationTest.kt             # NEW — every local record claimed, uploaded, unchanged (FR-008–FR-012, SC-001)
│   ├── SignInMigrationResumeTest.kt       # NEW — interrupted at each stage, then resumed (FR-009)
│   ├── SignInUnionTest.kt                 # NEW — account records + local records, nothing discarded (FR-011)
│   ├── SessionPersistenceTest.kt          # NEW — survives process death, renews silently (FR-005)
│   ├── SessionExpiryTest.kt               # NEW — unrenewable session keeps every record (FR-006)
│   ├── BackgroundSyncSchedulingTest.kt    # NEW — WorkManagerTestInitHelper, drains on constraint (FR-016, SC-003)
│   ├── SupabaseRemoteDataSourceContractTest.kt # NEW — live, skipped when unconfigured
│   ├── TwoDeviceConvergenceTest.kt        # NEW — two DBs, one fake remote (FR-020, SC-004, FR-024b)
│   ├── UndoTombstoneSyncTest.kt           # NEW — undo propagates as removal, never resurrection (FR-018)
│   ├── BackfillResumeTest.kt              # NEW — resumable, no re-fetch, no duplicate (FR-023a–c, SC-006)
│   ├── ForeignAccountIsolationTest.kt     # NEW — V signs in on U's device; no record crosses (FR-013)
│   ├── SignOutModesTest.kt                # NEW — keep vs remove; account untouched either way (FR-007a–d)
│   ├── RemoteCatalogueImmutabilityTest.kt # NEW — Principle III test for this increment (FR-026, SC-009)
│   ├── UnknownCatalogueVersionTest.kt     # NEW — unreadable publication ignored, no crash (FR-028)
│   ├── OfflineRecordingUnaffectedTest.kt  # NEW — signed-in airplane mode == signed-out latency (FR-014, SC-002)
│   └── FakeRemoteDataSource.kt            # NEW — in-memory Postgres semantics: upsert, RLS scoping, failure injection
└── build.gradle.kts                       # CHANGED — supabase-kt BOM, auth-kt, postgrest-kt, ktor engine, work-runtime

app/src/
├── main/java/com/giraffe/mizanapp/
│   ├── MizanApplication.kt                # CHANGED — WorkManager/Koin wiring, sync scheduled on start
│   ├── MainActivity.kt                    # CHANGED — + Destination.SignIn, Destination.Profile
│   ├── di/Modules.kt                      # CHANGED — new repositories, use cases, ViewModels, worker
│   ├── auth/
│   │   ├── SignInUiState.kt               # NEW — email kept across every failure (FR-002a)
│   │   ├── SignInViewModel.kt             # NEW
│   │   └── SignInScreen.kt                # NEW
│   ├── profile/
│   │   ├── ProfileUiState.kt              # NEW — email, optional display name, sync status, sign-out
│   │   ├── ProfileViewModel.kt            # NEW
│   │   └── ProfileScreen.kt               # NEW — both sign-out paths and their confirmations
│   ├── sync/SyncStatusBar.kt              # NEW — one neutral surface, reused on Today and Week
│   ├── today/TodayScreen.kt               # CHANGED — hosts the status surface; entry point to profile
│   ├── week/WeekScreen.kt                 # CHANGED — hosts the status surface
│   └── ui/DayCellColors.kt                # CHANGED — NOT_YET_KNOWN gets a neutral, non-red container
├── test/java/com/giraffe/mizanapp/
│   ├── auth/SignInViewModelTest.kt        # NEW — every step, resend wait, expired/incorrect code
│   ├── profile/ProfileViewModelTest.kt    # NEW — display-name edit, both sign-out modes, warnings
│   ├── ui/DayCellColorsTest.kt            # NEW — NOT_YET_KNOWN is distinct and non-red (Principle IX)
│   ├── NavigationRoutingTest.kt           # CHANGED — SIGNIN / PROFILE encode-decode round trip
│   └── sync/SyncStatusCopyTest.kt         # NEW — Principle IX audit over every status string (SC-011)
└── androidTest/java/com/giraffe/mizanapp/
    ├── auth/SignInScreenTest.kt           # NEW — round-trip states, offline sign-in attempt (US1 AS3)
    ├── profile/ProfileScreenTest.kt       # NEW — confirmation names what is removed (FR-007b)
    ├── sync/SyncStatusBarTest.kt          # NEW — no red, no blame, never blocks (Principle IX)
    └── NoAccountGateTest.kt               # NEW — no screen gates or nags for an account (FR-004, SC-007)

supabase/
├── migrations/0001_identity_cloud_sync.sql # NEW — applied schema; mirrors contracts/remote-schema.sql
└── seed/catalogue_publication.sql          # NEW — publishes the v1 catalogue the app already ships
```

**On Compose UI tests**: Principle I exempts only DI wiring, `@Preview` composables, and generated
code. `SignInScreen`, `ProfileScreen` and `SyncStatusBar` are none of those, so their tests precede
them in tasks.md, as `003`–`006` established.

**Structure Decision**: no new Gradle module. Sync lives in a `sync` package inside `:data`, where
the Supabase and Ktor types are already confined by the module boundary, and the domain gains only
interfaces and pure functions. A `:sync` module was considered and rejected in R3: it would need
`:data`'s Room types and `:data` would need its engine, which is a dependency cycle dressed up as
separation. The `supabase/` directory at the repository root holds the applied SQL so the remote
schema is version-controlled beside the code that depends on it.

## Complexity Tracking

No principle is violated, and nothing here is offered as an exception to Principle I or III. These
three entries are recorded because the constitution requires an explicit, written justification for
a new network surface, and because two design constraints deliberately established in earlier
increments are being relaxed.

| Relaxation | Why needed | Simpler alternative rejected because |
|---|---|---|
| **New network surface: supabase-kt + Ktor + WorkManager**, where the constitution names Retrofit | Supabase's auth flow (one-time code delivery, session persistence, silent token refresh, expiry) and PostgREST's upsert/`on_conflict` semantics are the whole substance of FR-005, FR-006, FR-017 and FR-019. WorkManager is the only sanctioned way to drain the queue with the app closed, which SC-003 states as a one-minute guarantee. | Hand-rolling GoTrue and PostgREST over Retrofit was costed in R1: it means owning token refresh, session storage, and PostgREST's conflict-target encoding by hand — the three places where a bug silently loses a user's record or their session. Retrofit is not cheaper here, only less tested. Draining the queue only while the app is foregrounded was rejected because it cannot meet SC-003 and turns "your record is backed up" into "your record is backed up if you happen to open the app". |
| **`DayPlanDao` gains two narrow write methods**, where `002` deliberately gave it none | `claimForUser` (sets `userId` where it is null) and `markSynced` (sets `syncedAt`) write sync bookkeeping only. **Neither can reach a date, a catalogue version, a points value, or a task set**, so no stored figure is writable through this DAO by any caller — which is what makes FR-024a structural rather than a promise. | Leaving the DAO write-free would mean either that a record can never be associated with an account — which makes FR-008 and FR-013 unimplementable — or that association is tracked in a side table that every read must join, reintroducing the forgotten-`WHERE`-clause risk R8 rejects. An earlier draft added a third method, `adoptMergedVersion`, which could move a recorded day's available-points total onto an older catalogue version. It was removed: Principle III admits no exception and may not be traded away here, so the convergence gap it closed is now stated honestly in SC-004 instead of engineered around. |
| **Six existing domain use cases gain a `RecordCoverage` input** | FR-023b forbids showing an unfetched date as 0% or as absent, in *every* view — week, history, streak, insights. The distinction between "not known yet" and "nothing recorded" is a property of the record, not of the sync mechanism, and it has to reach the read models to be renderable. | Deriving coverage inside each ViewModel was rejected: six ViewModels would each hold a copy of the rule, which is exactly the "no second opinion" failure Principle VII warns about. Signed out, `RecordCoverage` is `complete` with `knownFrom = earliestPlanDate()` — byte-for-byte today's behaviour — so this is a generalisation of the record-start floor `003` already threads through these same call sites, not sync leaking into the domain. |
