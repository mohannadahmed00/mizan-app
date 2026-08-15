package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import java.time.LocalDate

/**
 * The pure aggregate over a period's stored/derived plans and their live
 * completions, grouped by section.
 *
 * A section relabelled between two plans in the range resolves to the label
 * carried by the most recent date, since a display label reflects how the
 * catalogue reads *now* — only the points a day was awarded are frozen by
 * Principle III, not a section's display text (`006` contracts/use-cases.md).
 * Output order is `sectionOrder` ascending — never sorted by `rate`
 * (Clarification Q2).
 */
fun buildSectionBreakdown(plans: List<DayPlan>, completions: List<Completion>): List<SectionPerformance> {
    val liveByDateAndTask = completions.filter { it.isLive }.groupBy { it.creditedDate to it.taskSlug }

    data class Accumulator(
        var completed: Int = 0,
        var available: Int = 0,
        var sectionLabel: String = "",
        var sectionOrder: Int = 0,
        var latestDate: LocalDate? = null,
    )

    val bySection = mutableMapOf<String, Accumulator>()

    for (plan in plans) {
        for (task in plan.plannedTasks) {
            val accumulator = bySection.getOrPut(task.sectionId) { Accumulator() }
            val live = liveByDateAndTask[plan.date to task.taskSlug].orEmpty().size
                .coerceAtMost(task.maxOccurrencesPerDay)

            accumulator.completed += live
            accumulator.available += task.maxOccurrencesPerDay

            val latest = accumulator.latestDate
            if (latest == null || !plan.date.isBefore(latest)) {
                accumulator.latestDate = plan.date
                accumulator.sectionLabel = task.sectionLabel
                accumulator.sectionOrder = task.sectionOrder
            }
        }
    }

    return bySection.entries
        .sortedBy { it.value.sectionOrder }
        .map { (sectionId, accumulator) ->
            SectionPerformance(
                sectionId = sectionId,
                sectionLabel = accumulator.sectionLabel,
                sectionOrder = accumulator.sectionOrder,
                completed = accumulator.completed,
                available = accumulator.available,
            )
        }
}
