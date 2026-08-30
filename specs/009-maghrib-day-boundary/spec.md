# Feature Specification: Maghrib-Anchored Day and Week Boundary

**Feature Branch**: `spec/009-maghrib-day-boundary`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "Maghrib-anchored day and week boundary — the foundational increment
that makes constitution v2.0.0 Principle VII real."

## Context

The constitution was amended to v2.0.0 on 2026-08-30. It redefines the accountability day from local
midnight-to-midnight to Maghrib-to-Maghrib (the calculated sunset prayer time for the person's
current location), and the week from Saturday-to-Friday to Maghrib-Friday to Maghrib-Friday. It also
requires a single injected location and prayer-time calculation provider, held to the same
discipline the clock already is.

The code has not moved. The day rule still returns the device's civil date and the week rule still
starts weeks on Saturday. Every already-merged feature — Today, the weekly sheet, streaks, history,
insights, sync, and the leaderboard — reads those two rules. This feature is the increment that
changes them, and it is the only one permitted to.

Two decisions the constitution deliberately deferred to this spec, and requires be made explicitly
here rather than by default, are settled below: what "what day is it" resolves to when a location or
a calculation cannot be obtained (FR-012 through FR-016), and what happens to history already
recorded and closed under the previous boundary (FR-021 through FR-025).

## Clarifications

### Session 2026-08-30

- Q: How much of the leaderboard should this increment actually change to keep FR-032's guarantee
  true? → A: Narrow fix. Region derivation is left alone — spec 008's aggregation already groups by
  the device-assigned accountability date, so no cross-date comparison can occur. Only the weekly
  period span and the Honor Board close instant move onto the new boundary.
- Q: When someone revokes location permission after the app has already stored their coordinates,
  should the app keep using those stored coordinates to compute Maghrib? → A: Yes — keep the last
  known location and keep the Maghrib boundary, but disclose plainly that it is held and being used,
  and provide an explicit control that erases it and switches to the fallback boundary.
- Q: Which prayer-time calculation authority should the app be fixed to? → A: None globally. The
  convention is derived automatically from the person's region — Egypt gets the Egyptian General
  Authority of Survey, Saudi Arabia gets Umm al-Qura, and so on through an administrator-defined
  mapping — with a documented default where no regional authority is configured. It stays automatic
  and is never a per-person setting.
- Q: How long should the app keep trusting stored coordinates when it cannot get a fresh fix? → A:
  Indefinitely, for as long as the device's IANA time zone is unchanged — age alone never
  invalidates them. If the time zone changes, attempt a fresh fix; if none can be obtained, stop
  using the old coordinates and use the fallback boundary until one arrives, telling the person
  rather than letting the day boundary move silently.
- Q: At what point should the app ask for location permission? → A: On first launch, non-blocking.
  The app renders immediately on the fallback boundary and shows a dismissible explanation saying
  that location enables accurate local prayer times and the Maghrib-based Islamic day boundary. The
  system permission dialog is triggered only after the person explicitly chooses "Enable location";
  choosing "Not now" leaves the app fully usable on the fallback, and location setup stays reachable
  from settings.
- Note (raised while integrating an answer above, not a separate question): constitution v2.0.0
  Principle VII currently reads "a **single**, administrator-fixed calculation convention with no
  per-user choice of method". A region-keyed mapping satisfies that rule's intent — it is
  administrator-defined and offers no per-person choice — but not its literal wording. Principle VII
  needs a PATCH amendment reading "a single administrator-fixed convention per region" before this
  feature can pass its own Constitution Check. Recorded here as required follow-up; see FR-003d.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The day turns over at Maghrib (Priority: P1)

A person finishes their Maghrib practices and opens the app. The app has already moved on to the
next accountability day, exactly as the Islamic day does — not at some later midnight, and not at a
fixed clock time, but at the moment the sun set where they actually are.

**Why this priority**: This is the entire point of the amendment. Everything else in this feature
exists to make this correct, safe, and consistent. Without it the app records a different day than
the one the person is living.

**Independent Test**: With a fixed location and a controllable clock, advance time across a
calculated Maghrib and confirm the accountability day advances at that instant and not at midnight.
Advance across midnight and confirm nothing turns over there.

**Acceptance Scenarios**:

