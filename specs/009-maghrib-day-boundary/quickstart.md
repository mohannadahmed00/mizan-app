# Quickstart: Maghrib-Anchored Day and Week Boundary

**Feature**: `specs/009-maghrib-day-boundary` | **Date**: 2026-08-30

How to prove this increment works. Types and rules are in [data-model.md](./data-model.md) and
[contracts/](./contracts/); this document is the validation guide only.

---

## Prerequisites

- JDK 17, Android SDK 36, `minSdk 24` emulator or device
- Constitution **v2.0.1** — the region-per-convention amendment. Planning was gated on it (FR-003d)
- A device or emulator whose time zone and clock can be changed, for the seam and travel scenarios

---

## Commands

```bash
./gradlew :domain:test                 # boundary rules, clamp, convention mapping, at-risk offset
./gradlew :data:connectedAndroidTest   # migration 4→5, boundary state store, provider
./gradlew :app:test                    # ViewModel state for the prompt and the settings section
./gradlew :app:connectedAndroidTest    # Compose UI for both surfaces
./gradlew test connectedAndroidTest    # everything
```

Domain tests need no emulator and no fakes for the boundary rule itself — it takes the Maghrib
instant as a parameter (research R4).

---

## Scenario 1 — The day turns over at Maghrib (US1, SC-001)

**Setup**: fixed coordinates, `FakeTimeProvider`, `FakePrayerTimes` returning a known Maghrib.

1. Set the clock one minute before that date's Maghrib. Read `today()`.
2. Advance one minute past Maghrib. Read `today()` again.
3. Separately, set the clock either side of local midnight.

**Expect**: the date advances at step 2 and **not** at step 3. Repeat across a full year including
both solstices — SC-001 requires the whole year, because a rule that is right in March and wrong in
December is the failure mode here.

---

## Scenario 2 — The week closes at Friday Maghrib (US2, SC-002, SC-003)

1. Set the clock to Friday 12:00 with Maghrib at 18:00. Record a completion. Note the week.
2. Advance to Friday 19:00. Record another. Note the week.

**Expect**: step 1 lands in the closing week, step 2 in the new one. The weekly total freezes at
18:00 exactly.

**Note**: `WeekBoundary` is unchanged by this feature (research R1). This scenario is a regression
check that the *day* mapping produced the right week, not a test of new week code.

Then assert over a full year that every instant maps to exactly one day and one week — no instant
unmapped, none mapped twice (SC-003).

---

## Scenario 3 — Fresh install, airplane mode, no location (US3, SC-004, SC-010)

**This is the Principle IV scenario and the one most likely to regress.**

1. Uninstall. Enable airplane mode. Disable location entirely. Install and launch.
2. Open Today. Record a completion. Open the weekly sheet and the streak.
3. Open the profile screen.

**Expect**: the app renders immediately, with **no system permission dialog raised** (SC-017) and no
wait for a location. Everything records normally. The profile screen states that the Islamic day
boundary is unavailable, that the day currently runs local midnight to midnight, and that enabling
location resolves it (FR-016).

Then, on the first-launch prompt, choose "Not now" and confirm nothing is gated and nothing nags.

---

## Scenario 4 — Offline for 90 days with an unchanged zone (SC-005)

1. Obtain a location once. Go offline. Disable location services.
2. Advance the clock 90 days with the zone unchanged.

**Expect**: the Maghrib boundary is still in force for every one of those days, computed from the
retained coordinates with no network call. Age never invalidates coordinates (FR-012a).

---

## Scenario 5 — Travel invalidates coordinates (SC-005a, US3 scenarios 9–10)

1. With coordinates held, change the device time zone and make a fresh fix unobtainable.
2. Read the boundary and open the profile screen.
3. Make a fix available again.

**Expect**: at step 2 the fallback is in force and the app says the device moved to a new time zone
and the previous location is no longer being used (FR-012d) — this must not be silent. At step 3 the
Maghrib boundary resumes from the new coordinates (FR-012c).

Separately confirm a daylight-saving offset change does **not** invalidate anything (FR-012e).

---

## Scenario 6 — Revocation and erasure (US3 scenarios 6–7, SC-014)

1. With coordinates held, revoke location permission in system settings. Return to the app.
2. Open the profile screen and use the erase control.

