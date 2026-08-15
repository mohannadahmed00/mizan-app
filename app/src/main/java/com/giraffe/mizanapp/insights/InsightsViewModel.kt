package com.giraffe.mizanapp.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.insights.InsightsPeriod
import com.giraffe.mizanapp.domain.insights.PersonalBests
import com.giraffe.mizanapp.domain.insights.SectionPerformance
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import com.giraffe.mizanapp.domain.usecase.MonthOverviewOutcome
import com.giraffe.mizanapp.domain.usecase.PersonalBestsOutcome
import com.giraffe.mizanapp.domain.usecase.SectionBreakdownOutcome
import com.giraffe.mizanapp.domain.usecase.TrendOutcome
import com.giraffe.mizanapp.domain.week.DayCell
import com.giraffe.mizanapp.domain.week.WeekSummary
import com.giraffe.mizanapp.week.DayCellUi
import java.time.DayOfWeek
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TREND_WEEKS = 8

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class (constitution, Technology Constraints). Read-only end to end —
 * writes nothing anywhere (FR-009).
 */
class InsightsViewModel(
    private val getWeeklyTrend: GetWeeklyTrend,
    private val getMonthOverview: GetMonthOverview,
    private val getSectionBreakdown: GetSectionBreakdown,
    private val getPersonalBests: GetPersonalBests,
    private val time: TimeProvider,
    private val dayPlans: DayPlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    private var viewedMonth: YearMonth = YearMonth.from(time.today())
    private var sectionsScopedToMonth: Boolean = false

    init {
        load()
    }

    private fun load() {
        _state.value = _state.value.copy(status = InsightsUiState.Status.Loading)
        viewModelScope.launch {
            when (val outcome = getWeeklyTrend(before = null, weeks = TREND_WEEKS)) {
                is TrendOutcome.Ready -> {
                    _state.value = _state.value.copy(
                        status = InsightsUiState.Status.Ready,
                        trend = outcome.weeks.map { it.toTrendPointUi() },
                        trendHasMore = outcome.hasMore,
                    )
                }
                TrendOutcome.NoHistory -> {
                    _state.value = InsightsUiState(status = InsightsUiState.Status.RecordNotStarted)
                }
                is TrendOutcome.CouldNotLoad -> {
                    _state.value = _state.value.copy(status = InsightsUiState.Status.CouldNotLoad(outcome.detail))
                }
                is TrendOutcome.CatalogueUnavailable -> {
                    _state.value = _state.value.copy(status = InsightsUiState.Status.CatalogueUnavailable(outcome.detail))
                }
            }
        }
        loadPersonalBestsOnce()
    }

    private fun loadPersonalBestsOnce() {
        viewModelScope.launch {
            when (val outcome = getPersonalBests()) {
                is PersonalBestsOutcome.Ready -> {
                    _state.value = _state.value.copy(personalBests = outcome.bests.toUi())
                }
                else -> Unit // no personal bests to show; status is already carrying the failure story
            }
        }
    }

    fun onEvent(event: InsightsEvent) {
        when (event) {
            is InsightsEvent.SelectView -> selectView(event.view)
            InsightsEvent.LoadEarlierTrend -> loadEarlierTrend()
            InsightsEvent.PreviousMonth -> navigateMonth(-1)
            InsightsEvent.NextMonth -> navigateMonth(1)
            is InsightsEvent.SwitchSectionPeriod -> switchSectionPeriod(event.toMonth)
            InsightsEvent.Retry -> load()
        }
    }

    private fun selectView(view: InsightsView) {
        _state.value = _state.value.copy(selectedView = view)
        if (view == InsightsView.MONTH && _state.value.month == null) {
            loadMonth(viewedMonth)
        }
        if (view == InsightsView.SECTIONS && _state.value.sections.isEmpty()) {
            loadSections()
        }
    }

    private fun loadEarlierTrend() {
        val current = _state.value
        if (!current.trendHasMore || current.isLoadingEarlierTrend || current.status !is InsightsUiState.Status.Ready) return
        val cursor = current.trend.firstOrNull()?.weekKey ?: return

        viewModelScope.launch {
            _state.value = current.copy(isLoadingEarlierTrend = true)
            when (val outcome = getWeeklyTrend(before = cursor, weeks = TREND_WEEKS)) {
                is TrendOutcome.Ready -> {
                    _state.value = _state.value.copy(
                        trend = outcome.weeks.map { it.toTrendPointUi() } + _state.value.trend,
                        trendHasMore = outcome.hasMore,
                        isLoadingEarlierTrend = false,
                    )
                }
                else -> {
                    // A failure appending older weeks leaves the already-loaded
                    // trend on screen - only the loading indicator clears.
                    _state.value = _state.value.copy(isLoadingEarlierTrend = false)
                }
            }
        }
    }

    private fun navigateMonth(delta: Long) {
        viewModelScope.launch {
            val candidate = viewedMonth.plusMonths(delta)
            val bounds = monthBounds()
            if (delta < 0 && !bounds.canGoEarlier(candidate)) return@launch
            if (delta > 0 && !bounds.canGoLater(candidate)) return@launch
            loadMonthSuspending(candidate)
        }
    }

    private fun loadMonth(month: YearMonth) {
        viewModelScope.launch { loadMonthSuspending(month) }
    }

    private suspend fun loadMonthSuspending(month: YearMonth) {
        when (val outcome = getMonthOverview(month)) {
            is MonthOverviewOutcome.Ready -> {
                viewedMonth = month
                val bounds = monthBounds()
                _state.value = _state.value.copy(
                    month = MonthOverviewUi(
                        month = month,
                        days = outcome.overview.days.map { it.toDayCellUi() },
                        canGoEarlier = bounds.canGoEarlier(month.minusMonths(1)),
                        canGoLater = bounds.canGoLater(month.plusMonths(1)),
                    ),
                )
            }
            is MonthOverviewOutcome.CatalogueUnavailable -> {
                _state.value = _state.value.copy(status = InsightsUiState.Status.CatalogueUnavailable(outcome.detail))
            }
        }
    }

    private fun switchSectionPeriod(toMonth: Boolean) {
        sectionsScopedToMonth = toMonth
        loadSections()
    }

    private fun loadSections() {
        viewModelScope.launch {
            val period = if (sectionsScopedToMonth) {
                InsightsPeriod.ForMonth(YearMonth.from(time.today()))
            } else {
                InsightsPeriod.ForWeek(WeekBoundary.weekContaining(time.today()))
            }
            when (val outcome = getSectionBreakdown(period)) {
                is SectionBreakdownOutcome.Ready -> {
                    _state.value = _state.value.copy(sections = outcome.sections.map { it.toSectionRowUi() })
                }
                is SectionBreakdownOutcome.CatalogueUnavailable -> {
                    _state.value = _state.value.copy(status = InsightsUiState.Status.CatalogueUnavailable(outcome.detail))
                }
            }
        }
    }

    private class MonthBounds(private val earliestMonth: YearMonth?, private val currentMonth: YearMonth) {
        fun canGoEarlier(target: YearMonth): Boolean = earliestMonth != null && !target.isBefore(earliestMonth)
        fun canGoLater(target: YearMonth): Boolean = !target.isAfter(currentMonth)
    }

    private suspend fun monthBounds(): MonthBounds {
        val recordStart = dayPlans.earliestPlanDate()
        return MonthBounds(
            earliestMonth = recordStart?.let { YearMonth.from(it) },
            currentMonth = YearMonth.from(time.today()),
        )
    }

    private fun WeekSummary.toTrendPointUi(): TrendPointUi = TrendPointUi(
        weekKey = week.key,
        startDate = week.start,
        percentage = (score.fraction * 100).toInt(),
        isInProgress = score.elapsedAvailable < score.weekTarget,
    )

    private fun DayCell.toDayCellUi(): DayCellUi = DayCellUi(
        date = date,
        dayLabel = shortDayLabel(date.dayOfWeek),
        hijriLabel = hijriLabel,
        earnedPoints = earned,
        availablePoints = available,
        state = state,
    )

    private fun SectionPerformance.toSectionRowUi(): SectionRowUi = SectionRowUi(
        sectionLabel = sectionLabel,
        percentage = (rate * 100).toInt(),
    )

    private fun PersonalBests.toUi(): PersonalBestsUi = PersonalBestsUi(
        bestDay = bestDay?.let {
            BestDayUi(date = it.date, hijriLabel = it.hijriLabel, percentage = (it.fraction() * 100).toInt())
        },
        bestWeek = bestWeek?.let {
            BestWeekUi(startDate = it.week.start, endDate = it.week.end, percentage = (it.fraction * 100).toInt())
        },
    )

    private fun DayCell.fraction(): Float = if (available == 0) 0f else earned.toFloat() / available

    private fun shortDayLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
    }
}
