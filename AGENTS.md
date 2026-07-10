# MizanApp — AGENTS.md

## Project

Android app for tracking Islamic daily tasks (prayers, Quran, adhkar, fasting) with
Hijri date support via [aladhan.com](https://api.aladhan.com/v1/) (Umm al-Qura calendar).

## Modules (clean architecture)

| Module | Type | Depends on | Tests |
|--------|------|------------|-------|
| `:domain` | pure Kotlin/JVM lib | none | JUnit 5 + Truth + MockK |
| `:data` | Android lib | `:domain` | JUnit 5 + Truth |
| `:presentation` | Android lib | `:domain` | JUnit 4 (empty — scaffolding only) |
| `:app` | Android app | all three | JUnit 4 + Espresso |

## Commands

```bash
./gradlew build          # full build
./gradlew lint           # lint
./gradlew :domain:test   # domain tests only
./gradlew :data:test     # data tests only
./gradlew test           # all unit tests
```

## DI

Koin 4.2.2 with KSP annotation compiler (`@Single`, `@Factory`, `@Module`,
`@ComponentScan`). Each module has a `@Module @ComponentScan` class.
The root `@KoinApplication` is `com.giraffe.mizanapp.di.MyApp` (scans `com.giraffe.*`).
No XML/DSL modules — all wiring is annotation-driven.

## Known issues / caveats

- **Broken test**: `GetCurrentDateUseCaseTest` passes 1 of 2 required constructor args
  (missing `HijriDateRepository`) — will not compile.
- **Empty test**: `SyncMonthlyHijriDatesUseCaseTest` has no test methods.
- **Typo in package**: `com.giraffe.data.utli` (should be `util`).
- **`presentation` module** has zero main source files (`.keep` only) — scaffolding.
- **`GetTodayTasksUseCase`** and **`HijriDateRepository.getTodayTasks`** both return
  `emptyList()` — not yet implemented.
- Domain + data modules use **JUnit 5** (`useJUnitPlatform()`); app + presentation use
  **JUnit 4**. Running root `test` will execute both runners.
