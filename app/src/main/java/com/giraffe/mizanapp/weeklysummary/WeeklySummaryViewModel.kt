package com.giraffe.mizanapp.weeklysummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.streak.buildStreakSummary
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.ClosedWeekOutcome
import com.giraffe.mizanapp.domain.usecase.GetClosedWeekSummary
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.Week
import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Weekly Summary screen's one immutable state, always for an already-closed week
 * (FR-027a) — [GetClosedWeekSummary] never backfills, so opening this screen writes
 * no history.
 *
 * "Closed" here is approximated as any week strictly before the calendar week
 * containing today; the notification path's own close instant (Friday's Maghrib,
 * via `weekCloseInstant`) is the precise rule and is not duplicated here — this is
 * a read surface, not a scheduling decision.
 */
class WeeklySummaryViewModel(
    private val closedWeekSummary: GetClosedWeekSummary,
    private val dayPlans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val time: TimeProvider,
    initialWeek: WeekKey? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklySummaryUiState(WeeklySummaryContent.Waiting(time.today()), false, false))
    val state: StateFlow<WeeklySummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load(initialWeek?.let(::weekOf)) }
    }

    fun goEarlier() = viewModelScope.launch {
        val shown = shownWeek ?: return@launch
        load(WeekBoundary.weekContaining(shown.start.minusDays(7)))
    }

    fun goLater() = viewModelScope.launch {
        val shown = shownWeek ?: return@launch
        load(WeekBoundary.weekContaining(shown.start.plusDays(7)))
    }

    private var shownWeek: Week? = null

    private fun weekOf(key: WeekKey): Week = WeekBoundary.weekContaining(LocalDate.parse(key.value))

    private suspend fun load(explicit: Week?) {
        val recordStart = dayPlans.earliestPlanDate()
        val currentWeek = WeekBoundary.weekContaining(time.today())
        val recordStartWeek = recordStart?.let { WeekBoundary.weekContaining(it) }

        if (recordStartWeek == null || !recordStartWeek.start.isBefore(currentWeek.start)) {
            shownWeek = null
            _state.value = WeeklySummaryUiState(WeeklySummaryContent.Waiting(currentWeek.end), false, false)
            return
        }

        val mostRecentClosed = WeekBoundary.weekContaining(currentWeek.start.minusDays(7))
        val week = explicit ?: mostRecentClosed
        shownWeek = week

        val canGoEarlier = week.start.isAfter(recordStartWeek.start)
        val canGoLater = week.start.isBefore(mostRecentClosed.start)

        _state.value = try {
            when (val outcome = closedWeekSummary(week)) {
                is ClosedWeekOutcome.Ready -> {
                    val summary = outcome.summary
                    val daysEngaged = summary.days.count { it.earned > 0 }
                    val tasksRecorded = completions.liveBetween(week.start, week.end).size
                    val coveredFrom = summary.days.firstOrNull { it.state == DayCellState.OUTSIDE_RECORD }?.let { recordStart }
                        ?: summary.days.lastOrNull { it.state == DayCellState.OUTSIDE_RECORD }?.let { recordStart }
                    val streak = buildStreakSummary(
                        consistencyDates = completions.observeConsistencyDates().first(),
                        today = week.end,
                        now = week.end.plusDays(1).atStartOfDay(time.zone()).toInstant(),
                        dayEndsAt = week.end.plusDays(1).atStartOfDay(time.zone()).toInstant(),
                        recordStart = recordStart,
                    )
                    WeeklySummaryUiState(
                        content = WeeklySummaryContent.Closed(
                            weekKey = week.key,
                            range = "${week.start} - ${week.end}",
                            daysEngaged = daysEngaged,
                            daysInWeek = summary.days.size,
                            tasksRecorded = tasksRecorded,
                            pointsEarned = summary.score.earned,
                            pointsAvailable = summary.score.elapsedAvailable,
                            streakAtClose = streak.current,
                            coverage = coveredFrom?.let(::CoverageNote),
                            quiet = daysEngaged == 0,
                        ),
                        canGoEarlier = canGoEarlier,
                        canGoLater = canGoLater,
                    )
                }
                is ClosedWeekOutcome.NoCatalogue -> WeeklySummaryUiState(
                    WeeklySummaryContent.Unavailable("The task list could not be loaded."),
                    canGoEarlier,
                    canGoLater,
                )
            }
        } catch (e: Exception) {
            WeeklySummaryUiState(WeeklySummaryContent.Unavailable("This week couldn't load right now."), canGoEarlier, canGoLater)
        }
    }
}
