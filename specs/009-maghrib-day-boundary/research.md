# Phase 0 Research: Maghrib-Anchored Day and Week Boundary

**Feature**: `specs/009-maghrib-day-boundary` | **Date**: 2026-08-30

Nine questions this increment had to settle before design. Each records what was decided, why, and
what was rejected. R1 and R2 are the two that made the increment small; R3 is the one that made it
possible without touching thirty call sites.

---

## R1 — Does the week rule have to change?

**Decision**: No. `WeekBoundary` keeps its current implementation, unchanged, and so does every
consumer of it.

**Rationale**: Maghrib on Friday is the *start of accountability-Saturday*, not the end of
accountability-Friday. So in accountability-date space, the week still runs Saturday through Friday,
and "the Saturday on or before this date" is still exactly the right rule. Once `DayBoundary` maps
instants to accountability dates correctly, the week boundary instant moves for free, because it is
defined as the start of the week's first day — and that day now starts at Maghrib.

Worked through at the seam that matters. Take Friday 19:00 local with Friday's Maghrib at 18:00. The
instant is at or after Maghrib, so its accountability date is Saturday. `WeekBoundary.startOfWeek`
of that Saturday is that Saturday — the new week. Take Friday 12:00: before Maghrib, accountability
date Friday, week starts the previous Saturday — the old week. The totals therefore freeze at exactly
Friday 18:00, which is FR-018 and FR-019, with no change to the week code at all.

The consequence is large. These need **no modification**:

| Consumer | Why it is already correct |
|---|---|
| `WeekViewModel` | Works entirely in `LocalDate` from `time.today()` |
| `GetHistoryPage` | Same — `weekContaining(today)` and cursor arithmetic in date space |
| `BuildPersonalBests` | Groups cells by `weekContaining(cell.date).key` |
| `LeaderboardPeriod.periodFor` | Derives all three period kinds from a `LocalDate` |
| `GetWeekSummary` | Takes a `Week`, aggregates over stored plans by date |

**Alternatives considered**:

- *Rewrite the week rule to operate on instants.* Rejected — it would duplicate the day rule inside
  the week rule, which is precisely the "second opinion" Principle VII forbids, and it would touch
  every consumer above for no behavioural gain.
- *Keep a separate "leaderboard week" so remote periods stay Saturday-anchored.* Rejected outright:
  two week rules is the failure mode the constitution names by name.

---

## R2 — How many places actually resolve an instant to a date?

**Decision**: One. `SystemTimeProvider.today()` is the only production caller of
`DayBoundary.dateAt`; the change is confined to it and to `DayBoundary` itself.

**Rationale**: Grepped the whole tree. Production callers of `DayBoundary`: `SystemTimeProvider`
only. Every other reference is a test double (`FakeTimeProvider`, `DbTestBase`, `FakeRepositories`)
or a test. Every ViewModel and use case in the app reaches the current date through
`TimeProvider.today()`, never by converting an instant itself.

This is worth stating plainly because it is the payoff from eight increments of Principle VII
discipline, and it is what turns "redefine the accountability day" from a sweeping refactor into a
change to one function and its provider.

**Alternatives considered**: none — this is a measurement, not a choice. It is recorded because the
plan's scope claim depends on it and a reviewer should be able to re-run the grep.

---

## R3 — How does `today()` stay synchronous when the boundary now needs stored coordinates?

**Decision**: The boundary provider holds its resolved state in memory — coordinates, regime, the
current accountability date, and the instant that date next changes. `today()` is a read of that
state. Refresh happens off the UI path: at app start, on resume, when the resolved date's expiry
instant passes, and when a fresh location arrives.

**Rationale**: This is the hardest constraint in the feature. `today()` is called synchronously from
more than thirty places, and FR-015 forbids it blocking or failing. Making it `suspend` would change
every one of those call sites and put a coroutine on the path of rendering a date — and would still
not satisfy FR-015, because a suspending call that waits on a location fix is exactly the indefinite
block the constitution forbids.

Holding resolved state inverts the problem. Resolution is asynchronous and happens when it can;
reading is synchronous and always succeeds, because the state always holds *some* valid regime —
the fallback until coordinates exist. There is no window in which the app cannot answer "what day is
it", which is what makes SC-004 and SC-010 achievable.

