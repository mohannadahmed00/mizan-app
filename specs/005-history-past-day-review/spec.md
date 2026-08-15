# Feature Specification: History & Past-Day Review

**Feature Branch**: `spec/005-history-past-day-review`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "Read @docs/PLAN.md and create a specification for the Phase 5 — History & Past-Day Review"

## Overview

Roadmap Phase 5 (`docs/PLAN.md`), and the first increment past the MVP boundary. `002` gave the user
a day, `003` a week, `004` the run of days. This one gives them **the whole record** — and, more
importantly, proves the record is worth having.

The phase has two jobs, and the second is the reason it comes before charts and before sync:

- **Reflection.** The *muhasabah* the app is named for. Browse back through every week that was
  recorded, open any day, and see it as it actually was.
- **Proof.** The versioning machinery went in during `002` — immutable Task Versions, frozen Day
  Plans, `pointsAwarded` denormalised onto every completion, `versionEffectiveOn` picking the version
  a date was scored under. Nothing has yet *proved* it. This increment is the proof: seed history
  under one catalogue, change the points and the schedules, and assert that past days did not move.
  `docs/PLAN.md` asks for that suite as a first-class spec rather than a test chore, and it is
  User Story 3 below.

`docs/PLAN.md` also asks this phase to settle the retroactive-completion question "once and for all".
It is settled below, and it is settled by not moving: the past stays read-only. `002`'s
`DayWritePolicy` is not widened, and this increment's job is to make that rule **visible** rather
than to soften it.

**Note on spec granularity.** `docs/PLAN.md`'s execution order splits Phase 5 into three specs (edit
policy, history browsing, integrity verification). This specification keeps them together, for the
same reason `003` and `004` merged theirs: an edit policy that widens nothing is not an increment,
and Principle VIII forbids shipping a layer on its own. All three appear below as prioritised user
stories.

**Note on the one thing history writes.** Scrolling history writes nothing at all. `003` materialises
a plan for an elapsed unopened date in the week it is displaying, so a skipped day reads `0/69`
instead of vanishing; history does not inherit that, because a continuous list back to the record
start would turn a scroll into thousands of rows of plans carrying no completions. A history week row
works out an unplanned date's availability read-only instead, from the catalogue version in effect on
that date — deterministic, because a task version is immutable once published. A plan is built and
frozen only when the user opens that day, which is a deliberate act with a lasting reason. Every plan
built that way is still marked backfilled, so `004`'s streak is untouched either way.

## Clarifications

### Session 2026-08-15

- Q: Retroactive completion — fixed grace window, or read-only past? → A: **Read-only past.** Only
  the current date accepts writes; `002`'s `DayWritePolicy` is unchanged, not widened. Three reasons.
  It keeps `004`'s consistency rule sound — a completion credited to a date remains proof the app was
  open on that date, which is the coupling `004` explicitly handed forward to this phase. It keeps
  the record an accountability record rather than a mutable document, which is the promise this phase
  exists to prove. And it matches `docs/PLAN.md`'s own MVP exclusion of "retroactive editing beyond
  the current day". The concepts `docs/PLAN.md` assigns to this phase are still introduced — the
  Retro-Completion Window exists and its width is zero, and every elapsed date is a Locked Day — so
  the vocabulary is in place if the window is ever opened deliberately. The UI states the rule
  plainly where a user would reach for it, rather than presenting a disabled control.
- Q: How is the history list organised? → A: **A scrolling list of Saturday-to-Friday weeks, newest
  first**, each row carrying its seven day indicators and its earned-out-of-available total. It is
  the artifact the user already recognises from `003` and from the paper sheet; it reuses the week
  aggregate rather than inventing a second one; and it paginates naturally backwards to the record
  start. A month calendar was rejected as the heatmap Phase 6 owns, and because it drops the weekly
  total that is the thing the user actually reads.
- Q: Does history list weeks in which nothing was recorded? → A: **Yes — every week from the current
  week back to the week containing the record start appears, with no gaps.** `docs/PLAN.md` already
  settles the same question one level down: `003` backfills so a skipped day reads `0/69` "rather
  than vanishing", and a skipped week must not vanish either. A list that silently omits elapsed time
  misrepresents the record, which is the opposite of what this phase exists to prove — two adjacent
  rows three months apart with nothing saying so is a worse artifact than three months of honest
  blank rows. How those blanks read is governed by FR-030, not by hiding them.
