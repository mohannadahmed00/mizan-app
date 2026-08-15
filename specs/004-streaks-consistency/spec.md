# Feature Specification: Streaks & Consistency

**Feature Branch**: `spec/004-streaks-consistency`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "Read @docs/PLAN.md and create a specification for the Phase 4 — Streaks & Consistency"

## Overview

Roadmap Phase 4 (`docs/PLAN.md`), and the last increment of the MVP. Increment `002` gave the user a
day. Increment `003` gave them a week. This one gives them the product's actual thesis: **consistency
over perfection**. A day counts because something was done on it, not because everything was.

It is the cheapest increment in the roadmap and the one most easily got wrong, for two reasons:

- **It adds no data.** A streak is a fold over records that already exist. Nothing new is written on
  the completion path, no new source of truth appears, and if the streak is ever cached the cache
  must be reconstructible from the log rather than authoritative. An increment that invents a stored
  streak counter has invented a number that can disagree with the record.
- **It is where Principle IX is easiest to violate.** Every convention in this genre — the broken
  chain, the greyed-out box, the counter that resets to zero each morning — expresses a missed day as
  a loss. This feature has a running counter, an all-time figure, and a moment where the count ends.
  All three are shame-shaped by default, and all three have to be built the other way round.

**Note on spec granularity.** `docs/PLAN.md`'s suggested execution order splits Phase 4 into three
specs (consistency day rule, streak calculation, streak UI). This specification keeps them together,
for the same reason `003` merged its three: a consistency rule with nothing reading it is a layer,
and Principle VIII forbids shipping a layer as an increment. The three appear below as prioritised
user stories instead.

**Note on what "opened the app" means.** `docs/PLAN.md` states the rule as "the user opens the app
and completes at least one applicable task". Those are one condition, not two: `002`'s write rule
permits a completion only on the current date, so a completion credited to a date is already proof
the app was open on it. The criterion below is therefore the completion alone. `003`'s Plan Origin
corroborates it and is not consulted. This stops being true the moment Phase 5 allows retroactive
completion — see the assumption below, which is the one thing this increment hands forward.

## Clarifications

### Session 2026-08-15

- Q: Before today's first completion, what does the current streak read? → A: It stays alive, and
  the display marks today as not yet counted. The run is the consecutive Consistency Days ending on
  **today or yesterday**. A user who completed something every day for 38 days sees 38 all through
  the 39th day, marked pending, and 39 once they record. The alternative — requiring today — resets
  the visible count to zero every midnight and shows a loss the user has not had, which Principle IX
  forbids. The pending mark is also what the at-risk state reads.
- Q: Timezone travel and manual clock changes — what is the policy? → A: The stored facts are fixed
  and only the present moves. The streak is a fold over the dates completions are credited to, and
  those dates never change; a timezone or clock change alters only which date is "today". A jump
  forward past one or more days can end a run, exactly as living through those days would; a jump
  backward cannot invent one. No change is detected, nothing about the device's zone is stored, and
  no leniency is granted. Detection would require new state and a rule that can be gamed by setting
  the clock.
- Q: What triggers the streak-at-risk state? → A: A fixed local time — from 20:00 until local
  midnight, when a live streak has nothing credited to today. It is one rule, testable by advancing
  the fake clock, and it does not couple streak logic to the catalogue's section order the way a
  content-driven trigger ("the Isha block is showing") would.
- Q: Where is the streak visible on a screen that steps through one prayer block at a time? → A:
  Persistent at the top of Today, unchanged as the user moves between blocks, and still shown when
  the catalogue is unavailable — the figures are read from the record, not the catalogue, so they
  remain true there. It is omitted only while Today is still loading, which is the absence of a
  figure rather than the absence of a run.
- Q: Does Today wait for the streak figures before painting? → A: No. Today shows the day's tasks as
  fast as it does now; the streak element reserves its position and resolves once, never displaying a
  provisional or zero figure first. Putting a record-wide read on the critical path of the core loop
  is what Principle IV is written against, and a figure that arrives is not a figure that changed.
- Q: After a run ends, how long is the break notice shown? → A: While the last active date falls
  within the seven days ending today. After that the streak settles into the same plain start state a
  user with no records sees. It is derived from the record, so nothing is stored to remember it has
  been shown — and a user who lapses for a month is not greeted thirty times by the run they lost.
