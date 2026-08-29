# Phase 0 Research: Identity & Cloud Sync

Every item below was an unknown raised by the Technical Context, resolved against the merged code on
`develop-v1` (through `006`) and against the spec's clarifications, rather than assumed. No
`NEEDS CLARIFICATION` remains.

## R1 — The client is `supabase-kt`; Ktor arrives with it and is confined to `:data`

**Decision**: add `io.github.jan-tennert.supabase` via its BOM (3.5.0 at time of writing; the exact
version is pinned in `libs.versions.toml` and re-confirmed at implementation), taking `auth-kt` and
`postgrest-kt`, plus `io.ktor:ktor-client-okhttp` as the engine. `realtime-kt`, `storage-kt`,
`functions-kt` and both `compose-auth` artifacts are deliberately **not** taken. Every Supabase and
Ktor type stays inside `data/sync/`, behind the `RemoteDataSource` interface.

**Rationale**: this is the first network code in the repository — `grep` over `develop-v1` finds no
Retrofit, Ktor, or OkHttp in any build file or source file, and `HijriLabel` is computed locally with
no I/O, so the constitution's "existing Hijri date synchronisation" has no code on this branch. The
constitution permits a new network surface with explicit justification, which is what this section
is. The substance being bought is not HTTP plumbing: it is GoTrue's one-time-code flow, session
persistence across process death, silent refresh, and PostgREST's `on_conflict` upsert encoding —
which are precisely FR-005, FR-006, FR-017 and FR-019. Those are the four places where a hand-rolled
client loses a session or a record silently.

**Alternatives considered**:
- **Retrofit against GoTrue and PostgREST by hand** — the constitution's default. Rejected: it
  means owning token refresh and session storage, and encoding PostgREST's conflict targets and
  `Prefer: resolution=merge-duplicates` headers manually. Retrofit is already in the constitution,
  but writing an auth client is not cheaper than adopting one, only less tested. Recorded in the
  plan's Complexity Tracking.
- **Taking `realtime-kt` for live convergence** — rejected: the spec puts realtime subscriptions out
  of scope, WorkManager plus foreground sync meets SC-003, and a socket on the recording path is
  what Principle IV exists to prevent.
- **`compose-auth` / `compose-auth-ui`** — rejected: it ships Google/social sign-in affordances, and
  FR-002 permits exactly one method with no social provider.

## R2 — Sync decorates the Room repositories; it does not replace or modify them

**Decision**: `SyncingCompletionRepository(delegate: CompletionRepository, outbox: Outbox, scope:
AccountScope)` implements `CompletionRepository` by delegating the write and, in the same Room
transaction, enqueueing the outbox entry. Same shape for `SyncingDayPlanRepository`. Koin binds the
decorator as the `CompletionRepository`/`DayPlanRepository`; the Room implementations are bound by
concrete type and injected into it. The three existing interfaces are unchanged.

**Rationale**: the roadmap's own words for this phase — "swap or decorate implementations, leave
domain untouched. If a single domain use case has to change to accommodate sync, treat that as a
design smell and fix the boundary instead." Decoration also makes the Principle IV guarantee
structural: the decorator has no network dependency at all, only the outbox DAO, so there is no
socket it *could* touch on the record path.

**Alternatives considered**:
- Enqueue from inside `RoomCompletionRepository` — rejected: it puts sync bookkeeping in the class
  whose whole job is the local record, and it cannot be turned off for signed-out use.
- A Room `@Trigger`/observer that derives the outbox from row changes — rejected: Room offers no
  portable trigger DSL, and a queue derived from change notifications is not durable across process
  death at the moment of the write, which FR-015 requires.
- Enqueue from the ViewModel — rejected outright: a record written by any other caller would never
  be queued.

## R3 — Sync lives in a `sync` package inside `:data`, not a new `:sync` module

**Decision**: no new Gradle module. `data/src/main/kotlin/.../data/sync/`.

**Rationale**: the engine needs Room entities, DAOs, and the database transaction (the outbox row
must be written atomically with the record), and `:data` needs the engine to construct its
decorators. Splitting them creates a cycle, which is resolved either by duplicating the entity
definitions or by inverting into a third module — both of which are the speculative layering
Principle VIII forbids. The module boundary that matters (`:domain` sees no backend type) is already
enforced by `ModuleBoundaryTest` and is not weakened by this choice.

**Alternatives considered**: a `:sync` module, and a `:core:network` module. Both rejected as above,
and neither substitutes anything today (Principle VIII).

