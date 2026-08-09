package com.giraffe.mizanapp.domain.day

/**
 * Earned against available for one date.
 *
 * Derived, never stored. Both halves matter: earned points alone cannot be
 * read, because what was achievable that day depends on which tasks applied.
 */
data class DailyScore(
    val earned: Int,
    val available: Int,
) {
    init {
        require(earned >= 0) { "earned points may never be negative: $earned" }
        require(earned <= available) { "earned ($earned) may never exceed available ($available)" }
    }

    val fraction: Float get() = if (available == 0) 0f else earned.toFloat() / available
}

/**
 * Sums the points carried by the day's live records.
 *
 * Points come from the completions themselves, not from the plan and not from
 * the catalogue — each record carries what it was awarded (FR-011).
 */
fun scoreDay(plan: DayPlan, completions: List<Completion>): DailyScore =
    DailyScore(
        earned = completions.filter { it.isLive }.sumOf { it.pointsAwarded },
        available = plan.availablePoints,
    )