- Q: If the record cannot be read to compute the streak, what is shown? → A: A plain notice in the
  streak element's place, with a retry, attributing the failure to the app. Tasks, recording and undo
  are unaffected. A streak that silently vanishes reads as a streak that was lost; and letting the
  whole screen fail would allow an ornament to take down the core loop.
- Q: Is the all-time figure called "longest streak" or "longest streak"? → A: **Longest Streak** is the
  canonical term — in this specification, in test names, and in type names — matching `docs/PLAN.md`
  and the glossary's own rule that a term means exactly one thing everywhere. "Best" is permitted as
  on-screen copy only. `docs/GLOSSARY.md` also gains Longest Streak and Streak Break, which
  `docs/PLAN.md` lists as concepts this phase introduces and which the glossary does not yet define.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the run (Priority: P1) 🎯 MVP

A user opens the app and sees how many days in a row they have recorded something, alongside the
longest run they have ever had. The figures come from what they actually recorded — nothing is
stored to produce them.

**Why this priority**: This is the feature. Everything else in this increment is presentation around
these two numbers, and the numbers are useful the moment they are correct.

**Independent Test**: Seed several weeks of records with known gaps, open the app, and check the
current and longest figures by hand against the seeded dates.

**Acceptance Scenarios**:

1. **Given** completions recorded on each of the last five consecutive dates including today, **When**
   the streak is read, **Then** the current streak reads 5.
2. **Given** completions on each of the last five dates ending yesterday and nothing yet today,
   **When** the streak is read, **Then** the current streak still reads 5 and today is marked as not
   yet counted.
3. **Given** the state above, **When** the user completes their first task today, **Then** the current
   streak reads 6 and today is no longer marked pending — with no other figure changing.
4. **Given** a record whose most recent Consistency Day is the day before yesterday, **When** the
   streak is read, **Then** the current streak reads 0.
5. **Given** a record containing an earlier run of 12 days and a current run of 3, **When** the
   figures are read, **Then** the current streak reads 3 and the longest streak reads 12.
6. **Given** a record whose current run is also its longest, **When** the figures are read, **Then**
   both read the same number and nothing presents the user as behind their own record.
7. **Given** a user who has never completed anything, **When** the streak is read, **Then** it reads
   0 and is presented as a beginning rather than as a loss.
8. **Given** any streak is on screen, **When** the app is killed and relaunched, **Then** the same
   figures appear, recomputed from the record with nothing restored from a stored counter.
9. **Given** the streak is on screen, **When** the streak is computed, **Then** no plan, completion,
   or other record is created, altered, or removed as a result.

---

### User Story 2 - A day counts once, for the right reason (Priority: P1)

A day the user recorded anything on counts, once — whether they completed one task or all of them.
A day they recorded nothing on does not, however present they were.

**Why this priority**: Inseparable from User Story 1. A streak built on the wrong criterion is a
wrong number in front of the user on day one, and this rule is the entire product thesis compressed
into one sentence.

**Independent Test**: Seed a single date with one completion, then with forty, then with one that is
undone, and confirm the day counts once, once, and not at all.

**Acceptance Scenarios**:

1. **Given** a date with exactly one completion, **When** consistency is evaluated, **Then** the date
   counts.
2. **Given** a date on which every applicable task was completed, including multi-occurrence tasks at
   their limit, **When** consistency is evaluated, **Then** the date counts exactly once and
   contributes exactly one day to the run.
3. **Given** a date whose only completion has been undone, **When** consistency is evaluated, **Then**
   the date does not count.
4. **Given** today counted and the user undoes their only completion, **When** the streak is read
   again, **Then** today reverts to not yet counted, the current streak returns to what it was before,
   and no language frames this as a loss.
5. **Given** an elapsed date with a plan but no completions, **When** consistency is evaluated,
   **Then** the date does not count and the run is broken there.
6. **Given** a date whose plan was backfilled by `003` for a week the user never opened, **When**
   consistency is evaluated, **Then** the date does not count — a plan's existence is never evidence
   of activity.
7. **Given** a run of consecutive Consistency Days reaching back to the record's start, **When** the
   current streak is read, **Then** it counts every one of them and ends there without the record's
   start reading as a break.
