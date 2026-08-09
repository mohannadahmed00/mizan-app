package com.giraffe.mizanapp.domain.policy

import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate

/**
 * Decides whether a date accepts writes.
 *
 * In this increment only the current date does. Retroactive completion is
 * roadmap Phase 5 work, and **this file is the single place that widens** —
 * every write path consults it, so no screen can hold a different opinion.
 *
 * A policy that is never consulted is decoration; see `CompletionRepository`,
 * which calls it before touching storage.
 */
class DayWritePolicy(private val time: TimeProvider) {

    fun isWritable(date: LocalDate): Boolean = date == time.today()

    fun refusalReason(date: LocalDate): String =
        "only the current date (${time.today()}) accepts writes; $date does not"
}