- Q: Does scrolling history build and store day plans for weeks the user never opened? → A: **No —
  a plan is built only when a day is actually opened.** Q1 makes the list continuous, so scrolling
  past a three-month gap would otherwise write twelve weeks of plans, roughly 3,400 rows carrying no
  completions and answering no question the user asked. A week row instead works out each unplanned
  date's availability read-only, from the catalogue version in effect on that date, which is
  deterministic because task versions are immutable. Writes stay proportional to deliberate action,
  and "browsing does not write" becomes a property that can actually be asserted. The cost is named
  and accepted: a past date reached through the weekly sheet may already carry a frozen plan while
  the same date reached through history does not, so both paths MUST produce identical figures
  (FR-020b).
- Q: What opens when the user taps today's cell in the weekly sheet or in history? → A: **The Today
  screen — the recording surface.** FR-015 and User Story 4 contradicted each other, and this settles
  it in favour of one place to record. A read-only copy of today is a second opinion about a date the
  user can still act on: it shows unrecordable tasks on the one date that is recordable, which reads
  as locked and is exactly the dead end FR-024's copy exists to prevent. This changes behaviour `003`
  shipped, where every cell including today opened the read-only summary, and it makes returning from
  Today land back on whichever list it was opened from.
- Q: What is shown when a past day is opened, has no plan yet, and one cannot be built? → A:
  **Storing is best-effort and never blocks the read.** Q2 made opening a day the moment a plan is
  written, which created this failure mode. The two causes are not alike: a failed write leaves the
  figures perfectly knowable, because they were already worked out to render the week row, so the day
  opens normally and a later visit stores it. Only an unavailable catalogue version leaves nothing
  knowable, and that is the case FR-032 already governs. One new failure state, not two — and a user
  never loses sight of a day because a write they did not ask for went wrong.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse the whole record (Priority: P1) 🎯 MVP

A user opens history and sees every week they have recorded, most recent first, each showing which
days they were consistent on and what the week came to. They scroll back as far as their record goes,
and it stops there.

**Why this priority**: This is the feature the phase is named for, and it is useful the moment it is
correct. Everything else in this increment either hangs off it or protects it.

**Independent Test**: Seed several months of records with known gaps and known weekly totals, open
history, and check every visible week row by hand against the seeded dates.

**Acceptance Scenarios**:

1. **Given** a record spanning several weeks, **When** history is opened, **Then** the most recent
   week appears first and each earlier week follows in descending date order.
2. **Given** a week row on screen, **When** it is read, **Then** it shows the week's Saturday-to-Friday
   span, its earned and available points, and seven day positions in date order.
3. **Given** a week in which some days were recorded and some were not, **When** the row is read,
   **Then** each day position shows whether anything was recorded on it, using the same three states
   the weekly sheet already uses.
4. **Given** the user scrolls to the end of the list, **When** the record start is reached, **Then**
   the list ends there and states plainly that this is where the record begins.
5. **Given** the user scrolls past the currently loaded weeks, **When** more of the record exists,
   **Then** further weeks load and appear without the user losing their scroll position.
6. **Given** a user who has recorded nothing at all, **When** history is opened, **Then** it says the
   record has not started yet and offers the way to start it, rather than showing empty weeks.
7. **Given** history is open, **When** an elapsed day position is tapped, **Then** that day opens in
   its read-only detail; **and when** today's position is tapped, **Then** the recording surface
   opens instead, and going back from either returns to history where it was left.
8. **Given** the current week, **When** it appears in history, **Then** the days that have not yet
   elapsed are visually distinct from days that elapsed with nothing recorded.
9. **Given** a stretch of weeks in which nothing at all was recorded, **When** history is scrolled
   through it, **Then** every one of those weeks appears in its place with a total of zero out of its
   available points, and none is omitted, merged, or skipped over.

---

### User Story 2 - Read a past day exactly as it was (Priority: P1)

A user opens any recorded day and sees the tasks that applied on *that* day, what each was worth on
that day, how many times each was recorded, and what the day came to.

