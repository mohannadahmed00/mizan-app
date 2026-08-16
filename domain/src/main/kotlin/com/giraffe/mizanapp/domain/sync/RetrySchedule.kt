package com.giraffe.mizanapp.domain.sync

import java.time.Duration
import java.time.Instant

/**
 * When a queued change should next be attempted.
 *
 * **There is deliberately no expiry, no attempt ceiling, and no `shouldDrop`.**
 * A pending change is the only copy of a fact the user believes is recorded, so
 * the type is not permitted to express discarding one (FR-021a). Retries back
 * off and then continue at the ceiling indefinitely — a device offline for a
 * year loses nothing (SC-010).
 */
object RetrySchedule {

    /** First retry, and the growth base. */
    val INITIAL: Duration = Duration.ofSeconds(30)

    /** The ceiling. Reached in about ten attempts and held from then on. */
    val CEILING: Duration = Duration.ofHours(6)

    fun nextAttemptAt(attempt: Int, from: Instant): Instant {
        TODO("T022")
    }
}