1. **Given** a known location and a clock just before that location's Maghrib, **When** the clock
   passes Maghrib, **Then** the accountability day advances to the next date at that instant.
2. **Given** a known location and a clock just before local midnight, **When** the clock passes
   midnight, **Then** the accountability day does not change.
3. **Given** the clock is between Maghrib and midnight, **When** the person records a completion,
   **Then** it is credited to the accountability day that began at that Maghrib, not to the date the
   device calendar shows.
4. **Given** two locations at meaningfully different longitudes or latitudes on the same date,
   **When** each computes its own Maghrib, **Then** each turns over at its own instant rather than
   at a shared one.

---

### User Story 2 - The week closes at Friday Maghrib (Priority: P1)

At Maghrib on Friday, a person's week closes. The weekly total freezes at that instant and the new
week begins in the same moment, with nothing falling between the two.

**Why this priority**: The weekly sheet is the artifact the product is built around, and the week
boundary is what freezes it. A week that closes at the wrong instant makes every weekly figure in
the app wrong, and — once the leaderboard is involved — makes them wrong for other people too.

**Independent Test**: With a fixed location and a controllable clock, advance across Friday's
Maghrib and confirm the previous week's totals stop changing at that instant and the new week begins
at the same instant with no gap and no overlap.

**Acceptance Scenarios**:

1. **Given** a week in progress, **When** the clock reaches Friday's calculated Maghrib, **Then**
   that week's totals freeze at that instant and the following week begins at the same instant.
2. **Given** the clock is between Friday Maghrib and Friday midnight, **When** the weekly sheet is
   opened, **Then** it shows the new week, not the one that just closed.
3. **Given** a completion recorded between Friday Maghrib and Friday midnight, **When** weekly
   totals are computed, **Then** it counts toward the new week and never toward the closed one.
4. **Given** any instant, **When** the week containing it is determined, **Then** exactly one week
   contains it — no instant belongs to two weeks or to none.

---

### User Story 3 - The app still works when it cannot tell where the person is (Priority: P1)

A person installs the app in airplane mode, or declines location access, or is somewhere their
device cannot get a fix. They can still open the app, see today's tasks, record them, and see their
score. The app tells them plainly which day boundary it is currently using and why.

**Why this priority**: The constitution records this as a real, accepted tension: a device's own
midnight has zero dependencies, while a Maghrib boundary depends on a resolved location and a
successful calculation. The app must remain fully usable on a fresh install in airplane mode. A
boundary that can become unavailable would take the entire core loop down with it.

**Independent Test**: Fresh install, airplane mode, location permission never granted. Confirm the
app opens, resolves an accountability day, records completions, and states which boundary is in
force. Then supply a location and confirm the app moves onto the Maghrib boundary and says so.

**Acceptance Scenarios**:

1. **Given** a fresh install with no location ever obtained and no connectivity, **When** the person
   opens the app, **Then** the app resolves an accountability day deterministically, shows today's
   tasks, and allows recording.
2. **Given** the fallback boundary is in force, **When** the person looks at where the app states
   its day boundary, **Then** it says plainly that the Islamic day boundary is unavailable, which
   boundary is being used instead, and what would resolve it.
3. **Given** a location was obtained at least once and the device is now offline with no fresh fix,
   **When** the accountability day is resolved, **Then** the Maghrib boundary is still used,
   computed from the last known coordinates, with no network call and no guess.
4. **Given** location access is permanently denied, **When** the person uses the app over several
   days, **Then** every feature except the Maghrib boundary itself behaves normally, and no screen
   fails, blocks, or crashes.
5. **Given** a location or a calculation cannot be resolved, **When** the day boundary is asked for,
   **Then** it returns its specified fallback immediately — never an undefined result, never a
   guessed location, never an indefinite wait.
6. **Given** coordinates were obtained and location permission is then revoked, **When** the person
   keeps using the app, **Then** the Maghrib boundary continues from the retained coordinates, and
   the app states plainly that a last known location is held and is being used for the day boundary.
7. **Given** coordinates are held, **When** the person uses the control that erases them, **Then**
   the fallback boundary takes over immediately, the app stops saying a location is held, and no
   already-closed day or week changes.
