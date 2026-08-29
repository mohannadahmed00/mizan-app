package com.giraffe.mizanapp.domain.week

/**
 * How one day in a week reads. Every value is neutral — none of them is a
 * failure state (Principle IX). In particular, [NOTHING_RECORDED] is a fact,
 * not a miss, and it must be visually distinct from [OUTSIDE_RECORD] and
 * [NOT_YET_ELAPSED] — neither of the latter two is a day the user missed
 * either.
 */
enum class DayCellState {
    /** Before the record start — no plan ever will exist for this date. */
    OUTSIDE_RECORD,

    /** After today — nothing is recorded here yet because the day has not happened. */
    NOT_YET_ELAPSED,

    /**
     * In the record, but this device has not fetched it yet (FR-023b). Never
     * conflated with [NOTHING_RECORDED] — a date this device hasn't heard
     * from yet is not the same fact as a date it knows was empty.
     */
    NOT_YET_KNOWN,

    /** Elapsed, in the record, and nothing completed. */
    NOTHING_RECORDED,

    /** Some but not all of the day's available points were earned. */
    PARTLY_RECORDED,

    /** Every applicable task was completed to its limit. */
    FULLY_RECORDED,
}
