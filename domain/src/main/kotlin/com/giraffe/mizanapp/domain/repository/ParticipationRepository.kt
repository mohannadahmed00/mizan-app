package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.leaderboard.Participation
import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface ParticipationRepository {

    /** Off by default for every account, including pre-existing ones (FR-001). */
    fun observe(): Flow<Participation>

    /**
     * Opting in reports the device zone so the service can assign a region.
     * The caller supplies the zone from TimeProvider — this interface does not
     * read a clock (Principle VII).
     *
     * The client MUST NOT name a region. Region is assigned server-side (FR-014).
     */
    suspend fun optIn(reportedZone: ZoneId): ParticipationResult

    /**
     * Leaves every period still open — ranking and Honor Board — and keeps the
     * participant out of every period that opens afterwards (FR-004, FR-004b).
     *
     * Periods that have already CLOSED are left exactly as they stand, rankings
     * and Honor Board alike (FR-004a). A closed period admits no mutation, so a
     * participant cannot erase past standings — which is why FR-002a requires
     * the opt-in copy to say so before they join.
     *
     * MUST NOT alter, hide or delete any recorded history, points, streak or
     * insight (FR-005, SC-003).
     */
    suspend fun optOut(): ParticipationResult

    /** Re-reports the zone after a device timezone change, keeping FR-012 true (FR-013). */
    suspend fun reportZone(zone: ZoneId): ParticipationResult
}
