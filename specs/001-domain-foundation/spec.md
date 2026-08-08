# Feature Specification: Domain Foundation — Validation Contract, Glossary, Decisions

**Feature Branch**: `spec/001-domain-foundation`

**Created**: 2026-08-08

**Status**: Draft

**Input**: User description: "Read @docs/PLAN.md and create a specification for the phase 1"

## Overview

Roadmap Phase 1 (`docs/PLAN.md`), narrowed. **No production application code ships in this
feature.**

Phase 1 as written in the roadmap bundles three things: the catalogue validation rules, the domain
glossary, and the recorded architectural decisions — plus the catalogue content itself. The content
is not available in this repository (the ~40 Arabic task names and section labels exist only on the
source sheet), so **authoring the catalogue is deferred to a follow-on feature**. See
*Deferred to 002*.

What remains is the part that does not depend on the content: the **executable contract the
catalogue must satisfy**, the vocabulary, and the decision record. The arithmetic is fully known
from `docs/PLAN.md`, so the contract is completely specifiable today.

Deliberately ordering it this way: the catalogue then lands against a validation that already
exists and was written without knowledge of it, rather than a validation reverse-engineered to fit
whatever was typed. That is Principle I applied to data.

**Actor note**: no end-user-facing surface. The actor in every story is the person building and
maintaining Mizan. End-user value is indirect but real — every number the app later asks a user to
trust is admitted by the contract authored here.

## Clarifications

### Session 2026-08-08

- Q: What form should a task's stable identifier take in the catalogue — an opaque UUID, or a human-readable slug? → A: Human-readable stable slug (e.g. `fajr-sunnah-before`), unique across the catalogue, never reused.
- Q: Should each catalogue version record the date it took effect, so the app can work out which version applied on a day the user never opened? → A: Yes. Monotonic integer version, plus an effective-from date on each version.
- Q: Where should the twelve architectural decisions live once answered — a new standalone record, or `docs/PLAN.md` edited in place? → A: In place. Rename the section to *Architectural Decisions (Recorded)* and replace each recommendation with the decision plus rationale. No second document.
- Q: Should a task's display position order it only within its own section, or across the whole catalogue? → A: Section-scoped. Unique within a section; sections carry their own order.
- Q: Should the project spell it "catalogue" or "catalog"? → A: "catalogue" everywhere, matching the constitution. `docs/PLAN.md` is corrected to match as part of this feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Catalogue validation contract (Priority: P1)

The maintainer needs an executable statement of what makes a task catalogue valid, written before
any catalogue exists, so that the real catalogue is admitted only if it is arithmetically and
structurally correct.

The contract is exercised against purpose-built fixture catalogues — at least one known-good and a
set of known-bad ones — since the real catalogue is not yet available.

**Why this priority**: This is the whole risk of the project reduced to one artifact. A wrong total
is worse than a missing feature, and under Principle III a wrong total recorded against a past day
can never be corrected. The contract is the only thing standing between a typo and permanent
corrupted history.

**Independent Test**: Run the contract against the fixtures. It passes the good one and fails every
bad one, with a distinct failure per defect. Delivers value with no catalogue and no app.

**Acceptance Scenarios**:

1. **Given** a fixture catalogue, **When** the contract runs, **Then** it rejects any catalogue in
   which two tasks share a stable identifier.
2. **Given** a fixture catalogue, **When** the contract runs, **Then** it rejects any task with a
   point value of zero or less, an occurrence maximum below 1, a missing section, or no schedule
   rule.
3. **Given** a fixture catalogue, **When** the contract runs, **Then** it rejects any task whose
   schedule rule matches no day of the week.
4. **Given** a known-good fixture, **When** available points are summed per weekday, **Then** the
   contract asserts **69** for Saturday, Sunday, Tuesday and Wednesday; **74** for Monday and
   Thursday; **76** for Friday.
5. **Given** a known-good fixture, **When** the seven daily totals of one Saturday-to-Friday week
   are summed, **Then** the contract asserts **500**.
6. **Given** a known-good fixture, **When** section composition is checked, **Then** the contract
   asserts Fajr 6 tasks × 2 points, Dhuhr 4 × 2, Asr 3 × 2, Maghrib 3 × 2, Isha 3 × 2 (38 total);
   Qiyam and Witr 9; Quran memorisation and reading 4; nine Adhkar 18 — summing to the 69-point
   base day.
