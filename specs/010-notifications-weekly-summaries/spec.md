# Feature Specification: Notifications and Weekly Summaries

**Feature Branch**: `spec/010-notifications-weekly-summaries`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "Phase 10 — Notifications & Weekly Summaries. Local scheduled
notifications: prayer-window nudges, streak-at-risk reminders, end-of-week summary at Maghrib-Friday
week close. Per-category user control, quiet hours, reboot survival, never fire for already-completed
tasks, fully disableable. Consumes the Phase 9 location/prayer-time provider — must not introduce a
second. No server push, no social notifications."

## Context

Every screen in the app is something the person has to come and look at. Phase 10 is the first
increment that speaks first — and it is therefore the first increment where Principle IX
(Encouragement, Never Shame) can be violated while nobody is watching, in a system tray, hours after
the app was last opened.

Three things make this increment possible now and not earlier:

- **Phase 9 shipped the provider.** `PrayerTimesProvider` returns the five calculated prayer instants
  for a date and a location; `BoundaryStatus` holds the resolved accountability date, the instant the
  day ends, and which regime is in force. A prayer-window nudge and a week-close summary both need
  those instants, and constitution Principle VII forbids a second source for either. This feature
  reads them and adds nothing.
- **Phase 4 shipped the at-risk rule.** The at-risk window is already measured backward from the day's
  own end. The streak reminder fires at that instant and nowhere else.
- **Phase 6 shipped the aggregates.** The weekly summary reports figures already computed over
  recorded day plans and completions. It computes no new numbers and writes no new history.

Two decisions the roadmap deferred to this phase — notification copy and timing heuristics — are
settled below. A third, which the roadmap did not anticipate, is settled too: what a prayer-window
nudge does when the boundary is running on the fallback regime and no prayer time exists to anchor it
to (FR-016).

This feature writes nothing to recorded day plans or completions, and it may not (FR-044). Deleting
every line of it must leave the record untouched.

## Terminology

One word per idea, because three were in use and they are not the same act.

| Term | Means | Not |
|---|---|---|
| **Post** | Show a notification to the person. The only verb for that act, in this spec and in the code | "announce", "deliver", "send", "fire" |
| **Schedule** | Register an anchor so the device wakes at its instant. Scheduling is not posting — every scheduled anchor is re-evaluated at fire time and may be discarded | — |
| **Anchor** | The instant a notification is scheduled for, plus the date or week it speaks about | "alarm", "trigger" |
| **Withdraw** | Remove a notification that was already posted | "cancel", "dismiss" |
| **Produce / derive** | Compute a weekly summary's figures. A summary is derived on demand and is never stored (FR-024a); posting it is a separate act that may not happen at all | — |

"Fire" survives in one narrow sense only: *fire time*, the moment a scheduled anchor is evaluated. It
never means that anything was shown.

## Clarifications

### Session 2026-09-04

- Q: How often should prayer-window nudges fire? → A: One per prayer window — five per day at most —
  fired a fixed offset after the calculated prayer time, and skipped entirely when that section's
  tasks are already at their occurrence limits. This matches the stepped Today flow, which already
  presents one prayer block at a time, and it means a fully-recorded day produces zero notifications.
- Q: On a fresh install, which categories are on? → A: Weekly Summary on; Prayer Window Nudge and
  Streak At Risk off. A fresh install is quiet: one notification per week, not five per day. Nudges
  are re-engagement, and re-engagement the person never asked for is exactly the pressure Principle IX
  exists to prevent.
- Q: Friday's Maghrib often lands inside a plausible quiet-hours window. What happens to the weekly
  summary then? → A: Deferred, not dropped. Prayer nudges and the streak reminder are time-sensitive —
  a nudge delivered after its window has closed is noise, so quiet hours drop them. The weekly summary
  is not time-sensitive; it is held and delivered when quiet hours end, reporting the same frozen
  figures it would have reported at week close.
