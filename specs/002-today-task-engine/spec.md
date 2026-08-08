# Feature Specification: Today Screen — Local Task Engine

**Feature Branch**: `spec/002-today-task-engine`

**Created**: 2026-08-09

**Status**: Draft

**Input**: User description: "Read @docs/PLAN.md and create a specification for the phase 2"

## Overview

Roadmap Phase 2 (`docs/PLAN.md`). The core loop, and the first increment that ships something a user
can hold: open the app, see the practices that apply to today, record them as they happen, and watch
the day's points accumulate against what was available. Fully offline, on a fresh install, with no
account.

This is the smallest slice that replaces the paper sheet.

**Note on spec granularity.** `docs/PLAN.md`'s suggested execution order splits Phase 2 into five
specs (applicability, day-plan materialisation, completion logging, scoring, screen). This
specification keeps them together, because none of the five delivers a usable capability alone —
"applicability resolution" with no screen is a layer, which Principle VIII forbids as a standalone
increment. The five appear here instead as prioritised user stories, each independently testable, so
the split survives where it is useful without producing four increments nobody can run.

**Note on catalogue content.** Increment `001` built the validation contract; the real Arabic
catalogue is still blocked on the source sheet. This feature ships against the placeholder catalogue
already in the repository, whose structure is identical and whose totals are correct. When the real
content arrives it replaces one data file. That substitutability is Principle VI's whole point.

## Clarifications

### Session 2026-08-09

- Q: Are the nine Adhkar nine separate tasks worth 2 points each, or one task completed nine times at 2 points per occurrence? → A: One task, nine occurrences, 2 points each. Multi-occurrence is therefore central to the Today screen, not incidental, and the placeholder catalogue must be corrected before this increment ships.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record today's practice (Priority: P1) 🎯 MVP

A user opens the app and sees exactly the practices that apply to today, grouped by section in the
order the sheet defines. They mark each one as they complete it — some more than once where the
practice allows — and can undo the most recent record if they tapped by mistake. A header shows how
many points they have earned against how many were available today.

**Why this priority**: This is the product. Everything in later phases — the weekly sheet, streaks,
history, charts — is a projection over the records created here. Nothing downstream can be built or
trusted until this exists and is correct.

**Independent Test**: Install fresh, put the device in airplane mode, open the app on a known
weekday. The correct task set appears, completing and undoing changes the earned total by the right
amount, and the available total matches the expected figure for that weekday.

**Acceptance Scenarios**:

1. **Given** a fresh install with no network, **When** the user opens the app on a Sunday, **Then**
   the tasks applicable to Sunday are shown grouped by section, and the available total reads 69.
2. **Given** the app is open on a Monday, **When** the task list is shown, **Then** the voluntary
   fast appears and the available total reads 74.
3. **Given** the app is open on a Friday, **When** the task list is shown, **Then** the seven Friday
   practices appear and the available total reads 76.
4. **Given** a task worth 2 points, **When** the user marks it complete, **Then** the earned total
   increases by exactly 2 and the task shows as completed.
5. **Given** a completed task, **When** the user undoes it, **Then** the earned total decreases by
   exactly the points that record carried, and the task shows as not completed.
6. **Given** the Adhkar task, which may be completed nine times at 2 points each, **When** the user
   completes it twice, **Then** the task shows two of nine recorded and the earned total has
   increased by 4.
7. **Given** the Adhkar task at nine of nine, **When** the user attempts another completion,
   **Then** no further record is created and the earned total does not change.
7a. **Given** the Adhkar task at nine of nine, **When** the day's available total is read, **Then**
   that single task has contributed 18 points to it.
8. **Given** several completions and undos in any order, **When** the earned total is read,
   **Then** it equals the sum of the points carried by the records that remain.
9. **Given** the user has completed nothing, **When** the score is read, **Then** earned is 0,
   available is the correct figure for that weekday, and nothing in the interface presents this as
   a failure.
10. **Given** the app is killed and reopened, **When** today's screen loads, **Then** every record
    made earlier is still present and the totals are unchanged.

---

### User Story 2 - Today's record stays true tomorrow (Priority: P2)

The set of tasks that applied to a date, and the points that were available on it, are fixed the
first time that date is opened and never recomputed. A later change to the task catalogue changes
what future days look like and leaves recorded days exactly as they were.

**Why this priority**: Principle III, and the reason the whole project is structured as it is. It is
second only because it cannot be demonstrated without User Story 1 existing first — but it must ship
in the same increment, because retrofitting it means rewriting storage over real user history.

