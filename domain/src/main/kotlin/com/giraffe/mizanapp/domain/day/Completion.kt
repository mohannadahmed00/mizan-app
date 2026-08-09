package com.giraffe.mizanapp.domain.day

import java.time.Instant
import java.time.LocalDate

/**
 * One recorded occurrence of a task on a date.
 *
 * Append-only: undo sets [reversedAt] rather than deleting, because Principle V
 * requires deletion of a synchronisable record to be a tombstone.
 *
 * [pointsAwarded] is denormalised at write time and never recomputed — a later
 * change to the task's value must not re-score what is already recorded.
 */
data class Completion(
    val id: String,
    val dayPlanId: String,
    val taskSlug: String,
    val creditedDate: LocalDate,
    val pointsAwarded: Int,
    val recordedAt: Instant,
    val reversedAt: Instant? = null,
) {
    /**
     * Whether this record still counts.
     *
     * Reversed records are invisible everywhere the user can see: they consume
     * no occurrence slot, contribute no points, and appear in no list.
     */
    val isLive: Boolean get() = reversedAt == null
}
