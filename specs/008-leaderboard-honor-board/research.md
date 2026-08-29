# Phase 0 Research: Leaderboards & Honor Board

**Feature**: `specs/008-leaderboard-honor-board` | **Date**: 2026-08-29

Ten questions this increment had to settle before design. Each records what was decided, why, and
what was rejected.

---

## R1 — How does a participant read another participant's figures without reading their records?

**Decision**: Never widen an existing row-level policy. Expose a separate, purpose-built aggregate
table, `leaderboard_entries`, holding exactly `(period_kind, period_start, region_id, user_id,
display_name, points, days_engaged)` for opted-in participants only. `completions` and `day_records`
keep their `_select_own` policies unchanged, and Phase 7's `rls-verification.sql` assertions are
re-run alongside the new ones.

**Rationale**: The obvious implementation — a policy letting participants in a region read each
other's `completions` — would expose every completion row: which task, which date, which points,
when it was recorded, and whether it was later reversed. That is a person's complete worship record,
handed to strangers, to render a single number. The aggregate carries only what FR-002 says the
participant consented to publish.

This also makes SC-006 (tamper resistance) structural rather than procedural: `leaderboard_entries`
has no client-writable policy at all, so there is no write path to defend.

**Alternatives considered**:

- *Widen `completions` select to same-region participants.* Rejected — catastrophic disclosure, and
  it would put the burden of aggregation on the client, which FR-019 forbids.
- *A security-definer function returning aggregates over `completions`.* Viable and genuinely
  considered: it avoids a second copy of the data. Rejected on latency (R2) and because a function
  that can read every row is a larger blast radius than a table that only ever holds publishable
  fields. If the function is ever wrong, it leaks records; if the table is ever wrong, it leaks a
  number that was already consented to.
- *Postgres views with `security_invoker`.* Rejected — a view inherits the invoker's policies, so it
  returns nothing across accounts. That is the correct behaviour of RLS and cannot be worked around
  without widening the underlying policy.

---

## R2 — Aggregate on read, or precompute?

**Decision**: Precompute on a schedule into `leaderboard_entries`. The job recomputes only periods
that are still open.

**Rationale**: SC-014 requires a first ranking within 3 seconds and SC-009 requires an own-rank
lookup in a 10 000-participant region. A read-time aggregation over a region's completions re-scans
on every request from every participant — the cost scales with viewers times records, which is the
wrong shape. A precomputed table makes the read an indexed range scan and the own-rank lookup a
single row plus a count.

Restricting the job to open periods is what makes FR-025 and FR-031 mechanical: a closed period is
not in the job's working set, so it cannot be re-scored even if a completion for that date arrives
late or a catalogue changes.

**Alternatives considered**:

- *Materialised view refreshed on each completion upsert.* Rejected — it puts leaderboard cost on
  the sync write path, which Principle IV keeps clear.
- *Recompute all periods every run.* Rejected — it would let a late-arriving completion silently
  rewrite a closed period's standings, violating Principle III and FR-025.

---

## R3 — What fixes a period boundary, and how is region assigned?

**Decision**: The region carries an IANA timezone; the region's timezone fixes its period
boundaries. The client reports its own zone id (from `TimeProvider.zone()`); the server maps zone →
region through an administrator-defined table and stores the result on the account. The client never
names a region.

**Rationale**: This is FR-012's correctness requirement, not a preference. The catalogue schedules
day-specific tasks — the Friday section most visibly — so a participant's available points on a date
depend on the weekday that date is *for them*. Ranking someone inside a leaderboard Friday while
their device shows Thursday compares them against a denominator they never had.

Server-side mapping is what makes FR-014 hold: a client that lies about its zone is moved to the
region matching the claim and ranked against people on that clock. It cannot shop for a weak pool,
because it never names a pool — and any zone it claims lands it somewhere with the same rules.

**Alternatives considered**:

- *One global leaderboard on a single timezone (Makkah was the obvious candidate).* Rejected by the
  user's decision, and independently by FR-012: a global Makkah-time day puts a participant twelve
  zones away on the wrong weekday for a large part of every day.
- *Per-viewer computation in the viewer's own local zone.* Rejected — with each viewer on their own
  boundaries there is no single ordered list, so "position 4" means something different to every
  participant and no two people can discuss the same ranking.
