# Contracts: Core Daily Habit Tracking (MVP)

## Overview

This feature's contracts are the existing domain-layer interfaces and use
cases in the `:domain` module. No new external API contracts are introduced
in Phase 1.

## Existing Contracts

### HijriDateRepository (`:domain`)

```kotlin
interface HijriDateRepository {
    suspend fun getCompactDateOf(date: SimpleDate): CompactDate?
    suspend fun getTodayTasks(): List<Task>
    suspend fun syncMonthlyHijriDates(month: Int, year: Int)
}
```

**Phase 1 changes**: `getTodayTasks()` must be implemented (currently returns
`emptyList()`). See [data-model.md](../data-model.md) for the Room-backed
implementation.

### SystemDateProvider (`:domain`)

```kotlin
interface SystemDateProvider {
    fun getCurrentGregorianDate(): SimpleDate
}
```

**Phase 1 changes**: None — already implemented in `:data` module.

### Use Cases (`:domain`)

| Use Case | Input | Output | Status |
|----------|-------|--------|--------|
| `GetCurrentDateUseCase` | None | `CompactDate?` | Implemented |
| `GetTodayTasksUseCase` | None | `List<Task>` | Stub — must be implemented with `HijriDateRepository` |

### Room DAOs (`:data`)

| DAO | Operations |
|-----|-----------|
| `HijriDateDao` | `getByGregorianDate()`, `insertAll()` |
| `TaskDao` (new) | `getAllActive()`, `getById()`, `insertAll()`, `deleteAll()` |
| `TaskCompletionDao` (new) | `getCompletionsForDate()`, `getAllCompletions()`, `insert()`, `delete()` |

### Retrofit API (`:data`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `gToHCalendar/{month}/{year}?calendarMethod=UAQ` | GET | Fetch monthly Hijri calendar |

**Phase 1 changes**: None — the existing API endpoint is sufficient.

## Contract Summary

No new external API contracts. All internal contracts (interface boundaries
between `:domain`, `:data`, `:presentation`) follow Clean Architecture
conventions as defined in the [Constitution](../../../.specify/memory/constitution.md).
