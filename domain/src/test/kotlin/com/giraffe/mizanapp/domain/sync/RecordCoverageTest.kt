package com.giraffe.mizanapp.domain.sync

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCoverageTest {

    private val floor: LocalDate = LocalDate.of(2026, 6, 1)

    @Test
    fun `complete coverage knows every date, however far back`() {
        val coverage = RecordCoverage.completeFrom(floor)

        assertTrue(coverage.isKnown(floor.minusYears(5)))
        assertTrue(coverage.isKnown(floor))
        assertTrue(coverage.isKnown(floor.plusYears(5)))
    }

    @Test
    fun `incomplete coverage does not know a date before the floor`() {
        val coverage = RecordCoverage(knownFrom = floor, complete = false)

        assertFalse(coverage.isKnown(floor.minusDays(1)))
        assertFalse(coverage.isKnown(floor.minusYears(1)))
    }

    @Test
    fun `the floor itself is known, and so is everything after it`() {
        val coverage = RecordCoverage(knownFrom = floor, complete = false)

        assertTrue(coverage.isKnown(floor))
        assertTrue(coverage.isKnown(floor.plusDays(1)))
    }

    /**
     * A device with no record at all has nothing it could be missing, so an
     * absent floor must not read as "knows nothing" — that would render a fresh
     * install's whole calendar as still-loading.
     */
    @Test
    fun `a null floor knows every date even while incomplete`() {
        val coverage = RecordCoverage(knownFrom = null, complete = false)

        assertTrue(coverage.isKnown(floor))
        assertTrue(coverage.isKnown(floor.minusYears(3)))
    }

    @Test
    fun `completeFrom carries the record start through unchanged`() {
        assertTrue(RecordCoverage.completeFrom(floor).complete)
        assertTrue(RecordCoverage.completeFrom(floor).knownFrom == floor)
        assertTrue(RecordCoverage.completeFrom(null).knownFrom == null)
    }
}
