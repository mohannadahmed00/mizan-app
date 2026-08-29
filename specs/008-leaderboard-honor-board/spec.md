# Feature Specification: Leaderboards & Honor Board

**Feature Branch**: `spec/008-leaderboard-honor-board`

**Created**: 2026-08-29

**Status**: Draft

**Input**: User description: "Read PLAN.md and create a specification for the Phase 8 — Leaderboards & Honor Board"

## Overview

Phase 8 introduces the product's first comparative surface: raw-points rankings over daily, weekly
and monthly periods, plus an Honor Board that recognises sustained consistency.

This is the increment where the constitution bites hardest. Principle IX forbids penalties, failure
states and guilt-inducing copy or imagery; a leaderboard is, by construction, a device for telling
people where they stand relative to others. The two are reconcilable only if participation is
something a person chooses, comparison is never the default framing of their own worship, and no
surface anywhere renders a low position as a deficiency.

Four product decisions are fixed inputs, not open questions:

1. **The leaderboard is not a tab.** It lives inside Progress. The design's own rationale is that a
   permanent leaderboard tab "puts comparison at the same weight as worship." A person must be able
   to use Mizan indefinitely without encountering a ranking.
2. **Leaderboards are regional, not global.** Participants compete only with others in the same
   region, and each region carries its own timezone that fixes its period boundaries.
3. **The week is Saturday to Friday**, evaluated in the region's timezone. The day is a calendar day
   (Principle VII). Nothing here may introduce a second definition of either.
4. **Rankings have no local authority.** They are a remote read model, recomputed server-side from
   synced completions. A client's own arithmetic never determines anyone's position, including its
   own.

### Why regional, and why it is a correctness requirement

The catalogue contains day-specific tasks — the Friday section most visibly. A person's available
points on a given calendar date depend on which weekday that date is *for them*. Ranking a
participant inside a period that says Friday while their own device still says Thursday would
compare them against a denominator they never had, and would contradict the date their own Today
screen shows.

Regional grouping resolves this. A region's timezone defines its period boundaries, and participants
are grouped so that the region's calendar date matches their own. Comparison therefore always happens
between people who were on the same weekday, with the same day-specific tasks available.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Opting in and seeing where I stand (Priority: P1)

A signed-in person opens Progress and finds an invitation to join the leaderboard. It is off by
default and explains, before they decide, what joining publishes: a display name and a points total
for the current period, visible to other participants in their region. They opt in, and from then on
Progress shows the ranking for the selected period along with their own position in it, labelled
with the region it covers.

**Why this priority**: This is the whole feature's minimum viable slice. Without opt-in and a
correct, visible ranking there is nothing to build on. It is also where the consent model is
established, and every later story inherits it.

**Independent Test**: Sign in, open Progress, opt in, and confirm a ranking appears with the
participant's own position, total, and region label. Fully testable with no Honor Board and no period
switching — a single period is enough to deliver value.

**Acceptance Scenarios**:

1. **Given** a signed-in person who has never opted in, **When** they open Progress, **Then** no
   ranking and no other participant's name or total is visible anywhere, and an invitation to join
   is offered.
2. **Given** the invitation is shown, **When** the person reads it, **Then** it states plainly what
   becomes visible to others (display name, period points total) and that the ranking is limited to
   their region, before any choice is made.
3. **Given** a person who has opted in and whose completions have synced, **When** they open
   Progress, **Then** they see the ranking for the current period in their own region, including
   their own row, marked as theirs.
4. **Given** a person who has opted in but whose recent completions have not yet synced, **When**
   they view the ranking, **Then** their position reflects what the account has actually received,
   and the surface says the standing is still catching up rather than presenting a stale figure as
   final.
5. **Given** a person who is not signed in, **When** they open Progress, **Then** they see the
   Phase 4–6 progress surfaces unchanged and no leaderboard invitation, prompt or placeholder.

---

### User Story 2 - Leaving, and staying gone (Priority: P1)

A participant decides they would rather not compete. They turn participation off from the same
place they turned it on. They leave every period still running, and are not entered into any period
that opens afterwards. Periods that already finished stand as they were. Nothing they recorded is
lost.

**Why this priority**: Equal in priority to opting in, and deliberately so. An opt-in that cannot be
reversed is not consent. Principle IX means a person who finds comparison discouraging must be able
to stop it immediately, which makes this a correctness requirement rather than a nicety.

The boundary is deliberate and is the one place two principles pull against each other. Leaving stops
comparison from here on; it does not rewrite periods that have already closed, because a closed
period is a historical record (Principle III). The cost is that a participant cannot erase their
past standings — which is exactly why FR-002a requires them to be told so before they join.