**Why this priority**: A history list without the day behind it is a summary of a record the user
cannot inspect. This is where reflection actually happens, and it is the surface the integrity proof
in User Story 3 is asserted against.

**Independent Test**: Seed one date with a known plan and known completions, open it from history,
and check every row, every point value, and the day total by hand.

**Acceptance Scenarios**:

1. **Given** a date with a stored plan, **When** it is opened, **Then** it shows that date's civil
   date, its Hijri label, its sections in the order they applied, every task that applied, and the
   day's earned and available points.
2. **Given** a task that was recorded more than once, **When** its row is read, **Then** it shows how
   many times it was recorded against how many were allowed on that day.
3. **Given** a task that applied on that day and was never recorded, **When** its row is read,
   **Then** it appears with its point value and a count of zero, with no cross, no red, and no word
   implying fault.
4. **Given** a day on which nothing at all was recorded, **When** it is opened, **Then** it shows the
   full plan with a total of zero out of that day's available points, presented as a day that passed
   rather than a day that was failed.
5. **Given** a day whose plan was created by backfill for a week the user never opened, **When** it is
   opened, **Then** it is indistinguishable from any other day with nothing recorded — the origin of
   a plan is never surfaced to the user.
6. **Given** a date with no stored plan — before the record start, or in the future — **When** it is
   reached, **Then** it says there is no record for that date rather than showing an empty day or a
   zero score.
7. **Given** a past day is open, **When** every control on the screen is examined, **Then** there is
   no way to record, undo, add, remove, reorder, or reprice anything.
8. **Given** a past day is open, **When** the user goes back, **Then** they return to where they came
   from — history or the weekly sheet — and that list is in the state they left it.

---

### User Story 3 - The record does not change when the catalogue does (Priority: P1)

An administrator changes what a task is worth and which days it applies to. Every day the user has
already recorded still reports exactly what it reported before. Only days from the change onward
follow the new definitions.

**Why this priority**: This is Principle III made verifiable, and `docs/PLAN.md` names it as the
reason this phase precedes charts and sync. Charts would aggregate over the same data and sync would
move it between devices; both would inherit any defect found here, and by then real history exists to
damage.

**Independent Test**: Seed history under catalogue v1, introduce a v2 that changes point values and
schedule rules, and assert past days render v1 figures while the current day renders v2 — at the day
detail, in the week rows, in the weekly totals, and in the streak.

**Acceptance Scenarios**:

1. **Given** days recorded under catalogue v1, **When** v2 changes a task's point value and the app
   is reopened, **Then** every previously recorded day shows the v1 value on that task's row and its
   original day total.
2. **Given** the same change, **When** the current day is opened, **Then** it shows the v2 value and a
   day total computed from v2.
3. **Given** v2 removes a task from a weekday it previously applied to, **When** a past day that
   carried that task is opened, **Then** the task is still listed with its original value and the
   day's available points are unchanged.
4. **Given** v2 adds a task to a weekday, **When** a past day of that weekday is opened, **Then** the
   new task does not appear and the day's available points are unchanged.
5. **Given** a completion recorded under v1, **When** its task's value changes in v2, **Then** the
   points that completion contributed remain what they were awarded, everywhere they are counted.
6. **Given** the catalogue changes, **When** the weekly sheet and the history week rows are read,
   **Then** past weeks report their original earned and available totals.
7. **Given** the catalogue changes, **When** the streak figures are read, **Then** neither the current
   nor the longest streak moves — consistency is a fact about what was recorded, never about what
   applied.
8. **Given** an elapsed date with no plan, at or after the record start, **When** it is first opened
   after a catalogue change, **Then** the plan created for it uses the version that was in effect on
   that date, not the current one.
9. **Given** that same date before it is opened, **When** its week row is shown after a catalogue
   change, **Then** the available points it displays are the ones the version effective on that date
   would give, and they do not change when the date is subsequently opened.

---

### User Story 4 - One rule about what can be written, said plainly (Priority: P2)

The user understands, without having to discover it by tapping, that today is the day they record and
that days that have passed are a record rather than a form.

**Why this priority**: The rule is already enforced by `002` and nothing in this increment weakens
it, so the record is safe without this story. What is missing without it is the explanation — and an
unexplained locked screen is where a user decides the app is broken. P2 rather than P1 because
correctness does not depend on it.

