---
description: "Task list for Core Daily Habit Tracking (MVP) — Phase 1 of MizanApp"
---

# Tasks: Core Daily Habit Tracking (MVP)

**Input**: Design documents from `/specs/001-core-daily-tracking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: ❌ Tests are OPTIONAL per template. However, the project constitution
(Principle III — Test-First NON-NEGOTIABLE) REQUIRES TDD for domain and data
layers. Domain + data test tasks ARE included below.

**Organization**: Tasks are grouped by user story to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **`:domain`**: `domain/src/main/java/com/giraffe/domain/`
- **`:data`**: `data/src/main/java/com/giraffe/data/`
- **`:presentation`**: `presentation/src/main/java/com/giraffe/presentation/`
- **`:app`**: `app/src/main/java/com/giraffe/mizanapp/`
- **Domain tests**: `domain/src/test/java/com/giraffe/domain/usecase/`
- **Data tests**: `data/src/test/java/com/giraffe/data/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and dependency configuration

- [ ] T001 [P] Add presentation dependencies for ViewModel + Compose in
  `presentation/build.gradle.kts`:
  - `implementation(libs.koin.compose)`
  - `implementation(libs.koin.compose.viewmodel)`
  - `implementation(platform(libs.androidx.compose.bom))`
  - `implementation(libs.androidx.compose.material3)`
  - `implementation(libs.androidx.compose.ui)`
  - `implementation(libs.androidx.activity.compose)`
  - `implementation(libs.androidx.lifecycle.viewmodel)`
  - `implementation(libs.androidx.lifecycle.runtime.compose)`

- [ ] T002 [P] Create `presentation/src/main/java/com/giraffe/presentation/`
  directory structure with subdirectories: `dashboard/`, `stats/`, `common/`

- [ ] T003 [P] Create `data/src/main/java/com/giraffe/data/mapper/`
  directory for entity ↔ domain mappers

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Create `TaskEntity` in
  `data/src/main/java/com/giraffe/data/datasource/local/entity/TaskEntity.kt`:
  ```kotlin
  @Entity(tableName = "tasks")
  data class TaskEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val name: String,
      val category: String,
      val points: Int,
      val activeDays: String,
      val isActive: Boolean = true
  )
  ```

- [ ] T005 Create `TaskCompletionEntity` in
  `data/src/main/java/com/giraffe/data/datasource/local/entity/TaskCompletionEntity.kt`:
  ```kotlin
  @Entity(
      tableName = "task_completions",
      primaryKeys = ["taskId", "gregorianDateKey"],
      foreignKeys = [ForeignKey(
          entity = TaskEntity::class,
          parentColumns = ["id"],
          childColumns = ["taskId"],
          onDelete = ForeignKey.CASCADE
      )]
  )
  data class TaskCompletionEntity(
      val taskId: Long,
      val gregorianDateKey: String,
      val completedAt: Long
  )
  ```

- [ ] T006 Create `TaskDao` in
  `data/src/main/java/com/giraffe/data/datasource/local/dao/TaskDao.kt`:
  ```kotlin
  @Dao
  interface TaskDao {
      @Query("SELECT * FROM tasks WHERE isActive = 1")
      suspend fun getAllActive(): List<TaskEntity>

      @Query("SELECT * FROM tasks WHERE id = :taskId")
      suspend fun getById(taskId: Long): TaskEntity?

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insertAll(tasks: List<TaskEntity>)

      @Query("DELETE FROM tasks")
      suspend fun deleteAll()
  }
  ```

- [ ] T007 Create `TaskCompletionDao` in
  `data/src/main/java/com/giraffe/data/datasource/local/dao/TaskCompletionDao.kt`:
  ```kotlin
  @Dao
  interface TaskCompletionDao {
      @Query("SELECT * FROM task_completions WHERE gregorianDateKey = :dateKey")
      suspend fun getCompletionsForDate(dateKey: String): List<TaskCompletionEntity>

      @Query("SELECT * FROM task_completions")
      suspend fun getAllCompletions(): List<TaskCompletionEntity>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insert(completion: TaskCompletionEntity)

      @Query("DELETE FROM task_completions WHERE taskId = :taskId AND gregorianDateKey = :dateKey")
      suspend fun delete(taskId: Long, dateKey: String)
  }
  ```