- Q: When a week closes, does the app save that week's finished figures as a stored record, or
  recalculate them from the recorded days on demand? → A: Recalculate. A closed week's day plans and
  completions are already immutable, so recalculating gives the same figures forever without a new
  table, a new migration, or a second copy of numbers the weekly sheet also computes. Week close is
  only the instant the notification fires. The one thing that is persisted is the delivery bookkeeping
  FR-045 already requires — which week was announced, and whether a delivery is being held for quiet
  hours — which holds no figures at all.
- Q: Do the prayer nudges need to arrive at an exact minute, or is the right general window good
  enough? → A: Exact, for all three categories. A nudge that drifts out of its own window is not a late
  nudge, it is a wrong one, and the whole feature is anchored to instants the app calculates precisely.
  This costs a third platform permission on top of location and notifications. Where the platform
  withholds exact delivery, the app degrades to relaxed delivery rather than going silent, and the
  staleness rule (FR-012) is what keeps a drifted notification from being posted in the wrong window.
- Q: If someone stops using the app entirely, should the weekly summary keep arriving every Friday
  forever? → A: No. An empty week's summary still goes out, because a person who missed one week is
  exactly who a warm summary helps. After two consecutive empty weeks the category goes dormant on its
  own and stops announcing, resuming automatically at the first week close after anything is recorded
  again. A run of Friday notifications reporting that nothing happened is the shame Principle IX forbids,
  delivered on a schedule.
- Q: What does the Weekly Summary screen show before any week has closed? → A: A short waiting state
  naming when the first summary arrives, and pointing at the weekly sheet for the week in progress. No
  figures. The screen is the closed-week surface; rendering the live week here would put two
  differently-framed views of the same numbers in the same tab, and nothing is final until close.
- Q: When should the app ask for permission to send notifications? → A: At the first week close, when it
  first has a summary to deliver and can say what the notification would be — or earlier if the person
  switches a category on themselves, which is its own ask. Nothing is asked during the first week, so the
  first-run experience keeps only Phase 9's location prompt. The summary is on its screen either way.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The week closes and the person is told how it went (Priority: P1)

At Maghrib on Friday the week freezes. Shortly after, a notification arrives saying what the week held
— days engaged, tasks recorded, points earned. Tapping it opens a summary of that closed week, which
stays available afterwards whether or not the notification was ever seen.

**Why this priority**: It is the only category on by default, it is the one notification per week that
carries information rather than a prompt, and it is the half of this phase that still delivers value
to someone who turns every nudge off. It is also the piece that depends most directly on the
week-close instant Phase 9 defined.

**Independent Test**: With a fixed location and a controllable clock, advance across Friday's Maghrib
and confirm a summary is produced for the week that just closed, with figures matching the weekly
sheet for the same week, and that the summary screen shows the same figures on demand.

**Acceptance Scenarios**:

1. **Given** a week in progress with recorded activity, **When** the clock passes Friday's calculated
   Maghrib, **Then** a weekly summary is produced for the week that just closed and its figures match
   the weekly sheet's figures for that week exactly.
2. **Given** a summary has been produced for a closed week, **When** the task catalogue's points and
   schedules change, **Then** the summary reports the same figures it reported before the change.
3. **Given** the person opens the summary screen, **When** no notification was ever delivered — the
   category was off, or system permission was denied — **Then** the most recently closed week's summary
   is still shown in full.
4. **Given** a week in which nothing at all was recorded, **When** its summary is produced, **Then** it
   renders in encouraging terms, states what is available to do next, and contains no count of what was
   missed.
5. **Given** the device was powered off across Friday's Maghrib, **When** it is next powered on,
   **Then** the summary for the closed week is delivered, reporting the same figures it would have
   reported at week close.

---

### User Story 2 - A nudge arrives in the prayer window, and only when there is something to do (Priority: P1)

The person turns on prayer-window nudges. A short while after each calculated prayer time, if that
prayer's block still has tasks left, a notification arrives naming the block. Tapping it opens Today at
that block. On a day where everything is recorded as it happens, nothing arrives at all.