**Independent Test**: Opt in, confirm the row is visible to a second participant in the same region,
opt out, confirm the row is gone from the open period and absent from the next period to open, that
a closed period is unchanged, and that the first person's own records and streak are untouched.

**Acceptance Scenarios**:

1. **Given** a participant, **When** they turn participation off, **Then** their row no longer
   appears in the ranking for any period still open.
2. **Given** a participant who appears on the Honor Board for the period currently open, **When**
   they turn participation off, **Then** they are removed from it.
3. **Given** a participant who appears in the ranking or on the Honor Board for a period that has
   already closed, **When** they turn participation off, **Then** that period is unchanged — a
   closed period is a historical record and is not rewritten by a later change of mind.
4. **Given** a person who has opted out, **When** a new period opens, **Then** they do not appear in
   it.
5. **Given** a participant who turns participation off, **When** they return to Progress, **Then**
   their own recorded history, points, streak and insights are exactly as before — opting out
   affects visibility only, never the record.
6. **Given** a person who has opted out, **When** they later opt back in, **Then** they appear in
   the current period's ranking with their correct server-derived total and no duplicate row.
7. **Given** a participant with no display name set, **When** they opt in, **Then** they are shown
   under a neutral placeholder identity and are never identified by email address.

---

### User Story 3 - Comparing across periods, within a region (Priority: P2)

A participant switches between daily, weekly and monthly rankings from within Progress. Each period
is scored over the same raw points the rest of the product already uses, and each period's boundaries
are evaluated in the region's timezone — which is the same calendar day the participant's own Today
screen shows.

**Why this priority**: Valuable, but the feature delivers without it — a single period is a working
leaderboard. Deferring it keeps the first slice small.

**Independent Test**: With a fixed set of synced completions spanning a month, switch between the
three periods and confirm each total matches an independent hand-computation over that period's
boundaries in the region's timezone.

**Acceptance Scenarios**:

1. **Given** a participant viewing the ranking, **When** they switch period, **Then** the ranking
   recomputes over that period and their own position updates accordingly.
2. **Given** the weekly period, **When** its boundaries are examined, **Then** it runs Saturday to
   Friday in the region's timezone, identically to the Weekly Accountability Sheet.
3. **Given** any daily period, **When** its date is compared to the date the participant's own Today
   screen shows, **Then** the two match — a participant is never ranked inside a leaderboard day
   that is a different weekday from their own.
4. **Given** a period boundary passes while the app is open, **When** the ranking next refreshes,
   **Then** it presents the new period, and the previous period's standings are not retroactively
   altered.
5. **Given** two participants with identical point totals in a period, **When** the ranking is
   built, **Then** they are ordered by a stated, stable tie-break rule and neither is presented as
   having beaten the other.
6. **Given** a ranking is shown, **When** the participant reads its heading, **Then** the region the
   ranking covers is stated.

---

### User Story 4 - Recognition on the Honor Board (Priority: P3)

Beyond the ranking, an Honor Board recognises participants who engaged on enough days in a period.
It is a recognition surface, not a second ranking: everyone who meets the consistency threshold
appears, in no competitive order, and points play no part in qualifying.

**Why this priority**: The Honor Board is the part of this increment most aligned with the
constitution — recognition without ranking, and consistency rather than volume. It depends on
participation, identity and server-side aggregation already existing, so it is the last slice, and
the product is coherent without it.

**Independent Test**: With participants above and below the days-engaged threshold, confirm exactly
those at or above it appear, that the surface presents no ordering or position, and that those below
it are not listed, counted or alluded to.

**Acceptance Scenarios**:

1. **Given** a participant who engaged on at least the threshold number of days in a period, **When**
   the Honor Board for that period is shown, **Then** they appear on it.
2. **Given** two qualifying participants with very different point totals, **When** the Honor Board
   is shown, **Then** neither is placed above the other and no points figure distinguishes them.
3. **Given** a participant who does not meet the threshold, **When** they view the Honor Board,
   **Then** they see those who qualified, with no statement of their own shortfall, no distance-to-
   threshold, no count of who was excluded, and no negative framing of any kind.
4. **Given** the Honor Board for a completed period, **When** it is viewed later, **Then** its
   membership is unchanged — a past period's recognition is a historical record and is never
   recomputed (Principle III).

---

### Edge Cases

- **The account is unreachable.** Rankings and Honor Board are unavailable; Today, Week, Streak,
  History and Insights behave exactly as they do today, and recording is unaffected. The leaderboard
  surface says standings are unavailable without attributing the failure to the person.