8. **Given** held coordinates and an unchanged time zone, **When** 90 days pass with no fresh fix,
   **Then** the Maghrib boundary is still used and the coordinates are still trusted.
9. **Given** held coordinates, **When** the device's time zone identifier changes and no fresh fix
   can be obtained, **Then** the fallback boundary takes over and the app tells the person that it
   has, and why.
10. **Given** the fallback took over after a time-zone change, **When** a fresh location is
    obtained, **Then** the Maghrib boundary resumes from the new coordinates.
11. **Given** a first launch, **When** the app opens, **Then** it is immediately usable on the
    fallback boundary and no system permission dialog has been raised.
12. **Given** the first-launch explanation is shown, **When** the person chooses to decline, **Then**
    the app stays fully usable, the copy carries no pressure or guilt, and location setup remains
    reachable from settings.

---

### User Story 4 - Existing history still reads exactly as it did (Priority: P1)

A person who has been using the app opens last month. Every day and every week reads exactly the
figure it read before the boundary changed. Nothing was recomputed, corrected, or moved.

**Why this priority**: Principle III admits no exceptions. Days already recorded were true
statements under the boundary in force when they were recorded, and a record that rewrites itself
when a definition changes is not a record. This is also the single largest risk in the increment:
the tempting "fix up the old data" move is the one the constitution forbids.

**Independent Test**: Record several days and a closed week under the old boundary, apply the
change, and assert every previously closed day and week reports identical earned points, available
points, percentage, and Hijri label afterwards.

**Acceptance Scenarios**:

1. **Given** days recorded and closed before the change, **When** the change takes effect, **Then**
   every one of those days reports the same earned points, available points, and percentage as
   before.
2. **Given** a week closed before the change, **When** it is opened afterwards, **Then** its per-day
   figures and weekly total are unchanged.
3. **Given** a completion recorded before the change, **When** it is read afterwards, **Then** the
   accountability date it was credited to is unchanged.
4. **Given** the change has taken effect, **When** anything in the app is used, **Then** no
   previously closed day or week is ever rewritten, re-dated, or recomputed.

---

### User Story 5 - Every screen agrees about what day it is (Priority: P2)

A person moves between Today, the weekly sheet, their streak, history, insights, and the
leaderboard. All of them agree about which day it is and which week it is, at every instant,
including in the hours between Maghrib and midnight where the old and new answers differ.

**Why this priority**: The constitution's reason for a single home for these rules is that
duplicated boundary logic guarantees two screens eventually disagree. This feature changes the rule,
which is precisely when a second opinion gets left behind somewhere.

**Independent Test**: With the clock set between Maghrib and midnight, open each surface in turn and
confirm all report the same accountability date and the same week.

**Acceptance Scenarios**:

1. **Given** the clock is between Maghrib and midnight, **When** each surface is opened, **Then**
   every one reports the same accountability date.
2. **Given** the clock is between Friday Maghrib and Friday midnight, **When** each surface that
   shows a week is opened, **Then** every one reports the same week.
3. **Given** the streak's day-at-risk point, **When** it is evaluated, **Then** it falls inside the
   accountability day it belongs to, at every time of year and at any latitude the app supports.
4. **Given** any surface in the app, **When** it needs to know the current date or week, **Then** it
   obtains it from the single boundary rule rather than computing one of its own.

---

### Edge Cases

- **What happens on the day the change takes effect?** The accountability day already in progress
  keeps the date it was opened under, and no date is skipped (FR-022, FR-023). One or two days at
  the seam are irregular in length as a one-time consequence; they are recorded as they occurred and
  are never repaired afterwards.
- **What happens between Maghrib and midnight on the changeover day itself?** This is the window in
  which the old and new rules give different answers, and is therefore the case most likely to
  produce a duplicated or skipped date. It is covered explicitly by FR-022 and FR-023 and must be
  tested at both a before-Maghrib and an after-Maghrib cutover instant.
- **What happens at latitudes where the sun does not set on a given date?** Above the polar circles
  there is no Maghrib to calculate on some dates. The provider must return its unavailable outcome
  rather than a guessed or extrapolated time, and the fallback boundary applies for those dates.
- **What happens when a person is in a region the mapping does not cover?** They get the documented
  default convention (FR-003c). The app does not fail, does not ask them to choose, and does not
  leave the boundary unresolved.