**Alternatives considered**:

- *Make `today()` suspend.* Rejected — thirty-plus call sites, a coroutine on the render path, and it
  does not actually satisfy FR-015.
- *Block on first read with a timeout.* Rejected — a timeout is a nondeterministic input, and
  FR-017 requires the regime choice to depend only on whether coordinates are held and trusted,
  "never on timing, retries, or chance".
- *Cache the date in Room and read it synchronously from a prepared statement.* Rejected — a
  synchronous disk read on the render path, and Room discourages main-thread queries for good reason.

---

## R4 — What shape does the boundary rule take so it stays pure and testable?

**Decision**: `DayBoundary.dateAt` takes the Maghrib instant as a **parameter**, not a provider:

```
dateAt(instant, zone, maghribOnCivilDate: Instant?) -> LocalDate
```

The rule is: take the civil date of `instant` in `zone`; if `instant` is at or after that civil
date's Maghrib, the accountability date is the next day, otherwise it is that civil date. A null
Maghrib means the fallback regime, and the civil date is returned unchanged.

**Rationale**: Exactly one Maghrib is needed to decide the question — that of the instant's own civil
date — which keeps the signature small and the function total. Passing the instant rather than a
provider keeps `:domain` free of any calculation dependency (Principle II) and makes FR-004 nearly
free: a domain test supplies a literal `Instant` and never touches a location service or an
astronomical calculation.

It also means the fallback is not a separate code path bolted alongside the rule. It is the same
function with a null argument, so the two regimes cannot drift apart.

**Alternatives considered**:

- *Pass a `(LocalDate) -> Instant` resolver.* Rejected — a function parameter is harder to assert
  against, and invites callers to resolve dates the rule did not ask about.
- *Pass both bracketing Maghribs.* Rejected — the second is never consulted; an unused parameter is
  an invitation to pass the wrong thing.
- *Put the calculation in `:domain` and drop the parameter.* Rejected on Principle II — see R5.

---

## R5 — Where does the prayer-time calculation come from?

**Decision**: `com.batoulapps.adhan:adhan` 1.2.1 — the **Java port** (MIT) — added to `:data` only,
wrapped behind the `PrayerTimesProvider` interface declared in `:domain`.

**Amended 2026-08-30, on the reserve this entry already recorded.** `adhan2` was tried first and does
not build here: version 0.0.7 is compiled against Kotlin 2.4.0 and pulls `kotlin-stdlib:2.4.0` onto
`:data`'s compile classpath, whose metadata this project's Kotlin 2.2.10 cannot read (it reads up to
2.3.0). Every stdlib symbol in the KSP-generated `MizanDatabase_Impl.kt` then fails to resolve, so
`:data` does not compile at all. The Java port carries no Kotlin metadata and cannot conflict;
`:data:compileDebugKotlin` is green with it. The switch cost nothing because `AdhanPrayerTimes` had
not been written yet, which is exactly what holding the fallback in reserve was for.

It exposes the same surface this feature needs — `CalculationMethod`, `Madhab`, `PrayerTimes`,
`Coordinates`, `DateComponents` — over `java.util.Date` rather than `kotlinx-datetime`, so
`AdhanPrayerTimes` converts with `.toInstant()`. That removes `kotlinx-datetime` from the project
entirely and makes the `:data`-only confinement a matter of taste rather than necessity; it stays in
`:data` regardless, because `:domain` must not know what computes a prayer time.

**Rationale**: Prayer-time calculation is astronomy plus a body of convention-specific parameters,
and getting it subtly wrong produces a boundary that is off by minutes in a way no test the team
would think to write would catch. Adhan is the established implementation, is MIT licensed, ships
the named authorities FR-003 needs (Umm al-Qura, Egyptian General Authority, Muslim World League and
others) including the Asr madhab parameter, and computes entirely offline — which FR-002 requires.

It goes in `:data` and not `:domain` because constitution Principle II limits that module to Kotlin,
the standard library and coroutines, and `:domain`'s build file has no `com.android.library` precisely
so that violations fail the compile rather than the review. Confining a calculation engine to `:data`
behind a domain interface is the same shape every repository in the project already uses.

**Alternatives considered**:

