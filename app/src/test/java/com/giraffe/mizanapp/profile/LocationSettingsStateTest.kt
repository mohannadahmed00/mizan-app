package com.giraffe.mizanapp.profile

import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.FallbackReason
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSettingsStateTest {

    private fun stateWith(regime: BoundaryRegime, coordinates: Coordinates? = null): BoundaryState = BoundaryState(
        regime = regime,
        coordinates = coordinates,
        zoneIdWhenObtained = if (coordinates != null) "Africa/Cairo" else null,
        resolvedDate = LocalDate.parse("2026-03-14"),
        expiresAt = Instant.parse("2026-03-15T00:00:00Z"),
        lastResolvedDate = null,
        lastResolvedRegime = null,
    )

    @Test
    fun everyRegimeProducesADistinctNonEmptyStatement() {
        val statements = listOf(
            stateWith(BoundaryRegime.Maghrib, Coordinates(30.0, 31.2)),
            stateWith(BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION)),
            stateWith(BoundaryRegime.Fallback(FallbackReason.ERASED)),
            stateWith(BoundaryRegime.Fallback(FallbackReason.ZONE_CHANGED_AWAITING_FIX)),
        ).map { locationSettingsFor(it).statement }

        statements.forEach { assertTrue("statement must not be blank", it.isNotBlank()) }
        assertEquals("every regime must produce a distinct statement", statements.size, statements.distinct().size)
    }

    @Test
    fun zoneChangedAwaitingFixIsNeverSilent() {
        val settings = locationSettingsFor(stateWith(BoundaryRegime.Fallback(FallbackReason.ZONE_CHANGED_AWAITING_FIX)))
        assertFalse(settings.statement.isBlank())
    }

    @Test
    fun maghribRegimeReportsLocationHeld() {
        val settings = locationSettingsFor(stateWith(BoundaryRegime.Maghrib, Coordinates(30.0, 31.2)))
        assertTrue(settings.locationHeld)
        assertEquals(BoundaryRegimeLabel.MAGHRIB, settings.regime)
        assertFalse(settings.canEnable)
    }

    @Test
    fun fallbackRegimesReportNoLocationHeldAndCanEnable() {
        val settings = locationSettingsFor(stateWith(BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION)))
        assertFalse(settings.locationHeld)
        assertTrue(settings.canEnable)
    }

    @Test
    fun distinctFallbackReasonsMapToDistinctLabels() {
        val labels = listOf(
            FallbackReason.NEVER_HAD_LOCATION,
            FallbackReason.ERASED,
            FallbackReason.ZONE_CHANGED_AWAITING_FIX,
        ).map { locationSettingsFor(stateWith(BoundaryRegime.Fallback(it))).regime }

        assertNotEquals(labels[0], labels[1])
        assertNotEquals(labels[1], labels[2])
        assertNotEquals(labels[0], labels[2])
    }
}
