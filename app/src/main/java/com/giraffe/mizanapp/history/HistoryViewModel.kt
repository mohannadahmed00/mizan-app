package com.giraffe.mizanapp.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.HistoryOutcome
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.domain.week.WeekSummary
import com.giraffe.mizanapp.week.DayCellUi
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val WEEKS_PER_PAGE = 8

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class (constitution, Technology Constraints).
 *
 * Writes nothing (FR-001a, SC-008): every figure comes from [GetHistoryPage],
 * which never materialises a plan while paging.
 */
class HistoryViewModel(
    private val getHistoryPage: GetHistoryPage,
    private val catalogue: CatalogueRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    /** How many weeks are currently loaded - [refresh] reloads exactly this many. */
    private var loadedWeekCount = 0

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val seeded = catalogue.seedIfNeeded()) {
                is SeedOutcome.Failed -> {
                    _state.value = HistoryUiState(
                        status = HistoryUiState.Status.CatalogueUnavailable(
                            seeded.defects.joinToString { it.toString() },
                        ),
                    )
                    return@launch
                }
                else -> Unit
            }
            loadFirstPage()
        }
    }

    private suspend fun loadFirstPage() {
        _state.value = _state.value.copy(status = HistoryUiState.Status.Loading)
        when (val outcome = getHistoryPage(before = null, weeksPerPage = WEEKS_PER_PAGE)) {
            is HistoryOutcome.Ready -> {
                loadedWeekCount = outcome.page.weeks.size
                if (outcome.page.weeks.isEmpty() && outcome.page.recordStart == null) {
                    _state.value = HistoryUiState(status = HistoryUiState.Status.RecordNotStarted)
                } else {
                    _state.value = HistoryUiState(
                        status = HistoryUiState.Status.Ready,
                        weeks = outcome.page.weeks.map { it.toRowUi() },
                        hasMore = outcome.page.hasMore,
                    )
                }
            }
            is HistoryOutcome.CouldNotLoad -> {
                _state.value = HistoryUiState(status = HistoryUiState.Status.CouldNotLoad(outcome.detail))
            }
            is HistoryOutcome.CatalogueUnavailable -> {
                _state.value = HistoryUiState(status = HistoryUiState.Status.CatalogueUnavailable(outcome.detail))
            }
        }
    }

    fun onEvent(event: HistoryEvent) {
        when (event) {
            HistoryEvent.LoadMore -> loadMore()
            is HistoryEvent.OpenDay -> Unit // handled by the host navigating
            HistoryEvent.Retry -> load()
        }
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoadingMore || current.status !is HistoryUiState.Status.Ready) return
        val cursor = current.weeks.lastOrNull()?.weekKey ?: return

        viewModelScope.launch {
            _state.value = current.copy(isLoadingMore = true)
            when (val outcome = getHistoryPage(before = cursor, weeksPerPage = WEEKS_PER_PAGE)) {
                is HistoryOutcome.Ready -> {
                    loadedWeekCount += outcome.page.weeks.size
                    _state.value = _state.value.copy(
                        status = HistoryUiState.Status.Ready,
                        weeks = _state.value.weeks + outcome.page.weeks.map { it.toRowUi() },
                        hasMore = outcome.page.hasMore,
                        isLoadingMore = false,
                    )
                }
                else -> {
                    // A failure appending a page leaves the already-loaded weeks on
                    // screen - only the loading indicator clears.
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
            }
        }
    }

    /**
     * Reloads the weeks already on screen, keeping the same span. Called on
     * resume so returning from recording today's completion shows the change
     * (FR-015, SC-013).
     */
    fun refresh() {
        val weeksToReload = maxOf(loadedWeekCount, WEEKS_PER_PAGE)
        viewModelScope.launch {
            when (val outcome = getHistoryPage(before = null, weeksPerPage = weeksToReload)) {
                is HistoryOutcome.Ready -> {
                    loadedWeekCount = outcome.page.weeks.size
                    if (outcome.page.weeks.isEmpty() && outcome.page.recordStart == null) {
                        _state.value = HistoryUiState(status = HistoryUiState.Status.RecordNotStarted)
                    } else {
                        _state.value = _state.value.copy(
                            status = HistoryUiState.Status.Ready,
                            weeks = outcome.page.weeks.map { it.toRowUi() },
                            hasMore = outcome.page.hasMore,
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    private fun WeekSummary.toRowUi(): WeekRowUi = WeekRowUi(
        weekKey = week.key,
        startDate = week.start,
        endDate = week.end,
        earnedPoints = score.earned,
        availablePoints = days.sumOf { it.available },
        days = days.map { cell ->
            DayCellUi(
                date = cell.date,
                dayLabel = shortDayLabel(cell.date.dayOfWeek),
                hijriLabel = cell.hijriLabel,
                earnedPoints = cell.earned,
                availablePoints = cell.available,
                state = cell.state,
            )
        },
    )

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