- **What happens to the convention when a person travels between regions?** The convention follows
  the region the provider resolves, the same way the coordinates do. A day already closed keeps the
  convention it was closed under (FR-003e, FR-021).
- **What happens when the person travels across time zones or a long distance mid-day?** If a fresh
  fix is obtained, the boundary follows the new location. If the time zone changed and no fix can be
  obtained, the old coordinates stop being trusted and the fallback takes over until one arrives
  (FR-012b, FR-012d). A day shortened or lengthened by travel is expected and correct; a day
  duplicated or skipped is not.
- **What happens when someone travels a long distance without crossing a time zone?** The
  coordinates stay trusted and the boundary keeps using them until a fresh fix arrives. This is the
  known limit of the time-zone signal, accepted because north-south travel within one zone moves
  sunset far less than the east-west travel that changes zones, and because the alternative — an age
  cap — flips the boundary for people who have not moved at all.
- **What happens when the device's own clock or time zone is changed?** A time-zone identifier
  change invalidates coordinates per FR-012b; a daylight-saving offset change does not (FR-012e). A
  change to the device clock moves the current instant, which may advance or rewind the
  accountability day — the result must remain deterministic and must never rewrite a closed day.
- **What happens when a location is obtained for the very first time part-way through a day?** The
  app moves onto the Maghrib boundary from that point. The day in progress keeps its date and no
  date is skipped, the same rule as the initial changeover (FR-022, FR-023).
- **What happens to the streak's day-at-risk point when the day's length changes?** It is defined
  relative to the day's own end rather than as a fixed wall-clock time (FR-029), so it stays inside
  the day whether that day is fourteen hours long in winter or twenty-four hours long at the seam.
- **What happens to leaderboard participants who no longer share a date within a region?** Nothing
  needs to. Aggregation already groups by the accountability date the recording device assigned, so
  participants on different dates at the same instant land in different period buckets and are never
  compared (FR-032). What does move is when a leaderboard week closes (FR-032a).
- **What happens when a person is signed in on two devices in different places?** Each device
  resolves its own boundary from its own location. Records already carry the accountability date the
  recording device assigned, and that date is never re-derived elsewhere.

## Requirements *(mandatory)*

### Functional Requirements

**The single location and prayer-time provider**

- **FR-001**: The system MUST have exactly one provider of the person's location and of calculated
  prayer times. No other code may read device location or compute a prayer time.
- **FR-002**: The provider MUST compute prayer times entirely on the device from a location and a
  date. It MUST NOT require a network call, and MUST NOT fetch a time from any server.
- **FR-003**: The provider MUST select its calculation convention automatically from the person's
  region, through an administrator-defined mapping of region to convention — for example Egypt to
  the Egyptian General Authority of Survey, and Saudi Arabia to Umm al-Qura. The mapping is fixed
  content, in the sense Principle VI already uses for the task catalogue.
- **FR-003a**: There MUST be no per-person choice of calculation method, calculation authority, or
  Asr madhab anywhere in the product. Selection is automatic and is not a setting.
- **FR-003b**: The region a convention is selected for MUST be resolved entirely on the device, with
  no network call and no reverse-geocoding service. A convention that could only be resolved online
  would put the day boundary behind connectivity, which Principle IV forbids.
- **FR-003c**: When no mapping entry matches the person's region, the provider MUST use a single
  documented default convention rather than failing, guessing, or asking. The default is the Muslim
  World League convention with Standard (non-Hanafi) Asr.
- **FR-003d**: *(Satisfied 2026-08-30. Record of a closed gate, not buildable work — it carries no
  implementation task, by design.)* This feature required constitution Principle VII's wording —
  "a single, administrator-fixed calculation convention" — to be amended to permit one convention per
  region before it could be planned, since a plan may not pass a Constitution Check against a rule it
  contradicts on its face. Principle VII was amended accordingly in constitution v2.0.1, which also
  added the on-device region-resolution requirement now restated as FR-003b and the documented-default
  requirement now restated as FR-003c. This requirement is retained as the record of that gate, not as
  outstanding work.
- **FR-003e**: A change to the region-to-convention mapping MUST affect future days only, and MUST
  NOT alter any day or week already closed (a specific case of FR-021).