**Independent Test**: Open a past day and read every piece of copy on it; then confirm through the
record that no path from that screen can write anything.

**Acceptance Scenarios**:

1. **Given** a past day is open, **When** the screen is read, **Then** it states plainly that this day
   is a record and that recording happens on the current day, in language that carries no reprimand.
2. **Given** a past day is open, **When** the user attempts any interaction that would record or undo
   on another surface, **Then** nothing is written and nothing changes.
3. **Given** the current day is opened from history or from the weekly sheet, **When** it is shown,
   **Then** it is the recording surface with its full behaviour, not a locked read-only copy.
4. **Given** any write path anywhere in the app, **When** the writable date is decided, **Then** it is
   decided in exactly one place, so no two screens can hold different opinions about it.
5. **Given** a future date is somehow reached, **When** it is shown, **Then** it is not writable and
   is presented as a date that has not happened rather than as a locked one.

---

### Edge Cases

- The record contains exactly one day — history shows one week row with six positions outside the
  record and one inside it, and ends there.
- The record start falls mid-week — the days before it in that week read as outside the record, not
  as days with nothing recorded.
- A week straddles a month or a year boundary — the week is still Saturday to Friday and its total is
  still its own; no second boundary rule is introduced anywhere in this feature.
- The user scrolls back through a year of weeks — earlier weeks continue to load, the list never
  reports a total number of weeks it has not counted, and scrolling stops at the record start.
- A past week that was never opened is scrolled past — nothing is written, and the week still reports
  honest availability worked out from the version effective on each of its dates.
- A day within that week is then opened — a plan is built and frozen for it at that moment, its
  figures are identical to what the week row already showed, and the date does not become a
  Consistency Day.
- The same day is opened but storing its plan fails — the day opens anyway with the same figures, the
  user is told nothing about a write they did not ask for, no partial plan is left behind, and
  opening it again later stores it.
- The same day is opened and the catalogue version effective on that date is unavailable — nothing
  about what applied is knowable, so the day reports that rather than showing an empty plan scoring
  zero.
- The same past week is browsed twice, or the same past day opened twice — the second visit creates
  nothing, and every figure is identical to the first.
- The user records nothing for three months and comes back — the twelve intervening weeks each appear
  in their place reading zero out of their available points, in language that describes a period with
  nothing recorded rather than twelve failures, and the record start is still where the list ends.
- The catalogue is unavailable — history shows the weeks whose plans already exist and says plainly
  that the rest cannot be built yet, without inventing figures and without failing the whole screen.
- A day is opened directly by date and has no plan — it reports no record rather than a zero day.
- The user is on a past day when local midnight passes — nothing about that day changes, because
  nothing about a past day depends on the current date.
- The user opens today from history, records something, and goes back — they land on history, not on
  the app's start, and the row they came from reflects what they just recorded.
- The user is on the recording surface, opened from history, when local midnight passes — that date
  stops accepting writes at the moment it stops being today, and the user is not left holding a
  surface that appears recordable.
- The device clock is moved backwards so that recorded dates are now in the future — those dates are
  not shown as recordable, nothing stored is altered, and restoring the clock restores the view.
- A day carries a task recorded to its occurrence limit — the detail shows the limit reached as a
  completed state, never as a ceiling the user hit.
- Reads begin failing while history is open — it reports the failure as the app's, offers a retry,
  and does not present an empty record as an empty history.
- The user opens the same day from the weekly sheet and from history — it is the same screen showing
  the same figures, and going back returns to whichever one they came from.

## Requirements *(mandatory)*

### Browsing the record

- **FR-001**: The system MUST provide a history surface listing weeks in descending date order, most
  recent first, reachable from the weekly sheet.
- **FR-001a**: The list MUST be continuous: every Saturday-to-Friday week from the current week back
  to the week containing the record start MUST appear, including weeks in which nothing was recorded.
  No week within that span may be omitted, collapsed, or summarised away.
- **FR-002**: Each week row MUST show the week's Saturday-to-Friday span, its earned points, its
  available points, and seven day positions in date order.
- **FR-002a**: A completed week's available points MUST be the whole week's total. The **current**
  week's MUST be the availability of its elapsed days only, matching the rule `003` already applies
  to the weekly sheet — a Saturday morning must not read as a seventh of a week on either surface.
