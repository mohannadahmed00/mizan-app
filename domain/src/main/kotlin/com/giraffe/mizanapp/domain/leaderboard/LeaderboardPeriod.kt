package com.giraffe.mizanapp.domain.leaderboard

import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate
import java.time.ZoneId

/** Carries explicit inclusive boundaries so clients never infer a ranking span. */
data class LeaderboardPeriod(
    val kind: PeriodKind,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val regionId: RegionId,
)

/** Derives boundaries without reading a clock or defining a second week rule. */
@Suppress("UNUSED_PARAMETER")
fun periodFor(
    kind: PeriodKind,
    date: LocalDate,
    zone: ZoneId,
    regionId: RegionId,
): LeaderboardPeriod {
    val (start, endInclusive) = when (kind) {
        PeriodKind.DAILY -> date to date
        PeriodKind.WEEKLY -> WeekBoundary.weekContaining(date).let { week ->
            week.start to week.dates.last()
        }
        PeriodKind.MONTHLY -> date.withDayOfMonth(1) to date.withDayOfMonth(date.lengthOfMonth())
    }
    return LeaderboardPeriod(
        kind = kind,
        start = start,
        endInclusive = endInclusive,
        regionId = regionId,
    )
}
