# Feature Specification: Charts & Insights

**Feature Branch**: `006-charts-insights`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "Read @docs/PLAN.md and create a specification for the Phase 6 — Charts & Insights"

## Clarifications

### Session 2026-08-15

- Q: How should each day cell in the monthly overview encode completion percentage visually? → A: Discrete bands — four fixed states (no-data / untouched / partial / complete) mapped to distinct shades of the primary green, matching the day-cell states already used on the Week Screen (`no-data`: outline only, `untouched`: background `#EFECE5`, `partial`: `#14805C` at 40% opacity, `complete`: `#0B5D42` solid).
- Q: In the section breakdown, should a lowest-consistency section be called out at all? → A: No — every section is listed with its own rate in catalogue order, with no "lowest" label, badge, or emphasis of any kind. The user draws their own conclusions from the plain numbers.
- Q: How many past weeks should the weekly trend view show by default? → A: 8 weeks (or fewer if history is shorter), with older weeks reachable via previous/next navigation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Weekly consistency trend (Priority: P1)

A user who has been recording practices for several weeks opens Insights and sees how their weekly completion percentage has moved over recent weeks, so they can tell at a glance whether they are trending up, down, or holding steady.

**Why this priority**: This is the single chart that answers the product's core question — "am I becoming more consistent?" — and it depends only on data already trustworthy after Phase 5. Every other insight is secondary to this one.

**Independent Test**: Seed a history spanning several completed weeks with varying completion levels, open Insights, and verify the trend chart shows one data point per week matching the hand-computed weekly percentage for each.

**Acceptance Scenarios**:

1. **Given** a user with four fully recorded past weeks of mixed completion, **When** they open the weekly trend view, **Then** they see four points/bars, each showing that week's earned/available percentage matching the Week Screen's own totals for the same week.
2. **Given** a user with only two days of history (fresh install), **When** they open the weekly trend view, **Then** the current partial week is shown distinctly from a completed week rather than being mistaken for a low-consistency week.
3. **Given** a user navigating the trend further back than any recorded history, **When** they reach the boundary, **Then** the view stops at the install week and communicates there is nothing earlier, without an error state.

---

### User Story 2 - Monthly overview (Priority: P2)

A user opens a calendar-style monthly overview and sees, for every day of the month, a visual indication of how complete that day was, so they can spot patterns (e.g., weekends, specific weekdays) at a glance.

**Why this priority**: This is the natural companion to the weekly trend at a coarser time scale, and it is the view most suited to spotting recurring patterns (e.g., "Fridays are always strong, mid-week dips"). It depends on the same aggregation as User Story 1.

**Independent Test**: Seed a full calendar month with a known mix of complete, partial, and untouched days, open the monthly overview for that month, and verify every day's visual state matches its hand-computed completion percentage, including days before the app was installed.

**Acceptance Scenarios**:

1. **Given** a month with a mix of fully completed, partially completed, and untouched days, **When** the user opens that month, **Then** each day cell reflects its own completion percentage and no day cell shows a different value than its Day Plan/completion totals.
2. **Given** a month that spans before the user's install date, **When** the user opens that month, **Then** days before install are shown as "no data" rather than as 0% completion.
3. **Given** the user navigates across a year boundary (e.g., December to January), **When** the new month loads, **Then** the correct year's data is shown with no day miscounted into the wrong month.

---

### User Story 3 - Section breakdown and personal bests (Priority: P3)

A user opens a section breakdown and sees, for a chosen period, each section's (Fajr, Dhuhr, Adhkar, Quran, etc.) own completion rate listed plainly, plus their personal-best day and week, so they can draw their own conclusions about where to focus — framed as encouragement, never as a failure report.

**Why this priority**: This is the most actionable insight but depends on the aggregation groundwork from User Stories 1–2, and it is the view most exposed to Principle IX (no shame) risk, so it is sequenced after the safer, purely time-based charts are proven.

**Independent Test**: Seed a period with deliberately uneven completion across sections (e.g., Adhkar always completed, Qiyam rarely), open the section breakdown for that period, and verify each section's completion rate matches the hand-computed rate, and that the personal-best day/week matches the highest recorded percentage in the seeded range.

**Acceptance Scenarios**:

1. **Given** a period where one section is completed every occurrence and another is completed rarely, **When** the user opens the section breakdown, **Then** every section appears in the same plain list, in catalogue order, each showing its own completion rate as a positive/neutral measure (e.g., "Adhkar: 95% consistent") with no red, negative, or comparative-shaming language or color, and with no section singled out, sorted by rate, badged, or highlighted as lowest.
2. **Given** a period with at least one day, **When** the user views personal bests, **Then** the highest-percentage day and highest-percentage week within the user's recorded history are shown, framed as an achievement to note, not as a baseline others fell short of.
3. **Given** a period with uniform, unremarkable completion (no standout day), **When** the user views personal bests, **Then** the view still renders sensibly (e.g., shows the single available data point) without implying failure.

---

### Edge Cases