- **FR-003**: Each day position MUST show whether that date was recorded on, using the same states
  the weekly sheet already uses, and MUST distinguish a date outside the record and a date that has
  not yet elapsed from an elapsed date with nothing recorded.
- **FR-004**: The list MUST extend backwards no further than the record start — the earliest date
  carrying a plan — and MUST state where the record begins rather than ending silently.
- **FR-005**: The list MUST load further weeks as the user scrolls rather than loading the entire
  record at once, and MUST NOT lose the user's position when it does.
- **FR-006**: The system MUST NOT show weeks later than the current week.
- **FR-007**: A user with no records MUST see a statement that the record has not started, with the
  way to start it, rather than an empty list or fabricated weeks.
- **FR-008**: Selecting a week row, or a day position for an **elapsed** date, MUST open that date's
  read-only detail. Selecting the current date MUST NOT — it opens the recording surface instead
  (FR-015a). A date outside the record or later than today MUST NOT be selectable at all.
- **FR-009**: Week identity and week boundaries in this feature MUST come from the single week rule
  established in `003`. No second definition of a week may be introduced.

### The past day

- **FR-010**: A date's detail MUST be built entirely from that date's stored plan and its completions.
  The live catalogue MUST NOT be consulted to render, order, label, or score a past day.
- **FR-011**: The detail MUST show the date's civil date, its Hijri label, its sections in the
  order they applied, every task that applied with the points it was worth on that date, how many
  times each was recorded against how many were allowed, and the day's earned and available points.
- **FR-012**: A task that applied and was never recorded MUST be shown with its value and a count of
  zero, with no cross, no red, no negative figure, and no language attributing fault.
- **FR-013**: A date that has no plan and is not eligible for one — earlier than the record start, or
  later than today — MUST report that there is no record for it, and MUST NOT be rendered as a day
  scoring zero. An elapsed date at or after the record start is eligible, and opening it materialises
  its plan per FR-020 rather than reporting no record.
- **FR-014**: The origin of a plan MUST NOT be surfaced to the user anywhere. A backfilled day and an
  opened day with nothing recorded MUST look identical.
- **FR-015**: The past-day detail MUST be reachable from both the weekly sheet and history, MUST be
  the same surface in both cases, and MUST return to whichever surface it was opened from.
- **FR-015a**: Selecting the **current date** from either list MUST open the recording surface, not a
  read-only detail. There MUST be exactly one surface in the app on which a completion can be
  recorded. Returning from it MUST land on whichever list it was opened from.
- **FR-015b**: Crossing local midnight MUST NOT strand the user on the wrong surface: a date that was
  current when opened and has since elapsed MUST stop accepting writes (FR-028), and the app MUST NOT
  present it as still recordable.

### Historical integrity

- **FR-016**: A change to the task catalogue MUST NOT alter what any previously recorded date
  reports — not the tasks listed, not their point values, not the day's available points, not the
  day's earned points, not the week totals containing it, and not any streak figure.
- **FR-017**: A recorded completion MUST continue to contribute the points it was awarded, wherever
  those points are counted, regardless of what its task is worth now.
- **FR-018**: When a plan is created for an elapsed date, it MUST be built from the catalogue version
  in effect on that date, never from the current version.
- **FR-019**: No path in this feature may create, alter, or remove a completion; and no path may alter
  or remove an existing plan. The only write this feature performs is creating a plan that does not
  yet exist, under the conditions in FR-020.
- **FR-020**: A plan MUST be created only when a date is **opened** in its detail, and only when that
  date has elapsed and is on or after the record start. Scrolling, rendering a week row, or loading
  further weeks MUST NOT cause any plan to be created. Dates before the record start and dates later
  than today MUST NOT cause a plan to be created. Creation MUST be idempotent — opening the same date
  twice creates nothing the second time and changes no figure.
- **FR-020a**: A week row MUST show the available points of a date that has no plan without creating
  one, derived from the catalogue version in effect on that date. It MUST NOT use the current version
  for an elapsed date, and MUST NOT present the date as scoring against a total it did not have.
- **FR-020b**: A date's figures MUST be identical whether that date currently carries a plan or not.
  Reaching the same past date through the weekly sheet, which may already have materialised it, and
  through history, which has not, MUST produce the same available points, the same earned points, and
  the same day state.