- **FR-004**: Both the location and the calculated result MUST be substitutable in tests, so that
  every boundary rule can be exercised by fixing a location and advancing a clock, with no real
  location service and no real astronomical calculation involved.
- **FR-005**: The provider MUST request only the coarsest location accuracy sufficient to calculate
  prayer times. It MUST NOT request or retain finer accuracy than that.
- **FR-006**: The person's location MUST be used only to calculate prayer times on the device. It
  MUST NOT be transmitted anywhere, and MUST NOT be retained beyond what the calculation and the
  fallback rules below require.
- **FR-007**: The system MUST explain why location is needed before asking for it, and MUST continue
  to function if it is refused (FR-012 through FR-017).
- **FR-007a**: On first launch the app MUST render and be fully usable immediately, on the fallback
  boundary, without waiting for a location, a permission decision, or a dialog. Nothing may be placed
  in front of the core loop.
- **FR-007b**: On first launch the app MUST show a dismissible explanation stating that location
  enables accurate local prayer times and the Maghrib-based Islamic day boundary, offering an
  explicit choice to enable location or to decline.
- **FR-007c**: The system permission dialog MUST be raised only after the person explicitly chooses
  to enable location. It MUST NOT be raised automatically on launch or as a side effect of any other
  action.
- **FR-007d**: Declining MUST leave the app fully usable on the fallback boundary, and location
  setup MUST remain reachable afterwards from the existing settings surface.
- **FR-007e**: Neither the explanation nor the declined state may use pressure, guilt, warning
  framing, or any suggestion that the person's record is lesser for declining, per Principle IX.
  Declining is a supported way to use the app, not a degraded one.

**The day boundary**

- **FR-008**: The accountability day MUST run from Maghrib to the next Maghrib, calculated for the
  person's location.
- **FR-009**: The instant a day begins MUST be the calculated Maghrib, not a rounded, offset, or
  approximated time.
- **FR-010**: The rule mapping an instant to an accountability date MUST exist in exactly one place.
  No screen, query, or aggregate may hold a second opinion about which day an instant belongs to.
- **FR-011**: Every instant MUST belong to exactly one accountability day — never to two, never to
  none.

**Fallback when location or calculation is unavailable**

- **FR-012**: Whenever coordinates are held and trusted — obtained at least once, not since erased
  under FR-017c, and not invalidated under FR-012b — the system MUST use the Maghrib boundary,
  calculating it from the most recently obtained coordinates, with no network call required and for
  any date.
- **FR-012a**: Held coordinates MUST remain trusted for as long as the device's time zone identifier
  is unchanged, however old they are. Age alone MUST NOT invalidate them, because a stationary
  person's boundary must not flip on a timer.
- **FR-012b**: When the device's time zone identifier changes, the system MUST attempt to obtain a
  fresh location. Until one is obtained, the previously held coordinates MUST be treated as no
  longer trusted, and the fallback boundary of FR-013 MUST be used.
- **FR-012c**: When a fresh location is obtained after FR-012b, the system MUST resume the Maghrib
  boundary from the new coordinates. The changeover rules of FR-022 through FR-024 apply to both
  transitions.
- **FR-012d**: The system MUST tell the person when coordinates have stopped being trusted and the
  fallback has taken over, and why. The day boundary MUST NOT change without them being able to find
  out that it did.
- **FR-012e**: A change in the device's clock offset alone — daylight saving, for example — MUST NOT
  invalidate coordinates. Only a change of time zone identifier does.
- **FR-013**: When no coordinates are held — none ever obtained, or the person has erased them — the
  system MUST fall back to a boundary that requires no location at all: the device's local midnight
  for the day, and Saturday to Friday for the week.
- **FR-014**: The system MUST NOT substitute a guessed, default, or invented location in order to
  produce a Maghrib. Falling back per FR-013 is the only permitted response to having no location.
- **FR-015**: A request for the current accountability day MUST always return a result promptly. It
  MUST NOT block indefinitely waiting for a location, and MUST NOT fail or crash when a location or
  a calculation is unavailable.
- **FR-016**: While the fallback boundary of FR-013 is in force, the system MUST state plainly,
  somewhere the person can see, that the Islamic day boundary is unavailable, which boundary is
  being used instead, and what would resolve it.