- *Hand-write the solar-position calculation.* Genuinely considered, and attractive at first: Maghrib
  is sunset, the NOAA algorithm is about 120 lines, it is pure Kotlin, and it could live in `:domain`
  with no dependency at all. Rejected because spec 010 needs Fajr, Dhuhr, Asr and Isha from this same
  provider, and FR-003 names calculation authorities — so the real requirement is a full convention
  engine, not a sunset function. Writing that is more risk than taking it.
- *`com.batoulapps.adhan:adhan2` 0.0.7, the Kotlin rewrite.* Tried first and rejected on evidence, not
  taste: its Kotlin 2.4.0 metadata and the `kotlin-stdlib:2.4.0` it drags in are unreadable by this
  project's Kotlin 2.2.10 compiler, and `:data` does not compile with it on the classpath. Revisit only
  if the project's Kotlin version moves past 2.4.0, and only if there is a reason beyond tidiness.
- *A prayer-times API.* Rejected on sight — FR-002 and Principle IV forbid a network call on this
  path, and Principle VII forbids fetching the calculation from a server.

---

## R6 — How is the person's region resolved on-device?

**Decision**: From the IANA time zone id already available through `TimeProvider.zone()`, mapped to a
calculation convention through an administrator-defined seed, with the Muslim World League convention
and Standard Asr as the documented default where nothing matches.

**Rationale**: FR-003b requires region resolution with no network call and no reverse-geocoding
service, because a convention resolvable only online would put the day boundary behind connectivity —
which Principle IV forbids. The zone id satisfies that completely: it is already in hand, costs
nothing, needs no permission, and works in airplane mode on a fresh install. `Africa/Cairo` and
`Asia/Riyadh` identify their countries unambiguously, which is what a convention mapping needs.

It is also the *same* signal FR-012b uses to invalidate stale coordinates. One input driving both
rules is strictly better than two mechanisms that can disagree about whether the person has moved.

**Alternatives considered**:

- *Reverse-geocode the coordinates.* Rejected — `Geocoder` is a network call on most devices, which
  FR-003b forbids outright.
- *Bundle a country-boundary dataset and look the coordinates up offline.* Genuinely offline and more
  precise, but it adds megabytes to the APK and a data-maintenance burden, to distinguish cases the
  zone id already distinguishes. Rejected on Principle VIII — that is a capability this increment
  does not need.
- *Device locale or SIM country.* Rejected — locale is a language preference, not a location, and a
  traveller's SIM says where they bought it.

---

## R7 — How is the changeover handled without rewriting history?

**Decision**: A monotonic clamp in the boundary provider, **armed only across a regime change**. The
provider remembers the last accountability date it resolved *and the regime it resolved it under*.
When the regime is unchanged the computed date is adopted exactly as computed. When it differs — the
seam — the computed date is clamped so it can neither move backwards nor forward by more than one
day.

**Rationale**: The transition can otherwise skip or duplicate a date. If the changeover lands after
that evening's Maghrib, the newly computed date is one ahead of the date the app was already on, and
naive adoption would leave a date that never existed for that person. Clamping makes FR-023
structural rather than conditional: a skipped or duplicated date is unrepresentable, because the only
permitted moves are "stay" and "advance by one".

It also covers every transition with one mechanism rather than four special cases — the initial
changeover, the first location fix arriving mid-day, the person erasing coordinates, and a zone-change
invalidation are all just the resolved date moving, and all are clamped identically.

Crucially it touches nothing stored. No day plan is rewritten, re-dated or recomputed, so FR-021 and
Principle III hold by construction rather than by care. The irregular day or two at the seam is
recorded as it occurred, which FR-024 requires.

**Why the clamp is armed only at the seam.** An always-on clamp is not extra safety, it is a defect.
A person who does not open the app for five days returns to `computed = lastResolved + 5`, is handed
`lastResolved + 1`, and records the evening's completions against an accountability date that closed
four days ago — Principle III harm arriving from the other direction, since nothing stored is
rewritten but new rows land in a day that is already closed. It also makes the resolved date a
function of how often the app was opened, which FR-017 forbids outright, and it makes a corrected
device clock uncorrectable. Skipping and duplication are only possible *at* a regime transition,
because that is the only moment two different rules are applied either side of one instant; within a
regime the mapping already advances exactly one day per Maghrib and needs no help. So the clamp is
scoped to the case it exists for, and FR-023a records that scope in the spec.