- [ ] T008 Update `AppDatabase` in
  `data/src/main/java/com/giraffe/data/datasource/local/database/AppDatabase.kt`:
  - Add `TaskEntity` and `TaskCompletionEntity` to `entities` array
  - Bump `version = 2`
  - Add `autoMigrations = [AutoMigration(from = 1, to = 2)]`
  - Add abstract DAO methods: `taskDao(): TaskDao` and
    `taskCompletionDao(): TaskCompletionDao`

- [ ] T009 [P] Create `TaskMapper` in
  `data/src/main/java/com/giraffe/data/mapper/TaskMapper.kt`:
  - `fun TaskEntity.toDomain(): Task` — maps `category` string to
    `Category.valueOf()`, splits `activeDays` by comma and maps each to
    `Day.valueOf()`
  - `fun Task.toEntity(): TaskEntity` — reverse mapping
  - Reference: `data-model.md` Task ↔ TaskEntity mapping table

- [ ] T010 [P] Create `TaskCompletionMapper` in
  `data/src/main/java/com/giraffe/data/mapper/TaskCompletionMapper.kt`:
  - `fun TaskCompletionEntity.toDomain(taskDao: TaskDao, hijriDateDao: HijriDateDao): TaskCompletion`
    — maps `gregorianDateKey` to `CompactDate` via `HijriDateDao`; maps
    `completedAt` directly
  - Reference: `data-model.md` TaskCompletion ↔ TaskCompletionEntity mapping

- [ ] T011 [P] Create `TaskSeeder` in
  `data/src/main/java/com/giraffe/data/datasource/local/seed/TaskSeeder.kt`:
  - Implement `RoomDatabase.Callback` that inserts 10 pre-seeded tasks on
    `onCreate`:
    - Fajr (FAJR, 5pts, all 7 days), Dhuhr (DHUHR, 5pts, all 7),
      Asr (ASR, 5pts, all 7), Maghrib (MAGHRIB, 5pts, all 7),
      Isha (ISHA, 5pts, all 7), Quran Reading (QURAN, 10pts, all 7),
      Morning Adhkar (ADKAR, 5pts, all 7), Evening Adhkar (ADKAR, 5pts, all 7),
      Monday Fast (FAST, 20pts, MO only), Thursday Fast (FAST, 20pts, TH only)
  - Use `Executors.newSingleThreadScheduledExecutor()` for background execution
  - Reference: `research.md` Task 6 table for exact values

- [ ] T012 Implement `HijriDateRepositoryImpl.getTodayTasks()` in
  `data/src/main/java/com/giraffe/data/repository/HijriDateRepositoryImpl.kt`:
  - Inject `TaskDao` into constructor
  - `override suspend fun getTodayTasks()` calls `taskDao.getAllActive()`
    and maps each entity via `TaskMapper.toDomain()`
  - The existing `@Single` annotation is already present
  - Inject `TaskDao` as a constructor parameter

- [ ] T013 [P] Implement `GetTodayTasksUseCase` in
  `domain/src/main/java/com/giraffe/domain/usecase/GetTodayTasksUseCase.kt`:
  - Replace current stub with:
    ```kotlin
    @Factory
    class GetTodayTasksUseCase(
        @Provided private val hijriDateRepository: HijriDateRepository,
        @Provided private val systemDateProvider: SystemDateProvider,
    ) {
        suspend operator fun invoke(): List<Task> {
            val today = systemDateProvider.getCurrentGregorianDate()
            val todayDayOfWeek = Day.valueOf(
                java.time.LocalDate.of(today.year, today.month, today.day)
                    .dayOfWeek.name.take(2)
            )
            return hijriDateRepository.getTodayTasks()
                .filter { it.activeDays.contains(todayDayOfWeek) }
        }
    }
    ```
  - `SimpleDate` does not carry day-of-week — derive it from `java.time.LocalDate`
  - Map `java.time.DayOfWeek.name` (e.g. "MONDAY") to `Day` enum via
    `name.take(2)` ("MO") then `Day.valueOf()`

- [ ] T014 [P] Create `ToggleTaskCompletionUseCase` in
  `domain/src/main/java/com/giraffe/domain/usecase/ToggleTaskCompletionUseCase.kt`:
  - `@Factory` class with `@Provided HijriDateRepository` dependency
  - `suspend operator fun invoke(taskId: Long, date: CompactDate)` toggles
    completion: if a completion record exists for `(taskId, date.gregorian)`,
    delete it; otherwise insert a new one
  - This requires adding `insertCompletion` and `deleteCompletion` methods
    to the `HijriDateRepository` interface, which then delegates to
    `TaskCompletionDao`