- **FR-017**: The choice between the Maghrib boundary and the fallback MUST be deterministic and
  MUST depend only on whether coordinates are currently held, whether they are still trusted per
  FR-012a and FR-012b, and whether the calculation succeeded — never on timing, retries, elapsed age,
  or chance.
- **FR-017a**: When location permission is revoked after coordinates have been obtained, the system
  MUST retain those coordinates and MUST keep using the Maghrib boundary. Revocation stops the app
  acquiring a new location; it does not by itself change which day boundary is in force, because
  doing so would re-date the person's days without them asking for that.
- **FR-017b**: While coordinates are held, the system MUST state plainly, somewhere the person can
  reach, that a last known location is retained and is being used to determine the day boundary.
- **FR-017c**: The system MUST provide an explicit control that erases the retained coordinates.
  Using it MUST take effect immediately: the Maghrib boundary stops being used, the fallback of
  FR-013 takes over, and the changeover rules of FR-022 through FR-024 apply to that transition
  exactly as they do to any other.
- **FR-017d**: Erasing the retained coordinates MUST NOT alter, re-date, or recompute any day or
  week already closed (a specific case of FR-021).

**The week boundary**

- **FR-018**: The accountability week MUST run from Maghrib on Friday to Maghrib on the following
  Friday.
- **FR-019**: At the closing instant, the previous week's totals and standings MUST freeze and the
  new week MUST begin in the same instant, with no gap and no overlap.
- **FR-020**: The rule mapping an instant or a date to a week MUST exist in exactly one place,
  alongside the day rule.

**History already recorded under the previous boundary**

- **FR-021**: Days and weeks recorded and closed before this change takes effect MUST NOT be
  recomputed, re-dated, corrected, or migrated. They stand exactly as recorded. This follows
  Principle III, which admits no exceptions, and settles the tension the constitution's v2.0.0
  amendment recorded.
- **FR-022**: At the instant the new boundary takes effect, the accountability day already in
  progress MUST keep the date it was opened under, and MUST close at whichever boundary instant the
  new rule places next.
- **FR-023**: The changeover MUST NOT skip an accountability date and MUST NOT produce two days
  carrying the same date. Where the new rule would otherwise do either, the day in progress closes
  at the changeover instant and the next day begins there.
- **FR-023a**: The protection in FR-022 and FR-023 applies to a changeover — a transition between
  boundary regimes — and to nothing else. While the regime in force is unchanged, the accountability
  date MUST be exactly the one the boundary rule computes for the current instant, however far it has
  moved since the app was last opened and in whichever direction. A person who does not open the app
  for a week MUST see the current date on their return, not a date a week behind that advances one
  day per launch, and a corrected device clock MUST take effect immediately. Anything else would
  credit a new completion to a day that has already closed, and would make the resolved date depend
  on how often the app happened to be opened — which FR-017 forbids.
- **FR-024**: Days made irregular in length by the changeover MUST be recorded as they occurred and
  MUST NOT be repaired, padded, or annotated as errors afterwards.
- **FR-025**: The system MUST be accompanied by a test that records days and a closed week under the
  previous boundary, applies the change, and asserts every one of those days and that week reports
  identical figures afterwards, per the constitution's standing requirement for any change touching
  persistence.

**Bringing existing surfaces onto the new boundary**

- **FR-026**: Day rollover while the app is open MUST occur at the new boundary instant.
- **FR-027**: The weekly sheet MUST use the new week boundary for the current week, for navigation
  between weeks, and for backfilling elapsed days.
- **FR-028**: A consistency day for streak purposes MUST be an accountability day under the new
  boundary.
- **FR-029**: The streak's day-at-risk point MUST be defined relative to the end of the
  accountability day rather than as a fixed wall-clock time, so that it always falls inside the day
  it belongs to regardless of the day's length or the time of year.
- **FR-030**: History and insights MUST read the same accountability dates and weeks as every other
  surface, with no separate date derivation of their own.
- **FR-031**: The accountability date a record carries MUST continue to be assigned once, by the
  device that recorded it, and MUST NOT be re-derived afterwards by any other device or by any
  server.