- *Client asserts its region directly.* Rejected by FR-014 — trivially forgeable, and pool selection
  is the cheapest possible cheat.

---

## R4 — What exactly is summed, and how are reversals handled?

**Decision**: `points = sum(points_awarded)` and `days_engaged = count(distinct credited_date)`,
both over completions where `reversed_at is null`, grouped by `(user_id, region, period)`.
`credited_date` is the value the device already computed; the server never re-derives a date.

**Rationale**: Phase 7 froze `points_awarded` on the completion at write time and made `reversed_at`
set-only. Both properties are load-bearing here. Because points are frozen, a catalogue change
cannot retroactively move a past ranking. Because tombstones are set-only, excluding reversed rows
is stable — a completion cannot un-reverse and re-enter a total.

Grouping by the stored `credited_date` keeps Principle VII's single-clock rule intact: the date was
decided once, on the device, against `DayBoundary`. The server sorts dates into periods; it does not
decide what date anything happened on.

`days_engaged` deliberately reuses the streak's notion of an engaged day (at least one applicable
task completed), so the Honor Board and the streak cannot disagree about what a day of engagement is.

**Alternatives considered**:

- *Recompute points server-side from the catalogue.* Rejected outright — Principle III. It would let
  a catalogue change rewrite an earned figure.
- *Count reversed completions as engagement.* Rejected — a completion that was undone is not a
  completion; counting it would let a participant qualify for the Honor Board by recording and
  immediately undoing.

---

## R5 — When does a period close, and what closes it?

**Decision**: A period is open while the region's current local time is within it, plus a bounded
settlement window to let late-arriving offline records land. When the window passes, the job stops
recomputing that period and it becomes immutable.

**Rationale**: Principle IV guarantees a participant can record a full day offline and sync later;
Phase 7's own SC-010 contemplates a year offline. If a period froze exactly at its boundary, every
offline participant would be permanently under-counted, and the feature would systematically
penalise the users the constitution most wants to protect. A settlement window is the honest
reconciliation of Principle III (a closed period never changes) with Principle IV (offline recording
is not second-class).

The window is configuration, not a constant in code, so it can be tuned without a release.

**Alternatives considered**:

- *Freeze at the boundary instant.* Rejected — silently penalises offline use, which is the
  product's core promise.
- *Never freeze; always recompute.* Rejected — violates Principle III and FR-025, and would let a
  ranking a participant saw last month change underneath them.

---

## R6 — How does the client hold rankings without becoming an authority on them?

**Decision**: A local `leaderboard_cache` table stores the last retrieved page per (period, region)
with the instant it was fetched. The UI observes Room. A bounded refresh writes into Room and is
never called from a ViewModel.

**Rationale**: This is the pattern Phase 7 already established for pulled data, and reusing it keeps
one rule in the codebase rather than two. Caching satisfies the offline edge cases without making the
client an authority: the cache is disposable, is never summed or extended locally, and is stamped so
FR-036 can render its age rather than presenting it as current.

**Alternatives considered**:

- *No cache; show unavailable when offline.* Rejected — a participant who opens Progress on a train
  sees nothing, and the spec's offline edge case asks for the last known standing.
- *Cache and extend locally between refreshes.* Rejected — that makes the client an authority on
  positions, which FR-018 forbids, and invites a local total to disagree with the server's.

---

## R7 — What exactly does opting out reach?

**Decision**: Clearing consent removes the participant from every period that is still **open** —
both its ranking and its Honor Board — and keeps them out of every period that opens afterwards.
Periods that have already **closed** are left exactly as they stand, rankings and Honor Board alike.
A closed period admits no mutation of any kind.

**Rationale**: This is the user's Q3 answer, and it draws the line at the period boundary rather than
between comparison and recognition. Two principles genuinely pull against each other here, and the
resolution is that Principle III wins for the past while Principle IX wins for the present: leaving
stops comparison from this moment on, and does not rewrite history that other participants already
saw.

It also makes the whole model simpler and, more importantly, uniform. "A closed period never
changes" is now a single rule with no exception, which is what lets FR-025 and FR-031 be enforced
structurally: a closed period is simply not in any mutating code path — not the aggregation job's
working set, and not the withdrawal function's delete. Under the earlier split the withdrawal
function had to reach into closed rankings but stop short of closed Honor Boards, and a rule with a
carve-out is a rule waiting to be got wrong.

