package com.giraffe.mizanapp.domain.day

/**
 * One task as it stood on one date.
 *
 * Carries everything needed to render and score that day without consulting the
 * live catalogue — including the label, because a day able to show its numbers
 * but not its text would still depend on current content to be readable.
 */
data class PlannedTask(
    val id: String,
    val dayPlanId: String,
    val taskSlug: String,
    val sectionId: String,
    val sectionLabel: String,
    val sectionOrder: Int,
    val displayPosition: Int,
    val label: String,
    val points: Int,
    val maxOccurrencesPerDay: Int,
) {
    /** What this task contributes to the day's available total. */
    val availablePoints: Int get() = points * maxOccurrencesPerDay
}
