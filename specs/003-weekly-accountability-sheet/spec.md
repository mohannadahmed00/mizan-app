# Feature Specification: Weekly Accountability Sheet

**Feature Branch**: `spec/003-weekly-accountability-sheet`

**Created**: 2026-08-11

**Status**: Draft

**Input**: User description: "Read @docs/PLAN.md and create a specification for the Phase 3 — Weekly Accountability Sheet"

## Overview

Roadmap Phase 3 (`docs/PLAN.md`). Increment `002` gave the user one day at a time. This one gives
them the artifact they already recognise: a Saturday-to-Friday sheet, seven day cells, each showing
what was earned against what was available, and a week total against 500.

It is the first read model over the completion log, and it is the first time a past day is visible
at all. Two things follow from that, and they are the whole risk of this increment:

- **The sheet must account for days the app was never opened.** Increment `002` deliberately creates
  a plan only for the current date. A user who skips Tuesday has no Tuesday. On a weekly sheet an
  absent day is worse than a zero — it silently shrinks the denominator and makes the week look
  better than it was. This increment backfills those days.
- **The sheet must not become a way to edit the past.** Every cell is a read. The rule deciding
  which dates are writable already exists in one place from `002` and is not widened here.

**Note on spec granularity.** `docs/PLAN.md`'s suggested execution order splits Phase 3 into three
specs (week calculation, weekly scoring and backfill, week screen). This specification keeps them
together. A week calculator with nothing reading it is a layer, and Principle VIII forbids shipping
a layer as an increment. The three appear below as prioritised user stories instead.

**Note on backfill and "opened the app".** Increment `002` records that a later phase reading "the
user opened the app that date" may rely on that date's plan existing. Backfill breaks that reading:
after this increment a plan can exist for a date the user never saw. This increment therefore
requires each plan to record how it came into being, so Phase 4's streak rule is not quietly
handed a false signal. See FR-011 and the assumption below.

## Clarifications

### Session 2026-08-11

- Q: Mid-week, what is the sheet's weekly available total? → A: Both figures, shown separately. The
  primary reading is earned against what was available on the days that have elapsed — a number the
  user could actually have earned. The week's full target (500 for a normal week) is shown alongside
  it as context. Days that have not elapsed are projected from the current catalogue and get no
  plan.
- Q: Which catalogue version defines a backfilled date's tasks and points? → A: The version that was
  in effect on that date. This requires recording when each catalogue version became effective
  locally, which increment `002` does not yet store. The alternative — using whatever version is
  current at the moment of backfill — would silently re-score days under definitions that did not
  exist yet, which is the failure mode Principle III is written against.
- Q: On an existing install, what effective date does the catalogue version seeded before this
  increment get? → A: None — the earliest catalogue version the app has ever loaded is open-ended
  backwards. Any date resolves to it unless a later version supersedes it. Only versions loaded from
  this increment onward carry a real effective date. The backfill floor stays the record start
  (FR-012) and nothing else, so there is one lower bound in the system rather than two that can
  drift apart.
- Q: What is the acceptance target for opening a week that needs all seven days backfilled? → A:
  Blocking, within 300 ms. The sheet appears with final figures or not at all — no partial state, no
  spinner, no cell that changes under the user after it is on screen. Measured on a mid-range device
  from the moment the week is requested to the moment its figures are rendered, on a week requiring
  seven backfills.
- Q: If backfilling a week's missing days fails to write, what does the sheet show? → A: A week-level
  notice with a retry, and no figures for that week. An incomplete week is never presented as a valid
  one — that is the posture `002` set for an unavailable catalogue, and a week rendered with its
  unfillable days omitted would understate its own denominator, which is the failure User Story 2
  exists to prevent. No new cell state is introduced.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read the week (Priority: P1) 🎯 MVP

A user opens the weekly sheet and sees the seven days of the current week, Saturday through Friday,
each showing the points earned that day against the points that were available on it. Above them, the
week's own total. This is the paper sheet, rendered from what they actually recorded.

**Why this priority**: The weekly sheet is the instrument the product is named after. It is also the
first proof that the storage design from `002` is queryable — if a week aggregate is awkward to
compute, that is a Phase 2 problem discovered at the cheapest possible moment.

**Independent Test**: Seed a week of mixed activity, open the sheet, and check every per-day figure
and the week total by hand against the seeded records.

**Acceptance Scenarios**:

1. **Given** a week in which the user recorded on some days and not others, **When** the sheet is
   opened, **Then** seven day cells appear in Saturday-to-Friday order, each showing that date's
   earned points against that date's available points.
2. **Given** a fully elapsed week in which every applicable task was completed on every day, **When**
   the week total is read, **Then** it reads exactly 500 of 500.
2a. **Given** it is Tuesday of the current week, **When** the week's figures are read, **Then** the
   headline reading is the week's earned points against the points available on Saturday through
   Tuesday, and the week's target of 500 appears separately as context rather than as the
   denominator.
2b. **Given** it is Tuesday of the current week, **When** the sheet is displayed, **Then** Wednesday
   through Friday show the points that will be available on them, and none of those dates acquires a
   plan or any other record.
3. **Given** a week in which nothing was completed at all, **When** the week total is read, **Then**
   earned reads 0, available reads the correct figure for that week, and no cell, colour, or word
   presents this as a failure.
4. **Given** any week, **When** the per-day available figures are read, **Then** Saturday, Sunday,
   Tuesday and Wednesday read 69, Monday and Thursday read 74, and Friday reads 76.
5. **Given** any week, **When** the week's earned total is read, **Then** it equals the sum of the
   seven days' earned figures — no day is counted twice and none is omitted.
6. **Given** a date, **When** the week containing it is determined, **Then** a Saturday belongs to
   the week it begins and a Friday belongs to the week it ends.
7. **Given** a week that crosses the end of a month or the end of a year, **When** the sheet is
   opened, **Then** it still contains exactly seven consecutive dates in Saturday-to-Friday order.
8. **Given** each day of the week, **When** its cell is read, **Then** it carries that date's stored
   Hijri label, taken from the day's own record and not recomputed.

---

### User Story 2 - Skipped days still count (Priority: P1)

A user who does not open the app on Tuesday comes back on Thursday. Tuesday appears on the sheet as
0 out of 69 — a day that happened and was not recorded — rather than being absent from the week.

**Why this priority**: Equal to User Story 1, and inseparable from it. A sheet that omits skipped
days reports a week total the user did not earn, which is the exact class of dishonesty the whole
data model exists to prevent. Shipping the sheet without this would put a wrong number in front of
the user on day one.

**Independent Test**: With a fake clock, record activity on Saturday, advance to Thursday without
opening the app in between, open the sheet, and confirm Sunday through Wednesday each read 0 out of
their correct available totals and the week's available total is unreduced.

**Acceptance Scenarios**:

1. **Given** an elapsed date in the displayed week with no existing plan, **When** the week is
   opened, **Then** a plan is created for that date and it reads 0 out of that date's correct
   available total.
2. **Given** a backfilled date, **When** the week is opened again later, **Then** the same stored
   plan is read and nothing about it is recomputed.
3. **Given** a backfilled Monday, **When** its available total is read, **Then** it reads 74 — the
   voluntary fast is present because the schedule rule says so, not because anyone was there to see
   it.
4. **Given** a week containing dates earlier than the user's first recorded day, **When** the week is
   opened, **Then** those dates are not backfilled and are shown as outside the record rather than
   as days worth zero.
5. **Given** the current week partway through, **When** the sheet is opened, **Then** dates later
   than today are not backfilled and no plan is created for them.
6. **Given** a backfilled date, **When** anything asks whether the user opened the app that date,
   **Then** the answer is no — the plan's existence alone does not say the app was opened.
7. **Given** a date whose plan already exists because the user opened the app that day, **When**
   backfill runs over that week, **Then** that plan is left exactly as it was.
8. **Given** a date was skipped while one catalogue version was in effect and the catalogue has since
   been replaced, **When** that date is backfilled, **Then** its tasks and points come from the
   version that was in effect on that date, not the current one.
9. **Given** an install that already carries plans and a catalogue from `002`, **When** a skipped date
   predating this increment's update is backfilled, **Then** it resolves to that existing catalogue
   version and is filled — the absence of a recorded effective date never blocks a backfill.
10. **Given** an install that already carries plans from `002`, **When** anything asks whether the
    user was present on those dates, **Then** the answer is yes for every one of them.

---

### User Story 3 - Look into a day (Priority: P2)

Tapping a day cell opens that day as it was: the tasks that applied to it, which were completed, and
the points each carried. Nothing on the screen can change it.