- **FR-020c**: Storing a plan MUST be best-effort and MUST NOT gate the read. If the plan cannot be
  stored but what applied on that date is knowable, the day MUST open normally from the derived
  figures, and a later visit MUST be free to store it. A failed store MUST NOT be surfaced to the
  user as a failure of the day, and MUST NOT leave a partial plan behind.
- **FR-021**: A plan created this way MUST be marked as backfilled, so that it is never read as
  evidence the app was open on that date.

### What can be written

- **FR-022**: Only the current date MUST accept completions. Every elapsed date is a Locked Day, and
  the Retro-Completion Window is deliberately empty in this increment.
- **FR-023**: The decision of whether a date accepts writes MUST live in exactly one place and MUST
  be consulted by every write path, including the current-day recording surface. No screen may hold
  its own opinion about it.
- **FR-024**: A past day MUST offer no affordance to record, undo, add, remove, reorder, or reprice
  anything, and MUST state plainly, without reprimand, that recording happens on the current day.
- **FR-025**: A future date MUST NOT accept writes and MUST be presented as a date that has not
  happened, distinctly from a date that is locked because it has passed.

### Time

- **FR-026**: The current date MUST come from the single injected time source established in `002`.
  No date rule in this feature may read the system clock directly.
- **FR-027**: A stored date MUST be read exactly as stored. A device timezone or system clock change
  MUST NOT alter, re-credit, or re-evaluate any recorded date; it changes only which date is current
  and therefore which date is writable.
- **FR-028**: Crossing local midnight while the current day's detail is open MUST make that date stop
  accepting writes at the same moment it stops being today.

### Encouragement

- **FR-029**: No element in this feature may express a day with nothing recorded, an unrecorded task,
  a low total, or a gap in the record as a failure — no red, no cross, no broken or emptied imagery,
  no penalty, no negative figure, and no language attributing fault. History shows what was
  completed.
- **FR-030**: A record with long gaps MUST read as a record with gaps, not as a sequence of failures,
  and the surface MUST NOT summarise, rank, or characterise the user's consistency beyond the figures
  each period actually carries.

### Failure and offline

- **FR-031**: If the record cannot be read, history MUST say so, attribute the failure to the app, and
  offer a retry. It MUST NOT present an unreadable record as an empty one.
- **FR-032**: If the catalogue is unavailable so that a plan cannot be built for an elapsed date,
  history MUST still show every week whose plans already exist and MUST say plainly that the
  remainder cannot be built yet. It MUST NOT show a fabricated figure and MUST NOT fail the whole
  surface.
- **FR-033**: Every figure and state in this feature MUST be produced with no network, on a fresh
  install.

### Key Entities

- **History Page**: one loaded stretch of consecutive weeks, most recent first, bounded below by the
  record start. A window over the record, held nowhere.
- **Week Row**: one week's span, its earned and available points, and its seven day positions — the
  weekly summary `003` already defines, read in a list rather than one at a time.
- **Record Start**: the earliest date carrying a plan. The floor beneath which no date is shown, no
  plan is created, and no figure is computed.
- **Locked Day**: any elapsed date. Viewable in full, writable never.
- **Retro-Completion Window**: the set of elapsed dates that accept completions. Empty in this
  increment, by decision. The term exists so that opening it later is a deliberate, named change
  rather than a quiet widening of a policy.
- **Day Detail**: one date's read-only projection — its stored plan, its live completions, and the
  resulting figures. The projection `003` already defines, reached from a second place.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a record seeded with known completions, every week row's earned and available
  points equal the hand-computed totals, and every day position matches the seeded dates — verified
  across a fully recorded week reading 500/500, a partly recorded week, a week with nothing recorded,
  and the current week.
- **SC-002**: The history list ends exactly at the record start: no week earlier than the week
  containing it is reachable, and days within that week earlier than the record start are shown as
  outside the record rather than as unrecorded.
- **SC-002a**: The number of week rows between the current week and the record start equals the
  number of Saturday-to-Friday weeks in that span exactly, verified on a record containing a
  twelve-week stretch with no completions — no row is missing and none is duplicated.
