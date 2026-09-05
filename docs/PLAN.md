# Recommended Development Strategy

## How I read this product

Before the phases, four observations that actually drive the phase boundaries. If these are wrong, the roadmap is wrong.

**1. The hard requirement is not "track tasks" — it is "reproduce a past day exactly as it was."**
Snapshotting the points on each completion row is the obvious move, and it is not enough. The daily score is `earned / available`. Completions only give you the numerator. If the admin adds a task or changes a schedule, the *denominator* of every past day silently changes, and a day where the user completed nothing loses its record entirely. The fix is to materialize an immutable **Day Plan** — the list of applicable tasks and their point values for a given date — the first time that date is opened, and never recompute it afterwards. This decision must land in Phase 2, because retrofitting it later means rewriting the schema and losing real user history.

**2. "Applicable multiple times per day" means completion is an append-only event log, not a boolean.**
No `isCompleted` column anywhere. A completion is a row: which task version, which day, which occurrence, how many points awarded, when. Uncompleting is deleting (or tombstoning) the most recent occurrence. This also happens to be the shape a leaderboard and a sync engine both want, which is convenient.

**3. Supabase should stay out of the early phases — but sync-readiness should not.**
Deferring Supabase is right. Deferring *client-generated UUID primary keys, `updatedAt`, and a soft-delete marker* is not: those cost roughly an afternoon in Phase 2 and cost a full data migration in Phase 7. Ship local-only, but never let an auto-increment `Long` become the identity of a completion record.

**4. Hijri date is a display label and a future feature key — it is not the accountability key.**
Hijri must be *stored alongside* each Day Plan as a denormalized snapshot, not looked up at render time, or your history screens break when the cache is cold or the API-synced conversion shifts by a day. This lets the app work fully offline for any past date and unlocks later Ramadan/Ashura features without a new lookup path.

This holds even now that the accountability day is Maghrib-to-Maghrib (Phase 9, constitution v2.0.0), which *is* the traditional Hijri day. The boundary is computed independently from the injected location and prayer-time provider, never read off a Hijri calendar lookup. Originally this observation read "the accountability day is the local civil date"; that was true until the constitution was amended on 2026-08-30, and Phase 9 is the increment that changes it.

## Phase overview

| Phase | Name | Ships to user? | MVP |
|---|---|---|---|
| 1 | Foundation / Product & Domain Planning | No | Yes (planning) |
| 2 | Today Screen — Local Task Engine | Yes | Yes |
| 3 | Weekly Accountability Sheet | Yes | Yes |
| 4 | Streaks & Consistency | Yes | Yes |
| 5 | History & Past-Day Review | Yes | No |
| 6 | Charts & Insights | Yes | No |
| 7 | Identity & Cloud Sync (Supabase) | Yes | No |
| 8 | Leaderboards & Honor Board | Yes | No |
| 9 | Maghrib-Anchored Day & Week Boundary | Yes | No |
| 10 | Notifications & Weekly Summaries | Yes | No |
| 11 | Achievements, Friends & Challenges | Yes | No |

**Phase number and spec number stay aligned.** Phase N is spec `NNN` throughout. Notifications was
drafted first and briefly held `009`; it was renumbered to `010` when the constitution amendment made
the Maghrib boundary a prerequisite of it, so that build order and numbering do not disagree.

---

## Phase 1 — Foundation / Product & Domain Planning

### Goal
Produce the canonical task catalogue, the domain glossary, and the eight or nine architectural decisions that cannot be discovered later without rework. No production code.

### User value
None directly. Its value is that Phases 2–4 can be built without stopping to re-decide the data model.

### Scope
- Canonicalize the weekly accountability sheet into a machine-readable seed catalogue (stable task IDs, section, points, schedule rule, max occurrences per day, display order, catalogue version).
- Validate the point arithmetic. I checked it and it holds: 6×2 (Fajr) + 4×2 (Dhuhr) + 3×2 (Asr) + 3×2 (Maghrib) + 3×2 (Isha) = 38; + 9 (Qiyam/Witr) = 47; + 4 (Quran memorization and reading) = 51; + 18 (nine Adhkar) = **69 base day**. Monday and Thursday add fasting (5) = **74**. Friday adds seven 1-point activities = **76**. Week total = (69×4) + (74×2) + 76 = **500**. The sheet is internally consistent — worth locking as a regression test fixture in Phase 2.
- Write the domain glossary: Task Definition, Task Version, Section, Schedule Rule, Day Plan, Planned Task, Completion, Occurrence, Daily Score, Weekly Score, Consistency Day, Streak.
- Decide the accountability day boundary and the week boundary (see *Architectural Decisions (Recorded)*).
- Confirm the module shape (`:domain`, `:data`, `:app`) and that Koin is the sole DI framework. `develop-v1` carries no DI code of any kind, so this is a decision to record, not a migration to perform.
- Write the SpecKit constitution / spec skeletons for Phases 2–4.

### Out of scope
Room entities, Supabase schemas, Compose screens, tickets, sync design, leaderboard rules.

### Domain concepts introduced
All of the above, as definitions only.

### Data/storage requirements
None persisted. Output is a seed catalogue file (JSON or YAML) plus written specs, both version-controlled.

### Architecture requirements
Module boundary decision only: `:core:*`, `:domain`, `:data`, `:feature:today`, etc. Decide the shape, do not build empty modules you have no code for yet.

### UI/screens
None. Low-fidelity wireframes for Today and Week are useful but not required.

### Testing requirements
The catalogue seed gets a validation checklist: every task has a unique stable ID, a schedule rule, and a positive point value; per-day totals equal 69/74/76 and the week equals 500.