**Why this priority**: It turns a grid of numbers into a record the user can actually inspect, and
it is the first honest test of the immutability promise from `002` — a past day rendered from its
own stored plan rather than from the live catalogue. It is P2 only because the sheet's totals are
correct and useful without it.

**Independent Test**: Open a recorded past day from the sheet, change the task catalogue's points and
schedule, reopen the same day, and confirm every task, point value, and total is unchanged.

**Acceptance Scenarios**:

1. **Given** a day cell for a recorded date, **When** it is opened, **Then** the tasks that applied
   to that date are shown grouped by section, with each task's recorded occurrences and the points
   it carried.
2. **Given** a past day summary is open, **When** the user looks for any way to complete, undo, add,
   remove, or reorder anything, **Then** none exists.
3. **Given** a past day summary is open, **When** the task catalogue's points and schedules change,
   **Then** reopening that day shows the original tasks, the original points, and the original
   totals.
4. **Given** a backfilled day with nothing recorded, **When** it is opened, **Then** it shows the
   tasks that applied and none completed, with no language implying fault.
5. **Given** today's cell, **When** it is opened from the sheet, **Then** it is shown by the same
   read-only summary as any other day — the sheet is not a second way to record.

---

### User Story 4 - Move between weeks (Priority: P3)

The user steps back to previous weeks and forward again, within the range the record covers.

**Why this priority**: The current week is the one the user is living in and is enough on its own.
Navigation is the first step toward history browsing, which is Phase 5 — this increment provides
only the week-by-week movement the sheet itself implies.

**Independent Test**: With several weeks of seeded records, step back to the earliest and forward to
the current week, checking that each week's figures are stable and that movement stops at both ends
without an error state.

**Acceptance Scenarios**:

1. **Given** the current week is shown, **When** the user moves back one week, **Then** the previous
   Saturday-to-Friday week is shown with its own figures.
2. **Given** the earliest week containing any record is shown, **When** the user attempts to move
   back further, **Then** the interface does not move and does not present this as an error.
3. **Given** the current week is shown, **When** the user attempts to move forward, **Then** the
   interface does not move to a future week.
4. **Given** the user has moved back several weeks, **When** they leave the sheet and return,
   **Then** the current week is shown again — position is not remembered.
5. **Given** any week is shown, **When** the same week is shown again, **Then** every figure is
   identical to what it was the first time.
6. **Given** the app is open as the clock passes local midnight into a new week, **When** the sheet
   next shows the current week, **Then** it shows the new week.

---

### Edge Cases

- The week is viewed on a Saturday, at the very first moment of the week — six days still ahead and
  one current day, none of them backfilled, and a headline denominator of just that Saturday's
  available points.
- The catalogue is replaced mid-week, then a day earlier in the same week is backfilled — that day
  is planned against the older version while the remaining days of the week project against the
  newer one, so the week target changes and no stored day does.
- The week is viewed on a Friday evening, the last day of the week — the week is complete and the
  next movement forward is unavailable.
- The user's very first launch happens mid-week — the days before it are outside the record and are
  not fabricated as zeros.
- The device clock is moved backward into a week already shown — existing plans are reused, never
  replaced.
- The device clock is moved forward past several weeks — opening the sheet backfills only the
  elapsed days of the week being viewed, not everything in between.
- A week is viewed while it contains a date whose plan was created but which carries no completions —
  it reads 0 out of its available total, exactly like a backfilled day, and the two are
  indistinguishable to the user.
- Backfill is interrupted partway through a week — reopening the week completes it, and no date ends
  up with two plans. Until it completes, the week shows a plain notice and a retry rather than the
  days that did get written.
- Storage is unwritable and every backfill in a week fails — the week reports that it could not be
  loaded, in language that blames the app rather than the user and says nothing about what was
  recorded on those dates.
- The same week is opened from two places at once — a date gets exactly one plan.
- A week spans a daylight-saving transition — it still contains exactly seven dates.
- Every day of a week is fully complete except one task on one day — the week reads 498 of 500 and
  nothing marks the shortfall as a failure.

## Requirements *(mandatory)*

### Functional Requirements

**Week identity and boundaries**

- **FR-001**: The system MUST treat a week as running Saturday through Friday, and MUST determine
  which week any date belongs to in exactly one place. No screen, query, or aggregate may hold a
  second opinion.