**The cost is real and must be disclosed.** A participant cannot erase their past standings. That is
a genuine weakening of consent compared with total withdrawal, and it is only acceptable if they know
before they join — hence **FR-002a**, which requires the opt-in statement to say that completed
periods remain visible after leaving. Consent that a person would not have given had they understood
it is not consent, and this is the one place in the increment where that risk exists.

**Alternatives considered**:

- *Withdraw from everything, closed periods included.* The strongest privacy position, and rejected
  by the user's decision. It also makes every past ranking mutable, which sits badly with
  Principle III and would mean a ranking a participant saw last month could change underneath them.
- *Withdraw from closed rankings but preserve closed Honor Board recognition* (the earlier answer).
  Rejected on the user's revision. It split the rule along the shame/recognition line, which reads
  well but leaves "a closed period never changes" with an exception — and the exception is exactly
  the kind of conditional that regresses silently.

---

## R8 — How is Principle IX enforced against a future UI change?

**Decision**: Put the guarantees in the read model. The Honor Board endpoint returns qualifying
members and nothing else — no non-qualifier count, no threshold distance, no shortfall field.
SC-012 is verified against everything the client can *retrieve*, not against what it renders.

**Rationale**: A guarantee that lives only in a composable survives exactly until someone adds a
field to a screen. Principle IX is the constitution's most easily violated clause precisely because
it is a property of presentation, so the durable protection is to make the harmful data unavailable
rather than merely unrendered. If the client cannot retrieve "you were 3 days short", no future UI
can show it.

The same reasoning drives the last-place rule (FR-038): there is no `isLast` or `isBottom` flag in
the read model for a view to key styling off.

**Alternatives considered**:

- *Return the data; forbid rendering it by convention.* Rejected — convention is exactly what
  Principle IX says is easiest to violate by accident.
- *Return a shortfall so the UI can encourage.* Rejected — "3 more days to qualify" is a deficit
  framing, and the constitution forbids expressing progress as a shortfall.

---

## R9 — How does a participant find their own row in a large region?

**Decision**: A dedicated own-rank lookup returning the participant's position, total and
neighbouring entries, independent of whichever page is loaded. Ranking pages are bounded at 50 and
extend on demand.

**Rationale**: SC-009 requires locating one's own row among 10 000 participants without scrolling.
Paging until the row appears is both slow and, at the bottom of a large region, an unpleasant
experience the constitution has opinions about — scrolling past 9 000 people to find yourself is a
shame mechanic even if no copy says anything.

**Alternatives considered**:

- *Page until found.* Rejected on both latency and Principle IX.
- *Always centre the view on the participant.* Rejected as the only mode — a participant may
  legitimately want to see the top of the ranking; own-rank is a lookup, not a forced viewport.

---

## R10 — What proves the guarantees a fake cannot?

**Decision**: Extend Phase 7's `rls-verification.sql` into `rls-verification-008.sql`, covering:
(a) Phase 7's original assertions, re-run unchanged — raw completions and day records still
unreadable across accounts; (b) a participant cannot read `leaderboard_entries` for a region other
than their own; (c) a participant cannot write, update or delete any `leaderboard_entries` row,
including their own; (d) a participant cannot choose their own region, nor read the zone mapping
they would need in order to choose one; (e) an unmatched zone lands in the fallback region;
(f) opting out clears the participant from every **open** period while every **closed** period —
rankings and Honor Board alike — is left byte-identical; (g) the Honor Board exposes no
non-qualifier identity, count or distance.

**Rationale**: Phase 7 established that RLS is the one guarantee a fake remote cannot prove, and
SC-006 repeats its wording — "verified directly against the account service rather than through the
app". Extending the existing script rather than writing a parallel one means the Phase 7 guarantees
are re-proven on every run, so this increment cannot quietly regress them.

**Alternatives considered**:

- *A new, separate script.* Rejected — two scripts drift, and the Phase 7 assertions are exactly the
  ones most at risk from this increment.
- *Test through `FakeRemoteDataSource` only.* Rejected — a fake proves the client asked politely,
  which is precisely not what SC-006 asks for.
