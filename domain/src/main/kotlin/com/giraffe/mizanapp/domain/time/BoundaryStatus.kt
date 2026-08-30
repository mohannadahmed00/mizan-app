package com.giraffe.mizanapp.domain.time

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * App-facing authority for the active boundary rule and its disclosure.
 *
 * [promptShown] and [markPromptShown] are not part of the resolved [BoundaryState] — the
 * first-launch prompt is answered at most once, ever, regardless of how many times the regime
 * itself later changes, so it is tracked alongside the resolved state rather than folded into it.
 */
interface BoundaryStatus {
    fun current(): BoundaryState
    fun observe(): Flow<BoundaryState>
    suspend fun refresh(now: Instant, zone: ZoneId)
    suspend fun requestLocation(): LocationRequestOutcome
    suspend fun eraseLocation()
    fun promptShown(): Boolean
    suspend fun markPromptShown()
}

sealed interface LocationRequestOutcome {
    data object Obtained : LocationRequestOutcome
    data object PermissionDenied : LocationRequestOutcome
    data object NoFixAvailable : LocationRequestOutcome
}
