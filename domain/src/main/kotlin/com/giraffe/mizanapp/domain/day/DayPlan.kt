package com.giraffe.mizanapp.domain.day

import java.time.LocalDate

/**
 * The frozen record of one date: the tasks that applied, what each was worth,
 * and the points that were available.
 *
 * **Written once, never changed** (Principle III, FR-007). There is no update
 * path anywhere — not in a repository, not in a DAO, not here. A recorded day
 * must report the same figures forever, and this is the one category of bug
 * that cannot be repaired after the fact.
 */
data class DayPlan(
    val id: String,
    val date: LocalDate,
    val catalogueVersion: Int,
    val hijriLabel: String,
    val availablePoints: Int,
    val plannedTasks: List<PlannedTask>,
    val origin: PlanOrigin,
) {
    /** Sections in catalogue order, each with its tasks in display order. */
    fun sectionsInOrder(): List<Pair<String, List<PlannedTask>>> =
        plannedTasks
            .groupBy { it.sectionId }
            .toList()
            .sortedBy { (_, tasks) -> tasks.first().sectionOrder }
            .map { (sectionId, tasks) -> sectionId to tasks.sortedBy { it.displayPosition } }
}