8. **Given** a single elapsed date with nothing recorded between two runs, **When** the current streak
   is read, **Then** it counts only the later run.

---

### User Story 3 - What the last week looked like, and what happens when a run ends (Priority: P2)

A compact indicator shows which of the recent days the user recorded on. When a run ends, the app
says so in a way that keeps what was achieved and points at the next step.

**Why this priority**: The counter alone is a number without context — the indicator is what makes
consistency legible at a glance, and the break moment is the single point in the product where
Principle IX is most likely to be violated. P2 rather than P1 because the figures in User Story 1 are
correct and useful without either.

**Independent Test**: Seed a recent stretch with a mix of recorded and unrecorded days, one of them
before the record's start, and check every indicator position against the seeded dates; then seed a
broken run and review the resulting copy.

**Acceptance Scenarios**:

1. **Given** the last seven dates including today, **When** the indicator is read, **Then** each
   position shows whether that date is a Consistency Day, in date order, with today at the end.
2. **Given** a date in that stretch on which nothing was recorded, **When** its position is read,
   **Then** it is shown as simply not recorded — with no cross, no red, no broken imagery, and no
   word implying fault.
3. **Given** a date in that stretch earlier than the record's start, **When** its position is read,
   **Then** it is visually distinct from a date worth nothing and reads as outside the record.
4. **Given** today has nothing recorded yet, **When** today's position is read, **Then** it is
   distinct from an elapsed date with nothing recorded — a day still in progress is not a day missed.
5. **Given** a run of 38 days that ended within the past seven days, **When** the user opens the app,
   **Then** the longest streak still reads 38, it is presented as standing rather than lost, and the
   copy offers the next step rather than describing what went wrong.
5a. **Given** a run that ended more than seven days ago with nothing recorded since, **When** the user
   opens the app, **Then** the break notice is gone and the streak reads as a plain starting point.
   The longest streak is still there; nothing refers to the run having ended.
6. **Given** the user has never recorded anything, **When** the indicator is read, **Then** it shows
   an unstarted record rather than seven consecutive failures.

---

### User Story 4 - A nudge before midnight (Priority: P3)

Late in the evening, a user with a live run who has not recorded anything today sees that today is
still open. It is an offer, not a warning.

**Why this priority**: The smallest slice of the four and the one whose absence costs least — the
streak is complete and correct without it, and Phase 9 is where re-engagement properly belongs. It is
in this increment because it needs no new data and because the state it reads already exists.

**Independent Test**: With a fake clock, advance to 19:59 and then 20:00 on a day with a live run and
nothing recorded, and confirm the state appears exactly once and at the right moment.

**Acceptance Scenarios**:

1. **Given** a current streak of at least 1 and nothing credited to today, **When** the local time is
   20:00 or later and before local midnight, **Then** the at-risk state is shown.
2. **Given** the same conditions, **When** the local time is 19:59, **Then** the at-risk state is not
   shown.
3. **Given** the at-risk state is showing, **When** the user completes any task, **Then** it clears
   immediately and the current streak increases.
4. **Given** a current streak of 0, **When** the local time passes 20:00 with nothing recorded,
   **Then** no at-risk state is shown — there is nothing at risk, and a user without a run is not
   told they are failing.
5. **Given** the at-risk state is showing, **When** the copy is read, **Then** it names what is still
   possible and contains no countdown framed as a penalty, no warning colour, and no language of
   losing.
6. **Given** the at-risk state is showing, **When** local midnight passes with the app open, **Then**
   it clears, the ended run is reflected in the figures, and the new day begins pending rather than
   at risk.

---

### Edge Cases

- The user completes their only task at 23:59 — the day counts, and the at-risk state clears with a
  minute to spare.
- The user undoes their only completion at 23:50 — the day stops counting and the at-risk state
  returns, without any language implying they have undone something they should not have.
- Local time is exactly 20:00 — at risk. The boundary is inclusive, so there is one rule and no
  minute in which the state is undefined.
- The app is open across local midnight with today recorded — the run continues, the new day begins
  pending, and the count does not visibly drop at any point.
- The app is open across local midnight with today not recorded — the run ends, and what ends is
  described without fault.
- The device timezone changes such that "today" moves forward by a day, skipping one — that skipped
  date has no completions and the run ends there, exactly as it would have if the user had lived
  through it.
