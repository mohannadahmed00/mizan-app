package com.giraffe.mizanapp.domain.sync

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryScheduleTest {

    private val now: Instant = Instant.parse("2026-08-16T12:00:00Z")

    private fun delay(attempt: Int): Duration =
        Duration.between(now, RetrySchedule.nextAttemptAt(attempt, now))

    @Test
    fun `the first retry is soon`() {
        assertEquals(RetrySchedule.INITIAL, delay(0))
    }

    @Test
    fun `each attempt at least doubles the last, until the ceiling`() {
        var previous = delay(0)
        for (attempt in 1..8) {
            val current = delay(attempt)
            assertTrue(
                "attempt $attempt gave $current, expected at least twice $previous",
                current >= previous.multipliedBy(2) || current == RetrySchedule.CEILING,
            )
            previous = current
        }
    }

    @Test
    fun `the delay never exceeds the ceiling`() {
        for (attempt in 0..100) {
            assertTrue(delay(attempt) <= RetrySchedule.CEILING)
        }
    }

    /**
     * FR-021a as a test. A queue entry is the only copy of a fact the user
     * believes is recorded, so no attempt count may ever produce "give up" —
     * not by overflow, not by exception, not by a sentinel.
     */
    @Test
    fun `an absurd attempt count still yields a finite instant at the ceiling`() {
        val far = RetrySchedule.nextAttemptAt(attempt = 10_000, from = now)

        assertEquals(now.plus(RetrySchedule.CEILING), far)
    }

    @Test
    fun `a year of failed attempts never stops scheduling`() {
        // ~1460 attempts is a year at the six-hour ceiling.
        for (attempt in 1000..1500) {
            assertTrue(RetrySchedule.nextAttemptAt(attempt, now).isAfter(now))
        }
    }

    @Test
    fun `the schedule is relative to the instant it is given, never to a read clock`() {
        val later = now.plus(Duration.ofDays(3))

        assertEquals(
            later.plus(RetrySchedule.INITIAL),
            RetrySchedule.nextAttemptAt(attempt = 0, from = later),
        )
    }
}