- **FR-032**: Leaderboard region derivation MUST NOT change in this feature. The guarantee that no
  participant is ranked against someone on a different accountability date is already structural:
  aggregation groups by the accountability date the recording device assigned (FR-031), so two
  participants who are on different dates at the same instant fall into different period buckets and
  cannot be compared.
- **FR-032a**: The leaderboard's weekly period span and the Honor Board's close instant MUST move
  onto the new week boundary (FR-018), so that a leaderboard week closes at the same instant as the
  weekly sheet's week rather than at a Saturday-to-Friday span of its own.
- **FR-032b**: No remote schema change, aggregation-job rewrite, or row-level policy change is in
  scope for this feature. If planning finds one to be unavoidable, that is a signal to stop and
  reconsider the increment's boundaries rather than to widen them.
- **FR-033**: No leaderboard period already closed before this change MUST be reopened, recomputed,
  or re-ranked (a specific case of FR-021).

**The Hijri label**

- **FR-034**: The Hijri date MUST remain a label attached to a day and MUST NOT become the thing
  that defines the day's boundaries, even though Maghrib-to-Maghrib is the traditional Hijri day.
- **FR-035**: The Hijri label MUST continue to be computed independently on the device and MUST NOT
  be read from any synced Hijri calendar lookup or calendar service.
- **FR-036**: Hijri labels already recorded against closed days MUST NOT change (a specific case of
  FR-021).

### Key Entities

- **Accountability Day**: The unit everything is recorded against. It begins at a calculated Maghrib
  and ends at the next one, and carries a date, a Hijri label, and the set of tasks and points that
  applied to it.
- **Accountability Week**: Seven consecutive accountability days, beginning at Maghrib on Friday and
  ending at Maghrib on the following Friday.
- **Boundary Provider**: The single authority on which accountability day and week an instant
  belongs to, and on which boundary rule — Maghrib or fallback — is currently in force.
- **Location and Prayer Time Provider**: The single authority on the person's coordinates and on
  prayer times calculated from them. It has exactly three outcomes: a calculated time, an
  unavailable-because-no-location outcome, and an unavailable-because-calculation-failed outcome.
- **Calculation Convention**: The authority whose method is used to calculate prayer times —
  parameters such as the Fajr and Isha angles and the Asr madhab. Selected automatically per region,
  never by the person.
- **Region-to-Convention Mapping**: The administrator-defined table naming which convention applies
  in which region, plus the documented default used where no entry matches. Fixed content, resolved
  on the device, and versioned so that a change to it affects future days only.
- **Last Known Coordinates**: The most recently obtained coordinates, retained solely so that
  Maghrib remains computable offline and so that the boundary does not disappear the moment a fix
  cannot be taken. Their presence is disclosed to the person and they are erasable on demand
  (FR-017b, FR-017c). They carry the time zone identifier they were obtained under, which is what
  decides whether they are still trusted (FR-012a, FR-012b). Two things return the app to the
  fallback once it has left it: the person erasing them, and a time-zone change with no fresh fix
  available.
- **Boundary Regime**: Which of the two boundary rules is in force — Maghrib, or the no-location
  fallback — and therefore what the app tells the person about its own day boundary.
- **Changeover Seam**: The one-time transition between the previous boundary and the new one,
  including the irregular day or days it produces, which are recorded and never repaired.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With a fixed location, the accountability day advances at the calculated Maghrib
  instant and at no other instant, verified across a full year of dates including both solstices.
- **SC-002**: With a fixed location, the accountability week advances at Friday's calculated Maghrib
  and at no other instant, verified across a full year of dates.
- **SC-003**: Every instant across a full year maps to exactly one accountability day and exactly
  one accountability week, with no instant unmapped and no instant mapped twice.
- **SC-004**: On a fresh install in airplane mode with location never granted, a person can open the
  app, see today's tasks, record a completion, and see their score, with no error state and no wait
  for a location.
- **SC-005**: With coordinates obtained once, no further fix, no connectivity, and an unchanged time
  zone, the Maghrib boundary continues to resolve correctly for at least 90 consecutive days.
- **SC-005a**: After a time-zone change with no fresh fix obtainable, no accountability day is
  resolved from the superseded coordinates, and the person is told the boundary has changed and why.
- **SC-006**: Every day and week closed before the change reports identical earned points, available
  points, percentage, and Hijri label after the change, with zero exceptions.
