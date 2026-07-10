# Data Model: Core Daily Habit Tracking (MVP)

## Overview

Phase 1 introduces two new entities (`TaskEntity`, `TaskCompletionEntity`)
in the `:data` module and maps to existing domain models (`Task`,
`TaskCompletion`). A new DAO (`TaskDao`) provides persistence operations.
The existing `AppDatabase` gains two new tables.

## Entities

### TaskEntity (`:data` — Room)

New table for persisting task definitions.

| Field | Type | Constraint | Notes |
|-------|------|------------|-------|
| `id` | `Long` | `@PrimaryKey(autoGenerate = true)` | Auto-generated |
| `name` | `String` | NOT NULL | e.g., "Fajr" |
| `category` | `String` | NOT NULL | Maps to `Category` enum name |
| `points` | `Int` | NOT NULL, > 0 | Point value for completion |
| `activeDays` | `String` | NOT NULL | Comma-separated day codes, e.g. "SA,SU,MO,TU,WE,TH,FR" |
| `isActive` | `Boolean` | DEFAULT 1 | Soft-delete / visibility toggle (reserved for Phase 2) |

**Table name**: `tasks`

### TaskCompletionEntity (`:data` — Room)

New table for recording daily task completions.

| Field | Type | Constraint | Notes |
|-------|------|------------|-------|
| `taskId` | `Long` | NOT NULL, FK → `tasks(id)` | Which task was completed |
| `gregorianDateKey` | `String` | NOT NULL | e.g., "2026-07-10" — consistent with `CompactDateEntity` pattern |
| `completedAt` | `Long` | NOT NULL | Epoch millis timestamp of completion |

**Composite primary key**: `(taskId, gregorianDateKey)`
**Table name**: `task_completions`

### Consistent with Existing

| Entity | Module | Status |
|--------|--------|--------|
| `CompactDateEntity` | `:data` | Already exists — unchanged |
| `SimpleDateEntity` | `:data` | Already exists — unchanged |
| `Task` | `:domain` | Already exists — unchanged |
| `TaskCompletion` | `:domain` | Already exists — unchanged |
| `CompactDate` | `:domain` | Already exists — unchanged |
| `SimpleDate` | `:domain` | Already exists — unchanged |
| `Category` | `:domain` | Already exists — unchanged |
| `Day` | `:domain` | Already exists — unchanged |

## DAOs

### TaskDao (new — `:data`)

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

### TaskCompletionDao (new — `:data`)

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

## Database Schema

### AppDatabase (updated)

```kotlin
@Database(
    entities = [
        CompactDateEntity::class,   // existing
        TaskEntity::class,          // new
        TaskCompletionEntity::class // new
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hijriDateDao(): HijriDateDao      // existing
    abstract fun taskDao(): TaskDao                 // new
    abstract fun taskCompletionDao(): TaskCompletionDao // new
}
```

**Migration**: v1 → v2 adds two new tables (`tasks`, `task_completions`).
Auto-migration (`@Database(autoMigrations = [...])`) is acceptable since
no existing data is affected.

## Domain → Data Mapping

### Task ↔ TaskEntity

| Domain Field | Source / Mapping |
|-------------|------------------|
| `id` | `entity.id` |
| `name` | `entity.name` |
| `category` | `Category.valueOf(entity.category)` |
| `points` | `entity.points` |
| `activeDays` | `entity.activeDays.split(",").map { Day.valueOf(it) }.toSet()` |

### CompactDate ↔ CompactDateEntity

Existing mapper — unchanged.

### TaskCompletion ↔ TaskCompletionEntity

| Domain Field | Source / Mapping |
|-------------|------------------|
| `taskId` | `entity.taskId` |
| `date` | Look up `CompactDate` by `entity.gregorianDateKey` via `HijriDateDao` |
| `completedAt` | `entity.completedAt` |

## Validation Rules

- `Task.points`: MUST be > 0.
- `TaskCompletion`: MUST be unique per `(taskId, gregorianDateKey)` — enforced
  by composite primary key.
- `Task.activeDays`: MUST contain at least one day.
- `Task.name`: MUST be non-blank, max 100 characters.

## State Transitions

```
Task: (seeded) ──► active (always visible in Phase 1)
                    (isActive toggle reserved for Phase 2)

TaskCompletion:
  NOT_COMPLETED ──tap──► COMPLETED  (insert row)
        ▲                         │
        └──────── tap ────────────┘  (delete row)
```
