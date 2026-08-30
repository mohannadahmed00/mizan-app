package com.giraffe.mizanapp.domain.time

import com.giraffe.mizanapp.domain.prayer.Coordinates
import java.time.Instant
import java.time.LocalDate

data class BoundaryState(
    val regime: BoundaryRegime,
    val coordinates: Coordinates?,
    val zoneIdWhenObtained: String?,
    val resolvedDate: LocalDate,
    val expiresAt: Instant,
    val lastResolvedDate: LocalDate?,
    val lastResolvedRegime: BoundaryRegime?,
    /** Disclosure only (FR-017b) — never a trust input; age must never invalidate coordinates. */
    val obtainedAt: Instant? = null,
) {
    init {
        require((regime is BoundaryRegime.Maghrib) == (coordinates != null)) {
            "Coordinates must be present exactly for the Maghrib regime"
        }
    }
}
