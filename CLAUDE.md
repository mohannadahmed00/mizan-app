# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MizanApp is an Android app (Kotlin, Jetpack Compose) for tracking Islamic daily tasks
(prayers, Quran, adhkar, fasting) with gamified points/streaks, using Hijri dates
(Umm al-Qura calendar) sourced from the [aladhan.com](https://api.aladhan.com/v1/) API.
See `docs/mizanapp.md` for the full product spec.

## Commands

```bash
./gradlew build          # full build
./gradlew lint           # lint
./gradlew test           # all unit tests
./gradlew :domain:test   # domain tests only
./gradlew :data:test     # data tests only
./gradlew :domain:test --tests "com.giraffe.domain.usecase.GetStreaksUseCaseTest"  # single test class
```

Domain and data modules run on JUnit 5 (`useJUnitPlatform()`); app and presentation
run on JUnit 4. Running the root `test` task executes both runners.

## Architecture

Clean Architecture with four Gradle modules, dependencies pointing inward:

```
:app  →  :presentation, :data  →  :domain
```

| Module | Type | Depends on | Tests |
|--------|------|------------|-------|
| `:domain` | pure Kotlin/JVM lib | none | JUnit 5 + Truth + MockK |
| `:data` | Android lib | `:domain` | JUnit 5 + Truth |
| `:presentation` | Android lib | `:domain` | JUnit 4 |
| `:app` | Android app | all three | JUnit 4 + Espresso |

- `:domain` — models, repository interfaces (contracts), use cases. Zero Android
  dependencies.
- `:data` — implements domain repository contracts. Room (`datasource/local`:
  entities, DAOs, `AppDatabase`, seeders) + Retrofit (`datasource/remote`: API,
  DTOs, responses) + mappers between entities/DTOs and domain models.
- `:presentation` — feature-packaged Compose UI (e.g. `dashboard/`, `stats/`),
  each with its own `ViewModel`, `ViewState`, `ViewEffect`, `Screen`, and
  `@Module @ComponentScan` Koin module. Shared base classes live in `common/`.
- `:app` — `MainApplication`/`MainActivity`, app-level DI wiring, WorkManager
  workers (e.g. `SyncHijriDatesWorker`).

### Core domain concepts

- **Task**: a recurring activity definition (name, category, points, active
  days) — holds no completion state.
- **TaskCompletion**: a record that a task was completed on a given date; the
  record's existence *is* the completion. Points/streaks/stats are always
  derived from `TaskCompletion` records, never stored directly.
- **CompactDate**: pairs a Gregorian date with its cached Hijri equivalent.

### ViewModel pattern (required for all ViewModels)

Every ViewModel extends `presentation/common/BaseViewModel<UI_STATE, UI_EFFECT>`:
- `UI_STATE` is a data class with default values, exposed as `state: StateFlow<UI_STATE>`;
  mutate it only via `updateState { copy(...) }`.
- `UI_EFFECT` is a sealed interface for one-shot events (use `Unit` if none),
  emitted via `sendEffect()`.
- Use `tryToExecute` for a single coroutine action, `tryToExecuteAll` for
  parallel actions, `tryToCollect` for collecting a `Flow` — all catch
  exceptions and route them to an `onError: (Exception) -> EFFECT` lambda.

### Dependency injection

Koin 4.2.2 with KSP annotation compiler — annotation-driven only, no XML/DSL
modules. Every Gradle module has one `@Module @ComponentScan("com.giraffe.<module>")
@Configuration` class (e.g. `DomainModule`, `DataModule`, `DashboardModule`).
The root `@KoinApplication` is `com.giraffe.mizanapp.di.MyApp`, started in
`MainApplication.onCreate()` via `startKoin<MyApp>`.

### Offline-first data flow

Only Hijri calendar sync requires network (via `SyncHijriDatesWorker`,
scheduled on app start). Everything else — tasks, completions, points, streaks,
stats — is computed from local Room data and must work fully offline.

## Spec-driven development workflow

This repo uses the [spec-kit](https://github.com) SDD workflow: features are
specified under `specs/<NNN-feature-name>/` (spec.md, plan.md, research.md,
data-model.md, tasks.md, quickstart.md) before implementation, driven by the
`/speckit.*` slash commands (`.opencode/commands/speckit.*.md`) and governed by
`.specify/memory/constitution.md`. When adding a feature, check whether a spec
already exists under `specs/` and follow its plan/tasks rather than improvising
architecture. The constitution is the source of truth for architectural rules
(clean architecture, DI, TDD, ViewModel pattern) — the summary in this file is
a derivative; consult it directly for the authoritative and current rules,
including the quality gates for a change (`lint`, `:domain:test :data:test`,
full `build`).

TDD is required (NON-NEGOTIABLE per the constitution) for `:domain` and
`:data`: write the failing test before the implementation.

## Known issues / caveats

- Typo in package name: `com.giraffe.data.utli` (should be `util`).
- `SyncMonthlyHijriDatesUseCaseTest` has no test methods (empty scaffold).