**Independent Test**: Record a day, change the catalogue's points and schedules, reopen the recorded
day. Its task set and totals are unchanged while the current day reflects the new definitions.

**Acceptance Scenarios**:

1. **Given** a day that has been opened, **When** the task catalogue's point values change,
   **Then** that day still reports the tasks and totals it had when it was opened.
2. **Given** a day that has been opened, **When** a task is added to the catalogue, **Then** that
   task does not appear on the recorded day and does appear on days opened afterwards.
3. **Given** a recorded completion, **When** the underlying task's point value changes, **Then**
   the completion still carries the points it was awarded.
4. **Given** the app is open as the clock passes local midnight, **When** the screen next shows the
   day, **Then** it shows the new date with a newly created plan, and the previous date's plan is
   unaltered.
5. **Given** a catalogue is loaded a second time, **When** loading completes, **Then** nothing about
   existing records or plans has changed.

---

### User Story 3 - One block at a time (Priority: P3)

Rather than a single wall of forty rows, the day is presented one section at a time — the practices
attached to one prayer, then the next — with the user moving forward and back between blocks.

**Why this priority**: A presentation improvement over a working screen, valuable but not load
bearing. A single scrolling list satisfies User Story 1 completely; this makes it pleasant. It is
separable and therefore separate.

**Independent Test**: With the day's data unchanged, the screen shows one section at a time and
moving between sections never alters the day's totals.

**Acceptance Scenarios**:

1. **Given** the day has ten sections, **When** the screen opens, **Then** one section's tasks are
   shown and the day's overall earned and available totals remain visible.
2. **Given** the user is on a section, **When** they move to the next, **Then** records made in the
   previous section are retained.
3. **Given** the user is on the last section, **When** they attempt to move forward, **Then** the
   interface does not advance past the end and does not present this as an error.
4. **Given** the app is reopened, **When** the screen loads, **Then** the section shown is a
   defined, predictable starting point rather than an arbitrary one.

---

### User Story 4 - The day carries its Hijri label (Priority: P3)

Alongside the civil date, the day shows its Hijri date. The label is attached to the day when the
day is created and does not change afterwards.

**Why this priority**: Meaningful to the user and cheap, but the app is fully usable without it. It
is last because it is the only part of this increment that touches the network, and it must never
be allowed to block anything.

**Independent Test**: On a device that has never had network access, the app opens, shows the day,
and records completions normally, with the Hijri label absent or pending rather than blocking.

**Acceptance Scenarios**:

1. **Given** a Hijri date is known for today, **When** the day is shown, **Then** both the civil and
   Hijri dates appear.
2. **Given** no Hijri date has ever been retrieved, **When** the app opens with no network, **Then**
   the day, its tasks, and completion all work normally and the Hijri label is simply absent.
3. **Given** a day was created with a Hijri label, **When** the label's source later reports a
   different value for that date, **Then** the recorded day continues to show the label it was
   created with.
4. **Given** the network is unavailable, **When** the user completes a task, **Then** the completion
   is recorded immediately with no waiting.

---

### Edge Cases

- The user completes a task at 23:59:58 and the day rolls over before the record settles — the
  record must be credited to the date that was current when the action was taken.
- The device's timezone changes while the app is open — the accountability day is local midnight to
  local midnight, so the day shown must follow the device's local date.
- The device clock is moved backwards to a date already recorded — that date's existing plan must be
  reused, not replaced.
- The device clock is moved forward past several days — the skipped days are not created here;
  Phase 3 backfills them.
- The app is opened for the very first time on a Friday — the plan for that Friday must include the
  Friday practices, not a base day.
- A task's occurrence limit is reached and the user undoes once — one further completion becomes
  possible again.
- Undo is pressed when nothing has been recorded for that task — nothing happens and no error state
  is shown.
- The same task is completed twice in rapid succession beyond its limit — only the permitted number
  of records exist.
- The catalogue fails to load on first launch — the app must say so plainly rather than showing an
  empty day that looks like a completed one.
- A day is opened, nothing is completed, and the app is closed — the plan persists, and the day
  reports 0 out of its available total rather than disappearing.

## Requirements *(mandatory)*

### Functional Requirements

**Catalogue availability**

- **FR-001**: The system MUST load the task catalogue into local storage on first launch, and MUST
  do so idempotently — loading it again MUST NOT alter existing records or plans.
