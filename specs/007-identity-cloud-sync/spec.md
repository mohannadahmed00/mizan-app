# Feature Specification: Identity & Cloud Sync

**Feature Branch**: `spec/007-identity-cloud-sync`

**Created**: 2026-08-16

**Status**: Draft

**Input**: User description: "Read @docs/PLAN.md and create a specification for the Phase 7 — Identity & Cloud Sync (Supabase)"

## Clarifications

### Session 2026-08-16

- Q: Which single sign-in method should the app ship in this phase? (FR-002) → A: Emailed one-time code / magic link only — no password is stored, set, reset, or transmitted anywhere in the app, and no social provider is introduced.
- Q: After a user signs out, should their recorded history stay on the device or be removed from it? → A: Kept by default (plain sign-out leaves every record on the device, usable signed-out), with a separate explicit "sign out and remove data from this device" action for shared devices. Both paths warn plainly when changes are still pending, and the removing path additionally requires confirmation naming what will be lost.
- Q: When a device has been unable to reach the account for a long time, what should happen to the queue of changes waiting to be sent? → A: Retained indefinitely and unbounded — retry with backoff forever, never drop, expire, or cap an entry. A pending change is the only copy of a fact the user believes is recorded.
- Q: When a user signs in on a brand-new device, how much of their account history should the app fetch before the history and insights screens are usable? → A: Progressive — the current day and week are fetched first so the app is usable within seconds, and older history backfills in the background. Dates not yet fetched are shown as "still loading earlier days", never as 0% or as absent history.
- Q: Where does the display name on a user's profile come from, and is the user required to provide one? → A: Optional and empty by default, editable from the profile at any time. Never required to sign in or to use the app; the profile falls back to showing the email address when it is empty. Collecting a name for a public surface is Phase 8's problem, at the point of opting in.

### Session 2026-08-16 (post-plan)

- Q: When the same date was independently opened on two devices under different catalogue versions before either synced, and the two therefore disagree about that day's available-points total, which device is corrected? → A: **Neither.** A day already recorded on a device is never rewritten, because Principle III admits no exception and the project constitution supersedes this specification. The account settles on the lower (older) catalogue version, and that is what any device which has *not yet* materialised the date will derive. A device that already materialised it keeps exactly what it recorded, forever. Earned points are unaffected in every case — they are frozen on each completion.
- Q: On a device holding one account's records, what happens to those records when a different account signs in? → A: They are removed from the device, but only behind the same explicit, named confirmation that "sign out and remove data from this device" requires. This is authorised here rather than left implicit, because no other requirement permits removing a record that belongs to somebody else.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sign in and keep every existing record (Priority: P1)

A user who has been recording practices locally for weeks creates an account and signs in for the first time. Every day, every completion, and every score they had before signing in is still there afterwards, unchanged, and is now backed up.

**Why this priority**: This is the only story in the phase that can destroy real user history, and no other sync story can be exercised without it. It is also the entire user-facing promise of the phase — "your record is safe now". If it ships alone, the user gains backup; if it ships wrong, the product loses its reason to exist.

**Independent Test**: Seed a device with several weeks of local-only history under a known catalogue version, create an account, sign in, and verify every past day renders identical figures before and after sign-in, and that the same figures are retrievable after a full app reinstall and sign-in on the same account.

**Acceptance Scenarios**:

1. **Given** a device with local history recorded while signed out, **When** the user signs in for the first time, **Then** every previously recorded day shows exactly the same earned/available totals and the same per-task completions as it did before sign-in.
2. **Given** the same device after sign-in completes, **When** the app is uninstalled, reinstalled, and the same account signed in, **Then** all previously recorded days and their figures are restored.
3. **Given** local history and no network connectivity, **When** the user attempts to sign in, **Then** the app states that sign-in needs a connection, leaves all local history untouched, and remains fully usable offline.
4. **Given** sign-in is interrupted part-way through the upload of local history (process death, connection loss), **When** the app is reopened, **Then** the upload resumes and completes with no record duplicated and none lost.
5. **Given** an account that already holds history from another device, **When** the user signs in on a device that also has local-only history, **Then** the resulting account holds the union of both sets of records, with no record from either side discarded.