- The device clock is moved backwards past dates that already carry completions — those dates are
  now in the future and are ignored, so the current streak may read lower until the clock is
  restored. Nothing recorded is altered or removed, and restoring the clock restores the figure.
- The device clock is moved forward by a year and back again — no run is created, and the record is
  unchanged.
- A week spans a daylight-saving transition — the dates are unaffected and so is the run.
- The user's very first day: record start is today, the current streak reads 1 after the first
  completion, and the longest streak reads 1.
- The record contains a run longer than the recent-activity indicator can show — the indicator shows
  its window, and the counter shows the run.
- The record covers years and every date is a Consistency Day — the figures are still computed on
  demand within the screen's budget.
- The catalogue is unavailable so today has no plan — the streak element is still shown and still
  reports what the record already contains, because it reads completions rather than the catalogue.
- Today is still loading — the streak element is absent rather than showing a figure it does not yet
  have, and it arrives without displacing anything already on screen.
- The user steps forward and back through the day's prayer blocks — the streak element stays where it
  is and reads the same throughout.
- The last active date is exactly seven days ago — the break notice is still shown. One day later it
  is not, and its disappearance is not announced or explained.
- Reads begin failing while the streak is on screen — the figures are replaced by the notice, never
  by 0, and the day's tasks remain recordable throughout.

## Requirements *(mandatory)*

### Consistency

- **FR-001**: A date MUST count as a Consistency Day when at least one completion credited to it has
  not been reversed, and MUST NOT count otherwise.
- **FR-002**: Consistency MUST be a yes or no. A date with forty completions counts exactly as much
  as a date with one, and contributes exactly one day to any run.
- **FR-003**: Reversed completions MUST NOT count. Undoing the last live completion credited to a
  date MUST stop that date counting, with immediate effect wherever the figures are shown.
- **FR-004**: The existence of a plan for a date MUST NOT make it a Consistency Day, whether that
  plan was created by opening the app on that date or backfilled afterwards.
- **FR-005**: This feature MUST NOT consult the task catalogue to decide consistency. A date's
  Consistency is a fact about what was recorded, not about what applied.

### The streak

- **FR-006**: The **current streak** MUST be the number of consecutive Consistency Days in the run
  ending on today or on yesterday. If neither today nor yesterday is a Consistency Day, the current
  streak MUST be 0.
- **FR-006a**: The system MUST report separately whether today is already counted in the current
  streak. Today's absence MUST NOT reduce the figure before today has elapsed.
- **FR-007**: The **longest streak** MUST be the longest run of consecutive Consistency Days anywhere
  in the record, including the current run when it is the longest. *Longest Streak* is the canonical
  name for this figure everywhere — in this specification, in test names, and in type names.
  On-screen copy may call it the **best** streak.
- **FR-007a**: The longest streak MUST never decrease as a consequence of a run ending. It decreases
  only if the records it was computed from are themselves reversed.
- **FR-008**: The system MUST report the **last active date** — the most recent Consistency Day — or
  that there is none.
- **FR-009**: A run MUST be broken by an elapsed date, strictly earlier than today, that is not a
  Consistency Day. Today MUST NOT break a run before it has elapsed.
- **FR-010**: A run reaching the record's start MUST end there without that being treated as a break.
  Dates before the record's start MUST NOT be evaluated for consistency at all.
- **FR-011**: Dates later than today MUST be excluded from every figure, so a clock moved backwards
  cannot report days that have not happened.
- **FR-012**: Every figure MUST be derived from the recorded Consistency Days on demand. No streak
  value may be stored as a source of truth, and no figure may be read from anywhere the record cannot
  reproduce.
- **FR-013**: Computing or displaying any streak figure MUST NOT create, alter, or remove a plan, a
  completion, or any other record. This feature adds nothing to the completion path.

### Time

- **FR-014**: The current date and the current local time MUST come from the single injected time
  source established in `002`, so every rule here is testable by advancing a fake clock.
- **FR-015**: The dates completions are credited to MUST be read exactly as stored. A change of
  device timezone or system clock MUST NOT alter, re-credit, or re-evaluate any recorded date — it
  changes only which date is the current one.
- **FR-016**: No leniency, grace period, freeze, repair, or restoration MUST be granted for any
  reason, including a detected timezone or clock change. There is one rule and it applies to every
  day equally.
