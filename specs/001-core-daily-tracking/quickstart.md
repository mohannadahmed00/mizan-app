# Quickstart: Core Daily Habit Tracking (MVP)

## Prerequisites

- Android Studio Hedgehog (2023.1.1+) or later
- JDK 17
- Android SDK (compile SDK 36+)
- Emulator or physical device running API 24+

## Setup

```bash
# Clone and open the project in Android Studio, or build from CLI:
./gradlew assembleDebug
```

The project uses version catalog (`libs.versions.toml`) for dependency
management. No additional configuration needed.

## Validation Scenarios

### Scenario 1: Dashboard Shows Today's Tasks

**Steps**:
1. Launch the app on a weekday (Monday–Friday).
2. Observe the dashboard.

**Expected**:
- Gregorian date is displayed (e.g., "2026-07-10").
- Hijri date is displayed alongside (e.g., "15 Dhul Qa'dah 1447").
- At least 8 pre-seeded tasks are visible (5 prayers + Quran + 2 adhkar +
  plus fasting tasks if Monday or Thursday).
- Progress shows "0%".
- Points show "0".

**Data model reference**: [data-model.md](data-model.md) — `TaskEntity`,
`TaskCompletionEntity`

### Scenario 2: Complete a Task

**Steps**:
1. From the dashboard, tap the "Fajr" task.
2. Observe visual state change.
3. Tap "Fajr" again to undo.

**Expected**:
- After tap: "Fajr" shows a checked/completed state, progress increases,
  points increase by 5 (Fajr's point value).
- After second tap: "Fajr" reverts to incomplete, progress decreases,
  points decrease by 5.

### Scenario 3: Verify Streak Calculation

**Steps**:
1. Complete at least one task.
2. Close and reopen the app on the next calendar day.
3. Complete at least one task again.
4. Navigate to the statistics screen.

**Expected**:
- Current streak shows "2 days".
- Longest streak shows "2 days".

### Scenario 4: Offline Operation

**Steps**:
1. Enable airplane mode on the device.
2. Launch the app (after initial Hijri calendar sync has completed).
3. Complete a task.

**Expected**:
- Dashboard loads normally.
- Hijri date is displayed (from local cache).
- Task completion succeeds.
- Points and progress update.

### Scenario 5: First Launch with No Cache

**Steps**:
1. Clear app data or install on a fresh device.
2. Launch the app with network available.

**Expected**:
- Brief loading/skeleton state is displayed while Hijri calendar downloads.
- After sync completes, full dashboard appears with today's tasks and dates.

### Scenario 6: Performance — Quick Load & Instant Toggle

**Steps**:
1. Cold-launch the app (app process not running).
2. Measure time from `onCreate` to first rendered frame with task data.
3. Tap any task to toggle completion.
4. Measure time from tap to visible state change.

**Expected**:
- Dashboard renders within 1 second on a reference mid-range device
  (or API 33+ emulator with 2 GB RAM).
- Task toggle visual feedback appears before the DB write completes
  (optimistic UI — state flips immediately, then syncs).

## Running Tests

```bash
# Domain module tests (JUnit 5)
./gradlew :domain:test

# Data module tests (JUnit 5)
./gradlew :data:test

# All unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew :app:connectedAndroidTest
```

## Contracts

See [contracts/](contracts/) for interface contracts between layers.

## Related Documents

| Artifact | Path |
|----------|------|
| Feature Spec | [spec.md](spec.md) |
| Implementation Plan | [plan.md](plan.md) |
| Research | [research.md](research.md) |
| Data Model | [data-model.md](data-model.md) |
| Tasks | Created by `/speckit.tasks` |