- **SC-003**: A record spanning one year loads its first screen of weeks and scrolls back through the
  full year without loading the whole record at once, and without the user's scroll position moving
  when further weeks arrive.
- **SC-004**: Opening any recorded day shows every task from that date's stored plan, each with the
  point value stored on that plan and the recorded count from its live completions, and a day total
  equal to the sum of the points awarded — verified by hand against the seeded date.
- **SC-005**: Seeding history under catalogue v1, introducing a v2 that changes at least one point
  value and at least one schedule rule, and reopening the app leaves every pre-change day's task
  list, per-task values, available points, earned points, containing week totals, and both streak
  figures identical to what they were before the change — while the current day reflects v2.
  This is the phase's defining test and it MUST exist before the code it covers.
- **SC-006**: A completion recorded under v1 contributes its awarded points everywhere after v2
  changes its task's value — verified at the day detail, the day total, the week total, and the
  weekly sheet.
- **SC-007**: A plan created for an elapsed date after a catalogue change is built from the version
  effective on that date, verified by comparing its task set and available points against the same
  date planned before the change.
- **SC-008**: Scrolling the entire history of a record spanning one year changes the stored record in
  no way at all — every stored plan and every stored completion is identical before and after,
  verified by comparison.
- **SC-008a**: Opening an elapsed unopened date creates exactly one plan, marked backfilled, and
  nothing else; opening ten such dates creates exactly ten. No completion is created by any of them.
- **SC-009**: Browsing the same past week twice, or opening the same past day twice, produces
  identical figures and creates nothing on the second visit, verified by comparing the stored record
  and the rendered figures across both visits.
- **SC-009b**: A past date's available points, earned points, and day state are identical whether
  read from a materialised plan or worked out for a date with none — verified by capturing the
  figures for an unopened date, opening it, and comparing.
- **SC-010**: No date before the record start and no date later than today causes a plan to be
  created, verified by comparing the stored plan set after browsing a record whose start falls
  mid-week and whose current week is partly elapsed.
- **SC-011**: Streak figures are identical before and after browsing the full history, including a
  history containing weeks the user never opened, verified against the figures computed before
  browsing.
- **SC-012**: No write of any kind succeeds against an elapsed date from any surface, verified by
  attempting to record and undo on a past day through every path the app exposes and comparing the
  stored record before and after.
- **SC-013**: Selecting the current date from history and from the weekly sheet opens the recording
  surface in both cases, it accepts completions normally there, going back returns to the list it was
  opened from with the change reflected, and it stops accepting completions the moment local midnight
  passes — all verified with a fake clock.
- **SC-013a**: Exactly one surface in the app accepts a completion, verified by enumerating every
  route to a writable date and finding they all arrive at the same surface.
- **SC-014**: Zero interface elements introduced by this feature express a negative quantity, a
  penalty, a warning, or fault — verified by reviewing every string, colour, and state history, the
  day detail, and their empty and failure states can show.
- **SC-015**: On a record seeded with three years of daily completions, the first screen of history
  appears within 500 ms and any day opens within 300 ms on a mid-range device, and recording a
  completion on the current day is no slower than it is without this feature present.
- **SC-016**: With reads made to fail, history shows a notice and a retry rather than an empty
  record; with the catalogue unavailable, it shows every week whose plans exist and names what cannot
  be built — verified in both cases, and in both cases the current day remains recordable.
- **SC-016a**: With plan storage made to fail, opening an unopened past day still shows the same
  figures its week row showed, reports no error to the user, and leaves no plan stored; restoring
  storage and reopening the day stores exactly one plan. With the catalogue version for that date
  made unavailable, the same day reports no record rather than an empty day scoring zero.
- **SC-017**: The feature functions identically with the network permanently unavailable. This holds
  structurally rather than by test — the application has no network surface on this path — so it is
  verified by the fresh-install airplane-mode smoke check plus the absence of any network dependency.

## Assumptions

- **The past is read-only, and that is the answer, not a deferral.** `docs/PLAN.md` asks this phase to
  decide between a grace window and a read-only past, and the read-only past is chosen deliberately.
  It costs the honest case where a practice done at 23:55 is logged at 00:05; it buys an accountability
  record that means what it says, `004`'s consistency rule intact, and an increment small enough to
  finish. Opening the window later is a named change to one policy in one place — the vocabulary and
  the single decision point both exist for exactly that reason.
