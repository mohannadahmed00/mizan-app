package com.giraffe.mizanapp.daysummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.usecase.DayDetailOutcome
import com.giraffe.mizanapp.domain.usecase.GetDayDetail
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class (constitution, Technology Constraints).
 *
 * There is no write method here of any kind — this screen cannot record,
 * undo, add, remove, or reorder anything (FR-024, Principle VI). It is only
 * ever opened for an **elapsed** date — the current date routes to the
 * recording surface instead (FR-015a).
 */
class DaySummaryViewModel(
    private val getDayDetail: GetDayDetail,
    private val date: LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(DaySummaryUiState())
    val state: StateFlow<DaySummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = when (val outcome = getDayDetail(date)) {
                is DayDetailOutcome.NoRecord -> DaySummaryUiState(status = DaySummaryUiState.Status.NoRecord, civilDate = date)
                is DayDetailOutcome.CatalogueUnavailable -> DaySummaryUiState(
                    status = DaySummaryUiState.Status.CatalogueUnavailable(outcome.detail),
                    civilDate = date,
                )
                is DayDetailOutcome.Ready -> {
                    val summary = outcome.summary
                    DaySummaryUiState(
                        status = DaySummaryUiState.Status.Ready,
                        civilDate = summary.date,
                        hijriLabel = summary.hijriLabel,
                        sections = summary.tasks
                            .groupBy { it.task.sectionId }
                            .toList()
                            .sortedBy { (_, records) -> records.first().task.sectionOrder }
                            .map { (sectionId, records) ->
                                SummarySectionUi(
                                    id = sectionId,
                                    label = records.first().task.sectionLabel,
                                    tasks = records.map { record ->
                                        SummaryTaskUi(
                                            slug = record.task.taskSlug,
                                            label = record.task.label,
                                            points = record.task.points,
                                            recordedCount = record.recordedCount,
                                            maxOccurrences = record.task.maxOccurrencesPerDay,
                                        )
                                    },
                                )
                            },
                        earnedPoints = summary.score.earned,
                        availablePoints = summary.score.available,
                    )
                }
            }
        }
    }
}
