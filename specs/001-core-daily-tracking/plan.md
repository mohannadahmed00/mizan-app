# Implementation Plan: Core Daily Habit Tracking (MVP)

**Branch**: `001-core-daily-tracking` | **Date**: 2026-07-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-core-daily-tracking/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Build the Phase 1 MVP of MizanApp: a daily dashboard showing today's Gregorian
and Hijri dates, pre-seeded Islamic tasks, task completion toggles, points
derived from completions, overall streak tracking, and daily/weekly/monthly
statistics. All data is stored locally via Room; only Hijri calendar sync
requires network. The existing `:domain` and `:data` module foundations
(models, repository interfaces, API, Room DB) are already in place — this
feature wires them together and builds the UI layer.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM target 11)

**Primary Dependencies**: Jetpack Compose + Material 3, Koin 4.2.2 (KSP),
Room, Retrofit + OkHttp, AndroidX WorkManager, Kotlinx.datetime

**Storage**: Room (local SQLite) — existing `AppDatabase` with
`CompactDateEntity` table. Needs new `TaskEntity` and `TaskCompletionEntity`
tables for Phase 1 feature data.

**Testing**:
- Domain + data: JUnit 5, Truth, MockK
- App + presentation: JUnit 4, Compose UI Test, Espresso

**Target Platform**: Android (min SDK 24, target SDK 37, compile SDK 36+)

**Project Type**: mobile-app (Android)

**Performance Goals**: Dashboard loads all scheduled tasks within 1 second
from cold start on a mid-range device. Task toggle feedback is instant
(target <100ms from tap to visual state change).

**Constraints**:
- Offline-first: all core flows work without network after initial Hijri sync
- Single user, no authentication (Phase 1)
- min SDK 24, consistent with existing project
- Uniqueness constraint: `(taskId, completedDate)` — at most one completion
  per task per day

**Scale/Scope**: Phase 1 — single device, local-only, ~10 pre-seeded tasks,
single user

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture** ✅ — Feature layers map to existing modules:
  `:domain` (use cases, models, repository contracts), `:data` (Room entities,
  DAOs, API), `:presentation` (ViewModels, Compose screens), `:app`
  (composition root, DI, WorkManager). Dependency direction is inward.
- **II. Dependency Injection** ✅ — All wiring via Koin annotation-driven DI
  (`@Single`, `@Factory`). Each module has `@Module @ComponentScan` class.
  ViewModels use `@Inject constructor`.
- **III. Test-First (NON-NEGOTIABLE)** ✅ — Domain use cases + data
  repositories MUST have tests written before implementation. JUnit 5 + Truth
  + MockK for domain/data.
- **IV. Data Integrity & Offline Support** ✅ — Core principle of this
  feature. All data stored locally via Room. Hijri calendar via aladhan.com
  API (Umm al-Qura). Offline is primary mode.
- **V. Simplicity & YAGNI** ✅ — Phase 1 intentionally scoped to core
  tracking. No custom tasks, achievements, notifications, or social features.
  Points derived dynamically (no stored scores).
- **VI. ViewModel Architecture** ✅ — ViewModels extend `BaseViewModel
  <UI_STATE, UI_EFFECT>`, use `updateState()`, `sendEffect()`, `tryToExecute`.
  Dashboard state is a data class; one-shot navigation effects are a sealed
  interface.

**Result**: All gates pass. No complexity violations — proceeding to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-core-daily-tracking/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── spec.md              # Feature specification
```

### Source Code (repository root)

```text
# Mobile + API (Android)
domain/src/main/java/com/giraffe/domain/
├── model/               # Existing: Task, TaskCompletion, CompactDate,
│                        #   SimpleDate, Day, Category
├── usecase/             # Existing: GetCurrentDateUseCase, GetTodayTasksUseCase
│                        #   (GetTodayTasksUseCase is a stub — must be
│                        #   implemented)
├── repository/          # Existing: HijriDateRepository interface
└── provider/            # Existing: SystemDateProvider interface

data/src/main/java/com/giraffe/data/
├── datasource/
│   ├── local/
│   │   ├── database/    # Existing: AppDatabase (needs TaskEntity,
│   │   │                #   TaskCompletionEntity tables)
│   │   ├── dao/         # Existing: HijriDateDao (needs TaskDao)
│   │   └── entity/      # Existing: CompactDateEntity, SimpleDateEntity
│   │                    #   (needs TaskEntity, TaskCompletionEntity)
│   └── remote/
│       └── api/         # Existing: HijriApi
├── repository/          # Future: HijriDateRepositoryImpl (needs
│                        #   GetTodayTasks implementation)
└── mapper/              # Future: entity↔domain mappers

presentation/src/main/java/com/giraffe/presentation/
├── dashboard/           # Future: DashboardScreen, DashboardViewModel,
│                        #   DashboardViewState, DashboardViewEffect
└── stats/               # Future: StatsScreen, StatsViewModel,
                         #   StatsViewState

app/src/main/java/com/giraffe/mizanapp/
├── MainActivity.kt      # Existing — entry point
├── MainApplication.kt   # Existing — @KoinApplication root
└── di/                  # Existing: AppModule
```

**Structure Decision**: The project follows Option 3 (Mobile + API) from the
template, adapted to Android clean architecture with 4 Gradle modules
(`:domain`, `:data`, `:presentation`, `:app`). The existing structure is
preserved; Phase 1 adds `:presentation` source files and extends `:data` with
new Room tables.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations found. All constitution gates pass for Phase 1 scope.