---

### User Story 2 - Everyday recording keeps working, and syncs when it can (Priority: P2)

A signed-in user completes and undoes tasks exactly as before — instantly, with no spinner and no dependency on connectivity. Whatever they record offline reaches the account once a connection is available, and a small status surface tells them where things stand.

**Why this priority**: This is the sync engine proper, and it is what makes User Story 1's promise continuous rather than one-off. It is second because the migration must be trustworthy before there is anything worth syncing continuously.

**Independent Test**: With the device offline, record and undo a mixture of completions across several days, restore connectivity, and verify the account ends up holding exactly the set of completions visible on the device, with the status surface reporting a completed sync.

**Acceptance Scenarios**:

1. **Given** a signed-in user in airplane mode, **When** they complete and undo tasks, **Then** every action is recorded and reflected in scores immediately, with no error, no spinner, and no blocked interaction.
2. **Given** pending offline changes, **When** connectivity returns, **Then** the pending changes reach the account without the user taking any action, and the status surface moves from "pending" to "synced".
3. **Given** a completion that was recorded and then undone while offline, **When** sync runs, **Then** the account does not report that completion as recorded.
4. **Given** the same change is submitted more than once (retry after an ambiguous failure), **When** sync completes, **Then** the account holds exactly one record of it and scores are unchanged by the retry.
5. **Given** the account is unreachable or repeatedly failing, **When** the user continues using the app, **Then** the core loop is unaffected, and the status surface reports "not synced yet" without alarming or blaming language.
6. **Given** an expired session, **When** sync next runs, **Then** the session is renewed silently where possible, and where it is not, the user is prompted to sign in again while all local data and the offline core loop stay intact.

---

### User Story 3 - Two devices show the same record (Priority: P3)

A user signs in on a second device (new phone, tablet) and sees the same history. Recording on either device shows up on the other.

**Why this priority**: Multi-device continuity is the payoff users actually ask for, but it is only observable once Stories 1 and 2 are correct, and it adds no new writes of its own — it is convergence behaviour over the same engine.

**Independent Test**: Sign the same account in on two devices, record different tasks on each while both are offline, bring both online, and verify both devices converge to the same complete set of records and identical scores for every affected day.

**Acceptance Scenarios**:

1. **Given** a fresh device signed into an existing account, **When** initial sync completes, **Then** it shows the same days, completions, and totals as the original device.
2. **Given** a fresh device moments after sign-in, **When** the user opens the app, **Then** today and the current week are already populated and fully usable while older history is still arriving.
3. **Given** a fresh device whose backfill is still running, **When** the user opens a history or insights view covering a date not yet fetched, **Then** that date is shown as still loading rather than as 0%, as untouched, or as absent.
4. **Given** a backfill interrupted by connection loss or process death, **When** the app next runs, **Then** the backfill resumes from where it stopped without re-fetching what it already holds and without duplicating any record.
5. **Given** two devices offline recording different completions on the same day, **When** both reconnect, **Then** both converge to the union of completions and to the same daily score.
6. **Given** one device undoes a completion that the other device still shows as recorded, **When** both reconnect, **Then** both converge on the later action, and the outcome is the same on both devices.
7. **Given** two devices open the same date for the first time independently under the same catalogue version, **When** both reconnect, **Then** the day reports one set of applicable tasks and one available-points total — not a duplicated or doubled day.
8. **Given** two devices opened the same date independently under *different* catalogue versions before either synced, **When** both reconnect, **Then** neither device's already-recorded day is rewritten, both report the same completions and the same earned points for that date, the account settles on the older of the two versions, and a third device joining later derives that older version.

---

### User Story 4 - Catalogue comes from the account, history stays honest (Priority: P4)

The task catalogue is published centrally rather than baked into the app. A device picks up a newer catalogue version when one exists, applies it to days going forward, and leaves every already-recorded day reporting exactly what it always reported.

