package com.giraffe.mizanapp.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DayBoundaryTest {
    private val cairo = ZoneId.of("Africa/Cairo")
    private val date = LocalDate.of(2026, 3, 13)
    private val maghrib = instantAt(date, 18, 0)

    @Test fun instantBeforeMaghribBelongsToTheCivilDate() =
        assertEquals(date, DayBoundary.dateAt(instantAt(date, 17, 59), cairo, maghrib))
    @Test fun instantExactlyAtMaghribBelongsToTheNextDate() =
        assertEquals(date.plusDays(1), DayBoundary.dateAt(maghrib, cairo, maghrib))
    @Test fun instantAfterMaghribBelongsToTheNextDate() =
        assertEquals(date.plusDays(1), DayBoundary.dateAt(instantAt(date, 18, 1), cairo, maghrib))
    @Test fun instantAfterMidnightButBeforeMaghribStillBelongsToTheCivilDate() =
        assertEquals(date.plusDays(1), DayBoundary.dateAt(instantAt(date.plusDays(1), 1, 0), cairo, instantAt(date.plusDays(1), 18, 0)))
    @Test fun nullMaghribFallsBackToTheCivilDate() =
        assertEquals(date, DayBoundary.dateAt(instantAt(date, 21, 0), cairo, null))
    @Test fun midnightDoesNotAdvanceTheDateWhenMaghribIsSupplied() =
        assertEquals(date.plusDays(1), DayBoundary.dateAt(instantAt(date.plusDays(1), 0, 0), cairo, instantAt(date.plusDays(1), 18, 0)))

    private fun instantAt(localDate: LocalDate, hour: Int, minute: Int): Instant =
        LocalDateTime.of(localDate, java.time.LocalTime.of(hour, minute)).atZone(cairo).toInstant()
}