- **FR-002**: The system MUST be able to name a week — a stable identity derived from its dates —
  so the same week is recognised as the same week wherever it is referred to.
- **FR-003**: A week MUST always contain exactly seven consecutive dates, including weeks that cross
  a month boundary or a year boundary. A week spanning a daylight-saving transition MUST also
  contain seven dates — a guarantee that holds structurally, because a week is seven calendar dates
  and a calendar date carries no time-of-day offset to shift. Nothing needs to handle it, and
  nothing may introduce a representation that would.
- **FR-004**: The current date MUST be obtained from the single injected time source established in
  `002`, so week boundaries are testable by advancing a fake clock.

**Week aggregate**

- **FR-005**: For each of a week's seven dates the system MUST report the points earned on it and the
  points that were available on it.
- **FR-006**: A date's available points MUST come from that date's stored plan and MUST NOT be
  computed from the live catalogue.
- **FR-007**: A date's earned points MUST equal the sum of the points carried by that date's
  completions that have not been reversed.
- **FR-008**: The week's earned total MUST equal the sum of its seven days' earned figures, and the
  week's available total MUST equal the sum of its seven days' available figures.
- **FR-009**: The system MUST report two figures for a week's available points, and MUST NOT conflate
  them:
  - **Elapsed available** — the sum of the available points of the week's dates that have elapsed,
    including the current date. This is the denominator of the week's headline reading.
  - **Week target** — the sum across all seven dates, so a normal week reads 500.
- **FR-009a**: The week's headline proportion MUST be earned against elapsed available, so it never
  expresses days that have not yet happened as points the user failed to earn.
- **FR-009b**: The week target MUST be presented as context alongside the headline reading, clearly
  distinguishable from it, and MUST NOT be framed as a shortfall or a debt.
- **FR-009c**: For a week that has fully elapsed, elapsed available and week target MUST be equal.
- **FR-009d**: The available points of a date that has not yet elapsed MUST be projected from the
  catalogue version currently in effect. That projection is a calculation only — it MUST NOT create,
  persist, or freeze anything for that date, and MUST NOT be treated as a record of it.

**Backfilling unopened days**

- **FR-010**: When a week is displayed, the system MUST create a plan for every date in that week
  that has elapsed, has no plan already, and is not earlier than the record's start (FR-012). The
  plan MUST record the tasks that applied to that date, their point values, their occurrence limits,
  the total available, and that date's Hijri label — by the same rules `002` uses for the current
  date.
- **FR-010a**: A backfilled plan MUST be frozen on creation exactly as any other plan is. It MUST NOT
  be recomputed on any later viewing.
- **FR-010b**: Backfill MUST NOT alter, replace, or duplicate a plan that already exists for a date.
- **FR-010c**: Backfill MUST NOT create a plan for the current date or any later date. The current
  date's plan is created at launch by `002`; future dates have none.
- **FR-010d**: Backfill MUST create at most one plan per date regardless of how many times, or from
  how many places, a week is opened.
- **FR-011**: Every plan MUST record whether it was created because the app was open on that date or
  because it was backfilled afterwards. Anything later asking whether the user was present on a date
  MUST read this rather than inferring presence from the plan's existence.
- **FR-011a**: Backfill MUST NOT create any completion. A backfilled day has an available total and
  nothing earned.
- **FR-012**: The record MUST have a defined start — the earliest date for which a plan exists — and
  dates before it MUST NOT be backfilled. Such dates MUST be presented as outside the record, not as
  days on which nothing was done.
- **FR-013**: A backfilled plan MUST be built from the catalogue version that was in effect on the
  date being backfilled — not the version in effect at the moment of backfill.
- **FR-013a**: Given any date, exactly one catalogue version MUST be selectable as the one that
  applied to it, from the effective-from date each version already carries. This is a resolution
  rule, not new storage — `001` defined the effective-from date and `002` persists it.
- **FR-013b**: The earliest catalogue version the app has ever loaded MUST be open-ended backwards —
  every date resolves to it unless a later version supersedes that date. Only versions loaded from
  this increment onward carry a real effective date.
- **FR-013c**: Consequently no date inside the record may fail to resolve a catalogue version, and
  the floor on backfill MUST be the record start (FR-012) and nothing else. Version effectivity
  MUST NOT act as a second, independent lower bound.
- **FR-013d**: Recording a version's effective date MUST NOT alter any plan that already exists. It
  describes the catalogue, not the days.
