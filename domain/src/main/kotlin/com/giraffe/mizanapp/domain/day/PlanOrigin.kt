package com.giraffe.mizanapp.domain.day

/**
 * How a [DayPlan] came into being.
 *
 * `OPENED` means the app was running on that date and created the plan for
 * it. `BACKFILLED` means the plan was created afterwards, for a date the
 * user never saw. Phase 4's streak rule depends on this distinction — a
 * backfilled plan must never be read as evidence the user was present
 * (FR-011).
 */
enum class PlanOrigin { OPENED, BACKFILLED }