**Why this priority**: This is the re-engagement mechanic the phase exists for, and it is the category
that most directly consumes the Phase 9 provider. It is P1 alongside the summary rather than above it
because it is off by default — a person who never opens settings never receives one.

**Independent Test**: With a fixed location, a controllable clock and a seeded day plan, advance across
each of the five calculated prayer times and confirm exactly one nudge per window with outstanding
tasks, none for a completed block, and none at all when the category is off.

**Acceptance Scenarios**:

1. **Given** nudges are on, a known location, and the Asr block still incomplete, **When** the clock
   passes the nudge offset after the calculated Asr time, **Then** one notification is posted naming
   the Asr block.
2. **Given** the Asr block's tasks have all reached their occurrence limits, **When** that same instant
   is reached, **Then** no notification is posted.
3. **Given** a nudge for the Dhuhr block is showing, **When** the person records the last outstanding
   task in that block, **Then** the notification is removed without the person dismissing it.
4. **Given** nudges are on and the boundary is running on the fallback regime with no coordinates,
   **When** a full day elapses, **Then** no prayer-window nudge is scheduled or posted at any point,
   and the settings surface states plainly that nudges need location.
5. **Given** a nudge was scheduled and the person then records every task in that block and closes the
   app, **When** the scheduled instant arrives, **Then** nothing is posted.
6. **Given** a nudge's scheduled instant is reached after the following prayer time has already passed
   — the device was asleep, or the app was killed — **When** the scheduler runs, **Then** the stale
   nudge is discarded rather than posted late.

---

### User Story 3 - A protective reminder before the day ends (Priority: P2)

The person has a live streak and has not yet recorded anything today. Four hours before the day ends —
at Maghrib, or at midnight on the fallback regime — a single notification says that one task keeps the
streak going. If they have already recorded something, or have no streak to keep, nothing fires.

**Why this priority**: It is the highest-value single notification in the product, but it depends on a
streak already existing, so it delivers nothing on a fresh install. It is also the category where
Principle IX is easiest to violate: every conventional phrasing of this notification is a threat.

**Independent Test**: With a controllable clock and a seeded completion history, advance to the at-risk
instant under each of: live streak with nothing recorded, live streak with something recorded, and no
streak at all — and confirm exactly one, zero, and zero notifications.

**Acceptance Scenarios**:

1. **Given** a live streak and no completion recorded for today, **When** the at-risk instant is
   reached, **Then** exactly one notification is posted, phrased around keeping the streak rather than
   losing it.
2. **Given** a completion has already been recorded today, **When** the at-risk instant is reached,
   **Then** no notification is posted.
3. **Given** the current streak is zero, **When** the at-risk instant is reached, **Then** no
   notification is posted.
4. **Given** the reminder is showing, **When** the person records any task, **Then** the notification is
   removed.
5. **Given** the boundary is on the fallback regime, **When** the at-risk instant is computed, **Then**
   it is measured backward from that regime's own day end and the reminder still fires.

---

### User Story 4 - Turning it down, and turning it off (Priority: P2)

The person opens settings, switches off the categories they do not want, sets quiet hours across the
night, or switches everything off in one control. From that moment the app never speaks again until
they say otherwise.

**Why this priority**: "Fully disableable" is a stated requirement of the phase and a Principle IX
obligation — an app about worship that cannot be silenced is coercive. It is P2 only because it has
nothing to control until the categories above exist.

**Independent Test**: Enable everything, then switch each control off in turn and advance a full week of
clock time, confirming that nothing is posted for a disabled category and that already-scheduled
notifications are withdrawn at the moment the switch flips, not at their scheduled instant.

**Acceptance Scenarios**:

1. **Given** all three categories are on with notifications already scheduled, **When** the person turns
   everything off, **Then** nothing is posted at any point afterwards and the pending notifications are
   withdrawn immediately.
2. **Given** quiet hours are set across the night, **When** a prayer nudge or streak reminder would fall
   inside them, **Then** nothing is posted and nothing is queued for later.
