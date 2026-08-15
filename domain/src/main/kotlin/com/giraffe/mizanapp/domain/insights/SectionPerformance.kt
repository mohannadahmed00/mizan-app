package com.giraffe.mizanapp.domain.insights

/**
 * A section's own completion rate across an Aggregation Period — occurrences
 * completed against occurrences available (FR-003's literal wording, not
 * points). Never ranked against another section; the list this appears in is
 * always in catalogue order (Clarification Q2).
 */
data class SectionPerformance(
    val sectionId: String,
    val sectionLabel: String,
    val sectionOrder: Int,
    val completed: Int,
    val available: Int,
) {
    val rate: Float get() = if (available == 0) 0f else completed.toFloat() / available
}
