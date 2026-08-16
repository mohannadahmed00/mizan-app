package com.giraffe.mizanapp.domain.sync

import java.time.LocalDate

/**
 * The syncable projection of a date: which catalogue version it belongs to, and
 * nothing else.
 *
 * It deliberately carries no tasks and no available-points total. Both are a
 * pure function of `(catalogue, version, date)` via `buildDayPlan`, so every
 * device derives them identically and transmitting them would send ~15 rows a
 * day to reproduce something both ends can compute (FR-024, research R4).
 *
 * A device consults the settled version for a date **only when it has no local
 * plan for that date**. A day already materialised here is never re-derived,
 * re-versioned, or rewritten by anything (FR-024a).
 */
data class DayRecord(
    val date: LocalDate,
    val catalogueVersion: Int,
)