**Why this priority**: It removes the need to ship an app update to correct the catalogue, but it is the story most capable of quietly corrupting history, so it is sequenced after the sync engine is proven and it inherits Phase 5's historical-integrity suite as its acceptance bar.

**Independent Test**: Record history under catalogue version N, publish version N+1 with changed points and a changed schedule, sync, and verify every past day still reports version N figures while the current and future days follow N+1.

**Acceptance Scenarios**:

1. **Given** recorded history under a catalogue version, **When** a newer catalogue version is pulled, **Then** all past days report unchanged tasks, points, and totals.
2. **Given** a newer catalogue version has been pulled, **When** a new day is opened, **Then** that day's applicable tasks and available points follow the new version.
3. **Given** a fresh install with no connectivity, **When** the user opens the app, **Then** the app works immediately from its built-in catalogue and records days against it.
4. **Given** a device that recorded days against the built-in catalogue while offline, **When** it later pulls a central catalogue, **Then** those recorded days are not retroactively re-evaluated.
5. **Given** two devices on the same account and the same catalogue version, **When** each materialises the same date independently, **Then** both produce the same applicable task set and the same available-points total.

---

### User Story 5 - The app still belongs to someone who never signs in (Priority: P5)

A user who does not want an account uses the full app exactly as before, and a signed-in user can sign out and keep using it.

**Why this priority**: It is the constitutional guarantee of the phase (turning the backend off degrades the app to the MVP) and the regression bar for everything above. Low priority as *new* user value, non-negotiable as behaviour.

**Independent Test**: On a fresh install with no account and no network, run the complete Phase 2–6 flow — today, week, streak, history, insights — and verify nothing is blocked, hidden, or degraded, and that no screen prompts or nags for an account.

**Acceptance Scenarios**:

1. **Given** a fresh install in airplane mode with no account, **When** the user records, undoes, reviews history, and views insights, **Then** everything works with no account prompt blocking any path.
2. **Given** a signed-in user, **When** they sign out normally, **Then** the app remains fully usable and every recorded day, completion, and total remains on the device exactly as before, now unassociated with any account.
3. **Given** a signed-in user on a shared device, **When** they choose "sign out and remove data from this device" and confirm, **Then** the local records are removed, the app returns to a fresh signed-out state, and the account still holds everything that had been synced.
4. **Given** a signed-in user with changes not yet accepted by the account, **When** they sign out by either path, **Then** they are told plainly that changes have not been backed up yet, and the removing path additionally names what would be lost before proceeding.
5. **Given** a user signs out and later signs back into the same account, **When** initial sync completes, **Then** their full record is present with no duplication.
6. **Given** a device with local records belonging to a previous account, **When** a different account signs in on that device, **Then** no record crosses between the two accounts.

---

### Edge Cases

- What happens when the same account signs in on a device that has local-only records created signed-out, *and* the account already holds records for the same dates? (Union of occurrences; no day is recomputed, no occurrence is silently dropped.)
- What happens when a record is undone on one device and re-recorded on another before either syncs?
- What happens when a device's clock is wrong or is changed, making a locally recorded change appear older or newer than it is?
- What happens if the account is reachable but rejects a write (permission failure, malformed row, catalogue version unknown)? The local record must remain, the failure must be retried or surfaced, and the core loop must not break.
- What happens on repeated failed sync attempts over a very long offline period (months)? The queue keeps growing and nothing is discarded, so the storage cost of a year of unsent changes must stay negligible and the eventual reconnect must drain the whole queue without timing out.
- What happens when a user attempts to read another user's records? (Must be impossible from any device, not merely hidden by the app.)
- What happens when the account holds far more history than the device has ever seen (year-plus first sync on a new device)?
- What happens when a central catalogue version is published that the installed app is too old to understand?
- What happens when the same day was materialised independently on two devices before either synced? (Same catalogue version: identical, so nothing to resolve. Different versions: neither recorded day is rewritten, the account settles on the older version for the benefit of devices that have not yet materialised it — FR-024a, FR-024b.)
- What happens when the one-time code never arrives, arrives after it has expired, is entered incorrectly, or was requested for a mistyped email address? Each case must be recoverable from the sign-in screen without losing the entered address, and must leave local records untouched.
- What happens when sign-in succeeds but the first upload is rejected midway — the user must not be shown a "backed up" state they do not have.

