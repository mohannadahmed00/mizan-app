# Contract: `:domain` repository interfaces

**Feature**: `specs/008-leaderboard-honor-board`

Three new interfaces, declared in `:domain`, implemented in `:data`. No existing interface is
modified — the leaderboard adds, it does not change (Principle V).

All three are Android-free, Room-free and Supabase-free. `ModuleBoundaryTest` continues to enforce
that `:domain` has none of those on its classpath.

---

## `ParticipationRepository`

```kotlin
interface ParticipationRepository {

    /** Off by default for every account, including pre-existing ones (FR-001). */
    fun observe(): Flow<Participation>

    /**
     * Opting in reports the device zone so the service can assign a region.
     * The caller supplies the zone from TimeProvider — this interface does not
     * read a clock (Principle VII).
     *
     * The client MUST NOT name a region. Region is assigned server-side (FR-014).
     */
    suspend fun optIn(reportedZone: ZoneId): ParticipationResult

    /**
     * Leaves every period still open — ranking and Honor Board — and keeps the
     * participant out of every period that opens afterwards (FR-004, FR-004b).
     *
     * Periods that have already CLOSED are left exactly as they stand, rankings
     * and Honor Board alike (FR-004a). A closed period admits no mutation, so a
     * participant cannot erase past standings — which is why FR-002a requires
     * the opt-in copy to say so before they join.
     *
     * MUST NOT alter, hide or delete any recorded history, points, streak or
     * insight (FR-005, SC-003).
     */
    suspend fun optOut(): ParticipationResult

    /** Re-reports the zone after a device timezone change, keeping FR-012 true (FR-013). */
    suspend fun reportZone(zone: ZoneId): ParticipationResult
}
```

`ParticipationResult` is `Applied` / `Unreachable` / `SessionExpired`. There is no `Failed` with a
blame-carrying message — FR-035 forbids attributing a failure to the person, and the type is the
first place that guarantee can be made.

---

## `LeaderboardRepository`

```kotlin
interface LeaderboardRepository {

    /**
     * Observes the cached ranking for a period. Emits from local storage; a refresh
     * writes into local storage and is never awaited by a ViewModel (Principle IV).
     *
     * Emits with `retrievedAt` set so the caller can render age rather than
     * presenting a cached page as current (FR-036).
     */
    fun observeRanking(kind: PeriodKind): Flow<RankingState>

    /**
     * The viewer's own position and neighbours, independent of which page is
     * loaded — SC-009 requires this without paging through 10 000 rows (research R9).
     */
    fun observeOwnRank(kind: PeriodKind): Flow<OwnRankState>

    /** Extends the loaded page on demand (FR-024). Bounded; never unbounded. */
    suspend fun loadMore(kind: PeriodKind): LoadMoreResult

    /** Requests a refresh. Returns immediately; the result arrives through the Flows. */
    suspend fun refresh(kind: PeriodKind)
}
```

`RankingState` is `Unavailable` / `Cached(ranking)` / `Live(ranking)`. There is no `Empty` distinct
from `Unavailable` that a view could render as "nobody is ahead of you" — the spec's edge case
forbids exactly that framing.

**The interface has no method that computes, sums, sorts or extends a ranking locally.** FR-018
makes the service the only authority, and the absence of such a method is how that is guaranteed
rather than merely intended.

---

## `HonorBoardRepository`

```kotlin
interface HonorBoardRepository {

    /**
     * Qualifying members for a period, and whether the viewer is among them.
     *
     * MUST NOT expose a non-qualifier count, a threshold, a distance to it, or
     * any per-person days figure (FR-030, SC-012). The return type has no field
     * for any of it, so no view can render one by accident (research R8).
     */
    fun observe(kind: PeriodKind): Flow<HonorBoardState>

    suspend fun refresh(kind: PeriodKind)

    // WEEKLY and MONTHLY only (FR-027a). Passing DAILY is a programming error,
    // not a runtime state — the daily period has a ranking and no Honor Board.
}
```

`HonorBoardState` is `Unavailable` / `Cached(board)` / `Live(board)`.

---

## Use cases

Thin, as in every prior increment. Each is a single-responsibility wrapper the ViewModel depends on
rather than depending on a repository directly.

| Use case | Returns | Notes |
|---|---|---|
| `GetParticipationState` | `Flow<Participation>` | Drives whether the leaderboard section renders at all |
| `SetParticipation` | `ParticipationResult` | Opt in / opt out; takes the zone from `TimeProvider` |
| `GetRanking` | `Flow<RankingState>` | |
| `GetOwnRank` | `Flow<OwnRankState>` | |
| `GetHonorBoard` | `Flow<HonorBoardState>` | |

---

## Pure domain functions

Testable without a fake, a database or a clock.

| Function | Signature | Guarantee |
|---|---|---|
| `periodFor` | `(PeriodKind, LocalDate, ZoneId) -> LeaderboardPeriod` | `WEEKLY` delegates to the existing `WeekBoundary`; FR-011 forbids a second week definition |
| `qualifiesForHonorBoard` | `(daysEngaged: Int, threshold: Int) -> Boolean` | Points are **not a parameter** — the function cannot consult them even by accident (FR-027). Defined for `WEEKLY` and `MONTHLY` only (FR-027a) |
| `markViewer` | `(List<RankingEntry>, userId: String) -> List<RankingEntry>` | Sets `isViewer`; sets no `isLast`, no `isBottom` (FR-038) |

The tie-break (FR-022) is implemented in SQL, inside `recompute_open_periods()` — not a `:domain` pure function, which would be dead code (Principle VIII). Tested by `RankingAggregationTest` (T057).

---

## What no interface here offers

Recorded so the absence reads as a decision:

- No method returning a threshold, a shortfall, or a count of non-qualifiers.
- No method returning rank history, trend, or position change (FR-039).
- No method returning another region's ranking (FR-009).
- No local aggregation, sorting or extension of a ranking (FR-018).
- No write path to any leaderboard figure (FR-019).
