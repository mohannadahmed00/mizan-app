# Phase 0 Research: Core Daily Habit Tracking (MVP)

## Research Context

The feature specification was already thoroughly clarified during the
`/speckit.clarify` phase. No unresolved `[NEEDS CLARIFICATION]` markers
remain. This research phase validates architectural alignment and confirms
implementation approach.

## Research Tasks

### Task 1: Existing Domain Model Alignment

**Question**: Do the existing domain models (`Task`, `TaskCompletion`,
`CompactDate`, `SimpleDate`, `Category`, `Day`) cover all Phase 1 requirements?

**Decision**: Yes — with one addition.

**Rationale**:
- `Task` has `id`, `name`, `category`, `points`, `activeDays` — sufficient
  for the spec's FR-003 (load active tasks by day-of-week).
- `TaskCompletion` has `taskId`, `date` (CompactDate) — maps to FR-004/FR-005/
  FR-006 (load, complete, undo).
- `CompactDate` with hijri+gregorian `SimpleDate` — covers FR-001/FR-002
  (Gregorian + Hijri date determination).
- **Missing**: `Task` has no `description` or `icon` fields that the product
  doc lists as optional. Phase 1 can omit these; they can be added in Phase 2
  without breaking changes.

**Alternatives considered**: Adding `description` and `icon` now as nullable
fields. Rejected — violates Principle V (Simplicity/YAGNI) and would require
DB migration before the schema is even released.

### Task 2: GetTodayTasksUseCase Stub

**Question**: `GetTodayTasksUseCase` returns `emptyList()` — how should it be
implemented?

**Decision**: Implement as a concrete use case that calls
`HijriDateRepository.getTodayTasks()`.

**Rationale**:
- The existing `HijriDateRepository` interface already defines
  `suspend fun getTodayTasks(): List<Task>`.
- The use case should delegate to the repository, filter by `activeDays`
  containing today's day-of-week, and return tasks with `isActive == true`
  (once that field exists).
- Must be annotated `@Factory` for Koin DI, with `@Provided` dependency on
  `HijriDateRepository` and `SystemDateProvider`.

**Alternatives considered**: Embedding filtering logic directly in the use
case vs. the repository. Chosen: use case filters, repository fetches all
tasks — keeps separation of concerns per Clean Architecture.

### Task 3: Task Completion Persistence

**Question**: How should task completions be persisted?

**Decision**: New Room table `task_completions` with columns `taskId` (Long),
`gregorianDateKey` (String), and composite primary key of `(taskId,
gregorianDateKey)`.

**Rationale**:
- Matches the clarified uniqueness constraint: one completion per task per day.
- `gregorianDateKey` is a string like "2026-07-10" — consistent with the
  existing `CompactDateEntity.gregorianDateKey` pattern.
- `@Insert(onConflict = OnConflictStrategy.REPLACE)` for both insert and
  delete semantics (delete = remove row, insert = add row).

### Task 4: Points and Streak Calculation

**Question**: Should points and streaks be calculated in the domain layer or
data layer?

**Decision**: Domain layer (use cases).

**Rationale**:
- Per Clean Architecture, business logic belongs in `:domain`.
- Points are derived dynamically from `List<TaskCompletion>` + `List<Task>`
  (point values) — a pure function.
- Streaks are calculated from historical completions — also a pure function.
- `:data` should only provide raw persistence; domain interprets the data.

**Alternatives considered**: Database-level calculation (SQL aggregation).
Rejected — couples business rules to storage, harder to test, violates
Principle I (Clean Architecture).

### Task 5: ViewModel Layer Design

**Question**: How should the dashboard and stats screens be structured?

**Decision**: Two ViewModels: `DashboardViewModel` and `StatsViewModel`.

**Rationale**:
- `DashboardViewModel` handles US1 (view dashboard) + US2 (complete/undo
  tasks). State includes tasks list, completions, progress, points, dates.
- `StatsViewModel` handles US3 (view statistics). State includes current
  streak, longest streak, daily/weekly/monthly percentages, points summary.
- Both extend `BaseViewModel` per Principle VI.
- One-shot effects (navigation, snackbar) via sealed interface `ViewEffect`.
- Use cases injected via `@Inject constructor`.

### Task 6: Pre-Seeded Task Data

**Question**: What tasks should be pre-seeded?

**Decision**: 10 tasks with the following configuration:

| Task | Category | Points | Active Days |
|------|----------|--------|-------------|
| Fajr | FAJR | 5 | All 7 |
| Dhuhr | DHUHR | 5 | All 7 |
| Asr | ASR | 5 | All 7 |
| Maghrib | MAGHRIB | 5 | All 7 |
| Isha | ISHA | 5 | All 7 |
| Quran Reading | QURAN | 10 | All 7 |
| Morning Adhkar | ADKAR | 5 | All 7 |
| Evening Adhkar | ADKAR | 5 | All 7 |
| Monday Fast | FAST | 20 | MO |
| Thursday Fast | FAST | 20 | TH |

**Rationale**: Matches the point values from the product spec
(`docs/mizanapp.md`). Category enum `Category` already has all needed
values (`FAJR`, `DHUHR`, `ASR`, `MAGHRIB`, `ISHA`, `QURAN`, `ADKAR`, `FAST`,
`OTHER`). Seeded via Room database callback or a migration, not hardcoded
in app code.

## Summary

All research questions resolved. The existing codebase provides a solid
foundation — only incremental additions needed:
1. New Room entities/tables for Task and TaskCompletion
2. `GetTodayTasksUseCase` implementation
3. `HijriDateRepositoryImpl.getTodayTasks()` implementation
4. ViewModel + Compose UI layer
5. Task seeding mechanism