## Requirements *(mandatory)*

### Functional Requirements

**Identity**

- **FR-001**: Users MUST be able to create an account, sign in, and sign out from within the app.
- **FR-002**: The app MUST support exactly one sign-in method: the user supplies an email address and completes sign-in with a one-time code or link sent to that address. Passwords MUST NOT be set, stored, requested, or transmitted anywhere in the app, and no social or third-party sign-in provider may be introduced in this phase.
- **FR-002a**: The sign-in screen MUST handle the delivery round-trip visibly: a state while the code is being requested, a state while awaiting entry of the code, a way to request a new code after a stated wait, and clear handling of an expired or incorrect code — none of which may discard the entered email or any local record.
- **FR-003**: The app MUST remain fully usable, with every Phase 2–6 capability intact, while signed out and while offline (Principle IV).
- **FR-004**: No screen may block, gate, or repeatedly prompt for account creation to reach any existing capability.
- **FR-005**: A signed-in session MUST survive app restarts and process death without re-authentication, and MUST be renewed without user action where the identity provider allows it.
- **FR-006**: When a session cannot be renewed, the app MUST return to a signed-out but fully functional state, keep local records intact, and offer sign-in — never lose or hide history as a consequence.
- **FR-007**: Users MUST be able to view basic profile information — the email address they signed in with and an optional display name — and sign out from it.
- **FR-007e**: The display name MUST be optional and empty by default, editable from the profile at any time, and MUST NOT be requested or required during sign-in. Where a name is shown and none is set, the email address MUST be shown instead. No surface in this phase publishes the display name to any other user.
- **FR-007a**: Plain sign-out MUST leave every local record on the device, fully usable signed-out and unassociated with any account.
- **FR-007b**: A separate, explicitly labelled "sign out and remove data from this device" action MUST be available, and MUST require a confirmation that names what is about to be removed from the device.
- **FR-007c**: Both sign-out paths MUST warn when changes have not yet been accepted by the account, stating that those changes are not backed up; the removing path MUST NOT proceed past that warning without a further confirmation.
- **FR-007d**: Neither sign-out path may remove anything from the account.

**Local-to-account migration**

- **FR-008**: On first successful sign-in, every locally recorded day and completion MUST be associated with the signed-in account and uploaded, with no change to what any day reports.
- **FR-009**: Migration MUST be resumable and idempotent: an interruption at any point, followed by a retry, MUST leave exactly one copy of every record.
- **FR-010**: Migration MUST NOT delete, overwrite, or recompute local records at any point, including on failure.
- **FR-011**: If the account already holds records, migration MUST produce the union of account records and local records; no record from either side may be discarded.
- **FR-012**: The app MUST NOT report a backed-up state until the records in question have been accepted by the account.
- **FR-013**: Signing a different account into a device that holds another account's records MUST NOT expose, merge, or attribute records across the two accounts. Only records that have never been associated with an account are eligible for migration; records previously associated with a different account MUST be excluded from migration and MUST NOT be displayed to the newly signed-in account.
- **FR-013a**: A previous account's local records MAY be removed from the device to satisfy FR-013, and MUST NOT be removed by any other means. Removal MUST be preceded by the same explicit confirmation FR-007b requires — naming the account being replaced, what is about to be removed, and any changes of that account not yet backed up — and MUST NOT proceed without it. Declining leaves the device exactly as it was and no new session is opened. Nothing is removed from either account (FR-007d).

**Sync engine**