### Dependencies
None.

### Definition of Done
Seed catalogue exists and validates against the expected daily/weekly totals; glossary is written; every item under *Architectural Decisions (Recorded)* has a recorded answer with a one-line rationale; Phase 2 spec is drafted and reviewable.

### Why now
The task catalogue is the product's entire content model and it is fixed content, not user content. Getting it into a canonical, versioned form first means Phase 2 is plumbing rather than product design. It also surfaces the DI conflict and the day-boundary question before they become load-bearing.

---

## Phase 2 — Today Screen — Local Task Engine

### Goal
A user opens the app, sees exactly the tasks applicable to today grouped by section, marks them complete (including multiple times where allowed), and sees today's earned points against today's available points. Fully offline.

### User value
The core loop. This alone is a usable product — a digital replacement for the paper sheet.

### Scope
- Seed the versioned task catalogue into Room on first launch, idempotently, keyed by catalogue version.
- Resolve applicability for a date: daily, specific-weekday (fasting on Monday/Thursday), Friday-only.
- Materialize and persist an immutable Day Plan for the current date on first access, including the Hijri label snapshot and the computed available-points total.
- Append-only completion log with occurrence support; undo removes the latest occurrence.
- Daily score: earned points, available points, percentage.
- Sync-ready primitives from day one: client-generated UUID ids, `updatedAt`, soft-delete marker, nullable `userId`.

### Features included
Today screen as a **stepped flow** — one prayer block at a time rather than a single 40-row list, per the product design — with complete/undo, occurrence counters (`2/3`), daily points header, Gregorian + Hijri date display, and day rollover handling while the app is open.

The stepped flow is a presentation decision, not a domain one: sections and their order come from the catalogue exactly as before, and the day's available points are still the whole applicable set, not the block currently on screen. It does change the UI state shape — the screen holds a current-block position alongside the day's data — so budget for it in the Phase 2 spec rather than discovering it during implementation.

### Features explicitly NOT included
Weekly view, history browsing, streaks, charts, editing past days, task creation/editing of any kind, accounts, sync, notifications, achievements, leaderboards, settings beyond what the screen needs, animations and celebration UI.

### Domain concepts introduced
Task Definition, Task Version, Catalogue Version, Section, Schedule Rule (`Daily`, `DaysOfWeek`, later `HijriDate`), Occurrence limit, Day Plan, Planned Task, Completion, Daily Score.

### Data/storage requirements
Room, offline-only. Roughly: task definition/version tables, day plan + planned task tables, completion table. Day Plan rows are written once and treated as immutable for past dates. Hijri lookup and the date provider are **net-new** — `develop-v1` has no `HijriDateRepository`, no `GetCurrentDateUseCase`, and no Room, so budget for building them rather than reusing them. The clock goes behind an injectable provider from the first commit so tests can move time.

### Architecture requirements
Clean Architecture with an isolated domain layer holding scoring and applicability as pure functions. Repository interfaces in domain (`TaskCatalogueRepository`, `DayPlanRepository`, `CompletionRepository`) with Room implementations in data — Phase 7 replaces implementations only. MVVM + StateFlow, single immutable UI state per screen. Retrofit is introduced here for Hijri sync and is the only network surface in the app.

### UI/screens
`TodayScreen` only. English interface shell, Arabic task content, each Arabic string rendered right-to-left in an Arabic face so mixed rows do not reflow the layout. See the *Design* section of `CLAUDE.md` for tokens and the per-change audit list.

### Testing requirements
Unit tests for applicability (each weekday produces the right task set), scoring (partial, zero, all-complete, multi-occurrence), and the 69/74/76/500 fixtures. Room instrumentation tests for the completion log and Day Plan immutability. One ViewModel test per state transition. A day-rollover test with a fake clock.

### Dependencies
Phase 1 catalogue and decisions.

### Definition of Done
On a fresh install with no network, the user can complete and undo tasks for today, points update correctly, the Day Plan persists across process death, day rollover produces a new plan without mutating yesterday's, and the daily total matches the expected value for that weekday.

### Why now
Everything else in the product is a projection over completions and day plans. Until the log and the plan exist and are correct, nothing downstream can be built on solid ground — and this is the smallest slice that is genuinely useful on its own.

---

## Phase 3 — Weekly Accountability Sheet

### Goal
Turn the daily log into the weekly artifact the user actually recognizes: a Saturday–Friday sheet with per-day earned/available and a weekly total out of 500.

### User value
The paper sheet is a *weekly* instrument. This is the phase where the app matches the user's existing mental model, and where past days become visible for the first time.

### Scope
- Week boundary (Saturday → Friday) and week identity/key.
- Week aggregate: per-day earned and available, weekly earned, weekly available, weekly percentage.
- Materialize Day Plans for elapsed days in the current week that were never opened, using the catalogue version that was current for that date — so a skipped day shows `0/69` rather than vanishing.
- Week screen with per-day drill-in to a read-only day summary.

### Features included
Current-week sheet, weekly totals, day-cell states (untouched / partial / complete), navigation to previous and next week within the recorded range, Hijri labels per day.

### Features explicitly NOT included
Editing past days, charts, streaks, monthly views, export, sharing, sync.

### Domain concepts introduced
Week (Sat–Fri), Week Key, Weekly Score, Day Summary, backfilled Day Plan.

### Data/storage requirements
No new tables strictly required — week aggregates are queries over day plans and completions. Add indexed date columns. Consider a materialized `DaySummary` cache only if a real measurement shows the aggregate query is slow.