- **The device is offline.** The most recently retrieved ranking is shown, clearly marked as of a
  known point in time rather than presented as current. Absence of a cached ranking shows an
  unavailable state, never an empty list that reads as "nobody is ahead of you."
- **A participant travels to a different timezone.** Their region is re-evaluated so that the
  leaderboard day continues to match their own calendar day. Standings already recorded in the
  previous region for closed periods are not rewritten.
- **A participant's timezone matches no configured region.** They are placed in a defined fallback
  region rather than excluded from the feature, and the surface still states which region they are
  ranked in.
- **A region has very few participants.** The ranking renders normally with whoever is there; no copy
  implies the region is empty, underpopulated, or that the participant is alone because of anything
  they did.
- **A participant has recorded nothing in the period.** They are ranked with zero points like anyone
  else, and no surface characterises this as failure, absence or a lapse.
- **A participant appears at the bottom of the ranking.** The surface renders their row identically
  to every other row — no red, no emphasis, no marker, no separate treatment of any kind.
- **A very large regional population.** The ranking loads a bounded portion and extends on demand;
  the viewer's own row remains reachable without scrolling an unbounded list.
- **A client submits inflated totals.** Rankings are recomputed from synced completion records
  server-side; a client-reported total is never trusted, and a manipulated client cannot alter its
  own or anyone else's position.
- **A client claims a different region.** Region is established from evidence the account service
  holds, not from a client assertion, so a participant cannot move themselves into a region to gain
  a favourable comparison.
- **A participant changes display name.** Rankings reflect the new name on the next refresh; past
  Honor Board membership continues to identify the same person.
- **Sign-out with records removed.** Participation ends with the session; the person does not remain
  visible in any ranking after signing out.

## Requirements *(mandatory)*

### Functional Requirements

#### Participation and consent

- **FR-001**: Leaderboard participation MUST be off by default for every account, including accounts
  that existed before this increment.
- **FR-002**: The system MUST present, before any opt-in choice is made, a plain-language statement
  of exactly what becomes visible to other participants, and that visibility is limited to the
  participant's region.
- **FR-002a**: That statement MUST also say that entries for periods which have already completed
  remain visible after leaving. Because opting out reaches only periods still open (FR-004), a
  participant who is not told this before joining has not given informed consent.
- **FR-003**: Users MUST be able to turn participation on and off from the same place, at any time,
  without confirmation friction that discourages leaving.
- **FR-004**: When participation is off, the account MUST NOT appear in any ranking or Honor Board
  for a period that is still open.
- **FR-004a**: Periods that have already closed MUST be left exactly as they stand — both their
  rankings and their Honor Board membership. A closed period is a historical record and is not
  rewritten by a later change of mind (Principle III).
- **FR-004b**: While participation is off, the account MUST NOT be added to any newly opening
  period. Opting out ends ongoing comparison; it does not revise comparison that already happened.
- **FR-005**: Turning participation off MUST NOT alter, hide or delete any of the person's own
  recorded history, points, streak or insights.
- **FR-006**: A participant MUST be identified by display name only. Email addresses MUST NOT be
  exposed to other participants on any surface.
- **FR-007**: A participant with no display name set MUST be shown under a neutral placeholder
  identity rather than being excluded or identified by any other personal attribute.
- **FR-008**: Signing out MUST end participation for that device's session, and a signed-out account
  MUST NOT remain visible in rankings.

#### Regions and period boundaries

- **FR-009**: Every ranking MUST be scoped to a single region. A participant MUST NOT appear in, or
  be ranked against, any region other than their own.
- **FR-010**: Each region MUST carry a defined timezone, and that timezone MUST fix the boundaries of
  that region's daily, weekly and monthly periods.
- **FR-011**: The weekly period MUST run Saturday to Friday, evaluated in the region's timezone,
  identically to the Weekly Accountability Sheet. The system MUST NOT introduce a second definition
  of the week.
- **FR-012**: A participant's region MUST be assigned such that the region's calendar date matches
  the calendar date the participant's own device shows. A participant MUST NOT be ranked inside a
  leaderboard day that falls on a different weekday from their own.
- **FR-013**: A participant's region MUST be re-evaluated when their device timezone changes, so that
  FR-012 continues to hold.
- **FR-014**: Region MUST be established from evidence the account service holds. The system MUST NOT
  accept a client's assertion of its own region.
- **FR-015**: A participant whose timezone matches no configured region MUST be placed in a defined
  fallback region rather than excluded from the feature.