- **FR-013e**: On an install that already carries plans created by `002`, those plans MUST be treated
  as created while the app was open on their date (FR-011). `002` creates a plan only for the date
  the app is launched on, so no pre-existing plan can be a backfill.

**The sheet**

- **FR-014**: The sheet MUST show the seven dates of one week in Saturday-to-Friday order, each with
  its civil date, its stored Hijri label, and its earned and available points.
- **FR-014a**: A week MUST be shown only once its figures are final. Any backfill that week requires
  MUST complete before its cells are rendered. The sheet MUST NOT show provisional figures, and no
  cell may change state after it is on screen as a result of backfill completing.
- **FR-014b**: If a week's required backfill cannot be written, the sheet MUST show no figures for
  that week and MUST say plainly that the week could not be loaded, offering a retry. It MUST NOT
  render the week with the unfillable dates omitted, and MUST NOT render a total computed over an
  incomplete set of days.
- **FR-014c**: That message MUST attribute the failure to the app, never to the user, and MUST NOT
  imply anything about what was or was not recorded on the affected dates.
- **FR-014d**: A retry that succeeds MUST render the complete week. Whatever plans were written
  before the failure MUST remain valid and MUST NOT be created a second time.
- **FR-015**: Each day cell MUST distinguish a day with nothing recorded, a day partly recorded, and
  a fully recorded day. A day is fully recorded when its earned points equal its available points,
  partly recorded when earned is above zero and below available, and shows nothing recorded when
  earned is zero.
- **FR-016**: No day state may be expressed as a failure — no red, no cross, no penalty, no
  negative figure, and no language or imagery implying fault for a day with nothing recorded. A day
  outside the record MUST be visually distinct from a day worth zero, and neither may read as a
  mistake.
- **FR-017**: The sheet MUST show the week's earned points against its elapsed available points as
  the headline reading, with the week target shown alongside as context (FR-009, FR-009b).
- **FR-017a**: A date that has not yet elapsed MUST be visually distinct from a date that elapsed
  with nothing recorded. A day still ahead of the user is not a day they missed.
- **FR-018**: The sheet MUST allow moving to the previous week and to the following week, bounded by
  the week containing the record's start and the week containing the current date. At either bound
  the unavailable movement MUST simply be unavailable, not an error.
- **FR-019**: The sheet MUST return to the current week when it is next opened. The week being viewed
  MUST NOT be persisted.
- **FR-020**: While the app is open, crossing local midnight into a new week MUST move the sheet to
  the new week.
- **FR-021**: Arabic task content shown anywhere in this feature MUST render in an Arabic-appropriate
  typeface with correct bidirectional handling, and MUST NOT reflow the surrounding layout.

**The day summary**

- **FR-022**: Opening a day from the sheet MUST show that date's tasks grouped by section, in the
  catalogue order that applied to that date, with each task's recorded occurrences and the points it
  carried.
- **FR-023**: The day summary MUST be derived entirely from that date's stored plan and its
  completions, never from the live catalogue.
- **FR-024**: The day summary MUST offer no way to record, undo, add, remove, reorder, or otherwise
  change anything — for any date, including the current one.
- **FR-025**: This feature MUST NOT widen the rule established in `002` deciding which dates are
  writable. That rule remains in exactly one place and this feature only reads.

**Offline**

- **FR-026**: Viewing a week, backfilling its elapsed days, and opening a day summary MUST all work
  with no network, on a fresh install.

### Key Entities

- **Week**: seven consecutive dates running Saturday through Friday, identified by a stable key
  derived from those dates.
- **Weekly Score**: the week's earned points, its elapsed available points, its week target, and the
  proportion of earned to elapsed available — an aggregate over the week's seven days, stored
  nowhere. For a fully elapsed week, elapsed available and week target are the same number.
- **Catalogue Version Effectivity**: the date from which a loaded catalogue version applied locally.
  What makes "the catalogue as it stood on that date" answerable for a date nobody was there for.
  The earliest version carries none and applies open-ended backwards, so effectivity only ever
  separates one version from the next.
- **Day Summary**: one date's read-only view — its plan, its completions, and its resulting Daily
  Score. A projection, not a new record.
- **Day Plan** *(extended)*: as defined in `docs/GLOSSARY.md` and built in `002`, now additionally
  carrying how it came into being — created while the app was open on that date, or backfilled
  afterwards.