### Architecture requirements
`GetWeekSummaryUseCase` in domain, pure over repository data. Keep the week-boundary rule in one place (a `WeekCalculator`), not spread across queries.

### UI/screens
`WeekScreen`, read-only `DaySummary` detail.

### Testing requirements
Week-boundary tests including the Saturday and Friday edges and month/year crossings; backfill tests (day never opened → correct available points from the then-current catalogue version); a full-week fixture that must total 500.

### Dependencies
Phase 2.

### Why now
It is the first read-model over the log, it validates that the Phase 2 storage design is actually queryable, and it forces the backfill question while there is still almost no user data at risk.

### Definition of Done
A week with mixed activity renders correct per-day and weekly figures; a fully completed week reads exactly 500/500; navigating away and back is consistent; no past-day mutation is possible from this screen.

---

## Phase 4 — Streaks & Consistency

### Goal
Implement the product's actual thesis: consistency over perfection. A day counts toward the streak when the user opens the app and completes at least one applicable task.

### User value
The retention mechanic, and the emotional core of the app. Cheap to build, disproportionately motivating.

### Scope
- Consistency Day rule: at least one completion recorded against that date's Day Plan.
- Current streak, longest streak, last active date.
- Streak state on the Today screen; streak-at-risk state late in the day.
- Explicit grace/timezone policy (see deferred decisions) applied consistently.

### Features included
Current streak, best streak, a compact recent-activity indicator, break handling.

### Features explicitly NOT included
Freezes, repairs, purchased saves, achievements, badges, notifications, social comparison.

### Domain concepts introduced
Consistency Day, Streak, Longest Streak, Streak Break.

### Data/storage requirements
Derive from completions first — no new source of truth. Add a small cached streak row (current, longest, last active date, computed-through date) only if the derived query becomes visibly slow. Cache must be reconstructible from the log, never authoritative.

### Architecture requirements
`GetStreakUseCase` as a pure fold over consistency days. Injected clock. No Android dependencies in the streak logic.

### UI/screens
Streak element on `TodayScreen`; optionally a small streak detail sheet.

### Testing requirements
Same-day multiple completions count once; a single gap day breaks the streak; streak survives process death and app restart; behavior across a device timezone change and a manual clock change; long-history performance sanity check.

### Dependencies
Phases 2–3.

### Why now
It is the last piece the product needs to be *the app described in the brief* rather than a checklist. It is also the cheapest high-retention feature available, and it depends only on data that already exists.

### Definition of Done
Streak is correct across the tricky cases above, is visible on the main screen, and requires no new writes on the completion path.

---

## Phase 5 — History & Past-Day Review

### Goal
Let the user browse and reflect on any recorded past day or week, and decide once and for all whether past days are editable.

### User value
Reflection and self-accountability — the *muhasabah* the app is named for. Also the first real test of the historical-accuracy promise.

### Scope
- History list by week/month with completion indicators.
- Full past-day detail showing the tasks *as they were on that day*, with the points that were awarded.
- Retroactive completion policy: fixed grace window (e.g. yesterday until a cutoff), or read-only past. Decide, apply, and make the rule visible in the UI.
- Empty-state handling for dates before install.

### Features included
History browsing, past-day detail, retro-completion within the chosen window (if allowed), Hijri/Gregorian toggle if desired.

### Features explicitly NOT included
Charts, export, sharing, notes/journaling, sync.

### Domain concepts introduced
Retro-Completion Window, Locked Day, `completedAt` distinct from the day the completion is credited to.

### Data/storage requirements
Completions need both the credited date and the actual timestamp. Pagination over day plans. This is the phase where an admin catalogue change should be simulated against real stored history.

### Architecture requirements
A single `DayEditPolicy` in domain consulted by every write path, including Today. Do not let two screens hold different opinions about whether a day is writable.

### UI/screens
`HistoryScreen`, `DayDetailScreen`.

### Testing requirements
The critical one: seed history under catalogue v1, bump to v2 with changed points and a changed schedule, and assert that past days still render v1 values and totals while today renders v2. Plus grace-window boundary tests and pagination tests.

### Dependencies
Phases 2–4.

### Definition of Done
Any recorded day renders with its original definitions and totals after a catalogue change; the edit policy is enforced identically everywhere; history loads smoothly over a year of seeded data.

### Why now
The versioning machinery went in during Phase 2, but nothing has *proved* it. This phase is that proof, and it needs to happen before charts aggregate over the same data and before sync starts moving it between devices.

---

## Phase 6 — Charts & Insights

### Goal
Visualize consistency over weeks and months.

### User value
Pattern recognition — which sections are consistently missed, whether the trend is improving.

### Scope
Weekly trend, monthly overview/heatmap, per-section breakdown, best/worst day, personal bests. Read-only aggregations.

### Out of scope
Predictions, AI summaries, goal setting, sharing/export images, social comparison.

### Domain concepts introduced
Aggregation Period, Section Performance, Trend, Completion Rate.

### Data/storage requirements
Read-only aggregate queries; optional pre-computed monthly rollups if measured to be necessary. No new writes on the completion path.

### Architecture requirements
Aggregation use cases in domain returning chart-agnostic models. Chart library confined to the UI layer so it can be swapped.

### UI/screens
`InsightsScreen` with period switching; possibly a chart card on Today.

### Testing requirements
Aggregation correctness against fixtures, sparse-data and single-day cases, month/year boundaries, performance over a year of data.

### Dependencies
Phase 5 (needs trustworthy history).

### Definition of Done
Charts match hand-computed fixtures, render with 1 day and with 365 days of data, and add no measurable cost to the completion flow.

### Why now
Charts are only meaningful once there is enough correct history to chart, and they are pure read-model work — safe, self-contained, and a natural pause before the sync phase.

