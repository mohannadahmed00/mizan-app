package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.day.DayPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Access to the frozen record of a date.
 *
 * **There is deliberately no update method of any kind.** A day plan is written
 * once and never changed (Principle III), and an interface that cannot express
 * the forbidden operation is stronger than one that merely avoids calling it.
 */
interface DayPlanRepository {

    suspend fun planFor(date: LocalDate): DayPlan?

    /**
     * Returns the existing plan for [date], or creates and freezes one.
     *
     * An existing plan is returned untouched — never rebuilt, never reconciled
     * against the current catalogue (FR-007).
     *
     * Accepts any date rather than only today, because roadmap Phase 3 must
     * backfill elapsed days through this same method. The restriction on
     * writing lives in `DayWritePolicy`, consulted by [CompletionRepository].
     */
    suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome

    fun observePlan(date: LocalDate): Flow<DayPlan?>
}

sealed interface EnsureOutcome {
    data class Created(val plan: DayPlan) : EnsureOutcome
    data class AlreadyExists(val plan: DayPlan) : EnsureOutcome
    data class NoCatalogue(val date: LocalDate) : EnsureOutcome
}