- **FR-002**: The system MUST record which catalogue version is in effect, so that a day can later
  be attributed to the definitions that applied when it was created.
- **FR-003**: If no catalogue is available, the system MUST report that state explicitly and MUST
  NOT present an empty task list as a valid day.

**Applicability**

- **FR-004**: For any date, the system MUST resolve exactly the set of tasks whose schedule rule
  matches that date.
- **FR-005**: Applicability MUST be derived from the catalogue's schedule rules alone. No task may
  be shown or hidden by a rule written into a screen.

**The day's record**

- **FR-006**: The first time a date is opened, the system MUST create and persist that date's plan:
  the tasks that applied, the points each was worth, each task's occurrence limit, and the total
  points available.
- **FR-007**: Once created, a date's plan MUST NOT be recomputed or altered. Subsequent openings
  MUST read the stored plan.
- **FR-008**: A change to the task catalogue MUST affect only dates whose plans have not yet been
  created.
- **FR-009**: Each date's plan MUST carry the Hijri label determined when it was created, and MUST
  NOT re-derive it afterwards.

**Recording completions**

- **FR-010**: The system MUST record each completion as a separate entry carrying the task, the date
  it is credited to, the points awarded, and when it was recorded.
- **FR-011**: A completion MUST carry the points that applied at the moment it was recorded, and
  those points MUST NOT change afterwards.
- **FR-012**: A task MUST be completable up to its occurrence limit for that date and no further.
- **FR-013**: Undo MUST remove the most recently recorded completion for that task on that date, and
  MUST leave earlier completions intact.
- **FR-014**: Undo MUST be recorded as a reversal rather than an erasure, so that the change can be
  reconciled with other devices later without losing the fact that it happened.
- **FR-015**: The system MUST NOT permit completions to be recorded against any date other than the
  current one. The rule deciding which dates are writable MUST live in exactly one place, so that
  later phases can widen it without two screens disagreeing.

**Scoring**

- **FR-016**: The day's earned points MUST equal the sum of the points carried by that date's
  remaining completions.
- **FR-017**: The day's available points MUST come from the stored plan, never from the live
  catalogue.
- **FR-018**: Earned points MUST never be negative and MUST never exceed available points.
- **FR-019**: The score MUST be expressed as earned, available, and the proportion between them.

**Presentation**

- **FR-020**: The screen MUST show the day's tasks grouped by section, in the order the catalogue
  defines, with each task's completion state and — where more than one is permitted — how many of
  how many have been recorded.
- **FR-020a**: A multi-occurrence task MUST be presented as a single row showing progress toward its
  limit, not as repeated rows. Recording one occurrence MUST advance that row's count without
  removing it from the list until the limit is reached.
- **FR-021**: The screen MUST show the civil date, and the Hijri date when one is known.
- **FR-022**: The screen MUST reflect a completion or undo without the user having to leave and
  return.
- **FR-023**: While the app is open, crossing local midnight MUST move the screen to the new date.
- **FR-024**: The interface MUST NOT present incompleteness as failure: no negative figures, no
  penalty, no error styling, and no language or imagery implying fault for what was not done.
- **FR-025**: Task text MUST be rendered as content in an Arabic-appropriate typeface with correct
  bidirectional handling, and MUST NOT reflow the surrounding layout.

**Offline and independence**

- **FR-026**: Viewing tasks, recording a completion, undoing one, and reading the score MUST all
  work with no network, on a fresh install.
- **FR-027**: No network result may be required before any of those four actions can be performed or
  displayed.
- **FR-028**: Every record the user creates MUST carry a client-generated stable identifier, a
  last-modified timestamp, a soft-delete marker, and a nullable user reference, so it can be
  synchronised later without migrating existing data.

**Time**

- **FR-029**: The current date and time MUST be obtained from a single injected source, so that day
  boundaries and rollover can be tested by advancing a fake clock.
- **FR-030**: The accountability day MUST run from local midnight to local midnight. This rule MUST
  exist in exactly one place.

### Key Entities

- **Task Definition / Task Version / Section / Schedule Rule / Catalogue Version**: as defined in
  `docs/GLOSSARY.md` and validated by increment `001`. Unchanged here; this feature stores and reads
  them rather than redefining them.
- **Day Plan**: the frozen record of one date — its applicable tasks, the points available, the
  catalogue version in effect, and the Hijri label. Written once.
- **Planned Task**: one task's entry in a Day Plan, carrying the points it was worth that day and
  its occurrence limit.
