<!--
  Sync Impact Report

  Version change: (none) → 1.1.0
  Initial adoption + amendment 1 (addition of Principle VI).

  Modified principles: N/A (first adoption)
  Added sections:
    - I. Clean Architecture
    - II. Dependency Injection
    - III. Test-First (NON-NEGOTIABLE)
    - IV. Data Integrity & Offline Support
    - V. Simplicity & YAGNI
    - VI. ViewModel Architecture
    - Technology Stack & Constraints
    - Development Workflow & Quality Gates
    - Governance
  Removed sections: None

  Templates requiring updates:
    - .specify/templates/plan-template.md    ✅ already generic; defers to constitution
    - .specify/templates/spec-template.md    ✅ already generic; no principle refs
    - .specify/templates/tasks-template.md   ✅ already generic; task types unchanged
    - .specify/templates/checklist-template.md ✅ already generic; no principle refs

  Deferred TODOs: None
-->

# MizanApp Constitution

## Core Principles

### I. Clean Architecture
The project MUST follow clean architecture with strict layer separation:
- `:domain` — pure Kotlin/JVM library with zero Android dependencies.
- `:data` — Android library implementing domain-layer contracts.
- `:presentation` — Android library providing UI components.
- `:app` — Android application composing all modules.

Dependency direction MUST always point inward:
`:app` → `:presentation` / `:data` → `:domain`.
No module may depend on a module at the same or outer layer.

### II. Dependency Injection
All dependency wiring MUST use Koin annotation-driven DI (`@Single`, `@Factory`,
`@Module`, `@ComponentScan`). No XML or DSL module declarations. Each Gradle
module MUST contain a `@Module @ComponentScan` class scanning `com.giraffe.*`.
The root `@KoinApplication` is at `com.giraffe.mizanapp.di.MyApp`.

### III. Test-First (NON-NEGOTIABLE)
TDD MUST be practiced for domain and data layers. Tests MUST be written before
implementation. Red-Green-Refactor cycle enforced. Domain + data modules use
JUnit 5 (via `useJUnitPlatform()`). App + presentation modules use JUnit 4.
Test assertions MUST use Truth for domain/data. MockK is the mocking library.

### IV. Data Integrity & Offline Support
All Hijri date computations MUST use the aladhan.com API (Umm al-Qura calendar).
Prayer times, Quran progress, adhkar logs, and fasting records MUST be stored
locally via Room with periodic API sync. Offline MUST be the primary operating
mode; network is treated as a sync layer only. Data MUST never be lost on
configuration changes or process death.

### V. Simplicity & YAGNI
Start simple. Avoid premature abstraction. Every feature MUST justify its
complexity. Do not introduce organizational-only modules or libraries. When
complexity is unavoidable, document the rationale in the plan's Complexity
Tracking section. Prefer platform built-ins over third-party libraries unless
the library solves a clearly identified problem.

### VI. ViewModel Architecture
Every ViewModel MUST:
- Extend `BaseViewModel<UI_STATE, UI_EFFECT>`.
- Define state as a data class with default parameter values. UI state is
  exposed via `state: StateFlow<UI_STATE>` backed by `updateState()`.
- Define one-shot effects as a sealed interface; use `Unit` when no effects
  exist (`BaseViewModel<UiState, Unit>`). Effects are emitted via
  `sendEffect()`.
- Use `tryToExecute` for single coroutine operations, `tryToExecuteAll` for
  parallel operations, and `tryToCollect` for flow collection — all provided
  by `BaseViewModel`.

## Technology Stack & Constraints

- Language: Kotlin (JVM target 11)
- UI: Jetpack Compose with Material 3
- DI: Koin 4.2.2 with KSP annotation compiler
- Local storage: Room (with KSP compiler)
- Networking: Retrofit + OkHttp (with logging interceptor)
- Serialization: Gson
- Min SDK: 24 | Target SDK: 37 | Compile SDK: 36+
- Async work: AndroidX WorkManager
- Testing: JUnit 5 (domain + data), JUnit 4 (app + presentation), Truth, MockK

## Development Workflow & Quality Gates

- All changes MUST pass `./gradlew lint` before commit.
- Domain + data tests MUST pass: `./gradlew :domain:test :data:test`.
- Full build MUST pass: `./gradlew build`.
- PRs MUST verify constitution compliance.
- Complexity violations MUST be justified in the plan's Complexity Tracking
  section.
- Use `AGENTS.md` for runtime development guidance.

## Governance

This constitution supersedes all other practices. Amendments require:
1. Documented rationale in the proposal.
2. Team review and approval.
3. A migration plan for affected areas.

Versioning follows Semantic Versioning (MAJOR.MINOR.PATCH):
- MAJOR: Backward-incompatible governance or principle removals/redefinitions.
- MINOR: New principles or materially expanded guidance.
- PATCH: Clarifications, typo fixes, non-semantic refinements.

All PRs and reviews MUST verify compliance with this constitution.

**Version**: 1.1.0 | **Ratified**: 2026-07-10 | **Last Amended**: 2026-07-10