- **FR-017**: While the app is open, crossing local midnight MUST update every figure on screen to
  the new current date.

### On the Today screen

- **FR-018**: The Today screen MUST show the current streak and the longest streak.
- **FR-018a**: The streak element MUST hold one fixed position on the Today screen and MUST remain
  visible, unchanged, as the user steps between prayer blocks. Its content MUST NOT depend on which
  block is showing.
- **FR-018b**: The streak element MUST be shown when the catalogue is unavailable and the day
  therefore has no plan. It MUST be omitted only while the Today screen is still loading.
- **FR-018c**: The Today screen MUST NOT wait for the streak figures before showing the day's tasks.
  The streak element MUST reserve its position and resolve once, and MUST NOT display a provisional,
  placeholder, or zero figure before the real one. Recording and undoing MUST stay available while
  the figures are still resolving.
- **FR-019**: The Today screen MUST show whether today is already counted, distinctly from the count
  itself, so a pending day is never mistaken for a lost one.
- **FR-020**: The Today screen MUST show a compact indicator of the seven most recent dates up to and
  including today, each showing whether it is a Consistency Day.
- **FR-020a**: The indicator MUST distinguish three things: a date that counted, an elapsed date that
  did not, and today before it has counted. A date earlier than the record's start MUST be a fourth,
  visually distinct state and MUST NOT read as a date that did not count.
- **FR-021**: No streak element may express a missed day, an ended run, or a zero streak as a failure
  — no red, no cross, no broken or emptied imagery, no penalty, no negative figure, and no language
  attributing fault. A streak that ends MUST be reported with the longest streak intact and with the
  next step named.
- **FR-021a**: That break notice MUST be shown while the current streak is 0, a longest streak
  exists, and the last active date falls within the seven days ending today. Outside that window the
  streak MUST read as the same plain start state a user with no records sees. Whether to show it MUST
  be derived from the record; nothing may be stored to remember that it has been shown.
- **FR-021b**: If the record cannot be read, the streak element MUST show a plain notice in place of
  the figures, attribute the failure to the app, and offer a retry. It MUST NOT show 0, MUST NOT
  disappear without explanation, and MUST NOT prevent the rest of the Today screen from working —
  viewing tasks, recording, and undoing MUST all be unaffected.
- **FR-022**: A user with no records at all MUST see an unstarted record rather than a sequence of
  failures.
- **FR-023**: Streak figures MUST update immediately when a completion is recorded or undone on the
  same screen, without the user leaving or reloading it.
- **FR-024**: Arabic content shown alongside any streak element MUST render in an Arabic-appropriate
  typeface with correct bidirectional handling, and MUST NOT reflow the surrounding layout. The
  streak element itself carries no Arabic content — it holds figures and interface copy — so this
  requirement is satisfied by `002`'s existing handling on the Today screen and adds no work of its
  own. What it does require is that the element MUST NOT be placed in a way that disturbs it.

### At risk

- **FR-025**: The at-risk state MUST be shown when, and only when, all of the following hold: the
  current streak is at least 1, today is not yet a Consistency Day, and the local time is at or after
  20:00 and before local midnight.
- **FR-026**: The at-risk state MUST clear as soon as any completion is credited to today, and MUST
  clear when local midnight passes.
- **FR-027**: At-risk copy MUST state what is still possible and MUST NOT threaten, count down toward
  a penalty, use a warning colour, or describe an outcome as a loss.

### Offline

- **FR-028**: Every figure and state in this feature MUST be produced with no network, on a fresh
  install.

### Key Entities

- **Consistency Day**: a date on which at least one live completion is credited. A yes or no, derived
  and stored nowhere.
- **Streak**: a run of consecutive Consistency Days. The current streak is the run ending on today or
  yesterday; the longest streak is the longest run in the record.
- **Streak Break**: the point at which a run ends — an elapsed date, earlier than today, that is not
  a Consistency Day. A boundary read out of the record, never a stored event.
- **Streak Summary**: the current streak, the longest streak, the last active date, whether today is
  already counted, whether the run is at risk, whether the break notice applies, and the recent
  activity window — an aggregate over the record, held nowhere.
