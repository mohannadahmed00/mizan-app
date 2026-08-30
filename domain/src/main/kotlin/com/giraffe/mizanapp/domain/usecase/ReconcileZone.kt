package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.ParticipationRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import kotlinx.coroutines.flow.first

/**
 * Re-reports the device zone when it no longer matches the assigned region's
 * zone, so a traveller stays correctly regioned (FR-012, FR-013). Called on
 * app start and when the leaderboard section opens — never from a background
 * worker or receiver, which this increment has not justified.
 */
class ReconcileZone(
    private val repository: ParticipationRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke() {
        val participation = repository.observe().first()
        if (participation.optedIn && participation.region?.zone != time.zone()) {
            repository.reportZone(time.zone())
        }
    }
}
