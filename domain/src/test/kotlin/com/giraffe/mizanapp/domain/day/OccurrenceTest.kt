package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.day.DayFixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Occurrence limits, and the guarantee that undo is never destructive.
 *
 * The Adhkar task (limit 9) is the only multi-occurrence task in the catalogue,
 * so it is the only place these properties can be observed.
 */
class OccurrenceTest {

    private val plan = DayFixtures.planFor()
    private val adhkar = plan.task("adhkar")
    private val fajr = plan.task("fajr-1")

    private fun records(count: Int, reversedCount: Int = 0): List<Completion> =
        (0 until count).map { DayFixtures.completion(plan, adhkar, it, reversed = it < reversedCount) }

    @Test
    fun `live count ignores reversed records`() {
        assertEquals(5, liveCount(records(count = 8, reversedCount = 3), "adhkar"))
    }

    @Test
    fun `a task below its limit may be recorded`() {
        assertTrue(canRecord(records(8), adhkar))
    }

    @Test
    fun `a task at its limit may not be recorded`() {
        assertFalse(canRecord(records(9), adhkar))
    }

    @Test
    fun `reversing one frees exactly one slot`() {
        val atLimit = records(9)
        assertFalse(canRecord(atLimit, adhkar))

        val oneReversed = atLimit.mapIndexed { i, c ->
            if (i == 8) c.copy(reversedAt = c.recordedAt.plusSeconds(60)) else c
        }

        assertTrue("undo must always free a slot", canRecord(oneReversed, adhkar))
        assertEquals(8, liveCount(oneReversed, "adhkar"))
    }

    @Test
    fun `many reversed records never consume slots`() {
        // 20 reversed and 2 live: still 7 slots free, not fewer.
        val history = (0 until 22).map {
            DayFixtures.completion(plan, adhkar, it, reversed = it < 20)
        }

        assertEquals(2, liveCount(history, "adhkar"))
        assertTrue(canRecord(history, adhkar))
    }

    @Test
    fun `a single-occurrence task is at its limit after one record`() {
        val one = listOf(DayFixtures.completion(plan, fajr, 0))

        assertEquals(1, liveCount(one, "fajr-1"))
        assertFalse(canRecord(one, fajr))
    }

    @Test
    fun `latest live is the most recent unreversed record`() {
        val history = records(4)

        assertEquals("c-adhkar-3", latestLive(history, "adhkar")?.id)
        assertNull(latestLive(emptyList(), "adhkar"))
    }

    @Test
    fun `SC-012 - record to limit, undo, record again, ten times over`() {
        val neverUndone = records(9)
        val expectedCount = liveCount(neverUndone, "adhkar")
        val expectedEarned = neverUndone.filter { it.isLive }.sumOf { it.pointsAwarded }

        var history = neverUndone
        repeat(10) { round ->
            // undo the most recent live record
            val latest = latestLive(history, "adhkar")!!
            history = history.map {
                if (it.id == latest.id) it.copy(reversedAt = it.recordedAt.plusSeconds(1)) else it
            }
            assertTrue("round $round: a slot must be free after undo", canRecord(history, adhkar))

            // record again
            history = history + DayFixtures.completion(plan, adhkar, 100 + round)

            assertEquals("round $round: count", expectedCount, liveCount(history, "adhkar"))
            assertEquals(
                "round $round: earned",
                expectedEarned,
                history.filter { it.isLive }.sumOf { it.pointsAwarded },
            )
        }
    }
}
