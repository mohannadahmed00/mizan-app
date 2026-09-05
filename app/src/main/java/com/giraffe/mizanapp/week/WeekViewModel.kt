package com.giraffe.mizanapp.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import com.giraffe.mizanapp.domain.usecase.WeekOutcome
import com.giraffe.mizanapp.domain.week.Week
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class (constitution, Technology Constraints).
 *
 * Reads no clock of its own — the date comes from [TimeProvider]
 * (Principle VII). The week being viewed is never persisted (FR-019): it is
 * a private field, reinitialised to the current week on every construction.
 */
class WeekViewModel(
    private val getWeekSummary: GetWeekSummary,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
    private val dayPlans: DayPlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekUiState())
    val state: StateFlow<WeekUiState> = _state.asStateFlow()

    private var viewedWeek: Week = WeekBoundary.weekContaining(time.today())

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // The week screen must not depend on the Today screen having run
            // first (T040): it seeds the catalogue itself, exactly as
            // TodayViewModel does.
            when (val seeded = catalogue.seedIfNeeded()) {
                is SeedOutcome.Failed -> {
                    _state.value = WeekUiState(
                        status = WeekUiState.Status.CatalogueUnavailable(
                            seeded.defects.joinToString { it.toString() },
                        ),
                    )
                    return@launch
                }
                else -> Unit
            }
            loadWeek(viewedWeek)
        }
    }

    private suspend fun loadWeek(week: Week) {
        _state.value = _state.value.copy(status = WeekUiState.Status.Loading)

        when (val outcome = getWeekSummary(week)) {
            is WeekOutcome.Ready -> {
                viewedWeek = week
                val recordStart = dayPlans.earliestPlanDate()
                val earliestWeek = recordStart?.let { WeekBoundary.weekContaining(it) }
                val currentWeek = WeekBoundary.weekContaining(time.today())
                _state.value = WeekUiState(
                    status = WeekUiState.Status.Ready,
                    weekKey = outcome.summary.week.key,
                    startDate = outcome.summary.week.start,
                    days = outcome.summary.days.map { cell ->
                        DayCellUi(
                            date = cell.date,
                            dayLabel = shortDayLabel(cell.date.dayOfWeek),
                            hijriLabel = cell.hijriLabel,
                            earnedPoints = cell.earned,
                            availablePoints = cell.available,
                            state = cell.state,
                        )
                    },
                    earnedPoints = outcome.summary.score.earned,
                    elapsedAvailablePoints = outcome.summary.score.elapsedAvailable,
                    weekTargetPoints = outcome.summary.score.weekTarget,
                    canGoPrevious = earliestWeek != null && week.start.isAfter(earliestWeek.start),
                    canGoNext = week.start.isBefore(currentWeek.start),
                )
            }
            is WeekOutcome.NoCatalogue -> {
                _state.value = WeekUiState(
                    status = WeekUiState.Status.CatalogueUnavailable("no catalogue applies to this week"),
                )
            }
            is WeekOutcome.BackfillFailed -> {
                _state.value = WeekUiState(
                    status = WeekUiState.Status.CouldNotLoad("this week could not be loaded"),
                )
            }
        }
    }

    fun onEvent(event: WeekEvent) {
        when (event) {
            WeekEvent.PreviousWeek -> if (_state.value.canGoPrevious) {
                viewModelScope.launch { loadWeek(WeekBoundary.weekContaining(viewedWeek.start.minusDays(1))) }
            }
            WeekEvent.NextWeek -> if (_state.value.canGoNext) {
                viewModelScope.launch { loadWeek(WeekBoundary.weekContaining(viewedWeek.end.plusDays(1))) }
            }
            is WeekEvent.OpenDay -> Unit // handled by the host navigating
            WeekEvent.OpenHistory -> Unit // handled by the host navigating
            WeekEvent.OpenInsights -> Unit // handled by the host navigating
            WeekEvent.OpenWeeklySummary -> Unit // handled by the host navigating
            WeekEvent.Retry -> viewModelScope.launch { loadWeek(viewedWeek) }
        }
    }

    /**
     * Crossing local midnight into a new week moves the sheet forward
     * (FR-020) — but only when the viewed week is the one the user was
     * actually living in. Navigating to a past week and leaving the app
     * open must not snap the user back to the present.
     */
    fun refreshForCurrentDate() {
        val currentWeek = WeekBoundary.weekContaining(time.today())
        if (viewedWeek.key != currentWeek.key && viewedWeek.end.isBefore(time.today())) {
            viewModelScope.launch { loadWeek(currentWeek) }
        }
    }

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