- **SC-007**: The changeover produces no skipped accountability date and no duplicated
  accountability date, verified at a cutover placed before that day's Maghrib and again at one
  placed after it.
- **SC-008**: At any instant between Maghrib and midnight, every surface in the app reports the same
  accountability date and the same week as every other surface.
- **SC-009**: The streak's day-at-risk point falls inside its own accountability day on every day of
  a full year at the shortest and longest days the app supports.
- **SC-010**: A request for the current accountability day returns within the same time budget
  whether a location is available or not, and never fails.
- **SC-011**: The person's coordinates appear in no outbound request, no log, and no synced record,
  verified over a full session including a sync.
- **SC-012**: While the fallback boundary is in force, the app states which boundary it is using in
  a place the person can reach, and stops saying so once the Maghrib boundary takes over.
- **SC-013**: Exactly one place in the product determines the accountability day, exactly one
  determines the week, and exactly one reads location or computes a prayer time.
- **SC-014**: While coordinates are held, the app says so in a place the person can reach, and the
  erase control removes them and moves the app onto the fallback boundary within one use, with every
  already-closed day and week reporting identical figures afterwards.
- **SC-015**: A person in a region the mapping covers gets that region's convention, and a person in
  a region it does not cover gets the documented default, in both cases with no network call and no
  setting presented to them.
- **SC-016**: Changing the region-to-convention mapping leaves every already-closed day and week
  reporting identical figures.
- **SC-017**: On a first launch, the app is usable before any system permission dialog appears, and
  the dialog appears only after the person explicitly chooses to enable location.
- **SC-018**: Every string introduced by the location explanation and the declined state passes a
  review against the product's no-shame wording standard, with zero exceptions.

## Assumptions

- The changeover instant is the first launch after the change is installed. Nothing happens to a
  person's record before they open the app.
- Once coordinates have been obtained, Maghrib for any date is computable from them indefinitely
  without a network call, because the calculation is astronomical rather than looked up. This is
  what makes FR-012 possible, and is why offline days do not need the fallback at all. The fallback
  is reached in exactly three situations: before the first fix ever, after the person erases the
  coordinates, and after a time-zone change with no fresh fix obtainable.
- The fallback in FR-013 reuses the boundary the app used until now — local midnight and Saturday to
  Friday — rather than inventing a third rule, so that a person who never grants location keeps
  exactly the behaviour they have today rather than a degraded version of something new. The
  alternative considered and not chosen was asking the person to name a city, which would give a
  true Maghrib offline but introduces a setup screen and a second way for a location to enter the
  product; it remains available as a later increment if the fallback proves common in practice.
- How the person's region is resolved offline for FR-003b — from the device time zone the app
  already reports, from a bundled coarse coordinate lookup, or otherwise — is a planning decision.
  What the spec fixes is that it happens on the device, without a network call, and without asking
  the person.
- The Maghrib instant itself is largely insensitive to the choice of convention, since every
  convention in the mapping treats Maghrib as sunset. The region-keyed mapping therefore matters far
  more to the Fajr, Dhuhr, Asr and Isha windows spec 010 consumes than to this feature's own
  boundary, and is specified here only because this feature owns the provider.
- Coordinates are refreshed opportunistically — when the app is open and a fix is cheap, or when the
  location may plausibly have changed — not as a continuous background track.
- The precision of the last known coordinates need only be sufficient for prayer-time calculation,
  so a coarse fix, retained coarsely, is adequate for FR-012.
- "Meaningfully different location" for the purpose of recalculating means a distance at which the
  calculated Maghrib actually moves by more than a minute; the exact threshold is an implementation
  choice.
- The exact offset before the day's end at which the streak's at-risk point falls (FR-029) is a
  design choice, not a product decision requiring its own answer here, in the same way the fixed
  clock time it replaces was.
- Spec 010 (Notifications & Weekly Summaries) consumes the provider this feature introduces and does
  not build a second one. This feature is a prerequisite of that one and is planned and implemented
  first.
- No new network surface is introduced. The existing Hijri date synchronisation is unaffected and is
  still not the source of any boundary.
- Out of scope: notifications of any kind, any per-person choice of calculation method, any screen
  that displays prayer times as a feature in their own right, and any change to what tasks exist or
  what they are worth.