## R4 — A day syncs as a version pointer; planned tasks are re-derived, never transmitted

**Decision**: the remote `day_records` row is `(user_id, date, catalogue_version)` plus server
bookkeeping. Planned tasks are never sent or received. On ingest, a device that lacks a plan for that
date builds one with `buildDayPlan(catalogueAt(version), version, date, origin = BACKFILLED)`; a
device that already has one merges per R5. Completions carry `credited_date` and `task_slug`
remotely, never a `dayPlanId`, and are re-bound to the local plan by date on ingest.

**Rationale**: the roadmap recommends keeping Day Plans local and rebuildable "since two devices
must produce the same plan from the same version" — and `buildDayPlan` is already a pure function of
`(catalogue, version, date)`, so that holds. What does *not* hold without the pointer is which
version a date was materialised against: device A, offline on catalogue v1, and device B, on v2, will
derive different task sets and different available totals for the same date, and US3 AS7 explicitly
requires "one set of applicable tasks and one available-points total — not a duplicated or doubled
day". Sending one integer per day fixes that; sending the whole plan would transmit ~15 rows per day
to reproduce something both devices can compute. The local `DayPlan.id` and `PlannedTask.id` UUIDs
stay device-local, which is also why they never needed to agree.

**Alternatives considered**:
- **Sync full day plans and planned tasks** — rejected: ~5 500 planned-task rows per user-year to
  transmit and store for data that is a pure function of two values, and it makes the local plan a
  replica rather than the local source of truth (Principle IV).
- **Sync nothing about the day; derive the version from `versionEffectiveOn(date)` on each device**
  — rejected: it is correct only while every device holds the same catalogue, so a device that has
  been offline across a publication diverges permanently. It also depends on a publication rule
  (effective-from always in the future) that the app cannot enforce.

## R5 — Both merges are monotone, so no clock decides anything — and a recorded day is never rewritten

**Decision**:

```kotlin
fun mergeDayRecord(local: DayRecord?, remote: DayRecord?): DayRecord   // min(catalogueVersion)
fun mergeCompletion(local: Completion?, remote: Completion?): Completion // reversedAt = local ?: remote
```

A day record settles on the **lowest** catalogue version either side recorded. A completion is
`live` only while neither side has a tombstone; once either has one, the merged result is reversed
and stays reversed. Both are commutative, associative and idempotent, so every device reaches the
same result regardless of the order changes arrive in and regardless of how many times one arrives.

**The settled version is read in exactly one situation: by a device that does not yet have a local
plan for that date.** A device that has already materialised the date ignores it completely. There
is no `adoptMergedVersion`, no re-derivation, and no DAO method through which a stored day's
version, tasks, points, or available total could be written (FR-024a). See the Consequence below —
an earlier draft of this plan had one, and it was removed.

**Rationale**: FR-019 specifies last-write-wins per completion occurrence. The only mutable field on
a completion is `reversedAt` — re-recording after an undo creates a *new* row with a *new* UUID
(`RoomCompletionRepository.record`), it does not revive the old one — so "the later action" is always
the reversal, and monotone-tombstone and last-write-wins produce identical outcomes. Monotone gets
there without trusting a timestamp, which matters because the spec's Assumption names device clocks
as untrustworthy and an Edge Case asks what a wrong clock does: under this rule, nothing. For the day
record, `min` is chosen over first-writer-wins because it needs no server timestamp *and* because its
direction is the one Principle III cares about — a newer catalogue can never claim an older day.

**Alternatives considered**:
- **LWW on server `updated_at` for both** — rejected as strictly weaker: it needs the server round
  trip to be authoritative for correctness, and it produces order-dependent outcomes for concurrent
  writes with equal timestamps.