- [ ] T015 [P] Create `GetTodayStatsUseCase` in
  `domain/src/main/java/com/giraffe/domain/usecase/GetTodayStatsUseCase.kt`:
  - `@Factory` with `@Provided HijriDateRepository`
  - `suspend operator fun invoke(): TodayStats` returns:
    - `totalTasks: Int` — count of today's active tasks
    - `completedTasks: Int` — count of today's completed tasks
    - `completionPercent: Double` — `completedTasks.toDouble() / totalTasks * 100`
    - `todayPoints: Int` — sum of points for today's completed tasks
  - Create a `TodayStats` data class in `domain/src/main/java/com/giraffe/domain/usecase/TodayStats.kt`

- [ ] T016 [P] Create `GetStreaksUseCase` in
  `domain/src/main/java/com/giraffe/domain/usecase/GetStreaksUseCase.kt`:
  - `@Factory` with `@Provided HijriDateRepository`
  - `suspend operator fun invoke(): StreakData` returns:
    - `currentStreak: Int` — consecutive days ending today with ≥1 completion
    - `longestStreak: Int` — max consecutive days with ≥1 completion ever
  - Algorithm: Sort all completions by date, iterate to find consecutive
    day runs
  - Create a `StreakData` data class

- [ ] T017 [P] Update `HijriDateRepository` interface in
  `domain/src/main/java/com/giraffe/domain/repository/HijriDateRepository.kt`:
  - Add methods: `suspend fun getCompletionsForDate(dateKey: String): List<TaskCompletion>`
  - Add methods: `suspend fun getAllCompletions(): List<TaskCompletion>`
  - Add methods: `suspend fun insertCompletion(taskId: Long, dateKey: String, completedAt: Long)`
  - Add methods: `suspend fun deleteCompletion(taskId: Long, dateKey: String)`

- [ ] T018 Update `HijriDateRepositoryImpl` in
  `data/src/main/java/com/giraffe/data/repository/HijriDateRepositoryImpl.kt`:
  - Inject `TaskCompletionDao`
  - Implement the 4 new interface methods delegating to `TaskCompletionDao`
    and using `TaskCompletionMapper`

- [ ] T019 [P] Add TaskDao and TaskCompletionDao to DatabaseModule in
  `data/src/main/java/com/giraffe/data/di/DatabaseModule.kt`:
  - Ensure `@Single` provides for `TaskDao` and `TaskCompletionDao`
    are wired within the existing `@Module @ComponentScan`
  - Add `TaskSeeder` callback to the `AppDatabase` builder if necessary

- [ ] T020 [P] Add new use cases to DomainModule in
  `domain/src/main/java/com/giraffe/domain/di/DomainModule.kt`:
  - The `@ComponentScan` should auto-detect the `@Factory` annotated
    use cases, but verify the scan includes `com.giraffe.domain.usecase`

### Data Tests (JUnit 5 — write before implementations)

- [ ] T021 [US1] Write test for `TaskDao` in
  `data/src/test/java/com/giraffe/data/datasource/local/dao/TaskDaoTest.kt`:
  - Test `getAllActive()` returns only tasks with `isActive = 1`
  - Test `insertAll()` and `getById()` round-trip
  - Use Room in-memory database (`@RunWith(AndroidJUnit4::class)` or
    use `MigrationTestHelper` for JUnit 4; alternatively test via
    repository integration test)

- [ ] T022 [US1] Write test for `GetTodayTasksUseCase` in
  `domain/src/test/java/com/giraffe/domain/usecase/GetTodayTasksUseCaseTest.kt`:
  - Mock `HijriDateRepository` and `SystemDateProvider` with MockK
  - Test that tasks are filtered by today's day-of-week
  - Test that empty repository returns empty list
  - Follow the pattern from `GetCurrentDateUseCaseTest.kt`

- [ ] T023 [US2] Write test for `ToggleTaskCompletionUseCase` in
  `domain/src/test/java/com/giraffe/domain/usecase/ToggleTaskCompletionUseCaseTest.kt`:
  - Test that first call inserts a completion
  - Test that second call (same task+date) deletes the completion
  - Mock repository with MockK, use Truth assertions