7. **Given** two tasks in the same section sharing a display position, **When** the contract runs,
   **Then** it rejects the catalogue; **and given** two tasks in *different* sections sharing a
   display position, **Then** it accepts them.
8. **Given** a known-good fixture, **When** any single point value is altered, **Then** the contract
   fails. A contract that cannot fail is not a contract.
9. **Given** any fixture, **When** the contract reports, **Then** each defect appears as its own
   distinct failure rather than a single aggregate error.

---

### User Story 2 - Domain glossary (Priority: P2)

The maintainer needs one written definition per domain concept, so that a term means the same thing
in a specification, a test name, and a type name. Twelve terms: Task Definition, Task Version,
Section, Schedule Rule, Day Plan, Planned Task, Completion, Occurrence, Daily Score, Weekly Score,
Consistency Day, Streak.

**Why this priority**: Cheaper and less risky than the contract, but it is what stops two later
specifications using "task" to mean two different things. Required before Phase 2's specifications
can be written precisely.

**Independent Test**: Review the glossary against the term list. Every term defined; every
definition expressible without reference to a framework, database, or screen.

**Acceptance Scenarios**:

1. **Given** the glossary, **When** each of the twelve terms is looked up, **Then** a definition
   exists.
2. **Given** the glossary, **When** a definition is read, **Then** it distinguishes the term from
   its nearest neighbour — Task Definition from Task Version, Day Plan from Planned Task,
   Completion from Occurrence.
3. **Given** the glossary, **When** a definition is read, **Then** it names no technology.

---

### User Story 3 - Recorded architectural decisions (Priority: P3)

The maintainer needs each early decision answered once, in writing, with a one-line rationale, so
no later feature reopens it mid-build. The list is the twelve items under *Architectural Decisions
to Make Early* in `docs/PLAN.md`. The answers replace that section in place rather than forming a
second document, so the roadmap cannot outlive the decisions it recommended.

**Why this priority**: Lowest of the three because several answers are already fixed by the
constitution and need recording rather than deciding. Still gates Phase 2 — an unrecorded decision
gets re-litigated.

**Independent Test**: Read the record against the twelve-item list. Each has an answer and a
rationale; no answer contradicts the constitution.

**Acceptance Scenarios**:

1. **Given** the record, **When** each of the twelve decisions is looked up, **Then** it has an
   answer and a one-line rationale.
2. **Given** the record, **When** the day- and week-boundary answers are read, **Then** they state
   local midnight to local midnight and Saturday to Friday, and name the single place each rule
   will live.
3. **Given** the record, **When** the identity decision is read, **Then** it states client-generated
   stable identifiers and rules out auto-incrementing keys.
4. **Given** the record, **When** any answer is compared against the constitution, **Then** no
   answer contradicts a principle.

---

### Edge Cases

- Two tasks share a stable identifier — rejected, not silently de-duplicated.
- A zero or negative point value — rejected. Scores are never negative (Principle IX).
- A schedule rule matching no day of the week — rejected as unreachable content.
- An occurrence maximum of zero — rejected; a task that can never be completed is not a task.
- A point value edited after a catalogue has shipped — must produce a **new catalogue version**,
  never an in-place edit, because past days stay frozen against the version current for them
  (Principle III).
- Friday activities and the Monday/Thursday fast never co-occur, since no date is both — the
  contract must not encode them as mutually exclusive, only as weekday-scheduled.
- A date-anchored rule (Ramadan, Ashura) is not needed yet — the rule vocabulary must have room for
  one without redefining existing rules.
- A catalogue is loaded twice — the definition must be idempotent; reload changes nothing.
- Two catalogue versions share an effective-from date, or a higher version takes effect earlier than
  a lower one — rejected. "Which version was current on this date" must have exactly one answer.
- A date falls before the earliest effective-from date — no version is current, and the contract
  must say so rather than defaulting to the earliest.
- The contract is run with no catalogue present — it must report that clearly, not pass vacuously.

## Requirements *(mandatory)*

### Functional Requirements

**Validation contract**