- **FR-014**: Recording and undoing a completion MUST complete locally without any network call on the interaction path (Principle IV).
- **FR-015**: Every change to a synchronisable record MUST be durably queued locally for transmission, and MUST survive process death and reboot.
- **FR-016**: Queued changes MUST be transmitted automatically when connectivity is available, without user action.
- **FR-017**: Transmission MUST be idempotent on the record's client-generated identifier: resubmitting the same change any number of times MUST leave one record and unchanged scores.
- **FR-018**: An undone completion MUST propagate as a tombstone, never as an absence, so that other devices remove it rather than re-adding it.
- **FR-019**: Where two devices modify the same record, the conflict policy MUST be last-write-wins per completion occurrence, applied identically on every device so all devices converge on the same outcome. Because the only field of a completion that can change after it is written is its undo tombstone — re-recording after an undo creates a new occurrence with a new identifier rather than reviving the old one — the later action is always the undo. The policy MUST therefore be implemented as an order-independent rule ("once undone, stays undone"), which yields the same outcome as last-write-wins for every possible sequence while depending on no clock at all.
- **FR-019a**: The conflict policy MUST be stated on a surface the user can reach from within the app — not only in developer documentation — in plain language, alongside the sync status.
- **FR-020**: Sync MUST converge: after all devices are online and idle, every device MUST show the same records and the same score for every day.
- **FR-021**: Sync failures MUST NOT block, delay, or alter local recording, and MUST be retried with backoff rather than abandoned.
- **FR-021a**: Pending changes MUST be retained indefinitely. No entry may be dropped, expired by age, or evicted by a queue-size cap; retries continue with backoff for as long as the change is unaccepted.
- **FR-022**: The app MUST surface a sync status covering at minimum: up to date, changes pending, and not syncing (signed out or unreachable). Status language MUST be neutral and never blaming (Principle IX).
- **FR-023**: Records MUST be protected such that no account can read or modify another account's records, enforced by the account service itself and not by the client.
- **FR-023a**: On first sync of an account onto a device, the current day and current week MUST be fetched first and the app MUST become fully usable before older history has arrived; remaining history MUST backfill in the background.
- **FR-023b**: Any date not yet fetched MUST be presented as still loading, and MUST NOT be rendered as 0%, as untouched, or as absent in any history, week, streak, or insights view.
- **FR-023c**: Backfill MUST be resumable: an interruption at any point MUST resume without re-fetching already-held records and without duplicating any record.
- **FR-023d**: Streak, weekly, and insight figures MUST NOT be presented as final while backfill covering their range is still incomplete.
- **FR-024**: Day Plans MUST remain locally authoritative and re-derivable from the catalogue version they were created against; the same date and catalogue version MUST produce the same applicable task set and available-points total on every device.
- **FR-024a**: A day already materialised on a device MUST NOT be re-derived, re-versioned, or rewritten by sync under any circumstance. There MUST be no code path — no repository method, no data-access method, no merge — capable of changing a stored day's tasks, its per-task points, or its available-points total (Principle III).
- **FR-024b**: Where two devices materialised the same date under different catalogue versions before either synced, the account MUST settle on the lower (older) of the two versions, and that settled version is what any device that has not yet materialised the date MUST derive. Each device that already materialised the date keeps what it recorded (FR-024a). A newer catalogue version MUST never become the settled version for a date already recorded under an older one.

**Central catalogue**

- **FR-025**: The app MUST be able to pull a versioned task catalogue from the account service, and MUST fall back to its built-in catalogue when none has been pulled or the service is unreachable.
- **FR-026**: A newly pulled catalogue version MUST apply to days not yet materialised only; already-recorded days MUST continue reporting their original tasks, points, and totals (Principle III).
- **FR-027**: Users MUST NOT be able to create, edit, delete, reorder, reprice, or reschedule any task through any surface introduced by this phase (Principle VI).
- **FR-028**: A catalogue version the installed app cannot interpret MUST be ignored in favour of the last version it can, without crashing and without corrupting recorded days.

### Key Entities *(include if feature involves data)*

