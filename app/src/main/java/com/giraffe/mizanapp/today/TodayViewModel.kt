package com.giraffe.mizanapp.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.SectionProgress
import com.giraffe.mizanapp.domain.day.landingSectionIndex
import com.giraffe.mizanapp.domain.day.liveCount
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class (constitution, Technology Constraints).
 *
 * Reads no clock of its own — the date comes from [TimeProvider]
 * (Principle VII).
 */
class TodayViewModel(
    private val catalogue: CatalogueRepository,
    private val dayPlans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val time: TimeProvider,
    private val getStreakSummary: GetStreakSummary,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var observing: Job? = null
    private var streakJob: Job? = null
    private var loadedDate: LocalDate? = null

    init {
        load()
        observeStreak()
    }

    fun load() {
        viewModelScope.launch {
            when (val seeded = catalogue.seedIfNeeded()) {
                is SeedOutcome.Failed -> {
                    _state.value = TodayUiState(
                        status = TodayUiState.Status.CatalogueUnavailable(
                            seeded.defects.joinToString { it.toString() },
                        ),
                        streak = _state.value.streak,
                    )
                    return@launch
                }
                else -> Unit
            }
            openDate(time.today())
        }
    }

    /**
     * Subscribes independently of the day's task collector, so the tasks
     * paint without waiting on the streak (FR-018c). A failed read is caught
     * here — never in `GetStreakSummary` — because a zero produced by a
     * failure would be indistinguishable from a real zero (FR-021b).
     *
     * Launched on [Dispatchers.Default] rather than the ViewModel's default
     * Main dispatcher: `GetStreakSummary` re-schedules itself indefinitely to
     * track the 20:00 and midnight boundaries (research.md R2), and that
     * scheduling logic already has its own virtual-time coverage in
     * `GetStreakSummaryTest`. Collecting it on the same dispatcher a test
     * advances would make `advanceUntilIdle()` try to drain a queue that is
     * unbounded by construction. Updating [_state] from a background thread
     * is safe — `MutableStateFlow.value` is thread-safe, and Compose's
     * `collectAsStateWithLifecycle` marshals back to Main on its own.
     */
    private fun observeStreak() {
        streakJob?.cancel()
        streakJob = viewModelScope.launch(Dispatchers.Default) {
            getStreakSummary()
                .catch { e ->
                    _state.value = _state.value.copy(
                        streak = StreakPanelUi.Unavailable(e.message ?: "could not read the record"),
                    )
                }
                .collect { summary -> _state.value = _state.value.copy(streak = summary.toUi()) }
        }
    }

    private fun StreakSummary.toUi() = StreakPanelUi.Ready(
        current = current,
        longest = longest,
        todayCounted = todayCounted,
        recentActivity = recentActivity.map { ActivityDayUi(it.date, it.state) },
        showBreakNotice = showBreakNotice,
        isAtRisk = isAtRisk,
    )

    /**
     * Called when the app resumes or the date may have changed. Crossing local
     * midnight moves the screen to the new date (FR-023).
     */
    fun refreshForCurrentDate() {
        val current = time.today()
        if (current != loadedDate) {
            viewModelScope.launch { openDate(current) }
        }
    }

    private suspend fun openDate(date: LocalDate) {
        when (dayPlans.ensurePlanFor(date)) {
            is EnsureOutcome.NoCatalogue -> {
                _state.value = TodayUiState(
                    status = TodayUiState.Status.CatalogueUnavailable("no catalogue applies on $date"),
                    streak = _state.value.streak,
                )
                return
            }
            else -> Unit
        }

        loadedDate = date
        observing?.cancel()
        observing = viewModelScope.launch {
            combine(
                dayPlans.observePlan(date),
                completions.observeCompletions(date),
            ) { plan, records -> plan to records }
                .collect { (plan, records) -> if (plan != null) emit(plan, records) }
        }
    }

    private fun emit(plan: DayPlan, records: List<Completion>) {
        val score = scoreDay(plan, records)

        val sections = plan.sectionsInOrder().map { (sectionId, tasks) ->
            SectionUi(
                id = sectionId,
                label = tasks.first().sectionLabel,
                tasks = tasks.map { task ->
                    TaskRowUi(
                        slug = task.taskSlug,
                        label = task.label,
                        points = task.points,
                        recordedCount = liveCount(records, task.taskSlug),
                        maxOccurrences = task.maxOccurrencesPerDay,
                    )
                },
            )
        }

        // Derived on every emission, never stored (FR-020b).
        val landing = landingSectionIndex(
            sections.map { SectionProgress(it.id, it.isComplete) },
        )
        val keepPosition = _state.value.status is TodayUiState.Status.Ready &&
            _state.value.civilDate == plan.date

        _state.value = TodayUiState(
            status = TodayUiState.Status.Ready,
            civilDate = plan.date,
            hijriLabel = plan.hijriLabel,
            sections = sections,
            currentSectionIndex = if (keepPosition) {
                _state.value.currentSectionIndex.coerceAtMost(sections.lastIndex.coerceAtLeast(0))
            } else {
                landing
            },
            earnedPoints = score.earned,
            availablePoints = score.available,
            streak = _state.value.streak,
        )
    }

    fun onEvent(event: TodayEvent) {
        when (event) {
            is TodayEvent.CompleteTask -> viewModelScope.launch {
                loadedDate?.let { completions.record(it, event.slug) }
            }
            is TodayEvent.UndoTask -> viewModelScope.launch {
                loadedDate?.let { completions.undoLast(it, event.slug) }
            }
            TodayEvent.RetryStreak -> {
                _state.value = _state.value.copy(streak = StreakPanelUi.Resolving)
                observeStreak()
            }
            TodayEvent.NextSection -> move(+1)
            TodayEvent.PreviousSection -> move(-1)
        }
    }

    /** Clamps at both ends and surfaces no error (US3 scenario 3). */
    private fun move(delta: Int) {
        val current = _state.value
        val target = current.currentSectionIndex + delta
        if (target in current.sections.indices) {
            _state.value = current.copy(currentSectionIndex = target)
        }
    }
}
