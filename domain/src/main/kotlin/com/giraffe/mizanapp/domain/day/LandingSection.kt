package com.giraffe.mizanapp.domain.day

/** A section and whether every task in it has reached its occurrence limit. */
data class SectionProgress(
    val sectionId: String,
    val isComplete: Boolean,
)

/**
 * Which section the stepped flow opens on: the earliest one still containing an
 * incomplete task, or the first when the whole day is done (FR-020b).
 *
 * **Derived on every open, never stored.** Nothing to persist means nothing to
 * migrate, nothing to reconcile after a rollover, and nothing that can strand
 * the user mid-list. It also removes repeated navigation from the action the
 * user performs most often.
 */
fun landingSectionIndex(sections: List<SectionProgress>): Int {
    if (sections.isEmpty()) return 0
    val firstIncomplete = sections.indexOfFirst { !it.isComplete }
    return if (firstIncomplete == -1) 0 else firstIncomplete
}

/**
 * Builds the progress view a landing decision needs.
 *
 * A task below its occurrence limit leaves its section incomplete — three of
 * nine is not done.
 */
fun sectionProgress(plan: DayPlan, completions: List<Completion>): List<SectionProgress> =
    plan.sectionsInOrder().map { (sectionId, tasks) ->
        SectionProgress(
            sectionId = sectionId,
            isComplete = tasks.all { liveCount(completions, it.taskSlug) >= it.maxOccurrencesPerDay },
        )
    }