- [ ] T024 [US3] Write test for `GetStreaksUseCase` in
  `domain/src/test/java/com/giraffe/domain/usecase/GetStreaksUseCaseTest.kt`:
  - Test current streak with consecutive day completions
  - Test current streak resets after a missed day
  - Test longest streak exceeds current streak
  - Test empty completions returns 0 for both

**Checkpoint**: Foundation ready — user story implementation can now begin in parallel

---

## Phase 3: User Story 1 + User Story 2 — Dashboard & Task Completion (Priority: P1) 🎯 MVP

**Goal**: Users open the app and see their daily dashboard with tasks, dates,
progress, and points. Users can tap tasks to complete/undo them with instant
feedback.

**Independent Test**: Launch app on a Monday with 10 pre-seeded tasks.
Dashboard shows all 10 (Fajr-Dhuhr-Asr-Maghrib-Isha-Quran-Morning Adhkar-
Evening Adhkar-Monday Fast). Complete 3 → progress shows 30%, points show
sum of those 3 tasks' values (e.g., Fajr 5 + Dhuhr 5 + Asr 5 = 15).

### Implementation

- [ ] T025 [P] [US1][US2] Create `DashboardViewState` in
  `presentation/src/main/java/com/giraffe/presentation/dashboard/DashboardViewState.kt`:
  ```kotlin
  data class DashboardViewState(
      val gregorianDate: String = "",
      val hijriDate: String = "",
      val tasks: List<Task> = emptyList(),
      val completedTaskIds: Set<Long> = emptySet(),
      val completionPercent: Double = 0.0,
      val todayPoints: Int = 0,
      val isLoading: Boolean = true,
      val isNoTasksMessage: Boolean = false,
      val isAllComplete: Boolean = false
  )
  ```

- [ ] T026 [P] [US1][US2] Create `DashboardViewEffect` sealed interface in
  `presentation/src/main/java/com/giraffe/presentation/dashboard/DashboardViewEffect.kt`:
  ```kotlin
  sealed interface DashboardViewEffect {
      data class ShowError(val message: String) : DashboardViewEffect
  }
  ```

- [ ] T027 [US1][US2] Create `DashboardViewModel` in
  `presentation/src/main/java/com/giraffe/presentation/dashboard/DashboardViewModel.kt`:
  - `@Inject constructor` with use cases:
    - `GetCurrentDateUseCase`
    - `GetTodayTasksUseCase`
    - `ToggleTaskCompletionUseCase`
    - `GetTodayStatsUseCase`
  - Extend `BaseViewModel<DashboardViewState, DashboardViewEffect>`
  - `init` block calls `loadDashboard()`
  - `loadDashboard()`:
    1. Gets current date (Gregorian + Hijri) via `GetCurrentDateUseCase`
    2. Gets today's tasks via `GetTodayTasksUseCase`
    3. Gets today's stats via `GetTodayStatsUseCase`
    4. Updates state with all data, sets `isLoading = false`
    5. Shows loading state on first launch per clarification Q2
  - `onTaskToggled(taskId: Long)`:
    1. Calls `ToggleTaskCompletionUseCase`
    2. Re-loads stats to refresh points + progress
    3. Uses `tryToExecute` for coroutine management
  - References: spec.md US1 + US2, research.md Task 5, constitution Principle VI

- [ ] T028 [US1][US2] Create `DashboardScreen` in
  `presentation/src/main/java/com/giraffe/presentation/dashboard/DashboardScreen.kt`:
  - Composable function `DashboardScreen(viewModel: DashboardViewModel)`
  - Observes `viewModel.state` via `collectAsState()`
  - States to handle:
    - **Loading**: Show shimmer/skeleton (first launch clarification)
    - **No tasks**: Show "No tasks scheduled today" message
    - **Tasks**: LazyColumn of task items with:
      - Task name, category icon, point badge
      - Checkbox/checkmark for completion state
      - Tap to toggle (calls `viewModel.onTaskToggled()`)
    - **All complete**: Show congratulatory message + 100% progress
  - Top bar: Gregorian date + Hijri date
  - Progress section: Linear progress indicator + percentage + points
  - Follow `Material 3` design system

