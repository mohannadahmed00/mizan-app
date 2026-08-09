package com.giraffe.mizanapp.domain.time

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Hijri label is computed on the device, never fetched (research.md R4).
 *
 * That is what makes it always available — on a fresh install, in airplane
 * mode, on first launch — with nothing to wait for and nothing that can fail.
 */
class HijriLabelTest {

    @Test
    fun `a civil date yields a non-blank label`() {
        assertTrue(HijriLabel.forDate(LocalDate.of(2026, 1, 1)).isNotBlank())
    }

    @Test
    fun `the same date always yields the identical label`() {
        val date = LocalDate.of(2026, 8, 9)

        assertEquals(HijriLabel.forDate(date), HijriLabel.forDate(date))
    }

    @Test
    fun `consecutive civil dates yield different labels`() {
        val first = HijriLabel.forDate(LocalDate.of(2026, 6, 1))
        val second = HijriLabel.forDate(LocalDate.of(2026, 6, 2))

        assertNotEquals(first, second)
    }

    @Test
    fun `labels are produced across a wide range of dates without failing`() {
        var date = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2030, 1, 1)
        while (date.isBefore(end)) {
            assertTrue("blank label for $date", HijriLabel.forDate(date).isNotBlank())
            date = date.plusDays(17)
        }
    }
}
