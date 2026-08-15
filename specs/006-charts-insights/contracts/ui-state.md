# UI Contract: Charts & Insights

## `InsightsUiState`

Same shape family as `HistoryUiState` (`005`) and `WeekUiState` (`003`): one `Status`, plus the data
each of the three views needs once `Ready`. All four `Status` values below are shared across the
three views — there is one loading/failure story for the whole screen, not three, since the section
breakdown and personal bests are loaded alongside whichever chart is on screen.

```kotlin
data class InsightsUiState(
    val status: Status = Status.Loading,
    val selectedView: InsightsView = InsightsView.TREND,
    val trend: List<TrendPointUi> = emptyList(),
    val trendHasMore: Boolean = false, // false once the record-start week has been loaded (US1 AS3)
    val isLoadingEarlierTrend: Boolean = false, // distinct from Status.Loading — appending must not blank the chart
    val month: MonthOverviewUi? = null,
    val sections: List<SectionRowUi> = emptyList(),
    val personalBests: PersonalBestsUi? = null,
) {
    sealed interface Status {
        data object Loading : Status
        data object Ready : Status

        /** No plan exists anywhere - the pre-first-open empty state (Edge Case). */
        data object RecordNotStarted : Status
        data class CouldNotLoad(val detail: String) : Status

        /** Whatever already loaded still renders; only names what cannot be built. */
        data class CatalogueUnavailable(val detail: String) : Status
    }
}

enum class InsightsView { TREND, MONTH, SECTIONS }

data class TrendPointUi(
    val weekKey: WeekKey,
    val startDate: LocalDate,
    val percentage: Int, // 0..100, from WeeklyScore.fraction
    val isInProgress: Boolean, // elapsedAvailable < weekTarget — Q3, FR-008
)

data class MonthOverviewUi(
    val month: YearMonth,
    val days: List<DayCellUi>, // reused from week/WeekUiState.kt — same type WeekScreen renders
    val canGoEarlier: Boolean,
    val canGoLater: Boolean,
)

data class SectionRowUi(
    val sectionLabel: String,
    val percentage: Int, // 0..100
)

data class PersonalBestsUi(
    val bestDay: BestDayUi?,
    val bestWeek: BestWeekUi?,
)

data class BestDayUi(val date: LocalDate, val hijriLabel: String?, val percentage: Int)
data class BestWeekUi(val startDate: LocalDate, val endDate: LocalDate, val percentage: Int)
```

- `TrendPointUi.percentage` and every other `percentage` field is a plain non-negative `Int` —
  `InsightsScreen` never renders a signed delta, a rank, or a comparison figure (FR-010).
- `SectionRowUi` carries no `sectionId` because the UI never needs to look one up — it is rendered in
  the order `GetSectionBreakdown` already returned it in (catalogue order, Clarification Q2). It
  carries no "lowest" flag, no color derived from rank, and the list is not sortable by the user.
- `MonthOverviewUi.days` reuses `DayCellUi` from `week/WeekUiState.kt` rather than a parallel type —
  same reasoning as `MonthOverview` reusing `DayCell` at the domain layer (research R3).

## `InsightsEvent`

```kotlin
sealed interface InsightsEvent {
    data class SelectView(val view: InsightsView) : InsightsEvent
    data object LoadEarlierTrend : InsightsEvent // scrolls the trend further back (US1 AS3, FR-005)
    data object PreviousMonth : InsightsEvent
    data object NextMonth : InsightsEvent
    data class SwitchSectionPeriod(val toMonth: Boolean) : InsightsEvent // week ⇄ month scope
    data object Retry : InsightsEvent
}
```

`LoadEarlierTrend` mirrors `HistoryEvent.LoadMore` (`005`) rather than a `PreviousWeek`/`NextWeek`
pair: the trend is an open-ended, growing list scrolled backward from "now," not a single period
swapped for an adjacent one the way Month is — the same shape History already uses for the same
reason. `InsightsViewModel` handles it by calling `GetWeeklyTrend(before = <oldest loaded WeekKey>)`
and prepending the result, setting `trendHasMore = false` once the record-start week is reached so
the UI can render "nothing earlier" instead of silently doing nothing.

There is deliberately no event here for completing, undoing, adding, removing, reordering, or
repricing anything, and none for exporting, sharing, or generating an image — Principle VI and the
spec's explicit exclusions made structural, exactly as `002`–`005` did for their own event types.

## Navigation

`MainActivity`'s `Destination` sealed interface gains one case:

```kotlin
data object Insights : Destination
```

Encoded as `"INSIGHTS"` in the existing `encode`/`decode` pair (same pattern as `"HISTORY"`). Reached
from one entry point: a button on `WeekScreen`'s header row, next to the existing `OpenHistory`
button (`WeekEvent.OpenInsights`, mirroring `WeekEvent.OpenHistory`). Back returns to `Week`, the
same one-entry-point pattern `005` used for History.

## Shared color mapping

`ui/DayCellColors.kt` (new, `:app`):

```kotlin
@Composable
fun containerColorFor(state: DayCellState): Color
```

Extracted verbatim from `WeekScreen`'s private `containerColorFor`. `WeekScreen` and
`InsightsScreen`'s month grid both call this one function — the SC-006 "zero red anywhere" audit has
exactly one color table to check, not two.
