# Contract: Prayer Times and Location Provider

**Feature**: `specs/009-maghrib-day-boundary`

The single provider constitution Principle VII requires. Declared in `:domain`, implemented in
`:data`. Spec 010 consumes this same interface and must not introduce a second.

---

## `PrayerTimesProvider` — `:domain`

```kotlin
interface PrayerTimesProvider {
    suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome
}
```

**One method, and the coordinates are always explicit.** A convenience overload defaulting to "the
currently held coordinates" was considered and dropped: the only thing that holds coordinates is
`BoundaryStateStore`, which already depends on this provider, so the overload would close a
dependency cycle and give the held-coordinates question a second home. The caller that owns the
coordinates passes them.

`zone` is explicit for the same reason. The convention is selected from the IANA zone id (FR-003b),
and this provider may no more read the device's default zone than it may read the clock
(Principle VII).

`NoLocation` therefore describes a caller with no coordinates to pass that reached the provider
anyway. In the normal path the store recognises its own empty state and resolves
`Fallback(NEVER_HAD_LOCATION)` without calling here at all; the outcome stays in the type because the
spec's Key Entities name three outcomes and a caller must not be able to represent "no location" as
an exception.

### Outcomes

```kotlin
sealed interface PrayerTimesOutcome {
    data class Calculated(val times: PrayerTimes) : PrayerTimesOutcome
    data object NoLocation : PrayerTimesOutcome
    data class CalculationFailed(val reason: String) : PrayerTimesOutcome
}

data class PrayerTimes(
    val date: LocalDate,
    val fajr: Instant,
    val dhuhr: Instant,
    val asr: Instant,
    val maghrib: Instant,
    val isha: Instant,
)
```

**Outcomes are values, never exceptions.** A thrown exception would put the fallback decision in
whichever `catch` block happened to be nearest, giving Principle VII the second home it forbids.

`CalculationFailed` is a real case, not defensive padding: above the polar circles the sun does not
set on some dates and there is no Maghrib to return. The spec's edge case requires the unavailable
outcome there rather than an extrapolated time.

### Rules every implementation must satisfy

| Rule | Source |
|---|---|
| Computes entirely on-device; no network call on any path | FR-002, Principle IV |
| Selects its convention from the region, never from a user setting | FR-003, FR-003a |
| Resolves the region on-device, with no reverse-geocoding | FR-003b |
| Falls back to the documented default convention when no mapping entry matches | FR-003c |
| Never substitutes a guessed, default or invented location | FR-014 |
| Deterministic: same date, same coordinates, same convention ⇒ same instants | FR-004, FR-017 |

---

## `LocationSource` — `:domain`

```kotlin
interface LocationSource {
    suspend fun current(): Coordinates?
    fun hasPermission(): Boolean
}
```

The only location reader in the app (Principle VII, SC-013). Returns null rather than throwing when
no fix is available — an absent location is an ordinary outcome, not a failure.

### Rules

- Requests **coarse** accuracy only. Never fine (FR-005).
- Acquires opportunistically — last-known first, then a single current fix. **Never** a continuous
  background track.
- The returned coordinates carry no accuracy, bearing, speed or altitude. Nothing beyond what the
  calculation needs is read or retained (FR-006).
- Coordinates are never transmitted, logged, or written to a synchronisable row (FR-006, SC-011).

---

## Test doubles

FR-004 requires both the location and the calculated result to be substitutable, so every boundary
rule can be exercised by fixing a location and advancing a clock.

| Double | Purpose |
|---|---|
| `FakePrayerTimes` | Returns literal instants, or `NoLocation` / `CalculationFailed` on demand |
| `FakeLocationSource` | Returns fixed coordinates, null, or toggles permission |

Both are duplicated into **each consuming source set** — `domain/src/test`, `data/src/androidTest`
and `app/src/test` — rather than shared. `:domain`'s test sources are on none of the other modules'
classpaths (`data/build.gradle.kts` takes `api(project(":domain"))`, main only) and this project has
no `java-test-fixtures` setup, so a single copy in `:domain` would simply be invisible where most of
these tests run. `DbTestBase`'s own `TestTimeProvider` is the existing precedent for duplicating a
double rather than adding a fixtures module.

Note that **`DayBoundary`'s own tests need neither**. The rule takes the Maghrib instant as a
parameter (research R4), so its tests pass literals and never construct a provider at all. The fakes
exist for the provider's consumers, not for the rule.
