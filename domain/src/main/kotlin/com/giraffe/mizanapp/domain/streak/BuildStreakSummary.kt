package com.giraffe.mizanapp.domain.streak

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The pure fold over Consistency Days.
 *
 * The task catalogue is deliberately not a parameter — this function cannot
 * consult it even by accident (FR-005). Every rule here is one linear pass
 * over the retained, sorted, distinct dates.
 *
 * [now] and [zone] are parameters rather than a pre-computed at-risk flag:
 * passing a flag would put the 20:00 rule in the caller and give
 * Principle VII two homes.
 */
fun buildStreakSummary(
    consistencyDates: List<LocalDate>,
    today: LocalDate,
    now: Instant,
    zone: ZoneId,
    recordStart: LocalDate?,
): StreakSummary {
    val retained = consistencyDates
        .filter { !it.isAfter(today) && (recordStart == null || !it.isBefore(recordStart)) }
        .distinct()
        .sorted()

    if (retained.isEmpty()) {
        return StreakSummary(
            current = 0,
            longest = 0,
            lastActiveDate = null,
            todayCounted = false,
            recentActivity = buildRecentActivity(emptySet(), today, recordStart),
            showBreakNotice = false,
            isAtRisk = false,
        )
    }

    // Run length ending at each index: 1 if it doesn't continue the previous
    // date, otherwise one more than the run ending at the previous index.
    var longest = 1
    var trailingRun = 1
    for (i in 1 until retained.size) {
        trailingRun = if (retained[i] == retained[i - 1].plusDays(1)) trailingRun + 1 else 1
        if (trailingRun > longest) longest = trailingRun
    }

    val lastActiveDate = retained.last()
    val todayCounted = isConsistencyDay(today, retained.toSet())
    val current = if (todayCounted || lastActiveDate == today.minusDays(1)) trailingRun else 0

    // Derived from the record — nothing is stored to remember it has been
    // shown (FR-021a). A live run never shows it; neither does a record
    // with no run at all.
    val showBreakNotice = current == 0 && longest > 0 && !lastActiveDate.isBefore(today.minusDays(7))

    val isAtRisk = current >= 1 && !todayCounted && StreakClock.isAtRiskWindow(now, zone)

    return StreakSummary(
        current = current,
        longest = longest,
        lastActiveDate = lastActiveDate,
        todayCounted = todayCounted,
        recentActivity = buildRecentActivity(retained.toSet(), today, recordStart),
        showBreakNotice = showBreakNotice,
        isAtRisk = isAtRisk,
    )
}