- **FR-001**: A validation contract MUST exist that decides whether a given task catalogue is
  admissible.
- **FR-002**: The contract MUST require each task to carry a stable identifier that is a
  human-readable slug (lower-case words separated by hyphens, e.g. `fajr-sunnah-before`), unique
  across the catalogue. An identifier MUST NOT be reused for a different task once published, and
  MUST NOT change when the task's points or schedule change.
- **FR-003**: The contract MUST require each task to carry exactly one section, one positive point
  value, one schedule rule, one maximum-occurrences-per-day of at least 1, and one display position.
- **FR-003a**: Display position MUST be scoped to the task's section — unique within that section,
  and permitted to repeat across different sections. Sections MUST carry their own ordering,
  independent of the tasks inside them.
- **FR-004**: The contract MUST require the catalogue to carry a version identifier that changes
  whenever any task's points, schedule, occurrence limit, or membership changes. The identifier MUST
  be a monotonically increasing integer.
- **FR-004a**: The contract MUST require every catalogue version to carry an effective-from date,
  and MUST reject a catalogue in which version order and effective-from order disagree.
- **FR-004b**: The contract MUST reject a catalogue in which two versions share an effective-from
  date, so that exactly one version is resolvable as current for any date on or after the earliest
  effective-from date.
- **FR-005**: The contract MUST accept a schedule-rule vocabulary covering "every day" and "specific
  days of the week", and MUST admit date-anchored rules later without redefining existing rules.
- **FR-006**: The contract MUST assert per-weekday available-point totals of 69, 74 and 76, and a
  Saturday-to-Friday weekly total of 500.
- **FR-007**: The contract MUST assert the section-level composition in User Story 1, scenario 6.
- **FR-008**: The contract MUST report each defect as a distinct failure, not as one aggregate.
- **FR-009**: The contract MUST be exercised against at least one known-good fixture and one
  known-bad fixture per rule it enforces.
- **FR-010**: The contract MUST fail when a known-good fixture is mutated. A contract that cannot
  fail MUST NOT be considered complete.
- **FR-011**: The contract MUST distinguish "no catalogue present" from "catalogue valid".

**Glossary**

- **FR-012**: The glossary MUST define each of the twelve named terms.
- **FR-012a**: The canonical spelling is **catalogue**, matching the constitution. `docs/PLAN.md`
  MUST be corrected from "catalog" to "catalogue" as part of this feature. No document may use both.
- **FR-013**: Each definition MUST be free of technology names.
- **FR-014**: The glossary MUST explicitly distinguish Task Definition from Task Version, Day Plan
  from Planned Task, and Completion from Occurrence.

**Decision record**

- **FR-015**: The record MUST answer each of the twelve early decisions with a one-line rationale,
  and MUST live in `docs/PLAN.md` itself. The section currently titled *Architectural Decisions to
  Make Early* MUST be renamed to *Architectural Decisions (Recorded)*, and each item's
  recommendation MUST be replaced by the decision taken. No second document may restate them.
- **FR-016**: The record MUST NOT contain an answer contradicting the constitution. Where the
  constitution already fixes an answer, the record MUST cite the principle rather than restate a
  competing decision.
- **FR-017**: The record MUST name, for the day boundary and the week boundary, the single location
  each rule will occupy.

**Scope guards**

- **FR-018**: This feature MUST NOT define storage schemas, screens, network calls, or module
  wiring.
- **FR-019**: The contract MUST reject any catalogue carrying an affordance, field, or flag that
  would let an end user create, edit, delete, reorder, reprice, or reschedule a task.
- **FR-020**: No artifact in this feature may ship inside the running application. The contract is
  developer-facing tooling exercised against fixtures.

### Key Entities

Vocabulary. None is given concrete content in this feature.

- **Task Definition**: A practice that can be recorded. Identity is a human-readable slug; carries a
  section and a display position.
- **Task Version**: The point value, schedule rule and occurrence limit a Task Definition had under
  a given catalogue version. What a past day is scored against.
- **Section**: The grouping a task is displayed and totalled under — prayer blocks, Qiyam/Witr,
  Quran, Adhkar, the weekday fast, the Friday activities. Carries its own ordering; task display
  positions are scoped inside it.
