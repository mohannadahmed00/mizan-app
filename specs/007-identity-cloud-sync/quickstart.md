# Quickstart: Identity & Cloud Sync

How to provision the backend, run the increment, and prove each success criterion. Every command is
run from the repository root on Windows PowerShell; substitute `./gradlew` on a POSIX shell.

Design details are not repeated here — see [data-model.md](./data-model.md),
[contracts/sync-engine.md](./contracts/sync-engine.md) and
[contracts/repositories.md](./contracts/repositories.md).

## Prerequisites

- A Supabase project (free tier is sufficient), with the database URL and the **anon** key.
  Never put a service-role key in the app — it bypasses row-level security.
- The Supabase CLI, for applying migrations and running the RLS verification.
- An emulator or device for the `:data` and `:app` instrumented suites.

## 1. Provision the remote schema

```powershell
supabase link --project-ref <ref>
Copy-Item specs\007-identity-cloud-sync\contracts\remote-schema.sql supabase\migrations\0001_identity_cloud_sync.sql
supabase db push
```

The applied migration must stay identical to the contract file. Then publish the catalogue the app
already ships, so a fresh device pulls the same version it seeds locally:

```powershell
supabase db execute --file supabase\seed\catalogue_publication.sql
```

Verify: one row in `catalogue_publications`, `format_version = 1`, and its `payload` byte-identical
to `domain/src/main/resources/catalogue/valid-catalogue.json`.

## 2. Configure the app

`local.properties` (git-ignored), or CI secrets of the same names:

```properties
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_ANON_KEY=<anon key>
```

Both reach the app through `BuildConfig` and are read only by `SupabaseClientFactory`. Building
**without** them must still work: sign-in reports itself unavailable and the app runs as the offline
MVP. Check that first — it is SC-007's cheapest form.

```powershell
.\gradlew :app:assembleDebug          # with the keys absent
```

## 3. Run the test suites

```powershell
.\gradlew :domain:test                 # merges, backoff, status, coverage, sign-out rules
.\gradlew :app:test                    # sign-in / profile ViewModels, the Principle IX copy audit
.\gradlew :data:connectedAndroidTest   # migration, outbox, engine, convergence, wipe — all against the fake
.\gradlew :app:connectedAndroidTest    # sign-in, profile, status bar, no-account-gate
```

All four green is the merge bar, together with the Constitution Check in [plan.md](./plan.md) and
test-task-before-implementation-task ordering visible in the PR's commit history (Principle I).

## 4. Validate each success criterion

### SC-001 — every pre-existing local record survives first sign-in

*Automated*: `SignInMigrationTest`, `SignInMigrationResumeTest`.

*By hand*: record across several days signed out; note each day's earned/available. Sign in. Every
day must report identical figures. Then uninstall, reinstall, sign in again — the same figures return.

```powershell
adb uninstall com.giraffe.mizanapp
.\gradlew :app:installDebug
```

### SC-002 — signed-in recording is as fast as signed-out

*Automated*: `OfflineRecordingUnaffectedTest` asserts no network interaction on the record/undo path
and compares timings signed-in versus signed-out.

*By hand*: airplane mode, signed in. Record and undo repeatedly. No spinner, no disabled control, no
delay, no error.

### SC-003 — offline recording reaches the account within a minute of reconnecting

Airplane mode, signed in, record a full day. Close the app. Restore connectivity. Without opening the
app, wait 60 seconds, then query the account:

```powershell
supabase db execute --command "select count(*) from completions where credited_date = current_date;"
```

Force the worker to prove the scheduling path in isolation:

```powershell
adb shell cmd jobscheduler run -f com.giraffe.mizanapp <job-id>
```

### SC-004 — two devices converge

*Automated*: `TwoDeviceConvergenceTest` — two databases, one fake remote, covering concurrent
recording, concurrent undo, independent first-open of the same date under one catalogue version, and
independent first-open under two versions.

*By hand*: two emulators, one account. Both offline; record different tasks on each. Bring both
online. Both must show the same completions and the same earned points for every affected day.

**Note the one documented exception**: a date independently materialised on both devices under
*different* catalogue versions keeps a different available-points denominator on each, because a
recorded day is never rewritten (FR-024a, Principle III). Earned figures still match exactly, and
the account settles on the older version for any device joining later. Assert this, do not treat it
as a bug.

### SC-005 — resubmission is harmless

*Automated*: `OutboxIdempotencyTest`, plus the fake's ambiguous-acknowledgement injection. One record,
unchanged scores, however many submissions.

### SC-006 — a new device is usable in 10 seconds and complete in two minutes

*Automated*: `BackfillResumeTest` against a year-plus fixture.

*By hand*: sign into an account holding a year of history on a fresh install. Today must be
recordable within 10 seconds. History and Insights must show earlier dates as **still loading** — never
0%, never absent — until the backfill completes.

### SC-007 — the app degrades exactly to the MVP

*Automated*: `NoAccountGateTest`.

*By hand*: fresh install, airplane mode, no account. Walk Today, Week, Streak, History, Insights end
to end. Nothing blocked, nothing hidden, no account prompt in the way. Then repeat with the network
on but the Supabase project paused — same result.

### SC-008 — no account can read another's records

Not testable through the app, by requirement. Run against the project:

```powershell
supabase db execute --file specs\007-identity-cloud-sync\contracts\rls-verification.sql
```

Expected: the script prints `RLS OK`. Any `SC-008 FAILED` aborts it. It rolls back and leaves nothing
behind.

### SC-009 — a catalogue change never rewrites history

*Automated*: `RemoteCatalogueImmutabilityTest` — this increment's Principle III test.

*By hand*: record several days. Publish version 2 with changed points and a changed schedule:

```powershell
supabase db execute --command "insert into catalogue_publications (version, effective_from, format_version, payload) values (2, current_date + 7, 1, '<payload>'::jsonb);"
```

Pull, then check every past day reports unchanged tasks, points, and totals, while a day opened on or
after the effective date follows version 2. Then publish a version with `format_version = 99` and
confirm it is ignored with no crash (FR-028, `UnknownCatalogueVersionTest`).

### SC-010 — a year offline loses nothing

*Automated*: `OutboxDurabilityTest` seeds a year of daily recording, kills the process, restarts, and
asserts every entry survives and drains. No entry is expired, capped, or evicted at any point.

### SC-011 — no sync string blames anyone

*Automated*: `SyncStatusCopyTest` and `SyncStatusBarTest` scan every sync-related string and every
colour used by the status surface and the new day-cell state, against the `CLAUDE.md` Principle IX
list.

*By hand*: read every string introduced by this increment aloud, including the two sign-out
confirmations. None of them may attribute a failure to the user or render in red.

## 5. Before opening the PR

- `data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/3.json` exists and is committed.
- `MizanDatabaseMigrationTest` proves 2 → 3 keeps every plan, planned task and completion with
  identical figures, and that the migration is additive.
- `supabase/migrations/0001_identity_cloud_sync.sql` matches
  `contracts/remote-schema.sql` exactly.
- No Supabase, Ktor, or WorkManager import exists outside `data/sync/` and `:app`'s DI —
  `ModuleBoundaryTest` covers `:domain`; check `:data` by inspection.
- **No code path can rewrite a recorded day** (FR-024a): `git grep -n "adoptMergedVersion\|updateMergedVersion"`
  returns nothing, and `DayPlanDao`'s only write methods are `claimPlansForUser`,
  `claimPlannedTasksForUser`, `markSynced` and the original inserts.
- No password call site: `git grep -n "signInWith(Email"` returns nothing (FR-002).
- No service-role key anywhere in the repository or in CI logs.