3. **Given** quiet hours cover Friday's Maghrib, **When** the week closes, **Then** the summary is held
   and delivered at the end of quiet hours with unchanged figures.
4. **Given** system notification permission was never granted, **When** any category is on, **Then**
   nothing is posted, the app behaves normally, and the settings surface states that the system
   permission is off and offers a route to it.
5. **Given** a category is switched from off to on mid-day, **When** the next anchor for that category
   arrives, **Then** it fires — and no notification is backfilled for anchors already passed.

---

### Edge Cases

- **No Maghrib exists for the date** (high latitude, provider returns the unavailable outcome). The
  boundary already falls back; prayer nudges are not scheduled at all, and no prayer instant is
  approximated, extrapolated or borrowed from a neighbouring date. The streak reminder and the weekly
  summary continue on the fallback regime's own day and week ends.
- **The device time zone changes.** The boundary provider re-resolves; every pending notification is
  re-anchored to the newly resolved instants, and nothing fires twice for the same accountability date
  or week.
- **Location is erased while nudges are scheduled.** All pending prayer nudges are withdrawn at that
  moment, not left to fire against instants that no longer describe anything.
- **Location is obtained mid-day.** Nudges begin at the next prayer window; windows already passed are
  not backfilled.
- **Quiet hours cover the entire day.** Nothing time-sensitive is ever posted; the weekly summary is
  deferred to the end of the window, which is the moment it begins again — it is delivered once, at
  that instant, and never suppressed indefinitely.
- **The device is off across the whole week close.** The summary is delivered after boot, for the week
  that closed, with the figures frozen at close.
- **Two prayer instants fall very close together.** Each window is still a window: a nudge whose own
  window has already been overtaken by the next prayer time is discarded rather than posted late, so two
  nudges never arrive together.
- **The day rolls over between scheduling and firing.** The notification is discarded; a notification
  never speaks about a date other than the accountability date in force when it fires.
- **The person installs mid-week.** The first weekly summary covers only the recorded part of the week
  and says so as coverage, never as a shortfall. Until that week closes, the summary screen shows its
  waiting state rather than a partial figure.
- **System permission is revoked while categories are on.** Nothing is posted, nothing crashes, no
  repeat prompting; the settings surface reflects reality.
- **The clock is moved backwards manually.** No category fires a second time for an accountability date
  or week it has already been delivered for.
- **The person stops using the app for months.** Two empty weeks are announced, then the summary goes
  dormant. Nudges are off by default and suppressed on the fallback regime anyway; the streak reminder
  cannot fire with a streak of zero. The app therefore falls silent entirely on its own, without the
  person having to find a setting, and speaks again only after they record something.
- **Activity resumes after dormancy.** The next week close announces normally. Nothing is backfilled for
  the silent weeks, and the summary screen still shows every one of them on demand.

## Requirements *(mandatory)*

### Functional Requirements

#### Categories and control

- **FR-001**: The system MUST provide exactly three notification categories — Prayer Window Nudge,
  Streak At Risk, and Weekly Summary — and no others. No fourth category may be introduced by this
  feature.
- **FR-002**: Users MUST be able to switch each category on or off independently, and MUST be able to
  silence all three with a single control.
- **FR-003**: On a fresh install the Weekly Summary category MUST default to on, and the Prayer Window
  Nudge and Streak At Risk categories MUST default to off.
- **FR-004**: Notification settings MUST live on the existing profile/settings surface. This feature
  MUST NOT add a new navigation destination for settings.
- **FR-005**: Switching a category off, or silencing everything, MUST withdraw every already-scheduled
  and every already-posted notification of the affected categories at that moment. Nothing scheduled
  before the switch may fire after it.
- **FR-006**: The system MUST NOT allow a user to create, edit, rename, retime, or delete a
  notification, or to attach a reminder to an individual task. Control is per category only
  (Principle VI).
- **FR-007**: The system MUST request the platform notification permission only through a dismissible,
  non-blocking prompt, and MUST remain fully usable when it is declined or later revoked. Where the
  permission is absent, the settings surface MUST state so plainly and offer a route to the system
  settings, and MUST NOT re-prompt repeatedly.
