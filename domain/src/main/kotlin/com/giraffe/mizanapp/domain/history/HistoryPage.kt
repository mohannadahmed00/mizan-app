package com.giraffe.mizanapp.domain.history

import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.domain.week.WeekSummary
import java.time.LocalDate

/**
 * A loaded stretch of the record. Held in memory, never stored.
 *
 * [weeks] is continuous and descending - for every adjacent pair, the older
 * week starts exactly seven days before the newer one (FR-001a). No week
 * within the span is ever absent.
 */
data class HistoryPage(
    val weeks: List<WeekSummary>,
    val oldestLoaded: WeekKey?,
    val hasMore: Boolean,
    val recordStart: LocalDate?,
)
