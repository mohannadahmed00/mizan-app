# Contract: UI state and placement

**Feature**: `specs/008-leaderboard-honor-board`

One immutable state per surface, exposed as `StateFlow`, as every screen since `002`.

---

## Placement — read this before writing any composable

The leaderboard is a **section hosted inside Progress**. It is not a navigation destination, and
`Destination` gains no entry.

`CLAUDE.md` records the design's own rationale: a permanent leaderboard tab "puts comparison at the
same weight as worship." FR-032 makes it a requirement. Navigation stays three tabs.

A person who has not opted in sees, at most, one invitation inside Progress. A person who is not
signed in sees nothing at all — no invitation, no placeholder, no disabled row (FR-033, SC-001).

---

## `LeaderboardUiState`

```kotlin
data class LeaderboardUiState(
    val visibility: Visibility,
    val selectedPeriod: PeriodKind,
    val ranking: RankingState,
    val ownRank: OwnRankState,
    val honorBoard: HonorBoardState,
    val regionLabel: String?,
    val isRefreshing: Boolean,
)

sealed interface Visibility {
    data object Hidden : Visibility          // signed out — FR-033
    data object Invitation : Visibility      // signed in, not opted in — FR-001
    data object Participating : Visibility
}
```

`Hidden` is a distinct state rather than a null ranking, so "render nothing" is explicit rather than
an accident of empty data.

### Entry rows

```kotlin
data class RankingRowUiModel(
    val displayName: String,
    val points: String,
    val position: String,
    val isViewer: Boolean,
)
```

`isViewer` exists so the viewer can find their own row. **There is no `isLast`, no `isBottom`, no
`isTop`, and no `emphasis`.** FR-038 requires a last-place row to render identically to every other
row, and the way to guarantee that is for the view model to have no field it could key styling off
(research R8).

---

## The opt-in panel

Must state, before any choice is made:

1. What becomes visible to others — display name and period points total (FR-002).
2. That visibility is limited to the participant's region (FR-002).
3. **That entries for periods which have already finished remain visible after leaving** (FR-002a).

Point 3 is not optional copy. Opting out reaches only open periods, so a participant who is not told
this has not given informed consent — it is the one commitment they cannot take back (research R7).

Suggested copy, to be audited against the Principle IX list before shipping (SC-013):

> **Join the leaderboard**
> Other people in your region will see your name and your points for the current period.
> You can leave at any time. Leaving takes you out of the period running now and any that
> follow — periods that have already finished stay as they are.

No urgency, no social proof ("847 people in your region are competing"), no streak-loss framing, no
count of who is ahead. Joining is a choice, not a nudge.

---

## The leave control

Same place as the opt-in (FR-003). No friction that discourages leaving — one confirmation stating
what happens, and no retention plea ("Are you sure? You'll lose your position!"), which would be
both a dark pattern and a Principle IX violation.

Confirmation copy states the same asymmetry as the opt-in: leaving is immediate going forward and
changes nothing backwards.

---

## The Honor Board panel

Renders qualifying members only. No ordering that reads as ranking, no points, no days count, no
position.

For a viewer who did not qualify: the board renders normally and says nothing about them. No "you
were 3 days short", no "you didn't qualify this week", no progress bar toward the threshold, no
count of who missed out (FR-030, SC-012). The read model does not carry the data for any of it.

---

## Colour and copy rules

Audited before the increment is done (SC-013), against the `CLAUDE.md` Principle IX list.

- No red anywhere on either surface. No amber-as-warning.
- Every ranking row uses the same container, the same background and the same text colour. Position
  is a number, not a colour.
- The viewer's own row may be marked — a subtle container change is fine — but the marking must not
  vary with how high or low the position is.
- Unavailable states describe the system, never the person: "Standings aren't available right now",
  not "We couldn't load your rank" and never "You're offline".
- Cached states state their age: "As of 09:40", not a silent stale render (FR-036).
- Provisional states say the standing is catching up, not that it is wrong (FR-037).
- No string anywhere may contain a comparative that frames another participant as ahead of the
  viewer — "you're 40 points behind", "3 places to climb", "overtake". The read model carries no gap
  figure, so these cannot be written without inventing data.

---

## Degradation

The leaderboard surface failing must leave Today, Week, Streak, History and Insights untouched
(FR-034, SC-008). It is a section within Progress, so a failure renders an unavailable panel and the
rest of Progress continues to work — there is no navigation path anyone needs that passes through
it.