- **FR-007a**: The system MUST NOT request the notification permission during the app's first week. The
  first request MUST come either at the first week close, framed as what the summary notification would
  be, or earlier if the person switches a category on themselves.
- **FR-007b**: The exact-delivery permission (FR-036a) MUST NOT be requested before the notification
  permission has been granted, since nothing can be delivered without it. Each is asked once, separately,
  and each is independently refusable.

#### Prayer window nudges

- **FR-008**: The system MUST schedule at most one prayer-window nudge per prayer window, for the five
  prayer sections that have a calculated prayer instant — a maximum of five per accountability day.
- **FR-009**: Each nudge MUST be anchored to a fixed offset after that prayer's calculated instant. The
  offset MUST be an administrator-fixed constant and MUST NOT be exposed as a setting.
- **FR-010**: A nudge MUST NOT be posted when, at the moment it fires, every task in its section has
  reached its occurrence limit for the accountability day.
- **FR-011**: A posted nudge MUST be withdrawn automatically once its section becomes complete, without
  the person dismissing it.
- **FR-012**: A nudge MUST be discarded rather than posted when its window has already been overtaken by
  the following prayer instant, or when the accountability day has rolled over since it was scheduled.
- **FR-013**: Tapping a nudge MUST open Today at the section that nudge names.
- **FR-014**: Sections with no calculated prayer instant — Qiyam/Witr, Quran, Adhkar, Fasting and the
  Friday activities — MUST NOT produce a nudge of any kind.
- **FR-015**: Nudge copy MUST name the section and what remains available in it. It MUST NOT state or
  imply what was missed, MUST NOT count omissions, and MUST NOT use loss, failure or deficit framing
  (Principle IX).
- **FR-016**: While the boundary is running on the fallback regime, the system MUST NOT schedule or post
  any prayer-window nudge, and MUST NOT approximate, extrapolate or otherwise invent a prayer instant.
  The settings surface MUST state that nudges require location. When the Maghrib regime is restored,
  nudges MUST resume from the next window, with no backfill of windows already passed.

#### Streak at risk

- **FR-017**: The system MUST schedule at most one streak reminder per accountability day, anchored to
  the at-risk instant derived from the day's own end by the existing at-risk rule. It MUST NOT introduce
  a second at-risk rule or a second offset.
- **FR-018**: The reminder MUST NOT be posted when a completion has already been recorded for the
  accountability day.
- **FR-019**: The reminder MUST NOT be posted when the current streak is zero.
- **FR-020**: A posted reminder MUST be withdrawn automatically once any completion is recorded for that
  day.
- **FR-021**: Reminder copy MUST be framed around continuing what is already established. It MUST NOT
  use loss, breakage, expiry, countdown or warning framing, and MUST NOT state a consequence of inaction
  (Principle IX).
- **FR-022**: The reminder MUST fire under both boundary regimes, measured backward from whichever day
  end is in force.

#### Weekly summary

- **FR-023**: The system MUST post a summary notification for each week at that week's close instant, as defined
  by the single existing week rule — Friday's calculated Maghrib under the Maghrib regime, and that
  regime's own week end under the fallback.
- **FR-024**: A summary MUST be derived on demand, exclusively from the day plans and completions
  recorded for that week. It MUST NOT read the live task catalogue, and it MUST report identical figures
  forever after the week has closed (Principle III).
- **FR-024a**: The system MUST NOT store a week's figures. No summary record, table or cache of computed
  weekly figures may be introduced by this feature; the recorded plans and completions remain the only
  copy of those numbers, so the summary and the weekly sheet can never disagree. The only thing
  persisted for the summary is the delivery bookkeeping of FR-045, which holds no figures.
- **FR-025**: A summary MUST report: days engaged out of the week's days, tasks recorded, points earned
  against the points that were available, and the streak standing at close. It MUST NOT report a count
  of anything not done, MUST NOT rank the person against anyone, and MUST NOT present any figure as a
  shortfall against a target (Principle IX).
