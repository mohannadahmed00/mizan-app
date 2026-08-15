package com.giraffe.mizanapp.insights

/**
 * Everything the user can do on this screen. There is deliberately no event
 * for completing, undoing, adding, removing, reordering or repricing
 * anything, and none for exporting, sharing, or generating an image —
 * Principle VI and the spec's explicit exclusions made structural, exactly as
 * `002`–`005` did for their own event types.
 */
sealed interface InsightsEvent {
    data class SelectView(val view: InsightsView) : InsightsEvent

    /** Scrolls the trend further back (US1 AS3, FR-005) — mirrors `HistoryEvent.LoadMore`. */
    data object LoadEarlierTrend : InsightsEvent

    data object PreviousMonth : InsightsEvent
    data object NextMonth : InsightsEvent

    /** Switches the section breakdown's scope between the current week and the current month. */
    data class SwitchSectionPeriod(val toMonth: Boolean) : InsightsEvent

    data object Retry : InsightsEvent
}