**Expect**: after step 1 the Maghrib boundary continues from the retained coordinates (FR-017a) and
the app discloses that a last known location is held and in use (FR-017b). After step 2 the fallback
takes over immediately, the disclosure stops, and **every already-closed day and week reports
identical figures** (FR-017d).

---

## Scenario 7 — History is untouched (US4, SC-006) — **gating**

**The increment is not complete without this. Principle III admits no exceptions.**

1. On the previous build, record several days and let a week close under the midnight boundary.
   Capture every day's earned points, available points, percentage and Hijri label, and the weekly
   total.
2. Upgrade to this build. Obtain a location.
3. Re-read every captured figure.

**Expect**: identical, with zero exceptions. This is FR-025's required immutability test and it gates
completion — not a nice-to-have regression check.

---

## Scenario 8 — The seam skips and duplicates nothing (SC-007)

Run the changeover twice, with the cutover placed:

- **before** that day's Maghrib — the day in progress simply ends earlier than midnight;
- **after** that day's Maghrib — the case where naive adoption would skip a date.

**Expect**: in both runs, the sequence of accountability dates has no gap and no repeat. The clamp
makes this structural (research R7), so a failure here means the clamp was bypassed somewhere.

---

## Scenario 9 — Every surface agrees (US5, SC-008)

Set the clock between Maghrib and midnight — the window where the old and new rules disagree, and
therefore where a leftover second opinion shows up.

Open Today, the weekly sheet, the streak, history, insights and the leaderboard in turn.

**Expect**: all report the same accountability date and the same week.

---

## Scenario 10 — At-risk stays inside its day (SC-009)

Across a full year at the highest and lowest latitudes the product supports, assert the at-risk
instant falls strictly inside its own accountability day.

**Expect**: always inside. The old fixed 20:00 rule fails this at both solstices, which is why it was
replaced (research R8).

---

## Scenario 11 — Convention follows region (SC-015, SC-016)

1. Set the zone to `Africa/Cairo`; confirm the Egyptian convention is selected.
2. Set it to `Asia/Riyadh`; confirm Umm al-Qura.
3. Set it to a zone absent from the mapping; confirm the documented default.
4. Change the mapping's version and re-read closed days.

**Expect**: steps 1–3 select automatically with no network call and no setting shown anywhere
(FR-003a, FR-003b). Step 4 leaves every closed day and week identical (FR-003e, SC-016).

---

## Scenario 12 — Coordinates never leave the device (SC-011)

Run a full session including a sync with the network log captured.

**Expect**: no latitude or longitude in any outbound request, any log line, or any synced row
(FR-006).

---

## Scenario 13 — A return after an absence lands on today (FR-023a)

Record a day, then close the app and advance the clock five days with the regime unchanged. Reopen.

**Expect**: the accountability date shown is *today's*, not the date five days ago plus one, and a
completion recorded now is credited to today. Rewind the clock a day and reopen: the date follows it.
The clamp is armed only across a regime change (research R7); if this scenario shows a date days
behind, the clamp is being applied when the regime never changed.

---

## Scenario 14 — Rollover while the app is open (FR-026)

Leave the app open on Today with the clock a minute before the calculated Maghrib. Do not background
it, do not touch it.

**Expect**: at the Maghrib instant the day rolls over on screen — no resume event, no relaunch, no
pull-to-refresh. `expiresAt` then points at the following day's Maghrib.

---

## Definition of done

- Every scenario above passes, with Scenario 7 treated as gating.
- `data/schemas/…/5.json` is exported and committed; `MIGRATION_4_5` is purely additive.
- `MizanDatabaseFactory` registers `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4` **and**
  `MIGRATION_4_5`. Room runs only what `addMigrations` is given, and `MIGRATION_3_4` had never been
  registered — an upgrade from an installed v3 or v4 database must be exercised, not assumed.
- Exactly one file imports Adhan and exactly one reads `android.location` (SC-013).
- `DayBoundary.dateAt` still has exactly one production caller (`grep` it — research R2).
- `:domain` has no Adhan, no `kotlinx-datetime` and no Android on its classpath.
- Test tasks precede their implementation tasks in the PR's commit history (Principle I).
