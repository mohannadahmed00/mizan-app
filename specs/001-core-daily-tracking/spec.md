# Feature Specification: Core Daily Habit Tracking (MVP)

**Feature Branch**: `001-core-daily-tracking`

**Created**: 2026-07-10

**Status**: Draft

**Input**: User description: "create specifications from docs/mizanapp.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Daily Dashboard (Priority: P1)

The user opens the app and immediately sees today's dashboard: the Gregorian and
Hijri dates, a list of tasks scheduled for today, which ones are already
completed, their overall progress for the day, and points earned so far.

**Why this priority**: The dashboard is the primary screen and the first thing
users see every time they open the app. Without it the app delivers no value.

**Independent Test**: Launch the app on a day with 5 scheduled tasks and 0
completions. The dashboard shows all 5 tasks, progress at 0%, and points at 0.

**Acceptance Scenarios**:

1. **Given** the user opens the app for the first time today with 5 tasks scheduled,
   **When** the dashboard loads, **Then** all 5 tasks are visible and marked
   incomplete, completion progress shows 0%, and today's points show 0.
2. **Given** the user previously completed 2 of today's 5 tasks,
   **When** the dashboard loads, **Then** the 2 completed tasks show a
   completed state, progress shows 40%, and points reflect the sum of those
   2 tasks' values.

---

### User Story 2 - Complete and Undo Tasks (Priority: P1)

The user taps a task to mark it completed, then sees the progress bar update,
points increase, and the task visually change state. The user can tap again to
undo the completion.

**Why this priority**: Marking tasks complete is the core interaction loop of
the entire app. Without it nothing can be tracked.

**Independent Test**: The user completes 3 of 5 visible tasks. Progress shows
60% and total points equal the sum of the 3 completed tasks' values.

**Acceptance Scenarios**:

1. **Given** 5 tasks are displayed and 0 are completed,
   **When** the user taps an incomplete task,
   **Then** it becomes checked, progress increases proportionally, and points
   add the task's value.
2. **Given** a task is already marked completed,
   **When** the user taps it again,
   **Then** the task reverts to incomplete, progress decreases, and points
   subtract the task's value.

---

### User Story 3 - View Statistics and Streaks (Priority: P2)

The user navigates to a statistics screen to review their consistency. They see
their current streak (consecutive days with at least one completion), longest
ever streak, daily/weekly/monthly completion percentages, and points earned.

**Why this priority**: Statistics and streaks provide the motivation and
accountability that distinguish this app from a simple checklist.

**Independent Test**: The user completes at least one task for 3 consecutive
days. The statistics screen shows current streak of 3 days.

**Acceptance Scenarios**:

1. **Given** the user completed tasks yesterday and today,
   **When** they view the statistics screen,
   **Then** current streak shows 2 days.
2. **Given** the user completed tasks for 7 consecutive days last month but
   missed yesterday,
   **When** they view the statistics screen,
   **Then** current streak shows 0, longest streak shows 7 days.

---

### Edge Cases

- What happens when no tasks are scheduled for the current day? The dashboard
  shows a message indicating no tasks scheduled today.
- What happens when all tasks for the day are completed? The dashboard shows
  100% progress and a congratulatory state.
- What happens during midnight rollover? The app refreshes the task list and
  progress to reflect the new day's tasks.
- What happens on first launch with no cached Hijri data? The app shows a
  loading/skeleton state while downloading the Hijri calendar, then displays
  the full dashboard (including Hijri date) once cached.
- What happens when Hijri calendar sync fails? The app continues using the
  Gregorian date and retries on next launch; existing cached data is used if
  available.
- What happens when a user toggles a task rapidly? Each toggle completes
  atomically; the UI reflects the final state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST determine today's Gregorian date on launch.
- **FR-002**: System MUST retrieve the corresponding Hijri date from a local
  cache; if absent, download the monthly Hijri calendar from an external API
  and store it locally.
- **FR-003**: System MUST load all active tasks whose `activeDays` include
  today's day of the week.
- **FR-004**: System MUST load today's completion records for the current user.
- **FR-005**: Users MUST be able to mark any active task as completed for today
  with a single tap.
- **FR-006**: Users MUST be able to undo a completion with a single tap.
- **FR-007**: System MUST derive today's points dynamically from completion
  records (no stored score).
- **FR-008**: System MUST calculate the current streak — consecutive days with
  at least one completed task ending today.
- **FR-009**: System MUST calculate the longest ever streak from all historical
  completion records.
- **FR-010**: System MUST calculate and display completion percentage for the
  current day (completed tasks / total tasks).
- **FR-011**: System MUST calculate and display completion percentages for
  the current week and month.
- **FR-012**: System MUST function fully offline after initial Hijri calendar
  sync; no network access is required for core tracking and statistics.

### Key Entities *(include if feature involves data)*

- **Task**: A recurring activity definition with a name, category, point value,
  and set of active days. Does not store completion state.
- **TaskCompletion**: A record linking a task to a specific date (day-level
  granularity). The existence of this record means the task was completed.
  A task MAY be completed at most once per day — `(taskId, completedDate)` is
  unique.
- **CompactDate**: Date representation holding both Gregorian and Hijri
  components for any given day.
- **SimpleDate**: A minimal date structure (day, month, year) used to
  reference calendar dates without timezone complexity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The dashboard loads all scheduled tasks for today within 1 second
  of app launch on a mid-range device.
- **SC-002**: Task completion state and point totals update instantly (no
  perceivable delay) when the user toggles a task.
- **SC-003**: The app is fully functional offline after initial Hijri calendar
  sync — users can view, complete, and undo tasks, and see statistics without
  any network connection.
- **SC-004**: Streak calculations are accurate to the day level — a user who
  completes tasks on consecutive days sees their current streak increment by
  exactly 1 each day.
- **SC-005**: Completion percentage matches the ratio of completed to
  scheduled tasks with no rounding errors that would misrepresent user progress.

## Assumptions

- The app targets a single user with no authentication required in Phase 1.
- The device clock is assumed to be accurate; no timezone or DST edge cases
  are handled in this phase.
- The Hijri calendar API uses the Umm al-Qura calendar system (consistent with
  existing `:data` module implementation using aladhan.com).
- Default Islamic tasks (5 daily prayers, morning/evening adhkar, Monday and
  Thursday fasts, Quran reading) are pre-seeded on first launch with a
  mechanism for future customization. Task activation/deactivation is deferred
  to Phase 2; all pre-seeded tasks are always visible in Phase 1.
- Streaks are calculated as consecutive calendar days with at least one
  completion (overall streak), not per-task streaks.

## Clarifications

### Session 2026-07-10

- Q: Can the same task be completed more than once per day? → A: No, a task may be completed at most once per day. `(taskId, completedDate)` is unique.
- Q: Should the dashboard show a loading state during first-launch Hijri calendar sync? → A: Yes, show a loading/skeleton state on first launch only while the Hijri calendar downloads. Subsequent launches show cached data instantly.
- Q: Should Phase 1 include a toggle to activate/deactivate pre-seeded tasks? → A: No, all pre-seeded tasks are always visible. Deactivation arrives with custom tasks in Phase 2.
