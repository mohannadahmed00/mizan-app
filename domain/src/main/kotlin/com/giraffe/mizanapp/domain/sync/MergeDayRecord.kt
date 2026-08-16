package com.giraffe.mizanapp.domain.sync

/**
 * Settles which catalogue version a date belongs to, taking the **lower** of
 * the two.
 *
 * Commutative, associative and idempotent, so every device reaches the same
 * answer whatever order the changes arrive in and however many times one
 * arrives. No clock is consulted, which is why a wrong or altered device clock
 * cannot win or lose this (spec Assumptions, research R5).
 *
 * `min` rather than newest-wins is the whole safety property: reconciliation can
 * only ever settle a date on the *older* catalogue, so a newer publication can
 * never claim a date already recorded under an older one (FR-024b).
 *
 * **This decides what the account stores and what a device derives when it
 * materialises a date for the first time.** It is never applied to a day that
 * already exists locally — that day is left exactly as recorded (FR-024a).
 */
fun mergeDayRecord(local: DayRecord?, remote: DayRecord?): DayRecord {
    require(local != null || remote != null) { "mergeDayRecord requires at least one non-null side" }
    if (local == null) return remote!!
    if (remote == null) return local
    return if (local.catalogueVersion <= remote.catalogueVersion) local else remote
}