- [ ] T029 [US1][US2] Create `DashboardModule` in
  `presentation/src/main/java/com/giraffe/presentation/dashboard/DashboardModule.kt`:
  - `@Module @ComponentScan("com.giraffe.presentation.dashboard")`
  - Koin auto-detects `DashboardViewModel` via `@Inject` constructor

- [ ] T030 [US1][US2] Wire `DashboardScreen` into `MainActivity` in
  `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt`:
  - Use `setContent` with `MaterialTheme`
  - Obtain `DashboardViewModel` via `koinViewModel()`
  - Call `DashboardScreen(viewModel)`

**Checkpoint**: At this point, User Stories 1 AND 2 should be fully functional
and testable independently. Users can see their dashboard, view tasks, complete
and undo tasks, and see progress/points update.

---

## Phase 4: User Story 3 — View Statistics and Streaks (Priority: P2)

**Goal**: Users navigate to a statistics screen to view their current streak,
longest streak, daily/weekly/monthly completion percentages, and total points.

**Independent Test**: Complete at least one task for 3 consecutive days →
statistics screen shows current streak of 3 days. Miss a day → current streak
shows 0, longest streak shows 3.

### Implementation

- [ ] T031 [P] [US3] Create `StatsViewState` in
  `presentation/src/main/java/com/giraffe/presentation/stats/StatsViewState.kt`:
  ```kotlin
  data class StatsViewState(
      val currentStreak: Int = 0,
      val longestStreak: Int = 0,
      val dailyPercent: Double = 0.0,
      val weeklyPercent: Double = 0.0,
      val monthlyPercent: Double = 0.0,
      val totalPoints: Int = 0,
      val isLoading: Boolean = true
  )
  ```

- [ ] T032 [P] [US3] Create `StatsViewEffect` in
  `presentation/src/main/java/com/giraffe/presentation/stats/StatsViewEffect.kt`:
  Use `Unit` since no one-shot effects are needed for stats (no navigation).

- [ ] T033 [US3] Create `StatsViewModel` in
  `presentation/src/main/java/com/giraffe/presentation/stats/StatsViewModel.kt`:
  - `@Inject constructor` with use cases:
    - `GetStreaksUseCase`
    - `GetTodayStatsUseCase` (reused for daily stats display)
    - `GetMonthlyStatsUseCase` (see T041)
  - Extend `BaseViewModel<StatsViewState, Unit>`
  - `init` block calls `loadStats()`
  - States: loading, loaded with data

- [ ] T041 [US3] Create `GetMonthlyStatsUseCase` in
  `domain/src/main/java/com/giraffe/domain/usecase/GetMonthlyStatsUseCase.kt`:
  - `@Factory` with `@Provided HijriDateRepository`
  - `suspend operator fun invoke(yearMonth: String): MonthlyStats` returns:
    - `dailyPercents: Map<String, Double>` — completion % for each day in month
    - `monthlyAverage: Double` — average of daily percentages
    - `monthlyPoints: Int` — sum of all points earned in the month
  - Algorithm: Parse `yearMonth` (e.g. "2026-07"), iterate days 1..last day,
    query `getCompletionsForDate()` for each day, compute per-day stats
  - Create a `MonthlyStats` data class

- [ ] T034 [US3] Create `StatsScreen` in
  `presentation/src/main/java/com/giraffe/presentation/stats/StatsScreen.kt`:
  - Composable function `StatsScreen(viewModel: StatsViewModel)`
  - Sections:
    - **Streaks card**: Current streak (days), longest streak (days)
    - **Completion rates**: Daily %, Weekly %, Monthly % with progress bars
    - **Points total**: Total points earned
  - Material 3 cards, clean typography

- [ ] T035 [US3] Create `StatsModule` in
  `presentation/src/main/java/com/giraffe/presentation/stats/StatsModule.kt`:
  - `@Module @ComponentScan("com.giraffe.presentation.stats")`

- [ ] T036 [US3] Add navigation from Dashboard to Stats in
  `presentation/src/main/java/com/giraffe/presentation/common/Navigation.kt`:
  - Simple tab or button toggle between Dashboard and Stats screens
  - Or use a bottom navigation bar with 2 tabs: "Today" and "Stats"

