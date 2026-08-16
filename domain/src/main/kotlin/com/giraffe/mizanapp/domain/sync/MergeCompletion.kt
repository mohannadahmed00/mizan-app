package com.giraffe.mizanapp.domain.sync

import com.giraffe.mizanapp.domain.day.Completion

/**
 * Reconciles two copies of one recorded occurrence.
 *
 * The only field of a completion that can change after it is written is its undo
 * tombstone — re-recording after an undo creates a *new* occurrence with a new
 * identifier rather than reviving this one. So the later action is always the
 * undo, and "once undone, stays undone" gives exactly the outcome FR-019's
 * last-write-wins describes, for every possible sequence, while consulting no
 * clock at all (research R5).
 *
 * Commutative, associative and idempotent. Every other field is write-once:
 * `pointsAwarded` in particular is the frozen figure from the day the record was
 * made, and no merge, pull, or publication is an input to it (Principle III).
 */
fun mergeCompletion(local: Completion?, remote: Completion?): Completion {
    TODO("T020")
}