- **FR-016**: The system MUST state, on the ranking surface, which region the displayed ranking
  covers.
- **FR-017**: Regions MUST be administrator-defined. Users MUST NOT be able to create, choose,
  rename or switch regions (Principle VI).

#### Rankings

- **FR-018**: Rankings MUST be computed by the account service from synced completion records. The
  system MUST NOT rank from client-reported totals.
- **FR-019**: A client MUST NOT be able to alter its own or any other participant's position, region
  or total by any means available to it.
- **FR-020**: The system MUST provide rankings for daily, weekly and monthly periods.
- **FR-021**: Ranking MUST be by raw points earned within the period, using the same points the rest
  of the product awards.
- **FR-022**: Equal totals MUST be ordered by a stated, stable, deterministic tie-break rule that
  produces the same order on every retrieval.
- **FR-023**: A participant MUST be able to see their own position within the ranking without
  scrolling through an unbounded list.
- **FR-024**: Rankings MUST load a bounded portion of a large regional population and extend on
  demand.
- **FR-025**: A completed period's standings MUST NOT change after the period ends, for any reason —
  including a late-arriving completion, a catalogue change, or a participant opting out
  (Principle III). A closed period admits no mutation at all.
- **FR-026**: The system MUST state which period a displayed ranking covers, unambiguously enough
  that a total can be reconciled against the person's own records.

#### Honor Board

- **FR-027**: Honor Board qualification MUST be a days-engaged consistency threshold: the number of
  days within the period on which the participant completed at least one applicable task. Points
  MUST NOT affect qualification.
- **FR-028**: The threshold MUST be administrator-defined and MUST NOT be user-configurable
  (Principle VI).
- **FR-029**: The Honor Board MUST recognise every participant meeting the threshold for a period,
  presented without competitive ordering, position, or points figure.
- **FR-030**: The Honor Board MUST NOT display, imply or enable derivation of who failed to qualify,
  how many did not qualify, or any individual's distance from the threshold.
- **FR-031**: A completed period's Honor Board membership MUST NOT be recomputed or altered
  afterwards, including when a member later opts out (FR-004a). This is the same guarantee as
  FR-025, applied to recognition rather than ranking.

#### Placement, degradation and tone

- **FR-032**: Leaderboard and Honor Board surfaces MUST live within Progress. The system MUST NOT
  add a top-level navigation destination for them.
- **FR-033**: A person who is not signed in MUST see no leaderboard invitation, prompt, placeholder
  or entry point anywhere in the app.
- **FR-034**: Failure or unavailability of rankings MUST NOT degrade recording, Today, Week, Streak,
  History or Insights in any way.
- **FR-035**: When rankings are unavailable, the system MUST say so without attributing the cause to
  the person.
- **FR-036**: When a cached ranking is shown, the system MUST indicate the point in time it reflects
  rather than presenting it as current.
- **FR-037**: When a participant's recent completions have not yet reached the account, the system
  MUST indicate the standing is still catching up rather than presenting an incomplete figure as
  final.
- **FR-038**: No leaderboard or Honor Board surface may use red, negative framing, failure states,
  loss imagery, or copy attributing a low position to the person (Principle IX). A last-place row
  MUST render identically to every other row.
- **FR-039**: The system MUST NOT notify, nudge or otherwise proactively inform a person that their
  position has fallen.

### Key Entities

- **Region**: An administrator-defined grouping of participants with a defined timezone. Scopes every
  ranking and fixes every period boundary. Includes a fallback region for unmatched timezones.
- **Leaderboard Period**: A bounded span — a day, a Saturday-to-Friday week, or a month — evaluated
  in its region's timezone, over which points are totalled.
- **Ranking**: An ordered list of participants for one period within one region, derived server-side
  from synced completions. Each entry carries a display identity, a points total, and a position.
- **Participation Consent**: An account's opt-in state, off by default, revocable at any time, and
  determining whether that account appears to any other participant.
- **Display Identity**: The name and neutral placeholder under which a participant appears. Derived
  from the profile display name established in Phase 7; never an email address.
- **Honor Board Criteria**: The administrator-defined days-engaged threshold for a period, and the
  resulting membership list, which is fixed once the period closes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person who never opts in can use every part of the product indefinitely without
  encountering a ranking, another participant's name, or any invitation beyond the single opt-in
  entry point inside Progress.
- **SC-002**: Turning participation off removes the participant from every open period's ranking and
  Honor Board within one refresh, and keeps them out of every period that opens afterwards — while
  every closed period's rankings and Honor Board membership remain exactly as they were.