- **User Account**: The identity a set of records belongs to. Holds the email address used to sign in and an optional display name (empty by default, editable, never required). Every synchronisable record refers to at most one account. Records created signed-out refer to none and are the only records eligible for migration; a record that has ever been associated with an account stays with that account permanently.
- **Device**: A single installation holding a local copy of the record set and its own pending-change queue. Devices are peers; none is authoritative.
- **Sync State**: Per-record status of whether the local version has been accepted by the account, together with its last-modified timestamp. Drives both the queue and the status surface.
- **Pending Change (Outbox Entry)**: A durable local entry describing one create or one tombstone awaiting transmission, keyed by the record's client-generated identifier so retries are safe.
- **Tombstone**: The recorded fact that a completion occurrence was undone, propagated so other devices converge on removal rather than resurrecting it.
- **Catalogue Version**: An identified, immutable publication of the task catalogue. Day Plans reference the version they were materialised against, permanently.
- **Sync Status**: The user-facing summary of the above — up to date, pending, not syncing, or still loading earlier history — with no failure or blame framing.
- **Backfill Progress**: How far back a device has fetched an account's history, so that not-yet-fetched dates can be distinguished from genuinely empty ones and an interrupted backfill can resume.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of a user's pre-existing local records survive first sign-in with identical figures — zero records lost, zero duplicated, zero days whose totals change.
- **SC-002**: Recording or undoing a task completes in the same time signed-in as signed-out, with no additional user-perceived delay and no network wait on the interaction path.
- **SC-003**: A user who records for a full day in airplane mode has every one of those records present in their account within one minute of connectivity returning, without opening any screen or pressing anything.
- **SC-004**: Two devices on one account, after both are online and idle, show identical completions and identical **earned** points for 100% of days, and identical available-points totals for 100% of days except those independently materialised on both devices under different catalogue versions before either synced — where each device keeps the total it recorded, because a recorded day is never rewritten (FR-024a). Verified across a scenario set that includes concurrent recording, concurrent undo, independent first-open of the same date under one version, and independent first-open under two versions.
- **SC-005**: Resubmitting any change any number of times produces exactly one record and leaves every score unchanged.
- **SC-006**: A user moving to a new device can record on today within 10 seconds of sign-in completing, and holds their complete history — at least one year of it — within two minutes, with no screen ever showing a fetched-but-incomplete date as 0%.
- **SC-007**: With the account service fully unavailable, every Phase 2–6 capability still works with zero crashes and zero missing history — the app degrades exactly to the MVP.
- **SC-008**: No account can retrieve any record belonging to another account, verified directly against the service rather than through the app.
- **SC-009**: After a catalogue change is published, 100% of previously recorded days report unchanged figures while newly opened days follow the new version.
- **SC-010**: A device kept offline for a full year of daily recording loses zero pending changes, and on reconnect every one of them reaches the account.
- **SC-011**: Every sync-related string is free of failure, blame, or loss framing, verified against the Principle IX audit list.

## Assumptions

- The user-facing account service is Supabase, as recorded in `docs/PLAN.md`; this spec states behaviour, and the plan chooses the mechanisms.
- Sync covers completions, the day-plan records needed to interpret them, and the catalogue. Day Plans stay locally authoritative and re-derivable, per the PLAN recommendation, because two devices must produce the same plan from the same catalogue version.
- Conflict resolution is last-write-wins per completion occurrence, adequate because completions are near-immutable facts; anything more sophisticated is deferred. It is implemented as an order-independent rule rather than by comparing timestamps (FR-019), which is a strictly stronger guarantee, not a weaker one.
- The sync-ready record shape from Phase 2 — client-generated identifiers, last-modified timestamps, tombstone markers, nullable account reference — already exists and needs no data migration.
- The repository interfaces declared in `:domain` do not change; this phase substitutes and decorates implementations only. A domain use case needing modification is treated as a boundary defect to fix, not a change to accommodate.
- **No timestamp resolves a conflict at all.** Both merge rules — the undo tombstone on a completion, and the settled catalogue version on a date — are order-independent, so the outcome is the same however the changes interleave and whichever device's write arrives first. A wrong or altered device clock therefore cannot win or lose a conflict, because no clock is consulted. Server timestamps are used only as a pull cursor, never as a decision input.
- Background sync runs on connectivity and app foreground; no fixed polling interval is promised to the user.
- Leaderboards, friends, real-time subscriptions, push notifications, an admin console for editing the catalogue, and social profiles are all out of scope for this phase.
- Account deletion and data export are out of scope for this phase, and are noted as a follow-up obligation rather than a silent omission.
