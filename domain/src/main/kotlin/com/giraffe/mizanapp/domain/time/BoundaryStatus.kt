package com.giraffe.mizanapp.domain.time

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** App-facing authority for the active boundary rule and its disclosure. */
interface BoundaryStatus {
    fun current(): BoundaryState
    fun observe(): Flow<BoundaryState>
    suspend fun refresh(now: Instant, zone: ZoneId)
    suspend fun requestLocation(): LocationRequestOutcome
    suspend fun eraseLocation()
}

sealed interface LocationRequestOutcome {
    data object Obtained : LocationRequestOutcome
    data object PermissionDenied : LocationRequestOutcome
    data object NoFixAvailable : LocationRequestOutcome
}