- **Recent Activity Window**: the seven most recent dates up to and including today, each carrying
  whether it counted, whether it has elapsed, and whether it precedes the record's start.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For any seeded record, the current streak equals the hand-counted run of consecutive
  dates carrying at least one live completion, ending on today or yesterday — verified across a run
  ending today, a run ending yesterday, a run ending two days ago (current streak 0), and an empty
  record (current streak 0).
- **SC-002**: A date carrying forty completions and a date carrying one contribute the same single day
  to the run, verified by comparing two otherwise identical records.
- **SC-003**: Undoing the only live completion credited to a date removes that date from every figure,
  and re-recording restores them to exactly what they were.
- **SC-004**: A single elapsed date with no live completion between two runs leaves the current streak
  equal to the later run alone, verified for gaps of one, two, and seven days.
- **SC-005**: A date whose plan was backfilled contributes nothing to any figure, verified on a record
  in which every date in a week was backfilled by `003` and none carries a completion.
- **SC-006**: The longest streak equals the longest hand-counted run in the record, and does not decrease
  when a later run ends — verified by seeding a 12-day run, ending it, and starting a 3-day run.
- **SC-007**: A run reaching the record's start reports its full length, and no date before the
  record's start is evaluated — verified by comparing figures against a record extended backwards
  with dates that carry nothing.
- **SC-008**: Figures survive process death: killing and relaunching the app produces identical
  numbers, with no stored streak value involved in producing them.
- **SC-009**: Displaying any streak figure changes no stored record, verified by comparing every
  stored plan and completion before and after opening the screen, recording, undoing, and crossing
  midnight.
- **SC-009a**: Changing the task catalogue's point values and schedule rules leaves every streak
  figure identical. A figure that moved would mean the streak was reading the catalogue rather than
  the record.
- **SC-010**: With a fake clock, advancing the timezone such that today moves forward by two days
  ends the run and leaves every stored credited date unchanged; moving the clock backwards past dates
  carrying completions reports a lower current streak while changing nothing stored, and restoring
  the clock restores the original figure exactly.
- **SC-011**: Crossing local midnight with the app open updates the figures within one refresh of the
  screen, with no intermediate state in which the count reads lower than both the day before and the
  day after.
- **SC-012**: The at-risk state appears at exactly 20:00 local and not at 19:59, only when a run of at
  least 1 exists with nothing credited to today, and clears on the first completion and again at local
  midnight — all four transitions verified with a fake clock.
- **SC-013**: Zero interface elements introduced by this feature express a negative quantity, a
  penalty, a warning, or fault — verified by reviewing every string, colour, and state the streak
  element and the at-risk state can show, including the ended-run and never-started cases.
- **SC-014**: On a record seeded with three years of daily completions, the streak figures are
  produced within 100 ms on a mid-range device, measured from the moment they are requested.
  Recording and undoing a completion on the same record are no slower with the streak on screen than
  without it — `docs/PLAN.md`'s definition of done for this phase is that it requires no new writes
  on the completion path, and this is the measurement of it.
- **SC-015**: The feature functions identically with the network permanently unavailable. This holds
  structurally rather than by test — the application has no network surface on this path — so it is
  verified by the fresh-install airplane-mode smoke check plus the absence of any network dependency.
- **SC-016**: The streak element holds the same position and reports the same figures on every block
  of the stepped flow, and is present when the catalogue is unavailable — verified by stepping
  through a full day's blocks in both directions and by opening Today with no catalogue loaded.
- **SC-017**: The day's tasks appear no later with the streak on screen than without it, and no
  streak figure is displayed before it is final — verified by capturing the element from first paint
  until the figures settle and finding no intermediate number, and by recording a completion while
  they are still resolving.
- **SC-018**: The break notice is shown when the last active date is seven days ago and is not shown
  when it is eight, verified with a fake clock at both boundaries; and no stored value changes as a
  result of it being shown, verified by comparing stored records before and after.
- **SC-019**: With reads made to fail, the streak element shows a notice and a retry, shows no
  figure and no zero, and the day's tasks can still be viewed, recorded, and undone; restoring reads
  and retrying shows the correct figures.

## Assumptions

- **The streak is derived, never stored.** `docs/PLAN.md` permits a cached streak row only if the
  derived query becomes visibly slow, and Principle VIII forbids it until a measurement says so. No
  measurement exists. If one ever does, the cache must be reconstructible from the log and must never
  be authoritative — a stored counter that can disagree with the record is a worse bug than a slow
  screen.