- **FR-026**: The system MUST provide a Weekly Summary screen reachable from the existing weekly sheet,
  showing the most recently closed week and allowing earlier closed weeks within recorded history to be
  viewed. It MUST NOT add a navigation tab: the weekly sheet, insights and this screen together are what
  the product design calls Progress, and that grouping is not a destination in its own right.
- **FR-027**: The Weekly Summary screen MUST be fully available regardless of category settings and
  regardless of whether the platform notification permission was ever granted.
- **FR-027a**: The Weekly Summary screen MUST show only closed weeks. It MUST NOT render figures for the
  week in progress; that is the weekly sheet's job, and no second view of a live week's numbers may be
  introduced.
- **FR-027b**: Before any week has closed, the screen MUST show a waiting state stating when the first
  summary arrives and offering a route to the weekly sheet for the week in progress. It MUST show no
  figures and MUST NOT frame the absence as anything the person failed to do.
- **FR-028**: A summary for a week with no recorded activity MUST still render, in encouraging terms, and
  MUST offer a route back into the current day.
- **FR-029**: A summary covering a week only partly within recorded history MUST disclose the covered
  span as coverage, never as a deficit.
- **FR-030**: Tapping a weekly summary notification MUST open the Weekly Summary screen at the week that
  notification describes.
- **FR-030a**: After two consecutive closed weeks with no recorded activity, the system MUST stop posting
  the weekly summary notification. The two empty weeks themselves are still posted; the third and every
  subsequent consecutive empty week MUST NOT be.
- **FR-030b**: Posting MUST resume automatically at the first week close after any completion is
  recorded, with no action required from the person and nothing backfilled for the weeks that were
  skipped.
- **FR-030c**: Going dormant MUST NOT alter the person's category setting. The Weekly Summary category
  remains on, the settings surface MUST state that the notification is paused and what resumes it, and
  the Weekly Summary screen remains fully available throughout (FR-027).

#### Quiet hours

- **FR-031**: Users MUST be able to define one quiet-hours window, applying to all categories, and it
  MUST default to off.
- **FR-032**: The quiet-hours window MUST be expressible across midnight, and MUST be interpreted in the
  device's local time.
- **FR-033**: A prayer-window nudge or streak reminder whose instant falls inside quiet hours MUST be
  dropped. It MUST NOT be queued, deferred or delivered later.
- **FR-034**: A weekly summary whose instant falls inside quiet hours MUST be held and posted at the
  end of the quiet-hours window, reporting the figures frozen at week close, unchanged by the delay.
- **FR-035**: A held weekly summary MUST be posted exactly once. It MUST NOT be dropped by a quiet
  window that covers the whole day, and MUST NOT be delivered twice if quiet hours are edited while it
  is held.

#### Scheduling, determinism and reliability

- **FR-036**: All scheduling MUST be local to the device. The system MUST NOT introduce server push, and
  MUST NOT place a network call on any path that schedules, evaluates, posts or withdraws a notification
  (Principle IV).
- **FR-036a**: All three categories MUST be scheduled for delivery at their exact anchor instant. Where
  the platform requires a separate permission for exact delivery, the system MUST request it, MUST state
  plainly what it is for, and MUST NOT block any part of the app on it.
- **FR-036b**: Where exact delivery is unavailable — the permission was refused, revoked, or is not
  offered — the system MUST degrade to relaxed delivery rather than going silent, MUST disclose on the
  settings surface that timing may drift, and MUST rely on the staleness rules (FR-012, FR-040) to
  discard anything that arrives outside the window it was scheduled for. A notification MUST NOT be
  posted in a window other than its own under any delivery mode.
- **FR-037**: Scheduled notifications MUST survive device reboot, app update and process death, and MUST
  be re-established without the person opening the app first.
- **FR-038**: Schedules MUST be re-derived whenever the resolved boundary changes — a new accountability
  date, a regime change, a time-zone change, coordinates obtained, or coordinates erased.