- **Record Start**: the earliest date for which a plan exists. The floor for backfill and for week
  navigation; dates before it are outside the record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A fully elapsed week in which every applicable task was completed on all seven days
  reads exactly 500 of 500, and its elapsed available and week target are both 500.
- **SC-001a**: On each day of a week in progress, the headline denominator equals the sum of the
  available points of the days elapsed so far — 69, 138, 212, 281, 350, 424, 500 from Saturday
  through Friday for a week with no catalogue change — while the week target reads 500 throughout.
- **SC-001b**: Displaying a week in progress creates no plan and no record for any date after the
  current one, verified by comparing stored records before and after.
- **SC-002**: Per-day available figures read 69 on Saturday, Sunday, Tuesday and Wednesday, 74 on
  Monday and Thursday, and 76 on Friday — verified across a week that crosses both a month boundary
  and a year boundary.
- **SC-003**: For any seeded week, the week's earned total equals the hand-computed sum of its seven
  days' earned figures, and the same holds for available.
- **SC-004**: A date is assigned to exactly one week, verified at both edges — the Saturday that
  opens a week and the Friday that closes it — and for the dates immediately either side of each.
- **SC-005**: With a fake clock advanced across days on which the app was never launched, opening the
  affected week produces a plan for each elapsed skipped date reading 0 out of its correct available
  total, and the week's available total is the same as if the user had opened the app every day.
- **SC-006**: Opening the same week again produces exactly one plan per date and identical figures
  every time. Two openings prove this; more add nothing.
- **SC-007**: Backfill never produces a plan for a date at or after the current date, nor for any
  date before the record's start.
- **SC-008**: After backfill, every plan reports how it came into being: plans for dates the app was
  actually opened on read as opened, and backfilled plans read as backfilled. No presence *query*
  exists in this feature — Phase 4 builds the streak rule that consumes this fact — so what is
  verified here is the stored fact itself, which is the thing that rule will read.
- **SC-009**: Changing the task catalogue's point values and schedule rules leaves every previously
  stored day — opened or backfilled — reporting its original tasks and totals, on both the sheet and
  the day summary.
- **SC-009a**: A date skipped under one catalogue version and backfilled after a later version is
  loaded reports the earlier version's tasks and available total, while the current date reports the
  later version's.
- **SC-009b**: Upgrading an install that already holds plans and a catalogue from `002` leaves every
  existing plan reporting its original figures, marks every one of them as a date the user was
  present for, and backfills skipped dates predating the upgrade — no date inside the record is
  refused for want of an effective version.
- **SC-010**: No sequence of interactions available from the sheet or the day summary changes any
  stored completion or plan, verified by comparing the stored records before and after exercising
  every control on both screens.
- **SC-011**: Zero interface elements on either screen express a negative quantity, a penalty, or
  fault — verified by reviewing every string, colour, and state the two screens can show.
- **SC-012**: The sheet and the day summary function identically with the network permanently
  unavailable. This holds structurally rather than by test — the application has no network surface
  at all, so the criterion is verified by the fresh-install airplane-mode smoke check plus the
  absence of any network dependency. An automated test here would assert nothing.
- **SC-013**: Opening a week whose seven days all require backfilling renders its final figures
  within 300 ms on a mid-range device, measured from the moment the week is requested. The same
  holds for a week needing no backfill, against a store seeded with a year of records.
- **SC-013a**: No cell on the sheet changes its figures or its state after the week is on screen as a
  result of backfill work — verified by capturing the rendered week at first paint and again once
  all writes have settled, and finding them identical.
- **SC-013b**: With writes made to fail, opening a week that needs backfilling shows no figures and a
  retry; restoring writes and retrying renders the complete week with correct totals, and every plan
  written before the failure is reused rather than duplicated.

## Assumptions

- **Backfill is triggered by viewing a week, not by launching the app.** There is no sweep over all
  elapsed history at startup — that would put unbounded work on the launch path for no visible
  benefit. A week's missing days are filled when that week is looked at.
- **Backfill applies to any week the user views, not only the current one.** The same rule, floored
  at the record's start and stopping before the current date, holds wherever the user navigates. It
  is one rule so that two weeks never disagree about whether a date exists.
- **Dates before the record's start are outside the record, not zeros.** Fabricating plans for dates
  preceding the first launch would invent an accountability history the user never had, and would
  make the very first week look like a failure — which Principle IX forbids. Full empty-state
  handling for pre-install dates is Phase 5; this increment only refuses to invent them.