---

## Phase 7 — Identity & Cloud Sync (Supabase)

**Status: delivered** (`specs/007-identity-cloud-sync/`). Automated: `:domain:test`, `:app:test`,
`:data:test`, `:data:connectedAndroidTest`, `:app:connectedAndroidTest` all green. Validated by hand
before merge: SC-007 (fresh install, airplane mode, no account — then repeated with the network on
and the project paused), SC-011 (every new string read against the Principle IX list), and **SC-008,
the live RLS verification, which prints `RLS OK` against the project** (T129, T130, T132).

**Deferred validation — GitHub issue #15.** Five items ship unvalidated by hand: SC-001, SC-003,
SC-004, SC-006 and T131. All five need a signed-in account, and sign-in is passwordless OTP
(FR-002), so each needs a real code delivered to an inbox. A fixed test OTP in Supabase Auth would
open all five, but that setting is not available on the project's current Free plan. Each has a
passing automated counterpart (`SignInMigrationTest`, `BackgroundSyncSchedulingTest`,
`TwoDeviceConvergenceTest`, `BackfillResumeTest`). Merged with this understood and accepted; the
issue carries the full procedure and three ways to unblock.

**Conflict policy actually shipped** — concurrent-record and concurrent-undo, resolved without a
timestamp comparison: a record merges as the union (nothing recorded on either device is ever
discarded); an undo (a tombstone) beats a record, on whichever device or in whichever order the two
arrive, because a tombstone can only ever be set, never cleared, once applied. Two devices opening
the same date for the first time under two different catalogue versions settle on the lower version
— never re-derived, never re-priced, once a device has materialised its own copy of that day
(Principle III). This is what `mergeCompletion` and `mergeDayRecord` encode in
`domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/`; the plain-language version of it — "If
you record on two devices at once, both records are kept. If you undo something on one device, it
stays undone on the other." — is the FR-019a user-facing obligation, and it is satisfied by the
profile screen's own statement (`ProfileUiState.conflictPolicy`, T127), not by this file.

### Goal
Introduce accounts and bidirectional sync without changing the offline-first behavior of anything shipped so far.

### User value
Backup, device migration, multi-device continuity — and the precondition for anything competitive.

### Scope
- Supabase auth (email/OTP or a single provider — do not ship three).
- Local-first sync engine: outbox/queue, `syncState` per row, `updatedAt`, tombstones for undone completions, idempotent upserts on client-generated UUIDs.
- Remote task catalogue with versioning; local seed becomes the fallback, not the source of truth.
- Anonymous-to-authenticated migration for existing local data — existing users must not lose their history.
- Conflict policy: last-write-wins per completion occurrence is adequate here because completions are near-immutable facts; document it explicitly.

### Features included
Sign up / sign in / sign out, profile basics, background sync, sync status indicator, remote catalogue pull with local Day Plan preservation.

### Features explicitly NOT included
Leaderboards, friends, real-time subscriptions, admin console, push notifications, social profiles.

### Domain concepts introduced
User Identity, Sync State, Outbox, Tombstone, Remote Catalogue Version, Device.

### Data/storage requirements
`userId` becomes non-null after migration; sync metadata columns (already present from Phase 2); Supabase tables for profiles, task definitions/versions, and completions with row-level security. Day Plans are a deliberate choice: keep them local-authoritative and re-derivable, or sync them. Recommendation: sync completions and catalogue; keep Day Plans local and rebuildable from the catalogue version, since two devices must produce the same plan from the same version.

### Architecture requirements
This is where the Phase 2 repository interfaces pay off: swap or decorate implementations, leave domain untouched. If a single domain use case has to change to accommodate sync, treat that as a design smell and fix the boundary instead.

### UI/screens
Auth screens, profile/settings, sync status surface.

### Testing requirements
Offline-then-online reconciliation, duplicate-submission idempotency, undo-then-sync (tombstone) correctness, two-device convergence, local-history migration on first sign-in, auth failure and token expiry paths, RLS verification that no user can read another's completions.

### Dependencies
Phases 2–5. Phase 6 is not required but usually already done.

### Definition of Done
A user with local-only history signs in and keeps every record; two devices converge; airplane-mode usage syncs cleanly on reconnect; the app remains fully usable signed-out and offline.

### Why now
Late on purpose. Sync is the highest-complexity, lowest-immediate-value phase, and its cost drops sharply once the local model is stable and proven. Introducing it earlier would have made every Phase 2–5 decision an argument about sync.

---

## Phase 8 — Leaderboards & Honor Board

### Goal
Competitive raw-points rankings — daily, weekly, monthly — plus the Honor Board.

### Scope
Server-computed rankings over raw points, opt-in participation and display name, own-rank visibility, period boundaries agreed server-side (whose timezone defines "daily" is a real question — resolve it here). Honor Board as a curated/threshold recognition surface.

### Out of scope
Friends, challenges, chat, private groups, anti-cheat beyond basic server-side validation.

### Domain concepts introduced
Leaderboard Period, Ranking, Participation Consent, Display Identity, Honor Board Criteria.

### Data/storage requirements
Server-side aggregation (Postgres views or scheduled functions); client caches rankings for offline display. Never rank from client-reported totals — recompute from synced completions.

### Architecture requirements
Rankings are a remote read model with no local authority. Leaderboard failure must never degrade the core loop.

### Testing requirements
Tie-breaking, period boundaries across timezones, opt-out honored everywhere, large-list pagination, offline cache behavior, tamper resistance (client cannot inflate points).

### Dependencies
Phase 7.

