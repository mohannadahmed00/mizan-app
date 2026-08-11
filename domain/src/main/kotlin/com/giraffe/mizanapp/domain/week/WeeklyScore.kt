package com.giraffe.mizanapp.domain.week

/**
 * The week's earned points against two different denominators, kept
 * deliberately separate:
 *
 * - [elapsedAvailable] — the sum of available points over the dates that
 *   have elapsed. This is the headline denominator (FR-009a).
 * - [weekTarget] — the sum across all seven dates, so a normal week reads
 *   500. Shown as context, never as the divisor.
 *
 * **[fraction] must never divide by [weekTarget].** That single choice is
 * what stops a Sunday morning reading as 10% of a week.
 */
data class WeeklyScore(
    val earned: Int,
    val elapsedAvailable: Int,
    val weekTarget: Int,
) {
    init {
        require(earned >= 0) { "earned points may never be negative: $earned" }
        require(earned <= elapsedAvailable) {
            "earned ($earned) may never exceed elapsed available ($elapsedAvailable)"
        }
        require(elapsedAvailable <= weekTarget) {
            "elapsed available ($elapsedAvailable) may never exceed the week target ($weekTarget)"
        }
    }

    val fraction: Float get() = if (elapsedAvailable == 0) 0f else earned.toFloat() / elapsedAvailable
}