- **Schedule Rule**: The statement of which dates a task applies to.
- **Catalogue Version**: A monotonically increasing integer fixing the whole set of Task Versions,
  paired with the date from which it takes effect. Together these make "which version applied on a
  given date" answerable from the catalogue alone.
- **Occurrence**: One recordable instance of a task within a day, bounded by the task's maximum.
- **Day Plan**: The frozen set of tasks applicable to one date and the points available on it.
- **Planned Task**: One task's entry within a Day Plan.
- **Completion**: A recorded occurrence, carrying the points awarded at the time.
- **Daily Score / Weekly Score**: Earned against available, for a day or a Saturday-to-Friday week.
- **Consistency Day**: A date on which at least one applicable task was completed.
- **Streak**: A run of consecutive Consistency Days.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The contract passes the known-good fixture and fails **every** known-bad fixture.
- **SC-002**: The contract enforces the totals 69 / 74 / 76 per weekday and 500 per week; mutating
  any single point value in the good fixture causes a failure.
- **SC-003**: Every rule in FR-002 through FR-007 has at least one dedicated failing fixture. Zero
  rules are asserted without a fixture proving they can fail.
- **SC-004**: All **12** glossary terms are defined; zero terms used in this specification are left
  undefined.
- **SC-005**: All **12** early decisions carry a recorded answer and rationale; zero contradict the
  constitution. Zero occurrences of "catalog" (without the trailing "ue") remain in any project
  document, excluding passages that quote the spelling itself in order to forbid it. Baseline at
  clarification time: `docs/PLAN.md` 32, constitution 0, `CLAUDE.md` 0.
- **SC-006**: A maintainer who has never seen the paper sheet can state, from the contract alone,
  every structural and arithmetic condition a catalogue must meet.
- **SC-007**: Zero lines of production application code are added. Nothing from this feature is
  reachable from a running app.

## Assumptions

- **Fixtures stand in for the real catalogue.** The arithmetic is known from `docs/PLAN.md`, so a
  known-good fixture can be constructed that hits 69 / 74 / 76 / 500 using placeholder task text.
  Task *text* is irrelevant to every rule the contract enforces.
- **Day and week boundaries are already fixed** by Principle VII (local midnight to local midnight;
  Saturday to Friday). Recorded here, not decided.
- **Identity, sync-ready fields and tombstones are already fixed** by Principle V. Recorded, not
  decided.
- **DI framework is not an open question.** `develop-v1` carries no DI code, so Koin is recorded
  rather than migrated to.
- **Task content is Arabic.** The interface shell language is a product decision recorded here, not
  settled in the catalogue — the catalogue holds content, not interface strings.
- **The nine Adhkar are one section of nine tasks at 2 points each**, and **the seven Friday
  activities are 1 point each, additional to the 69-point base** (76 − 69 = 7). Both derived from
  the arithmetic in `docs/PLAN.md`, to be confirmed when the source sheet arrives.
- **Contract placement is an implementation choice** for `/speckit-plan`. Constraint: it must not
  require creating a module that has no other code in it yet.

## Dependencies

None. This is the first increment.

## Out of Scope

Authoring the real catalogue (see below); anything else requiring the source sheet; storage schemas
and migrations; screens and layouts; network calls; module creation and dependency wiring; sync
design; leaderboard rules; notification content; anything a user can click.

In scope by consequence of clarification: edits to `docs/PLAN.md` — recording the twelve decisions
in place (FR-015) and correcting the "catalog" spelling (FR-012a).

## Deferred to 002

Authoring the real task catalogue — the ~40 Arabic task names, exact section labels, and their
assignment to the point values and schedule rules specified here. Blocked on the source sheet.

When it lands, the acceptance test is already written: **the real catalogue must pass this
feature's contract unmodified.** If the contract has to be relaxed to admit the real catalogue,
that is a finding about one of them, not a licence to edit the contract quietly.

## Note on Principle VIII

Building a validation contract before the data it validates could read as speculative abstraction.
It is not, on two grounds: the catalogue is the immediately next increment and is named, and the
contract is testable and complete on its own today. The alternative — author first, validate after —
inverts Principle I and produces a validation shaped to fit whatever was typed.