- **FR-039**: No code introduced by this feature may read the system clock, read device location, or
  compute a prayer time other than through the existing injected providers. This feature MUST NOT
  introduce a second time, location or prayer-time provider (Principle VII).
- **FR-040**: A notification MUST NOT be posted for an accountability date or week other than the one in
  force at the moment it fires.
- **FR-041**: Each category MUST be posted at most once per anchor — once per prayer window, once per
  accountability day, once per closed week — even if the device is rebooted, the clock is moved
  backwards, or the schedule is re-derived repeatedly.
- **FR-042**: The system MUST post no more than seven notifications for any single accountability day —
  five prayer windows, one streak reminder, one weekly summary — and MUST post none at all for a day
  where every applicable task is recorded before its window and no week closes. A summary held across
  quiet hours (FR-034) counts against the day its anchor belonged to, not the day it is finally posted,
  so a person may see eight notifications arrive within one calendar day without this limit being
  exceeded.

#### Data

- **FR-043**: Notification preferences and quiet hours MUST be stored device-locally and MUST NOT be
  synchronised. Devices legitimately differ in when they may speak.
- **FR-044**: No part of this feature may write to, modify, or tombstone a day plan or a completion.
  Recorded history MUST be identical before and after any amount of notification activity.
- **FR-045**: The system MUST record which category has been posted for which anchor, device-locally,
  solely to satisfy FR-041. This record MUST NOT be treated as historical accountability data and MUST be
  reconstructible or discardable without affecting any figure the app reports.

### Key Entities

- **Notification Category**: One of the three fixed kinds — Prayer Window Nudge, Streak At Risk, Weekly
  Summary. Administrator-defined, not user-authored; carries its own default enablement and its own
  quiet-hours behaviour.
- **Notification Preferences**: The person's device-local choices — per-category enablement, the master
  silence control, and the quiet-hours window. Never synchronised.
- **Quiet Hours Window**: A start and end time in device-local time, possibly crossing midnight, possibly
  absent.
- **Notification Anchor**: The instant a notification is scheduled for, together with the accountability
  date or week key it belongs to. A prayer anchor derives from a calculated prayer instant; the streak
  anchor derives from the day's end; the summary anchor derives from the week's close.
- **Delivery Record**: Device-local bookkeeping of which category was delivered for which anchor, so a
  category never speaks twice about the same window, day or week.
- **Weekly Summary**: The figures for a closed week — days engaged, tasks recorded, points earned, points
  available, streak at close. Computed on demand from the recorded plans and completions, never from the
  live catalogue, and never stored (FR-024a). It is frozen by the immutability of what it reads, not by
  being written down.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a fresh install in airplane mode with no account and no location, the app is fully
  usable, no prayer-window nudge is ever scheduled, and neither the notification permission nor the
  exact-delivery permission is requested during the first week. (Phase 9's location prompt is unaffected
  and still appears at first launch — this feature adds no prompt to first run.) Once notification
  permission is granted, the person receives exactly one notification per week — the summary — and
  nothing else.
- **SC-002**: On an accountability day where every applicable task is recorded before its own prayer
  window and no week closes, the person receives zero notifications.
- **SC-003**: With the master silence control on, a full simulated week of clock advance produces zero
  notifications of any category.
- **SC-004**: After a device reboot, the next anchor for each enabled category still fires within its
  window without the app having been opened since the reboot.
- **SC-005**: No time-sensitive notification is ever posted inside a configured quiet-hours window; a
  weekly summary that falls inside one is delivered exactly once, at the window's end.
- **SC-006**: A weekly summary's figures equal the weekly sheet's figures for the same closed week, and
  still equal them after the task catalogue's points and schedules have changed.
- **SC-007**: With coordinates present and exact delivery available, each of the five nudges lands within
  one minute of its scheduled instant, and always after its own calculated prayer instant and before the
  following one, on every day of a simulated year including both solstices.
- **SC-007a**: With exact delivery refused, no notification is ever posted in a window other than the one
  it was scheduled for, across a simulated month of delayed deliveries — the count of notifications falls,
  and the count of wrongly-placed notifications is zero.
