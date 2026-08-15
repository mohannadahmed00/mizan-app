package com.giraffe.mizanapp.domain.history

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import java.time.LocalDate

/**
 * What a [DayPlan] for [date] would be under [version], without storing it.
 *
 * This is NEVER persisted, NEVER returned from a repository, and NEVER reaches
 * the sync surface — [id] is a fixed, non-identifying constant. It exists to be
 * read once and discarded.
 *
 * Reuses [buildDayPlan], the same function `ensurePlanFor` calls before
 * inserting, so a derived plan and the plan that would be stored for the same
 * date are equal on every field except identity (FR-020b, research.md R1).
 * Always marked [PlanOrigin.BACKFILLED] - deriving is never evidence the app
 * was open on this date (FR-014, FR-021).
 *
 * Stable because [version] is immutable once published (Architectural
 * Decision 3): the same (catalogue, version, date) always derives the same
 * figures, forever.
 */
fun deriveDayPlan(catalogue: Catalogue, version: Int, date: LocalDate): DayPlan =
    buildDayPlan(catalogue, version = version, date = date, origin = PlanOrigin.BACKFILLED) { DERIVED_ID }

private const val DERIVED_ID = "derived"