- **A completion is sufficient evidence the app was open.** `002`'s write rule admits completions only
  on the current date, so "opened the app and completed at least one task" collapses to "a completion
  is credited to that date". `003`'s Plan Origin is not consulted here, though it corroborates every
  case. **Phase 5 must revisit this**: the moment retroactive completion is allowed, a completion can
  exist for a date the app was never opened on, and this increment's rule would count it. That is a
  decision for the retro-completion spec, and this specification deliberately does not pre-empt it —
  it only records that the coupling exists.
- **The current run does not require today.** A user is judged on days that have finished. Requiring
  today would make the counter read 0 every morning, which is a loss the user has not had and which
  Principle IX forbids. The pending mark is what carries the information instead, and it is what the
  at-risk state reads.
- **The stored facts are fixed and only the present moves.** No timezone or clock change is detected
  and none is compensated for. A user who genuinely travels forward across a date loses the run the
  same way anyone who missed that day would; a user who sets the clock backwards gains nothing. The
  alternative requires storing the device's zone and inventing a rule about what counts as travel —
  new state, in service of a case that is rare, and a rule whose only reliable exploit is the clock.
- **The at-risk hour is 20:00 and is not configurable.** Settings are out of scope for the MVP, and a
  configurable nudge is a preference surface this increment has no reason to open. Whether 20:00 is
  the right hour is a question for using the app, not for specifying it.
- **The recent-activity window is seven days.** It matches the Saturday-to-Friday week the user
  already reads on the sheet, so the two surfaces cover the same span. It is an indicator, not a
  history — Phase 5 is where browsing lives.
- **This increment adds no persistence.** Nothing is stored, no table changes, and no migration is
  required — including the break notice, whose seven-day window is read out of the last active date
  rather than remembered. A stored "already shown" flag would be the only new writable state in the
  feature, and it would exist purely to suppress a message. The historical-immutability test mandated by Principle III for increments touching
  persistence or the catalogue is therefore not triggered by a schema change — but the figures must
  still be shown to be unaffected by a catalogue change, because a streak that moved when task points
  changed would mean it was reading the catalogue.
- **No streak detail sheet.** `docs/PLAN.md` offers one as optional. It is not built: the figures fit
  on Today, and Principle VIII forbids a screen whose content is four numbers already on display.
- **No celebration, milestone, or badge.** Achievements are Phase 10. A run reaching 100 days reads
  as 100 days.
- **The streak appears on Today only.** The weekly sheet stays what `003` made it. Two surfaces
  showing the same figure is two places for it to disagree.
- **The streak is an ornament on the core loop, not part of it.** It is read after the day's tasks
  are on screen, and it fails on its own — a record that cannot be read costs the user their figures,
  never their ability to record. This is the reason the failure posture here differs from `003`'s
  week, where the figures *were* the screen.
- **The glossary gains two terms.** `docs/PLAN.md` names Longest Streak and Streak Break as concepts
  this phase introduces; `docs/GLOSSARY.md` defines neither. Both are added there so the canonical
  name has exactly one home, and "best" stays what it is — a word on screen, not a domain term.
- **Catalogue content is still the placeholder from `002`.** Nothing in this increment depends on the
  task text.

## Dependencies

Increment `002` — the completion log with its reversal marker, the injected time source, the rule
deciding which dates accept writes, and the Today screen this feature's elements attach to.

Increment `003` — the record start, and Plan Origin. Neither is a criterion here; the record start is
the floor beneath which no date is evaluated, and Plan Origin exists so that a future rule reading
"was the user present" has an honest answer available.

Both are built and merged, so this increment extends working code rather than a design. It changes
nothing either of them established: no stored fact is added, no rule is widened, and no existing
figure moves.

## Out of Scope

Streak freezes, repairs, purchased saves, restoration of any kind, and any leniency for travel or
clock changes. Achievements, badges, milestones, and celebration UI. Notifications and reminders of
any kind, including the streak-at-risk notification — Phase 9 owns that, and this increment ships
only the in-app state it would read. Social comparison, leaderboards, and friends. History browsing,
charts, and monthly views. Retroactive completion. Editing any past day. Accounts and sync. Any task
creation, editing, reordering, or repricing.