Detecting the seam needs the previous *regime*, not just the previous date, so `boundary_state`
carries `lastResolvedRegime` alongside `lastResolvedDate`. Persisting it is what makes the seam
survive process death — the changeover is almost always the first launch after an update, so an
in-memory-only comparison would miss the one transition this mechanism exists for.

**Alternatives considered**:

- *A one-off data migration re-dating recent completions.* Rejected — this is exactly the
  Principle III violation the constitution's v2.0.0 sync-impact report warned about, and it is
  non-negotiable.
- *Adopt the computed date immediately and accept a skipped date.* Rejected — FR-023 forbids it, and
  a person losing a day from their record for no reason they can see is the kind of thing that
  destroys trust in an accountability app.
- *Freeze the boundary until the next midnight and switch cleanly.* Rejected — it adds a third,
  temporary regime that exists only during the transition, and Principle VII forbids a second opinion
  about when a day begins.
- *Keep the clamp armed at all times.* Rejected — see above. It credits new completions to a closed
  date after any absence, makes the resolved date depend on launch frequency in violation of FR-017,
  and stops a corrected device clock from taking effect.

---

## R8 — What replaces the streak's 20:00 at-risk rule?

**Decision**: A fixed offset before the accountability day's **end** — the next Maghrib — rather than
a fixed wall-clock time. `StreakClock` keeps its role as the single home of the rule; only the rule's
definition changes.

**Rationale**: A day that begins at 17:00 Maghrib in winter ends at 17:00 the next day. A fixed 20:00
at-risk time would fire three hours into a twenty-four hour day — absurdly early — and at a high
enough latitude in summer it can fall outside the day altogether, which would make the at-risk state
either permanent or unreachable. FR-029 requires the point to sit inside its own day at every time of
year, which only an offset from an endpoint can guarantee.

The end is the right endpoint rather than the start: the point of the state is "there is still time,
but not much", which is a statement about how much day remains.

`StreakClock.nextBoundaryAfter` — which `GetStreakSummary` uses to re-emit without polling — becomes
the earlier of the at-risk instant and the day's end, with both supplied rather than derived from a
clock, exactly as the current signature already does.

**Alternatives considered**:

- *Keep 20:00 and accept the drift.* Rejected — SC-009 fails at both solstices.
- *A proportion of the day's length, e.g. the last sixth.* Rejected as harder to explain to a user
  and no more correct; a fixed offset is predictable and testable.
- *Leave the at-risk state out of this increment.* Rejected — it reads `TimeProvider` and would be
  silently wrong the moment the boundary moves, which is worse than changing it.

---

## R9 — Which location API?

**Decision**: The platform `LocationManager`, requesting **coarse** accuracy only. No Google Play
Services dependency is introduced.

**Rationale**: The app currently has no Play Services dependency, and adding one for a city-accurate
position would be a large dependency for a small need. Prayer-time calculation is insensitive to
metre-level precision — coarse location moves Maghrib by seconds — so FR-005's "coarsest accuracy
sufficient" points at `ACCESS_COARSE_LOCATION` and nothing finer. Requesting only coarse permission
is also the honest posture for FR-006: the app cannot retain precision it never asked for.

Acquisition is opportunistic, matching the spec's assumption: `getLastKnownLocation` first, which is
free and often sufficient, then a single current-fix request when it is not. `getCurrentLocation`
exists from API 30; below that (the project's `minSdk` is 24) a single-shot
`requestLocationUpdates` with immediate removal is the equivalent. No continuous tracking is
requested at any point.

**Alternatives considered**:

- *`FusedLocationProviderClient`.* Better fixes and better battery behaviour, but a new Google
  dependency for a need that coarse platform location already meets. Rejected on Principle VIII.
- *Fine location for accuracy.* Rejected — it buys nothing the calculation can use and asks the
  person for more than the feature needs.
- *Continuous location updates so travel is detected immediately.* Rejected — the spec's assumption
  is explicit that this is a periodic or on-demand fix, not a background track, and FR-012b's
  zone-change signal already covers the travel case that matters.
