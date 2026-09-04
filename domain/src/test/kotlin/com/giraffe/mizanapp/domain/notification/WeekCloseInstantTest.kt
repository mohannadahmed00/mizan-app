package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekCloseInstantTest {
    private fun boundary(date: LocalDate, expires: Instant) = BoundaryState(BoundaryRegime.Fallback(com.giraffe.mizanapp.domain.time.FallbackReason.NEVER_HAD_LOCATION), null, null, date, expires, null, null)
    @Test fun `only Friday closes the week`() { val instant = Instant.parse("2026-09-04T15:00:00Z"); assertEquals(instant, weekCloseInstant(boundary(LocalDate.of(2026, 9, 4), instant))); assertNull(weekCloseInstant(boundary(LocalDate.of(2026, 9, 3), instant))) }
}