**Checkpoint**: All user stories should now be independently functional.
MVP complete: dashboard + task completion + statistics.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T037 [P] Add Compose Navigation or simple state-based screen switching
  in `presentation/src/main/java/com/giraffe/presentation/common/MizanNavHost.kt`:
  - Sealed class `Screen { object Dashboard, object Stats }`
  - Simple `remember` state to switch between screens
  - Pass ViewModels via Koin

- [ ] T038 [P] Add `SystemDateProviderImpl` day-of-week utility in
  `data/src/main/java/com/giraffe/data/datasource/system/SystemDateProviderImpl.kt`:
  - Use `java.time.LocalDate.of(year, month, day).dayOfWeek` to derive
    the day-of-week from `SimpleDate`
  - Map `java.time.DayOfWeek` to `Day` enum values (SUNDAY → SU, etc.)

- [ ] T039 String extraction: Move all user-facing strings to
  `presentation/src/main/res/values/strings.xml` for future localization

- [ ] T040 Run quickstart.md validation: Launch app, verify all scenarios
  in `quickstart.md` produce expected outcomes

- [ ] T042 Smoke & performance validation:
  - Manual instrumentation or Compose UI test verifying:
    - **SC-001**: Dashboard loads within 1 second (measure from `onCreate`
      to first frame with task data rendered)
    - **SC-002**: Task toggle updates UI instantly (verify `DashboardViewState`
      reflects toggle before `ToggleTaskCompletionUseCase` returns — optimistic
      UI pattern)
  - Document test steps and pass/fail criteria

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user
  stories
- **US1+US2 (Phase 3)**: Depends on Foundational phase completion — this is
  the MVP. No dependencies on other stories
- **US3 (Phase 4)**: Depends on Foundational phase completion — independent
  of US1/US2 but shares the same data sources
- **Polish (Phase 5)**: Depends on US1+US2 and US3 being complete

### User Story Dependencies

- **User Story 1 (P1) + User Story 2 (P1)**: TIGHTLY COUPLED — share the same
  ViewModel (`DashboardViewModel`). Must be implemented together.
- **User Story 3 (P2)**: Fully independent — separate ViewModel, separate
  screen. Only depends on the same repository layer.

### Within Each Phase

- Tests MUST be written and FAIL before implementation (Test-First principle)
- Models/entities before services/repositories
- Services before ViewModels
- ViewModels before UI screens
- Core implementation before polish

### Parallel Opportunities

| Tasks | Why Parallel |
|-------|-------------|
| T001, T002, T003 | Different directories, no shared files |
| T009, T010, T011 | Independent mapper files, no overlap |
| T013, T014, T015, T016, T017 | Independent use cases, can write all at once |
| T025, T026 | State + Effect are independent data classes |
| T031, T032 | State + Effect are independent data classes |
| T031, T032, T041 | Stats state, effect, and monthly use case are independent |

---

## Parallel Example: Phase 3 (US1 + US2)

```bash
# Create ViewModel dependencies (state + effect) in parallel:
Task: "Create DashboardViewState in presentation/.../DashboardViewState.kt"
Task: "Create DashboardViewEffect in presentation/.../DashboardViewEffect.kt"

# Then implement ViewModel (depends on state + effect):
Task: "Create DashboardViewModel in presentation/.../DashboardViewModel.kt"

# Then create UI (depends on ViewModel):
Task: "Create DashboardScreen in presentation/.../DashboardScreen.kt"
```

## Parallel Example: Phase 4 (US3)

```bash
# State + Effect in parallel:
Task: "Create StatsViewState in presentation/.../StatsViewState.kt"
Task: "Create StatsViewEffect in presentation/.../StatsViewEffect.kt"

# Then ViewModel:
Task: "Create StatsViewModel in presentation/.../StatsViewModel.kt"

# Then UI:
Task: "Create StatsScreen in presentation/.../StatsScreen.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2 Only)

1. Complete Phase 1: Setup (dependency configuration)
2. Complete Phase 2: Foundational (entities, DAOs, mappers, use cases, DB)
3. Complete Phase 3: Dashboard + Task Completion (US1 + US2)
4. **STOP and VALIDATE**: Open app, see dashboard, toggle tasks, verify
   progress and points update
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add Phase 3 (Dashboard US1+US2) → Test → Deploy (MVP!)
3. Add Phase 4 (Statistics US3) → Test → Deploy

### Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Domain and data tests MUST fail before implementation (TDD)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that
  break independence
