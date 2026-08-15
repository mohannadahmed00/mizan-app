package com.giraffe.mizanapp.domain.streak

import java.time.LocalDate

/**
 * Everything the streak element needs, folded together in one pass.
 *
 * Derived on every read. There is no repository, DAO or entity for this type,
 * and none may be added (FR-012) — deleting every line of this feature must
 * leave the record untouched.
 */
data class StreakSummary(
    val current: Int,
    val longest: Int,
    val lastActiveDate: LocalDate?,
    val todayCounted: Boolean,
    val recentActivity: List<ActivityDay>,
    val showBreakNotice: Boolean,
    val isAtRisk: Boolean,
) {
    init {
        require(current >= 0) { "current may never be negative: $current" }
        require(longest >= current) { "longest ($longest) may never be less than current ($current)" }
        require(!todayCounted || current >= 1) { "today counted implies a live current streak" }
        require(recentActivity.size == 7) { "the recent activity window is always exactly seven days" }
        require(!showBreakNotice || current == 0) { "a break notice implies no live current streak" }
        require(!isAtRisk || !todayCounted) { "at risk implies today is not yet counted" }
        require(!isAtRisk || current >= 1) { "at risk implies a live current streak" }
    }
}
