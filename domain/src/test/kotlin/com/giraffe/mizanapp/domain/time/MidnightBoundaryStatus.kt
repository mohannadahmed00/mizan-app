package com.giraffe.mizanapp.domain.time

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [BoundaryStatus] double for tests that only need a plausible `expiresAt` — next local
 * midnight, recomputed fresh from [time] on every read — and don't care about the Maghrib
 * regime itself. Real Maghrib-regime behaviour is `BoundaryStateStoreTest`'s job, in `:data`.
 */
class MidnightBoundaryStatus(private val time: TimeProvider) : BoundaryStatus {

    override fun current(): BoundaryState = BoundaryState(
        regime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION),
        coordinates = null,
        zoneIdWhenObtained = null,
        resolvedDate = time.today(),
        expiresAt = time.today().plusDays(1).atStartOfDay(time.zone()).toInstant(),
        lastResolvedDate = null,
        lastResolvedRegime = null,
    )

    override fun observe(): Flow<BoundaryState> = flowOf(current())
    override suspend fun refresh(now: java.time.Instant, zone: java.time.ZoneId) = Unit
    override suspend fun requestLocation(): LocationRequestOutcome = LocationRequestOutcome.NoFixAvailable
    override suspend fun eraseLocation() = Unit
    override fun promptShown(): Boolean = true
    override suspend fun markPromptShown() = Unit
}