- User has exactly one day of recorded history: weekly trend, monthly overview, and section breakdown must all render meaningfully with a single data point rather than erroring or showing an empty chart.
- User has zero recorded history (never opened Today): Insights shows an explicit empty state, not a zeroed-out chart.
- A month or week under review straddles the boundary between two task-catalogue versions (an admin content update occurred mid-period): each day's contribution to the aggregate must use that day's own immutable Day Plan totals, not the current catalogue, per Principle III.
- A week or month is only partially elapsed (the current, in-progress period): it must be visually distinguishable from a completed period so an unfinished week is never read as a low-consistency week.
- A full year of history is loaded: all charts must remain responsive (see Success Criteria).
- Devices with no data prior to install must never show pre-install days as 0%; they must be visibly absent from the record.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a weekly trend of completion percentage across a range of past weeks, computed from the same Weekly Score used by the Week Screen (Phase 3), so the two never disagree.
- **FR-002**: System MUST display a monthly overview in which every day of a selected month shows its completion state as one of four discrete bands — no-data, untouched (0%), partial (>0% and <100%), complete (100%) — each mapped to a distinct shade of the primary green (never red), derived from that day's own persisted Day Plan and completions.
- **FR-003**: System MUST display a per-section breakdown for a selected period (week or month) showing every section's completion rate (occurrences completed vs. occurrences available) across that period, listed in catalogue order with no section singled out, ranked, badged, or emphasized as lowest.
- **FR-004**: System MUST identify and display the user's personal-best day and personal-best week (highest earned/available percentage) within their recorded history.
- **FR-005**: System MUST allow the user to switch the aggregation period (at minimum: weekly trend and monthly overview) and to move between periods (previous/next), bounded by the earliest and latest recorded data. The weekly trend defaults to the most recent 8 weeks (or fewer if history is shorter) on open.
- **FR-006**: System MUST compute every chart value from persisted, immutable historical records (Day Plans and the completion log) and MUST NOT recompute a past period's figures from the live task catalogue, consistent with Principle III.
- **FR-007**: System MUST distinguish days/weeks/months with no recorded data (before install, or never opened) from days/weeks/months with recorded-but-zero completion; the former MUST render as "no data," never as 0%.
- **FR-008**: System MUST distinguish an in-progress (not-yet-elapsed) period from a completed period in every chart that could otherwise misrepresent it as low-consistency.
- **FR-009**: System MUST NOT introduce any write path from Insights — no chart, filter, or drill-in may create, edit, or delete a completion, Day Plan, or task definition.
- **FR-010**: System MUST render all completion-rate and comparison visuals using neutral or positive framing only — no red or failure-coded color, no "missed" language, no ranking of the user against anyone else, consistent with Principle IX.
- **FR-011**: System MUST remain fully available offline, computing all aggregations from the local database with no network dependency on the chart-rendering path.

### Key Entities

- **Aggregation Period**: A bounded span of recorded days (a week, a month, or a custom trend range) over which completion figures are summarized. Always derived from already-persisted Day Plans and completions, never a new source of truth.
- **Section Performance**: A section's (e.g., Fajr, Adhkar, Quran) completed-vs.-available occurrence count across an Aggregation Period, expressed as a completion rate.
- **Trend**: An ordered sequence of per-period completion percentages (e.g., one value per week) used to show direction of change over time.
- **Completion Rate**: Earned points (or completed occurrences) divided by available points (or occurrences) for a given scope — a day, a section, or a period — matching the same earned/available model used elsewhere in the app.
- **Personal Best**: The single day and single week, within the user's recorded history, with the highest completion percentage.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every chart value matches its hand-computed fixture value exactly, for seeded data covering sparse (single-day), typical (multi-week), and dense (full-year) histories.
- **SC-002**: A user can open Insights and see their weekly trend, monthly overview, and section breakdown, each within 1 second of navigating to it, even with a full year of recorded history.
- **SC-003**: Charts correctly represent a period spanning a task-catalogue content change, with no visible discrepancy between a day's chart contribution and that day's own recorded totals.
- **SC-004**: Opening, viewing, or navigating Insights adds no observable delay to completing or undoing a task on the Today screen.
- **SC-005**: A user with only one day of history can open every Insights view without encountering an error state or a blank/broken chart.
- **SC-006**: Zero occurrences of red, "missed," or comparative-shaming visual treatment appear anywhere in Insights, verified against the same design audit checklist used for other screens.

## Assumptions

- Insights is reachable from the Progress area of the app's three-tab navigation, consistent with the product design's existing tab structure; it does not introduce a fourth tab.
- The weekly trend view defaults to the last 8 weeks rather than the user's entire history at once (see Clarifications), with older weeks reachable by navigating backward.
- "Best/worst day" from the roadmap is deliberately reframed as "personal best" only (no "worst" surface) to satisfy Principle IX; a low-completion day is not called out or ranked, only the high point is celebrated. The section breakdown likewise never singles out a lowest-consistency section (see Clarifications).
- Charts consume existing Weekly Score, Day Plan, and completion data with no new writes on the completion path; a pre-computed rollup table is not assumed unless a later measurement shows the live aggregate query is too slow (consistent with the roadmap's stated preference).
- Gregorian dates are the primary axis for all period navigation, with the Hijri label available per day as already snapshotted on each Day Plan, consistent with existing history/week screens.
- This phase is read-only: no export, sharing, image generation, predictions, or AI-generated summaries are in scope, per the roadmap's explicit exclusions.
- The section breakdown (User Story 3) always reflects the *current* week or month only — it has no previous/next navigation to a past period. FR-005's period-navigation requirement is satisfied by the weekly trend (scrolls backward to the record start) and the monthly overview (steps month to month); repeating the same historical navigation a third time for sections would duplicate what those two views already show, for a chart whose value is comparing sections *right now*, not tracking their trend over time — that comparison is the trend chart's job.
