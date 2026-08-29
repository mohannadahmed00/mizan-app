package com.giraffe.mizanapp.domain.leaderboard

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
fun periodFor(
    kind: PeriodKind,
    date: LocalDate,
    zone: ZoneId,
    regionId: RegionId,
): LeaderboardPeriod = TODO("T017")