- **Backfilled plans are distinguishable from opened ones.** `002` recorded that plan existence could
  stand for "the app was opened that date". This increment makes that untrue, so the distinction is
  stored explicitly rather than left for Phase 4 to discover. The user never sees the difference — a
  day with nothing recorded reads the same either way.
- **The current week is read against what has happened, not against what is coming.** The headline
  denominator covers only elapsed days, so a Sunday morning does not read as 10% of a week. The 500
  target still appears, because it is the sheet's own number and the user knows it — it is context,
  not the measure. Principle IX bites here: the same two numbers arranged the other way round make
  every week look like a failure until Friday night.
- **Catalogue version effective dates already exist.** `001` defined `effectiveFrom` on every
  catalogue version and validated its ordering; `002` persists it and already resolves the version
  effective on a date. This increment adds no storage for it — it changes one rule: the earliest
  version currently resolves to nothing for dates before its effective-from, and must instead apply
  open-ended backwards. Today there is exactly one version, so behaviour is currently
  indistinguishable from using the current version; the point is that it stays correct the first
  time that stops being true.
- **The upgrade from `002` invents nothing.** Both new facts have a correct answer that is already
  known rather than guessed: every existing plan was created by opening the app, because that is the
  only way `002` creates one; and the existing catalogue version applies open-ended backwards, so
  nothing has to be dated retroactively. This is what keeps the migration additive and keeps a
  skipped day from before the upgrade backfillable.
- **A failed week is reported, not approximated.** `002` established that an incomplete thing is
  never presented as a valid one — an empty task list must not pass for a completed day. The weekly
  equivalent is that a week missing days it should have is not shown at all, because a total computed
  over five days of a seven-day week is wrong in the one direction that flatters the user. Retry is
  the whole recovery path; there is no partial mode to design or test.
- **A week renders whole or not at all.** Backfill is bounded — at most seven plans per week viewed —
  so blocking on it is cheap, and it buys away a transient state the sheet would otherwise need. A
  cell that changes from "outside the record" to "0 of 69" under the user's eyes is a Principle IX
  problem disguised as a loading state. The 300 ms budget is also the first honest measurement of
  whether `002`'s storage design is queryable, which is one of the things this increment exists to
  find out.
- **Week aggregates are computed, not stored.** `docs/PLAN.md` permits a cached day summary only if a
  measurement shows the aggregate query is slow. No measurement exists, and Principle VIII forbids
  the cache until one does. Indexed dates on the existing tables are expected to be enough.
- **The sheet is read-only, including for today.** Recording stays on the Today screen. Two screens
  that can both write to the same day is exactly the disagreement `002`'s single writability rule
  exists to prevent.
- **The week being viewed is not persisted.** It is derived from the current date on each opening, so
  it survives process death and week rollover with no stored state to reconcile later.
- **Catalogue content is still the placeholder from `002`.** Structure, totals, and schedule rules are
  correct; the Arabic text is not final. Nothing in this increment depends on the text.
- **Streaks are not part of this increment.** The sheet shows a week's figures; consistency and
  streaks are Phase 4 and read the same records.

## Dependencies

Increment `002` — the task catalogue in storage, Day Plan materialisation, the completion log, daily
scoring, the injected time source, and the single rule deciding which dates are writable. This
feature reads all of them. `002` is built and merged, so this increment extends working code rather
than a design. It changes what `002` established in exactly two places:

- **One new stored fact** — each Day Plan records how it came into being, opened or backfilled
  (FR-011). Additive; it changes no figure any stored day already reports.
- **One changed rule** — resolving the catalogue version effective on a date must treat the earliest
  version as open-ended backwards (FR-013b) rather than returning nothing for dates before its
  effective-from. No storage changes; `effectiveFrom` already exists on every version from `001`.

Everything else this feature needs, `002` already provides: `ensurePlanFor` accepts any date and was
written to be Phase 3's backfill entry point, the day-writability rule is already isolated, and the
injected clock is already the only source of the current date.

## Out of Scope

Editing or completing any past day, retroactive completion of any kind, streaks and consistency,
charts and insights, monthly views, history browsing beyond week-by-week movement, export, sharing,
accounts, sync, notifications, achievements, leaderboards, and any task creation, editing,
reordering, or repricing.
