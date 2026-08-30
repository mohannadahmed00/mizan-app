# Contract: Boundary Provider

**Feature**: `specs/009-maghrib-day-boundary`

The app-facing surface for "what day is it". `TimeProvider` keeps its existing shape so no caller
changes; `BoundaryStatus` is new and exists only so the app can *tell the person* which rule is in
force.

---

## `TimeProvider` — unchanged signature

```kotlin
interface TimeProvider {
    fun now(): Instant
    fun today(): LocalDate      // the current accountability date
    fun zone(): ZoneId
}
```

**This interface does not change.** That is deliberate and load-bearing: more than thirty call sites
depend on `today()` being synchronous and total, and research R3 settles that it stays that way.

### Guarantees `today()` must keep

| Guarantee | Source |
|---|---|
| Returns in constant time, with no disk read, no location call and no calculation | FR-015, SC-010 |
| Never blocks, never throws, never returns null | FR-015 |
| Returns the same value for the same resolved state, regardless of retries or timing | FR-017 |
| Advances by at most one day per transition, and never moves backwards | FR-022, FR-023 |

Implementations read `BoundaryState.resolvedDate`. Resolution happens elsewhere, off the UI path.

---

## `BoundaryStatus` — new

```kotlin
interface BoundaryStatus {
    fun current(): BoundaryState
    fun observe(): Flow<BoundaryState>
    suspend fun refresh(now: Instant, zone: ZoneId)
    suspend fun requestLocation(): LocationRequestOutcome
    suspend fun eraseLocation()
}
```

**Where it lives**: declared in `:domain` as `domain/time/BoundaryStatus.kt`, implemented in `:data`
by `BoundaryStateStore`, and bound in Koin to that **same singleton instance** — never a second
construction, or the app would observe one state while `today()` reads another. `:domain` already has
`kotlinx-coroutines-core`, so `Flow` costs it no new dependency.

`current()` is the synchronous read behind `TimeProvider.today()`, and is also how a caller obtains
`expiresAt` — the day's end that `StreakClock` needs and that FR-026's in-app rollover fires at.
Everything outside `:data` depends on this interface, not on the concrete store.

### `observe()`

Emits the current `BoundaryState` and again on every change. Drives the disclosure FR-016 and
FR-012d require, and the settings section of FR-017b.

### `refresh(now, zone)`

Re-resolves. Called at app start, on resume, when `expiresAt` passes, and after a fresh location
arrives. Must be safe to call concurrently and repeatedly — it is idempotent for a given input state.

**The current instant and zone are parameters, not something `refresh` reads.** The implementation
lives in `:data` beside `SystemTimeProvider`, and `SystemTimeProvider` derives `today()` *from* the
state this method resolves — so a `refresh` that called back into the time provider would close a
cycle and put a second clock read outside the one place permitted to have one (Principle VII). The
caller that already holds the injected `TimeProvider` passes both values in.

Refresh performs, in order:

1. Compare `zone` against `zoneIdWhenObtained`. If they differ and coordinates are held, move to
   `Fallback(ZONE_CHANGED_AWAITING_FIX)` and attempt a fresh fix (FR-012b).
2. If coordinates are held and trusted, compute the Maghrib of `now`'s civil date in `zone` through
   `PrayerTimesProvider.timesFor(date, at, zone)`; on `Calculated`, the regime is `Maghrib`.
3. On `NoLocation` or `CalculationFailed`, the regime is `Fallback` with the corresponding reason.
   **Never** substitute a guessed or default location (FR-014).
4. Compute the date via `DayBoundary.dateAt(now, zone, maghrib)`, then pass it through
   `resolveBoundaryDate` with `lastResolvedDate` and
   `regimeChanged = (regime != lastResolvedRegime)` (FR-022, FR-023, FR-023a).
5. Compute `expiresAt`, the instant `resolvedDate` next changes:
   - under `Maghrib`, the **next** Maghrib — today's if `now` is before it, otherwise **tomorrow's**,
     which costs a second `timesFor` call for the following civil date;
   - under `Fallback`, the next local midnight in `zone`.

   A `CalculationFailed` on that second call does not change the regime already chosen in step 2; it
   sets `expiresAt` to the next local midnight so the state is re-examined promptly, and the
   following `refresh` resolves it properly.
6. Persist `lastResolvedDate` and `lastResolvedRegime`; publish the new state.

**Step 4 may never be skipped, and may never be widened.** Skipped, and a transition can drop or
duplicate an accountability date. Widened — clamping when the regime has *not* changed — and a person
returning after an absence is handed a date days behind, with that evening's completions credited to
a day that has already closed (FR-023a).

**`expiresAt` from step 5 is load-bearing.** FR-026 requires the day to roll over at that instant
while the app is open, and `StreakClock` takes it as `dayEndsAt` (FR-029). It is never null, and
never in the past when `refresh` returns.

### `requestLocation()`

Raises the system permission dialog if needed, then attempts a fix. **Called only from an explicit
user action** — the first-launch prompt's "Enable location", or the settings section (FR-007c). Never
on launch, never as a side effect.

```kotlin
sealed interface LocationRequestOutcome {
    data object Obtained : LocationRequestOutcome
    data object PermissionDenied : LocationRequestOutcome
    data object NoFixAvailable : LocationRequestOutcome
}
```

Every outcome leaves the app fully usable (FR-007d). None of them is an error state.

### `eraseLocation()`

Nulls the stored coordinates, moves the regime to `Fallback(Erased)`, and re-resolves through the
clamp (FR-017c). **Must not** alter any stored day, week or completion (FR-017d, FR-021).

---

## Forbidden by this contract

These are the ways the feature would go wrong, stated so a reviewer can check for them directly.

- No method returns an "unknown" or "unresolved" day. A regime is always in force.
- No implementation reads `Instant.now()`, `ZoneId.systemDefault()`, a location API, or computes a
  prayer time outside the single provider (Principle VII, SC-013).
- No caller of `TimeProvider` may convert an instant to a date itself. `DayBoundary.dateAt` has one
  production caller and must keep having one (FR-010, research R2).
- No path here writes to `day_plans`, `planned_tasks` or `completions` (FR-021).
- Coordinates appear in no log, no outbound request and no synced row (FR-006, SC-011).
