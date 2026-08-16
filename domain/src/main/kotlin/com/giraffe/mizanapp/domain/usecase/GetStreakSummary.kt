package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.streak.StreakClock
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.streak.buildStreakSummary
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The streak, derived from the record on every emission.
 *
 * It never writes: `ensurePlanFor` is never called from here, and
 * `CatalogueRepository` is deliberately not a dependency, so FR-005 is
 * enforced by the constructor rather than by review. A read that fails
 * propagates — this function does not catch it, because a zero produced by a
 * failed read would be indistinguishable from a real zero (FR-021b).
 * Turning a failure into a visible notice is the ViewModel's job.
 *
 * Also re-emits at the next streak boundary (20:00, then local midnight) so
 * the at-risk state and day rollover update with the app open and no user
 * action, without polling on a timer (research.md R2).
 */
class GetStreakSummary(
    private val completions: CompletionRepository,
    private val dayPlans: DayPlanRepository,
    private val time: TimeProvider,
    private val recordCoverage: RecordCoverageRepository,
) {
    operator fun invoke(): Flow<StreakSummary> =
        combine(
            completions.observeConsistencyDates(),
            boundaryTicks(),
        ) { dates, _ -> dates }
            .map { dates ->
                val coverage = recordCoverage.coverage()
                buildStreakSummary(dates, time.today(), time.now(), time.zone(), dayPlans.earliestPlanDate())
                    .copy(provisional = !coverage.complete)
            }
            .distinctUntilChanged()

    /**
     * Emits immediately, then again at each streak boundary. The immediate
     * emission is what makes the first summary appear without waiting for a
     * boundary; without it `combine` would wait for the first tick.
     */
    private fun boundaryTicks(): Flow<Unit> = flow {
        emit(Unit)
        while (true) {
            val now = time.now()
            val wait = Duration.between(now, StreakClock.nextBoundaryAfter(now, time.zone()))
            // Guards against a zero wait; unreachable while nextBoundaryAfter
            // stays strict, so a mistake there degrades into a slow loop
            // rather than a hang.
            delay(wait.toMillis().coerceAtLeast(1L))
            emit(Unit)
        }
    }
}