### Definition of Done
Rankings are correct and server-derived, participation is opt-in and revocable, and the app works normally when the leaderboard is unavailable.

### Why now
Impossible before identity and sync exist, and it introduces social pressure — which is best added to a product whose numbers are already trustworthy.

### Delivered (spec 008)
- **Regions, not raw timezones.** Period boundaries are pinned to an administrator-seeded region
  (an IANA zone, one row per zone in `region_zone_map`), never to the device's own offset — so the
  leaderboard day always matches the participant's own calendar day (FR-010–FR-013, SC-005).
- **Honor Board qualification is days-engaged, not points.** A per-period-kind threshold
  (`honor_board_config`, WEEKLY 5 / MONTHLY 20) lives server-side only — no client can read it
  (FR-027–FR-030).
- **Immediate freeze, no settlement window.** `recompute_open_periods()` closes a period on its own
  region-local boundary the moment it is next scheduled to run; nothing waits for stragglers
  (FR-025). Tradeoff: a participant recording offline right at a boundary can lose that period's
  ranking window even though their own record still counts in full (FR-025a, SC-016). Revisit
  trigger: if offline recording near a boundary turns out to be common enough that participants
  perceive this as unfair, consider a short settlement window before closing — not before then.

---

## Phase 9 — Maghrib-Anchored Day & Week Boundary

**Spec `009-maghrib-day-boundary`.** Not in the original roadmap. Added 2026-08-30 when the
constitution was amended to v2.0.0, redefining the accountability day from local midnight to
Maghrib-to-Maghrib and the week from Saturday-to-Friday to Maghrib-Friday to Maghrib-Friday.
Amended again to v2.0.1 so the calculation convention could be fixed per region rather than globally.

### Goal
Make the app's accountability day the Islamic day. Introduce the single location and prayer-time provider constitution Principle VII now requires, and move every existing surface onto the new boundary without touching a single already-recorded day.

### User value
The app finally records the day the user is actually living. A completion after Maghrib belongs to the new day, as it does in practice, rather than waiting for a civil midnight that means nothing in this domain.

### Scope
- One injected location and prayer-time provider for the whole app — on-device, no network, no per-user choice of method, region-derived calculation convention.
- The day boundary redefined: Maghrib to the next Maghrib, calculated for the person's location.
- An explicit, deterministic fallback for when no location has ever been obtained, when the person erases it, and when a time-zone change invalidates it. Principle VII deliberately deferred this decision to this spec; it is settled in the spec, not at planning time.
- The changeover: no already-closed day or week is recomputed, and no accountability date is skipped or duplicated at the seam.
- Every existing consumer brought onto the new boundary — Today's rollover, the weekly sheet, streaks, history, insights, sync's credited date, leaderboard period timing.

### Out of scope
Notifications of any kind (Phase 10 consumes this provider), any per-person choice of calculation method, any prayer-times display screen, any new network surface, and any remote schema change.

### What it turned out *not* to require
Two findings during planning cut the increment down, and are recorded because they are the reason this phase is small rather than sweeping:

- **The week rule needs no change at all.** Maghrib on Friday *starts* accountability-Saturday, so "the Saturday on or before" is still exactly right in accountability-date space. `WeekBoundary` and every consumer of it — the week screen, history paging, personal bests, leaderboard periods — are untouched.
- **The day rule has exactly one production call site.** Every ViewModel and use case reaches the date through `TimeProvider.today()`. Eight increments of Principle VII discipline held, so redefining the day touches one function and its provider.

### Domain concepts introduced
Coordinates, Calculation Convention, Region-to-Convention Mapping, Prayer Times, Boundary Regime, Boundary State, Changeover Seam.

### Data/storage requirements
One additive Room migration and one device-local single-row table holding the last known coordinates, the zone they were obtained under, and the last resolved accountability date. Deliberately **not** synchronisable — coordinates never leave the device, and each device resolves its own boundary. No recorded-history table is written by this phase at all.

### Architecture requirements
Provider interfaces in `:domain`; the calculation library and the location reader in `:data`. The boundary rule stays a pure function that takes the Maghrib instant as a parameter, so it is testable with literals and `:domain` keeps zero calculation dependencies. `TimeProvider.today()` stays synchronous, backed by resolved state held in memory — it may never block on a location fix.

### UI/screens
No new destination. A dismissible first-launch prompt on Today, and a location section on the existing profile screen stating which boundary is in force and offering an erase control.

### Testing requirements
Boundary mapping across a full year including both solstices; every instant maps to exactly one day and one week; the seam skips and duplicates nothing, tested at a cutover before and after that day's Maghrib; ninety offline days with an unchanged zone keep the Maghrib boundary; a zone change with no fresh fix falls back and says so. **Gating:** days and weeks closed under the old boundary must report identical figures afterwards — the Principle III immutability test.

### Dependencies
Phases 2–8, in the sense that it changes all of them. Blocks Phase 10.

### Definition of Done
The day turns over at the calculated Maghrib; a fresh install in airplane mode with no location is fully usable and says which boundary it is using; every screen agrees about the date in the window between Maghrib and midnight; every previously closed day and week reads exactly as it did.

### Why now
Forced. The constitution changed, and until the code follows it every increment is built against a rule the project has already abandoned. It also has to precede notifications: a weekly summary that fires "at week close" is meaningless until week close is defined.

---

## Phase 10 — Notifications & Weekly Summaries

**Spec `010-notifications-weekly-summaries`.** Drafted before Phase 9 existed, under the number `009`;
renumbered to `010` when the boundary became its prerequisite. Replanned on top of Phase 9.

