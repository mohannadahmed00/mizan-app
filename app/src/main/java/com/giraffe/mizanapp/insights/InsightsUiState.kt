package com.giraffe.mizanapp.insights

import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.week.DayCellUi
import java.time.LocalDate
import java.time.YearMonth

/**
 * One immutable state for the Insights screen, exposed as
 * [kotlinx.coroutines.flow.StateFlow]. Same shape family as `HistoryUiState`
 * (`005`) and `WeekUiState` (`003`) — one [Status], plus the data each of the
 * three views needs once [Status.Ready].
 */
data class InsightsUiState(
    val status: Status = Status.Loading,
    val selectedView: InsightsView = InsightsView.TREND,
    val trend: List<TrendPointUi> = emptyList(),
    val trendHasMore: Boolean = false,
    val isLoadingEarlierTrend: Boolean = false,
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