- **`002` already separated when a completion happened from the day it counts for.** `docs/PLAN.md`
  lists that separation as introduced in this phase; it shipped in `002`, which stores both the
  credited date and the moment of recording. Nothing here needs to add it. Nothing here displays the
  recording time either — a past day shows what was done, not when it was logged, and a timestamp on
  every row invites exactly the forensic reading Principle IX argues against.
- **`DayWritePolicy` keeps its name.** `docs/PLAN.md` calls the object `DayEditPolicy`. `002` shipped
  it as `DayWritePolicy`, every write path already consults it, and renaming a correct policy to
  match a roadmap sentence would trade a real risk for a cosmetic gain. One object, one name, one
  opinion — which is the property the roadmap was actually asking for.
- **The day detail is `003`'s day summary, reached from a second place.** It already renders a stored
  plan with its completions and already carries no write path of any kind. Building a second screen
  would be a second opinion about what a past day is. What this increment adds to it is the plain
  statement of the recording rule and the no-record state for a date with no plan.
- **History does not inherit `003`'s backfill, and `003` is not changed.** Materialising a plan for an
  elapsed unopened date stays exactly the rule `003` shipped, in the one place it already lives, still
  triggered by the weekly sheet displaying a week. History simply does not trigger it: a continuous
  list back to the record start makes scrolling far too cheap an action to attach thousands of writes
  to. What history needs from an unplanned date is its available points, and those are derivable from
  an immutable task version without storing anything. The two paths must agree (FR-020b), and a test
  says so.
- **The record start is the earliest date carrying a plan.** There is no separate install date stored,
  and none is added — a date before the first plan is outside the record, which is the same statement
  for a fresh install and for a restored one.
- **No Hijri-only or Gregorian-only mode.** `docs/PLAN.md` offers the toggle as optional. Both labels
  are already shown together on every surface that has them, a toggle is a settings surface this
  increment has no reason to open, and the Hijri label a past day shows is the one snapshotted onto
  its plan either way.
- **No search, no jump-to-date, no filtering.** Scrolling reaches everything, the record is bounded
  by the record start, and Principle VIII forbids a navigation surface before scrolling has been
  shown to be insufficient.
- **History is reached from the weekly sheet.** The three-tab shell in the design is a Phase 6-and-
  later concern; this increment adds a destination, not a navigation architecture. Where it is
  reached from may change without any requirement here changing.
- **No notes, journalling, annotation, or export.** Reflection here means reading the record. Anything
  the user writes onto a past day is user-authored content attached to an immutable record, and both
  halves of that are out of scope by construction.
- **Catalogue content is still the placeholder from `002`.** Nothing in this increment depends on the
  task text. The integrity suite depends on being able to change point values and schedule rules,
  which the placeholder supports.
- **This increment adds no stored fact.** No table, no column, no migration. The historical-immutability
  test that Principle III requires of any increment touching persistence or the catalogue is therefore
  not triggered by a schema change — but it is the entire point of User Story 3 regardless, and this
  is the increment that owes it in full.

## Dependencies

Increment `002` — the frozen day plan, the append-only completion log with denormalised awarded
points, the catalogue's effective-version lookup, the injected time source, and `DayWritePolicy`,
which this increment surfaces and does not widen.

Increment `003` — the Saturday-to-Friday week rule, the week aggregate that each history row renders,
the read-only day summary that becomes the past-day detail, the record start, backfill, and Plan
Origin.

Increment `004` — the streak figures, which this increment must leave untouched. `004` recorded that
its consistency rule depends on completions existing only for dates the app was open on, and handed
that coupling to this phase; the read-only decision above discharges it without changing `004`.

All three are built and merged, so this increment extends working code rather than a design.

## Out of Scope

Retroactive completion of any kind, edit windows, grace periods, and unlocking a past day. Editing,
deleting, or annotating any recorded day. Charts, heatmaps, trends, monthly aggregates, and
per-section breakdowns — Phase 6. Export, sharing, and screenshots. Notes and journalling.
Search, filtering, and jump-to-date. Streak freezes, repairs, or restoration. Notifications.
Accounts, sync, and any Supabase dependency. Leaderboards and social comparison. Any task creation,
editing, reordering, or repricing.