### Goal
Bring the user back at the right moments without becoming nagging — prayer-window nudges, streak-at-risk reminders, and an end-of-week summary.

### Scope
Local scheduled notifications, per-category user control, streak-at-risk timing, weekly summary generation and screen/notification, quiet hours.

### Out of scope
Push from server, social notifications. **Prayer-time calculation from geolocation is no longer excluded** — it was listed here as out of scope in the original roadmap, on the grounds that it is a whole feature in itself. It is, and it became Phase 9. This phase consumes that provider and must not introduce a second.

### Dependencies
Phases 4 and 6 (streaks and aggregates), and **Phase 9** — the provider and the week-close instant both come from there.

### Definition of Done
Notifications respect user settings and quiet hours, survive reboot, never fire for already-completed tasks, and can be fully disabled.

### Delivered (spec 010)
Two pure `:domain` functions decide everything — `buildNotificationPlan` (which anchors exist right
now) and `evaluateAnchor` (what happens when one fires). Android supplies only instants and
delivers broadcasts; the delivery ledger's `anchorKey` primary key is the only idempotency
guarantee, never application logic layered on top.

Five clarifications shaped the design, all resolved on 2026-09-04: weekly summaries are
recalculated on demand and never stored (`GetClosedWeekSummary`, never `GetWeekSummary` — see
below); delivery is exact when the platform allows it, with a stated relaxed-mode degradation path
that discards a late-arriving anchor as `WINDOW_PASSED` rather than showing it stale; the summary
goes dormant after two consecutive empty weeks and resumes the moment any task is recorded; the
Weekly Summary screen renders only closed weeks, with a dedicated `Waiting` state before the first
one; and no notification permission is requested during the app's first week, deferring to the
first week close or an earlier deliberate opt-in.

**Research finding R4 — `GetWeekSummary` cannot be reused for the summary notification.** It calls
`ensurePlanFor`, which writes a `DayPlan` for any date it is asked about. A background worker
deciding whether to post a notification must never have a side effect on recorded history
(Principle III), so `GetClosedWeekSummary` was written as a strictly read-only sibling: it reads
stored plans with `plansBetween`, projects only the *elapsed, never-opened* days of an already-past
week using `projectAvailablePoints` against the catalogue version in force on each date, and calls
the same `buildWeekSummary` the weekly sheet uses. `SummaryAgreesWithSheetTest` proves the two
produce identical figures for the same seed.