- **Completion**: one recorded occurrence — which task, which date it is credited to, the points
  awarded, when it was recorded, and whether it has since been reversed.
- **Daily Score**: earned against available for one date, derived from that date's plan and
  completions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a fresh install in airplane mode, a user can open the app, complete a task, and see
  the score change — with no account and no setup step.
- **SC-002**: Available points read exactly 69 on Saturday, Sunday, Tuesday and Wednesday, 74 on
  Monday and Thursday, and 76 on Friday.
- **SC-003**: After any sequence of completions and undos, earned points equal the sum of the
  remaining records' points — verified across at least 20 mixed operations.
- **SC-004**: Changing a task's points and schedule leaves every previously opened day reporting its
  original tasks and totals, while the current day reflects the change.
- **SC-005**: Closing and reopening the app preserves every record and total exactly.
- **SC-006**: Advancing a fake clock past local midnight produces a new day with its own plan, and
  the previous day's plan is byte-for-byte unchanged.
- **SC-007**: A task with an occurrence limit of *n* accepts exactly *n* completions and rejects the
  next, and one undo makes exactly one more possible. Verified specifically against the Adhkar task
  at *n* = 9, which is the only multi-occurrence task in the catalogue and therefore the only place
  this can be observed.
- **SC-011**: Completing the Adhkar task nine times contributes exactly 18 earned points, matching
  the 18 it contributes to available — a fully completed day reads exactly its available total, not
  more.
- **SC-008**: Recording a completion is perceived as immediate — the interface never shows a waiting
  state for it.
- **SC-009**: Zero interface elements express a negative quantity, a penalty, or fault. Verified by
  reviewing every string and state the screen can show.
- **SC-010**: The app functions identically with the network permanently unavailable, differing only
  in that the Hijri label may be absent.

## Assumptions

- **The catalogue content is placeholder.** The structure, totals, and schedule rules are correct
  once the Adhkar correction above is applied; the task text is not the real Arabic yet. This
  feature is built against it, and the real catalogue replaces one data file without touching this
  feature's code.
- **Adhkar is the only multi-occurrence task.** Every other task has a limit of 1. That makes the
  occurrence counter a real but narrow surface, and it means a single fixture exercises the entire
  multi-occurrence path. If the real sheet turns out to have others, nothing in this feature changes
  — only the data does.
- **Records are reversed, not erased.** Principle V requires deletion of a synchronisable record to
  be a tombstone. Undo therefore marks a completion reversed rather than removing it.
- **Only the current date is writable.** Retroactive completion is Phase 5 work. The policy that
  decides this exists here as a single named rule so Phase 5 can widen it in one place.
- **Days that are never opened are not created here.** Phase 3 backfills them. A day skipped
  entirely simply has no plan until then.
- **The Hijri label is retrieved opportunistically** and never sits on the path of any user action.
  A day created without one keeps that absence rather than acquiring a label retroactively.
- **The stepped flow starts at a defined position**, not at wherever the user left off — resuming
  position is a refinement, not a requirement of this increment.
- **No settings screen** beyond what the Today screen itself needs.

## Dependencies

Increment `001` — the catalogue, its validation contract, and the recorded decisions. The catalogue
this feature seeds from is the fixture `001` validated.

## Out of Scope

The weekly sheet, history browsing, streaks, charts, editing any past day, task creation or editing
of any kind, accounts, sync, notifications, achievements, leaderboards, settings beyond this
screen's needs, and celebration animation.

## Required catalogue correction

The placeholder catalogue from `001` models the Adhkar section as nine separate tasks with an
occurrence limit of 1 each. Per the clarification above that is wrong: it is **one task completed
nine times**.

Correcting it is part of this increment, before anything is built on top:

| | Before | After |
|---|---|---|
| Adhkar tasks | 9, limit 1 each | 1, limit 9 |
| Adhkar available points | 9 × 2 = 18 | 2 × 9 = 18 |
| Total tasks in catalogue | 40 | 32 |

Every total is unchanged — 69 base day, 74, 76, 500 — because available points are already defined
as points × occurrence limit. The validation contract from `001` passes the corrected catalogue
without modification, which is the property it was built to have. Two count assertions in `001`'s
parse test (40 tasks, 40 task versions) become 32 and 32; that is test data catching up with
content, not a contract change.

**If the contract has to be weakened to admit the corrected catalogue, stop** — that would mean
something is wrong with one of them, and the arithmetic is not the thing to adjust.