- **SC-003**: Opting out leaves the person's own recorded history, points, streak and insights
  byte-for-byte unchanged.
- **SC-004**: Every ranking position and points total matches an independent recomputation from the
  account's stored completion records, for all three periods, evaluated in the region's timezone.
- **SC-005**: For every participant, on every day, the leaderboard day they are ranked in falls on
  the same weekday as the day their own Today screen shows — verified across participants in at
  least three regions spanning at least 12 hours of offset, including a date on which the catalogue
  schedules day-specific tasks.
- **SC-006**: A modified client cannot change its own or any other participant's position, total or
  region, verified directly against the account service rather than through the app.
- **SC-007**: No participant appears in a ranking for a region other than their own, under any
  sequence of timezone changes.
- **SC-008**: With the account service unreachable, recording, Today, Week, Streak, History and
  Insights behave identically to a run with the service available.
- **SC-009**: A participant can locate their own row in a ranking of at least 10,000 participants
  without scrolling through the list.
- **SC-010**: A ranking for a completed period returns identical membership, totals and order on
  every retrieval, except for participants who have opted out.
- **SC-011**: Honor Board membership is determined solely by days engaged: two participants with the
  same days-engaged count and very different point totals both qualify or both do not.
- **SC-012**: The Honor Board reveals nothing about non-qualifying participants — not identity, not
  count, not distance from threshold — verified by inspecting everything the client can retrieve,
  not only what it displays.
- **SC-013**: No leaderboard or Honor Board string or colour appears on the forbidden list in
  `CLAUDE.md` Principle IX; no surface renders a low position in red or with distinguishing
  emphasis, verified by reading every introduced string and inspecting every introduced colour.
- **SC-014**: A first ranking is readable within 3 seconds of opening the leaderboard surface on a
  working connection, and an unavailable ranking resolves to its unavailable state within 10 seconds
  rather than hanging.

## Assumptions

- **Phase 7 is the foundation.** Accounts, passwordless sign-in, profile display name, the outbox and
  the sync engine all exist and are merged. Rankings read the completion records Phase 7 already
  synchronises; this increment adds no new client write path for record data.
- **Regions are administrator-defined and coarse.** They are timezone groupings, not social groups —
  a participant does not choose one, cannot see a list of them to join, and cannot invite anyone into
  one. This keeps regional scoping clear of the friends/cohorts boundary PLAN.md draws.
- **Region is derived from the device timezone**, reported through the account service and stored on
  the account, then re-evaluated when the device timezone changes. The client reports its timezone;
  it does not assert a region (FR-014).
- **A fallback region exists** so that no timezone can leave a participant unable to use the feature.
- **Points are the existing raw points.** No new scoring, weighting, handicap or normalisation is
  introduced. A period total is the sum of what the product already awards.
- **"Days engaged" reuses the existing streak-day definition** — a day on which at least one
  applicable task was completed — so the Honor Board and the streak agree about what a day of
  engagement is.
- **Anti-cheat is server-side recomputation only.** Behavioural detection, rate limiting beyond basic
  validation, and account-reputation systems are out of scope per PLAN.md.
- **The leaderboard adds a read-only remote surface.** It introduces no new authoring affordance and
  no new local source of truth (Principles IV and VI).
- **Notifications are Phase 9.** Nothing here sends a push, and FR-039 makes the absence of
  rank-change nudges a requirement rather than an omission to be filled in later.
- **Display names are not moderated in this increment.** A name reported as abusive is handled out of
  band; in-app reporting and moderation tooling are not in scope.

## Dependencies

- Phase 7 (`specs/007-identity-cloud-sync/`) — merged as `b235673`. Supplies identity, display name,
  the synced completion records rankings are computed from, and the account service itself.
- The existing points model from Phases 1–4, unchanged.
- The existing streak-day definition from Phase 4, reused by the Honor Board threshold.
- The Saturday-to-Friday week from Principle VII, now evaluated per region.

## Out of Scope

Carried from PLAN.md Phase 8, and restated here so the boundary is testable:

- Friends, followers, challenges, chat, private groups, user-chosen cohorts. Regions are an
  administrator-defined timezone grouping and are not a social feature.
- A global, cross-region ranking.
- Anti-cheat beyond server-side recomputation and basic validation.
- Any user-configurable ranking, threshold, region, scoring weight or period definition
  (Principle VI).
- A top-level leaderboard navigation destination (design decision, `CLAUDE.md`).
- Notifications of any kind, including rank-change alerts (Phase 9, and forbidden by FR-039).
- Moderation or reporting of display names.