- **First-writer-wins on the day record via `created_at`** — semantically attractive ("the version
  in force when the date was first opened") but it reintroduces a timestamp dependency, and in the
  case that actually matters — a lagging device on an older catalogue — `min` gives the same answer.
- **Vector clocks or a CRDT library** — rejected: two monotone joins over near-immutable facts do
  not need a framework, and Principle VIII forbids the abstraction.

**Consequence, stated plainly**: two devices that independently materialised the same date under
*different* catalogue versions before either synced keep different available-points totals for that
date, permanently. Their completions and their earned points agree exactly — `pointsAwarded` is
frozen on each completion and is not an input to any merge — and any device joining later derives
the settled (older) version. Only the denominator differs, and only on that date.

This is a deliberate reversal of an earlier draft, which closed the gap with an `adoptMergedVersion`
method that re-derived the losing device's day onto the older version. That method could change what
a recorded day reports, which Principle III forbids without exception, and the constitution's
Governance section makes the constitution supersede this feature's own success criteria. So the
method is gone and **SC-004 was amended to describe what actually holds** rather than the code being
bent to match the original wording. The residual divergence needs a stale catalogue *and* a
publication landing between two devices' materialisation of the same date; the publication rule at
the foot of `contracts/remote-schema.sql` (effective-from in the future) makes it rarer still.

## R6 — Outbox entries have deterministic ids, which is what makes retry and migration idempotent

**Decision**: `OutboxEntity.id = "$entityType:$entityId:$operation"` — e.g.
`completion:6f2c…:UPSERT`, `day_record:2026-08-16:UPSERT`. Enqueue is `INSERT OR REPLACE`, so the
same change enqueued any number of times leaves one row carrying the newest payload. Remote writes
are upserts on the client-generated UUID (completions) or on `(user_id, date)` (day records).

**Rationale**: FR-009 (resumable, idempotent migration), FR-017 (idempotent transmission) and SC-005
are the same requirement seen from three angles, and a deterministic key satisfies all three with no
dedupe pass. It also makes the first-sign-in upload indistinguishable from an ordinary retry: claim
the rows, enqueue them all, drain — re-running any step changes nothing.

**Alternatives considered**: a random outbox id with a uniqueness index on
`(entityType, entityId, operation)` — equivalent, but it requires `INSERT … ON CONFLICT` gymnastics
in Room for no gain. A monotonically increasing sequence — rejected, it is an auto-increment identity
by another name and would reorder badly across the reconciled queue.

## R7 — First sign-in: claim, enqueue, drain — in that order, resumable at every step

**Decision**: on a successful sign-in where the device scope is empty or already this account:

1. Set `account_scope` to the signed-in user (single row).
2. `UPDATE day_plans SET userId = :u WHERE userId IS NULL`, then `planned_tasks`, then `completions`.
   Idempotent by the `IS NULL` guard.
3. Enqueue one outbox entry per claimed day record and per claimed completion, oldest date first.
4. Drain the outbox (R6 semantics), then pull (R11) to obtain the union with anything the account
   already holds.

No local row is deleted, overwritten, or recomputed at any point in this sequence, including on
failure (FR-010). "Backed up" is reported per row from `syncedAt`, set only when the account has
accepted the write (FR-012).

**Rationale**: step order is chosen so that any interruption leaves a state the next run repairs:
scope-without-claim re-claims, claim-without-enqueue re-enqueues (the `IS NULL` guard has already
fired, so the enqueue reads claimed-but-unsynced rows instead), enqueue-without-drain drains. FR-011's
union falls out of upsert semantics plus the pull — no client-side merge pass is needed for records
that only one side has.

**Alternatives considered**:
- Upload first and claim on acknowledgement — rejected: a crash between the two loses the knowledge
  of which rows were meant for the account, and re-deriving it requires a full remote read.
- A dedicated one-shot "migration" table tracking progress — rejected: the outbox already is that
  table, and a second progress ledger is a second thing to get out of step (Principle VIII).

## R8 — One account's records live on a device at a time; switching accounts is an explicit, confirmed wipe

**Decision**: a single-row `account_scope` table holds the account whose records the device is
currently carrying. Signing in as account **V** while the scope holds account **U** runs the same
confirmation flow as "sign out and remove data from this device" — it names what will be removed,
warns if U has unsent changes, and requires a second confirmation — and then clears U's local rows
before V's session opens. Plain sign-out leaves the scope and every row in place (FR-007a); signing
the *same* account back in never wipes anything.

**Rationale**: FR-013 and US5 AS6 require that no record crosses between accounts, and **FR-013a
authorises the removal explicitly** — added to the spec after the first analysis pass noted that
deleting a record belonging to somebody else was not sanctioned by any requirement. The alternative —
adding `AND (userId = :scope OR userId IS NULL)` to every existing read — spreads the guarantee
across thirty-odd queries in three DAOs, each of which fails open: one forgotten clause silently
shows one user another user's worship record, which is the worst failure this feature can produce.
Scoping the *device* instead makes the guarantee structural: there is only ever one account's data
present, so no query can leak. It also keeps every `002`–`006` query byte-for-byte unchanged, which
is worth a great deal in an increment this size.

**Alternatives considered**:
- **Per-row filtering on every read** — rejected as above. Fails open, touches every query, and
  is untestable exhaustively.
- **Wipe silently on foreign sign-in** — rejected: it destroys local records without the explicit,
  named confirmation FR-007b establishes as this product's standard for local destruction.
- **Multiple local profiles on one device** — rejected: no requirement asks for it, and it multiplies
  the read-filter risk rather than removing it (Principle VIII).

## R9 — `RecordCoverage` generalises the record-start floor `003` already threads through

**Decision**:

```kotlin
data class RecordCoverage(val knownFrom: LocalDate?, val complete: Boolean) {
    companion object { fun completeFrom(recordStart: LocalDate?) = RecordCoverage(recordStart, true) }
}
```

`buildDayCells` takes it and emits a fifth, neutral `DayCellState.NOT_YET_KNOWN` for a date below
`knownFrom` while `complete` is false. `GetWeekSummary` does not backfill such dates. `GetStreakSummary`,
`GetSectionBreakdown` and `GetPersonalBests` carry a `provisional` flag while coverage over their
range is incomplete (FR-023d). Signed out, `RoomRecordCoverageRepository` returns
`completeFrom(earliestPlanDate())`, which reproduces today's behaviour exactly.

**Rationale**: FR-023b forbids rendering an unfetched date as 0%, untouched, or absent — in every
view. That distinction cannot live in the ViewModels without six copies of the rule, and it cannot
live in `:data` because it changes what the read models emit. It is genuinely domain-level: "how far
back the record is known" is the same kind of fact as "where the record starts", which `003` already
passes into `buildWeekSummary` as `recordStart`. Adding a fifth enum value rather than a boolean
beside it means the compiler forces every `when` — including `DayCellColors` — to handle it.

**Alternatives considered**:
- A nullable `DayCell.isKnown` boolean — rejected: exhaustiveness is not enforced, and the colour
  mapping would silently keep painting an unknown day as `NOTHING_RECORDED`.
- Blocking the UI until backfill completes — rejected outright by SC-006 and Principle IV.
- Letting `GetWeekSummary` backfill below the floor anyway — rejected: it materialises a competing
  day record for a date the account may already hold under a different version, creating exactly the
  duplicate-materialisation the R5 merge then has to clean up.

## R10 — A catalogue pull may insert a version and may never alter one

**Decision**: `catalogue_publications(version, effective_from, format_version, payload jsonb)` is
readable by any authenticated user and writable by no one through the API. The client pulls
publications with a `format_version` it understands, skips any it does not (FR-028), runs the payload
through the **existing** `scanForAuthoringAffordances` → `parseCatalogue` → `CatalogueValidator`
chain, and inserts only versions absent from `catalogue_versions`. There is no update path, no
delete path, and no method on `CatalogueDao` that could rewrite an existing version's task versions.

**Rationale**: Principle III's stated hazard is a catalogue edit rewriting recorded history, and the
cheapest defence is that the operation does not exist in the code. Insert-only also makes the pull
naturally idempotent. Reusing `001`'s validator means a server payload faces the same gate as the
local seed — including the `FORBIDDEN_AUTHORING_FIELDS` scan, which already lists `userId`,
`editable` and `custom`, so a payload shaped for user authoring is rejected wholesale (FR-027,
Principle VI). `parseCatalogue`'s `ignoreUnknownKeys = false` is a second net: a future payload shape
fails to parse rather than silently defaulting a task to zero points, and the app keeps the newest
version it can read.

**Alternatives considered**:
- Replace the whole catalogue on pull — rejected: it deletes the versions past days were scored
  against, which is the Principle III violation this feature is most capable of committing.
- Version the payload inside the JSON instead of in a column — rejected: the client would have to
  parse a payload it may not understand in order to discover that it does not understand it.

## R11 — Backfill is date-descending, paged, and resumable from a stored floor

**Decision**: after sign-in, the pull runs in two parts. **Head**: today's and the current week's day
records and completions, so the app is usable in seconds (FR-023a, SC-006). **Backfill**: pages of
90 days, descending from the day before the head window, each page written in one transaction and
followed by a `sync_cursors` update of `backfill_floor`; a page that returns nothing older sets
`backfill_complete`. Subsequent incremental pulls use a `updated_at > cursor` read bounded to
`>= backfill_floor`.

**Rationale**: FR-023c requires resumption without re-fetching or duplicating. Storing the floor after
each committed page makes a crash cost at most one page, and upsert semantics make even that page
harmless to re-apply. Descending order is what makes the *useful* history arrive first — a user
opening History sees recent weeks immediately. 90 days is chosen as roughly one insights period and
a few hundred rows; it is a constant, not a promise to the user.

**Alternatives considered**: a single unbounded fetch (rejected — a year-plus first sync is the Edge
Case that would time out, and it is not resumable); ascending order (rejected — the least useful
history arrives first); a per-row cursor (rejected — dates are already the natural page key and the
day-descending order is what the UI consumes).

## R12 — WorkManager drains the queue; the app foreground and connectivity both nudge it

**Decision**: `SyncWorker` is a `CoroutineWorker` with a `NetworkType.CONNECTED` constraint,
enqueued as unique expedited work when (a) a change is written, (b) the app is foregrounded, and
(c) WorkManager's own connectivity constraint is met. Backoff is WorkManager's exponential policy for
the worker, with the per-entry `RetrySchedule` deciding which entries are due within a run.
`koin-androidx-workmanager` supplies the worker's dependencies.

**Rationale**: SC-003 promises delivery within a minute of connectivity returning "without opening
any screen or pressing anything", which requires execution with the app closed. WorkManager is the
only sanctioned mechanism for that on `minSdk 24`, it already owns constraint handling and process
death, and it does not introduce a second DI container — the Koin integration binds the worker.

**Alternatives considered**:
- A foreground-only coroutine on a connectivity callback — rejected: cannot satisfy SC-003 and
  redefines "backed up" as "backed up if you open the app".
- A `JobScheduler`/`AlarmManager` implementation — rejected: reimplementing WorkManager badly.
- A periodic 15-minute poll only — rejected: it is the worst of both, a fixed interval the spec
  explicitly declines to promise (Assumptions) and a minute-scale guarantee it cannot meet.

## R13 — Row-level security is the client's non-guarantee, so it is tested outside the client

**Decision**: RLS is enabled on `profiles`, `day_records` and `completions`, with
`USING (user_id = auth.uid())` for select and `WITH CHECK (user_id = auth.uid())` for insert and
update. No delete policy exists on any table — nothing in the product deletes a record, only
tombstones it. `catalogue_publications` has a select-only policy for authenticated users and no write
policy at all. SC-008 is verified by [contracts/rls-verification.sql](./contracts/rls-verification.sql):
two test users, each inserting a record, each attempting to read and to update the other's, asserting
zero rows and zero affected rows in both directions.

**Rationale**: FR-023 requires enforcement "by the account service itself and not by the client", and
SC-008 requires verification "directly against the service rather than through the app". A test that
runs through `RemoteDataSource` proves only that the client asked politely.

**Alternatives considered**: filtering by `user_id` in client queries alone (rejected — that is the
client enforcing it, which the requirement names as insufficient); a service-role key in the app
(rejected — it would bypass RLS entirely and must never ship in a client).

## R14 — Every sync test runs against a fake remote, including two-device convergence

**Decision**: `RemoteDataSource` is an interface in `data/sync/` with two implementations —
`SupabaseRemoteDataSource` and `FakeRemoteDataSource`. The fake implements the Postgres semantics the
engine depends on and nothing else: upsert on the declared conflict target, `LEAST()` on day-record
version, `COALESCE` on completion tombstones, per-user row scoping, a monotonic `updated_at`, and
injectable failures (unreachable, rejected write, mid-batch drop, ambiguous acknowledgement).
`TwoDeviceConvergenceTest` runs two in-memory `MizanDatabase` instances against one fake and asserts
identical records and identical scores after both drain.

**Rationale**: SC-004, SC-005, SC-010 and the whole of US2/US3 are properties of the engine, and an
engine tested only against a live project is tested only when the network is up. The fake makes the
adversarial cases — a year of queued entries, an interruption at a chosen byte, a duplicate submit —
ordinary unit tests. The two guarantees a fake cannot honestly provide are RLS (R13, tested in SQL)
and the real client's auth flow, which gets a thin contract test run against a project when one is
configured and skipped otherwise.

**Alternatives considered**: Testcontainers with a local Supabase stack (rejected for instrumented
Android tests — the harness cost dwarfs the fake, and it still would not run in CI on a device);
testing only against a live project (rejected — non-hermetic, and the failure-injection cases are
unreachable).