- **SC-008**: With no coordinates, zero prayer-window nudges are scheduled across a simulated month, and
  the settings surface states why.
- **SC-009**: Every user-visible string introduced by this feature — notification titles, bodies, settings
  labels, and every line of the summary screen — passes the Principle IX review: no loss, failure,
  deficit, warning or comparative framing, and no count of anything not done.
- **SC-010**: No accountability day produces more than seven notifications — counting a held summary
  against the day it was anchored to — and the count falls to zero as the day's tasks are recorded.
- **SC-011**: The streak reminder fires on no day where a completion was already recorded, and on no day
  where the current streak is zero.
- **SC-012**: Recorded day plans and completions are unchanged, row for row and value for value, after a
  simulated week of full notification activity.
- **SC-013**: Across reboot, backward clock movement, time-zone change and repeated schedule re-derivation,
  no category is delivered twice for the same anchor.
- **SC-014**: A person who records nothing for three months, with notification permission granted and
  every category left at its default, receives exactly two notifications in total — the two empty weeks —
  and none thereafter. Recording a single task makes the following week close post again.
- **SC-015**: The Weekly Summary screen shows a waiting state and no figures at every point before the
  first week closes, and shows the closed week in full immediately after.

## Assumptions

- **Nudge offset**: a fixed twenty minutes after the calculated prayer instant, chosen so the notification
  lands after the obligatory prayer rather than during it. Administrator-fixed, per FR-009; the exact
  value is a planning-time constant, not a setting, and may be tuned without changing this spec.
- **At-risk offset**: unchanged from Phase 4 — four hours before the day's own end, read from the existing
  rule rather than restated here.
- **Summary home**: the Weekly Summary screen is reached from the existing weekly sheet, beside the
  link to insights. Those three surfaces together are what the product design calls Progress, but that
  grouping is not a destination in the code and this feature does not make it one. Navigation gains no
  tab.
- **Platform channels**: one system notification channel per category, so the person can also tune them
  from system settings. Category enablement in the app is authoritative for whether anything is scheduled
  at all; the system channel governs only how a posted notification presents.
- **Permission timing**: the notification permission and the exact-delivery permission are both requested
  through the same non-blocking, dismissible pattern Phase 9 used for location, and never on a path that
  blocks recording a task. This feature brings the app's permission count to three; each is independently
  refusable, each has a stated behaviour when refused (FR-007, FR-036b), and none is asked in the first
  week (FR-007a), so first-run still shows only Phase 9's location prompt.
- **Weekly summary while unpermitted**: the Weekly Summary category being on by default governs only
  whether the announcement is posted. The summary itself is always available on its screen, derived on
  demand, whether or not a notification was ever permitted or delivered.
- **Copy language**: notification and summary copy is interface text in the English shell, not catalogue
  content. Arabic task and section content appearing inside a notification is rendered with the same
  bidirectional discipline the rest of the app uses.
- **Existing providers**: the prayer-time provider, the boundary status, the time provider, the at-risk
  rule and the weekly aggregate are consumed as they stand. If any of them turns out to need a change,
  that is a finding for the plan, not an assumption of this spec.

## Out of Scope

- Server-sent or push notifications of any kind, and any new network surface.
- Social, friend, challenge or leaderboard notifications, including rank changes and Honor Board entries.
- Per-task reminders, custom reminder times, snooze, or any user-authored notification.
- A notification inbox or history surface inside the app.
- A prayer-times display screen, a second prayer-time provider, or any per-person choice of calculation
  convention.
- Widgets, wearable surfaces, and full-screen or alarm-style presentation.
- Any change to how a day, a week, a score or a streak is computed.

## Dependencies

- **Phase 9** — the location and prayer-time provider, the resolved accountability date, the day's end
  instant, and the week-close instant. This feature consumes all four and adds none.
- **Phase 4** — the streak summary and the at-risk rule.
- **Phase 6** — the weekly and per-section aggregates the summary reports.
