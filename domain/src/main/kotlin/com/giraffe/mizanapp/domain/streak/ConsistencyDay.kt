package com.giraffe.mizanapp.domain.streak

import java.time.LocalDate

/**
 * Whether [date] is a Consistency Day.
 *
 * A date counts when at least one live completion is credited to it (FR-001).
 * It is a yes or no: a date with forty completions counts exactly as much as
 * a date with one (FR-002). [consistencyDates] arrives already filtered of
 * reversed and tombstoned records — nothing downstream re-checks liveness.
 *
 * `PlanOrigin` and a plan's existence are deliberately **not** consulted here
 * (FR-004). `DayWritePolicy` admits completions only on the current date,
 * which makes a completion sufficient evidence the app was open — **Phase 5
 * must revisit this**: retroactive completion breaks that premise.
 */
fun isConsistencyDay(date: LocalDate, consistencyDates: Set<LocalDate>): Boolean =
    date in consistencyDates