**Two corrections `/speckit-analyze` forced before implementation began**, on top of the design
`/speckit-analyze` had already reworked once (see tasks.md's Revision note): `buildNotificationPlan`
returns `NotificationPlan(anchors, refreshAt)` rather than a bare list, because the day-end wake
that lets the worker notice a rolled-over day cannot be typed as a notification anchor without
inventing a fourth, fake category; and `weekCloseInstant(boundary)` was pulled out as the single
named derivation of a week's close, rather than leaving it an undefined parameter — Principle VII's
"one week rule" would otherwise have had no single place to point to.

**Deferred rather than shipped:** the full quiet-hours picker is a plain HH:mm text entry, not a
platform time-picker dial — functionally complete (round-trips, handles midnight-crossing windows)
but not the design's intended widget. Settling on it beyond a device-connected environment was out
of scope for this pass. The androidTest suites proving reboot survival, relaxed-mode degradation,
history immutability under a full week of notification activity, and offline behaviour (SC-007a,
SC-012, SC-013, FR-036/FR-045) are written and compile against this module's existing device-test
harness (`TestTimeProvider`, the Room-backed `DbTestBase` pattern), but were not run against a
device or emulator in this environment — that, the four by-hand device checks, and
`:data`/`:app:connectedAndroidTest` are outstanding before this spec's pull request can merge.

---

## Phase 11 — Achievements, Friends & Challenges

### Goal
Long-horizon engagement: badges and milestones, friend connections, time-boxed challenges.

### Scope
Achievement definitions and unlock evaluation over existing history, friend requests/accept/block, challenge creation and participation.

### Dependencies
Phases 7 and 8.

### Definition of Done
Achievements evaluate correctly against pre-existing history, social interactions are consent-based and blockable, and none of it is required for solo use.

---

# MVP Boundary

**MVP = Phase 1 + Phase 2 + Phase 3 + Phase 4.**

The first usable version:

- Today screen: applicable tasks grouped by section, correct for the weekday (fasting on Monday/Thursday, the seven Friday activities on Friday).
- Complete and undo, with multiple occurrences where the task allows it.
- Daily score as earned/available with the correct 69/74/76 denominators.
- Weekly Saturday–Friday sheet with per-day figures and a weekly total out of 500.
- Read-only past-day summaries reachable from the week sheet.
- Consistency streak: current and longest, driven by "opened the app and completed at least one task."
- Gregorian and Hijri dates displayed.
- Fully offline, no account, no network dependency for the core loop.
- Versioned task catalogue and immutable Day Plans underneath, so history stays honest from the very first record.

**Deliberately excluded from MVP:** accounts, sync, leaderboards, Honor Board, charts, achievements, friends, challenges, notifications, retroactive editing beyond the current day, task customization of any kind, export, and any Supabase dependency.

**Deferred, in order of when it should arrive:** history browsing and past-day review → charts → identity and sync → leaderboards → notifications → social. The one thing *not* deferred despite being invisible is the versioning/snapshot model, because it is the only item on this list that cannot be added later without damaging data that already exists.

---

# Future Roadmap

| After MVP | Phase | Unlocks |
|---|---|---|
| Next | 5 — History & Past-Day Review | Proves historical accuracy; enables reflection |
| Then | 6 — Charts & Insights | Pattern recognition over trustworthy history |
| Then | 7 — Identity & Cloud Sync | Backup, multi-device; precondition for everything social |
| Then | 8 — Leaderboards & Honor Board | Competition on raw points |
| Then | 9 — Maghrib-Anchored Day & Week Boundary | The app records the Islamic day; unblocks Phase 10 |
| Then | 10 — Notifications & Weekly Summaries | Re-engagement |
| Later | 11 — Achievements, Friends, Challenges | Long-horizon retention |

---

# Architectural Decisions (Recorded)

These are settled. Each states the decision taken and why. They are not open questions and must not be reopened per-feature — a decision here is changed only by amending this section deliberately, and where a decision is fixed by the constitution it cannot be changed here at all.

1. **Accountability day boundary.** Maghrib to the next Maghrib, calculated on-device from the person's location. *Why:* fixed by constitution Principle VII as amended to v2.0.0 on 2026-08-30 — the app's day should follow the Islamic day rather than the civil calendar day. Still testable without a real clock or a real location, because the rule is a pure function taking the Maghrib instant as a parameter. Hijri remains a label attached to a day, never the thing defining its boundaries, even though Maghrib-to-Maghrib *is* the traditional Hijri day: the boundary is computed independently and never read off a calendar lookup. **Superseded decision:** this read "local midnight to local midnight" until 2026-08-30, and predicted that a Maghrib-based day "must arrive as a stored per-day rule rather than a code change". That prediction was wrong — it arrived as a change to the one function that maps an instant to a date (Phase 9). Days closed under the old boundary are never recomputed (Principle III).
   - **1a. Fallback when no location is available.** Local midnight and Saturday-to-Friday — the boundary the app shipped with — until coordinates have been obtained at least once, and again if the person erases them or a time-zone change invalidates them. *Why:* Principle VII forbids an undefined result and requires this decision to be made explicitly by the spec introducing the provider; Principle IV requires a fresh install in airplane mode to be fully usable, which rules out blocking. Once coordinates exist, Maghrib is computable offline for any future date forever, so ordinary offline use never reaches the fallback.
   - **1b. Calculation convention.** One administrator-fixed convention *per region*, selected automatically from an administrator-defined mapping resolved on-device from the IANA time zone id, with Muslim World League and Standard Asr as the documented default. Never a per-person setting. *Why:* a user in Egypt and a user in Saudi Arabia follow different authorities, so one global convention is wrong for one of them. Required constitution v2.0.1, since v2.0.0 said "a single convention" and meant it literally.
2. **Week boundary.** Maghrib on Friday to Maghrib on the following Friday, implemented in exactly one place. *Why:* fixed by Principle VII, which also forbids a second implementation — two screens must never disagree about which week a date falls in. In *accountability-date* space this is still Saturday through Friday and the existing `WeekBoundary` implements it unchanged, because Maghrib on Friday is the start of accountability-Saturday. The week's *instant* moved; the week's *rule* did not.
3. **Historical accuracy strategy.** Immutable Task Versions, materialised immutable Day Plans, and `pointsAwarded` denormalised onto each completion. *Why:* Principle III. A recorded day must report the same figures forever, and this is the only category of bug that cannot be repaired after the fact.
4. **Completion representation.** Append-only occurrence log. No boolean `isCompleted` column anywhere; undo removes or tombstones the most recent occurrence. *Why:* it is the only shape that supports multi-occurrence tasks, and it is what a sync engine and a leaderboard both want.
5. **Identity of records.** Client-generated UUIDs for completions, day plans, and task versions — never auto-increment. **Task Definition identity is a human-readable slug** (`fajr-sunnah-before`), not a UUID. *Why:* Principle V governs synchronisable rows, and a catalogue definition is administrator content rather than user data. A slug keeps a hand-authored catalogue reviewable in a diff and stays a valid natural key when the catalogue later moves server-side. Recorded in `001` clarification Q1.
6. **Sync-ready columns from day one.** `updatedAt`, a soft-delete/tombstone marker, and a nullable `userId` on every synchronisable row. No sync code yet — only the shape. *Why:* Principle V. These cost hours now and prevent a data migration over real user history later.
7. **Schedule rule representation.** A sealed, extensible rule type — `EveryDay`, `DaysOfWeek`, with `DateAnchored` reserved for Ramadan and Ashura — rather than boolean columns like `isFriday`. *Why:* the occasions are clearly coming, and adding one must extend the type rather than redefine the existing variants. Reserved, deliberately not implemented until needed.
8. **DI framework.** Koin, sole and uncontested. *Why:* `develop-v1` contains no Hilt, no KSP, and no DI wiring at all, so this is a decision to record rather than a migration to perform. Two DI frameworks may never coexist.
9. **Module and layer boundaries.** `:domain` has zero Android and zero Room dependencies; repository interfaces live there and are implemented in `:data`. *Why:* Principle II. This is what makes the arrival of a backend an implementation swap instead of a rewrite.
10. **Hijri date storage.** Snapshotted per Day Plan, never looked up at render time. *Why:* history screens must work offline for any past date, and a cold cache or a shifted API conversion must not change what a recorded day displays.
11. **Clock and location injection.** A single injected time provider from the first commit; no other code reads the system clock, current date, or default timezone. Since v2.0.0 the same discipline covers location: one injected provider reads device location and computes prayer times, and no other code may do either. *Why:* Principle VII. Day rollover and streak logic are untestable without it, and every hard bug in an app like this is a date bug. Anchoring the boundary to a calculated, location-dependent instant added a second axis of nondeterminism that has to be held to the same standard or the same class of bug returns in a harder-to-reproduce form.
12. **Content and language strategy.** Task text is Arabic and is treated as data, not UI strings. The interface shell language is a product decision recorded in the design — an English shell with Arabic task content, each string rendered in an Arabic face with its own direction — and is not settled in the catalogue, which holds content rather than interface strings. *Why:* the catalogue must stay a content model; how it is presented is a separate concern that may change without touching it.

---

# Decisions That Can Be Deferred

- Supabase project setup, schema, and RLS design (Phase 7).
- Auth provider choice (Phase 7).
- Conflict-resolution refinement beyond last-write-wins (revisit in Phase 7).
- Whether Day Plans sync or are re-derived per device (Phase 7).
- Chart library (Phase 6) — the domain returns library-agnostic models.
- Leaderboard period timezone semantics and tie-breaking (Phase 8).
- Whether the Honor Board is threshold-based or curated (Phase 8).
- Retroactive edit window length, and whether it exists at all (Phase 5) — but the *policy object* it will live in should exist in Phase 2.
- Streak grace rules, freezes, timezone-travel leniency (Phase 4, refine later).
- Whether to cache streak and day-summary values (only when measurement says so).
- Achievement catalogue (Phase 11).
- Theming, dark mode, animations, onboarding polish.
- Analytics and crash reporting vendor.
- Admin tooling for editing the catalogue (until Phase 7 makes it remote).
- Notification copy and timing heuristics (Phase 10).
- Which regions the calculation-convention mapping covers beyond those actually served, and whether the streak's at-risk offset stays four hours before the day's end (Phase 9 ships both with a documented default).

---

# What Changes When Supabase Arrives — and What Must Not

**Likely to change (design for it):**
- Task catalogue *source*: local seed → remote pull with versioning. The catalogue repository *interface* should not change.
- Identity: `userId` goes from absent/nullable to required.
- Completion writes: local-only → local write plus outbox enqueue.
- New concerns that do not exist today: auth session, sync status, tombstone propagation, remote catalogue version reconciliation.
- Leaderboard and Honor Board read models — entirely new, entirely remote.

**Must remain backend-independent:**
- All scoring: daily earned/available, weekly totals, percentages.
- Applicability resolution and Day Plan materialization.
- Streak and consistency computation.
- Aggregations behind charts.
- Every domain model and use case; every repository *interface*.
- Room as the offline source of truth for the core loop — Supabase is a peer to sync with, never the thing the UI reads from directly.

The test for whether the boundary is right: **turning Supabase off should degrade the app to exactly the MVP, with no crashes and no missing history.** If that is not true, the coupling is in the wrong place.

---

# Recommended SpecKit Execution Order

Work in small specs. One spec should be implementable and verifiable in a sitting or two — a solo developer's real constraint.

**Stage 1 — Foundations (no user-visible output)**
1. `spec: task-catalogue` — canonical catalogue, stable IDs, schedule rules, occurrence limits, point totals as fixtures.
2. `spec: domain-glossary-and-decisions` — glossary plus the twelve early decisions with recorded rationale.
3. `spec: persistence-foundation` — Room schema for task definitions/versions, day plans, completions, with UUID ids and sync-ready columns; seeding and migration strategy.

**Stage 2 — Phase 2 (Today)**
4. `spec: task-applicability` — resolve the applicable task set for a date.
5. `spec: day-plan-materialization` — create and freeze the Day Plan, including the Hijri snapshot.
6. `spec: completion-logging` — append/undo occurrences, `pointsAwarded` denormalization.
7. `spec: daily-scoring` — earned/available/percentage.
8. `spec: today-screen` — Compose UI, ViewModel, state, day rollover.

**Stage 3 — Phase 3 (Week)**
9. `spec: week-calculation` — Saturday–Friday boundaries and week keys.
10. `spec: weekly-scoring-and-backfill` — aggregates plus Day Plans for unopened elapsed days.
11. `spec: week-screen` — sheet UI and read-only day summary.

**Stage 4 — Phase 4 (Streak)**
12. `spec: consistency-day-rule`
13. `spec: streak-calculation`
14. `spec: streak-ui`

*MVP ships here. Use it yourself for at least two full weeks before starting Stage 5 — the sheet will tell you things the spec cannot.*

**Stage 5 — Phase 5 (History)**
15. `spec: day-edit-policy` — retro window, locked days, enforced from one place.
16. `spec: history-browsing`
17. `spec: historical-integrity-verification` — the catalogue-change regression suite. Treat this as a first-class spec, not a test chore.

**Stage 6 — Phase 6 (Insights)**
18. `spec: aggregation-use-cases`
19. `spec: insights-screen`

**Stage 7 — Phase 7 (Sync)**
20. `spec: supabase-schema-and-rls`
21. `spec: authentication`
22. `spec: sync-engine` — outbox, tombstones, idempotency, conflict policy.
23. `spec: remote-task-catalogue`
24. `spec: local-to-account-migration` — write this *before* the sync engine ships; it is the one that can destroy real user data.

**Stage 8 — Phase 8 and beyond**
25. `spec: leaderboard-aggregation` (server-side)
26. `spec: leaderboard-ui-and-consent`
27. `spec: honor-board`
28. `spec: maghrib-day-boundary` — the provider, the redefined day, the fallback, and the changeover. Must precede the two below: a nudge fired "during Maghrib's window" and a summary fired "at week close" both need this to exist first.
29. `spec: notifications`
30. `spec: weekly-summary`
31. `spec: achievements`, then `spec: friends`, then `spec: challenges`

The ordering principle throughout: **specs that define immutable data shapes come before specs that read them, and specs that touch existing user data come with their migration spec attached.**
