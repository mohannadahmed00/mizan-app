package com.giraffe.mizanapp.domain.sync

import java.time.LocalDate

/**
 * How far back this device knows the account's record.
 *
 * A generalisation of the record-start floor `003` already threads through
 * `buildWeekSummary`: [knownFrom] is where knowledge begins, which signed-out is
 * simply where the record begins. Signed out — or once backfill has finished —
 * this is always [completeFrom], so the offline product's behaviour is
 * unchanged.
 *
 * The distinction it exists to carry is "not fetched yet" versus "nothing
 * recorded". Collapsing those two is exactly what FR-023b forbids: an unfetched
 * date must never render as 0%, as untouched, or as absent.
 */
data class RecordCoverage(
    val knownFrom: LocalDate?,
    val complete: Boolean,
) {

    /** Whether this device can speak for [date] yet. */
    fun isKnown(date: LocalDate): Boolean = complete || knownFrom == null || !date.isBefore(knownFrom)

    companion object {

        /**
         * The whole record is known — the only state that exists signed out, and
         * the state every backfill ends in.
         */
        fun completeFrom(recordStart: LocalDate?) = RecordCoverage(recordStart, complete = true)
    }
}
