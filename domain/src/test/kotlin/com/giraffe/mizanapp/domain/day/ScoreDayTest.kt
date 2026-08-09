package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.day.DayFixtures.task
import java.time.DayOfWeek
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreDayTest {

    private val plan = DayFixtures.planFor(DayOfWeek.SATURDAY)

    /** Completes every task to its limit. */
    private fun fullDay(): List<Completion> =
        plan.plannedTasks.flatMap { task ->
            (0 until task.maxOccurrencesPerDay).map { DayFixtures.completion(plan, task, it) }
        }

    @Test
    fun `an untouched day is zero of its available total`() {
        val score = scoreDay(plan, emptyList())

        assertEquals(0, score.earned)
        assertEquals(69, score.available)
        assertEquals(0f, score.fraction, 0.0001f)
    }

    @Test
    fun `a fully completed day earns exactly its available total`() {
        val score = scoreDay(plan, fullDay())

        assertEquals(69, score.earned)
        assertEquals(69, score.available)
        assertEquals(1f, score.fraction, 0.0001f)
    }

    @Test
    fun `adhkar completed nine times contributes eighteen`() {
        val adhkar = plan.task("adhkar")
        val nine = (0 until 9).map { DayFixtures.completion(plan, adhkar, it) }

        assertEquals(18, scoreDay(plan, nine).earned)
    }

    @Test
    fun `reversed records contribute nothing`() {
        val adhkar = plan.task("adhkar")
        val mixed = (0 until 9).map { DayFixtures.completion(plan, adhkar, it, reversed = it < 4) }

        assertEquals(10, scoreDay(plan, mixed).earned)
    }

    @Test
    fun `earned may never be negative or exceed available`() {
        val score = scoreDay(plan, fullDay())

        assertTrue(score.earned >= 0)
        assertTrue(score.earned <= score.available)
    }

    @Test
    fun `fraction is zero when nothing is available`() {
        val empty = plan.copy(availablePoints = 0, plannedTasks = emptyList())

        assertEquals(0f, scoreDay(empty, emptyList()).fraction, 0.0001f)
    }

    /**
     * SC-003. Seeded so it reproduces exactly: after every single operation in a
     * mixed sequence, earned must equal the sum over live records.
     */
    @Test
    fun `SC-003 - the invariant holds across twenty mixed operations`() {
        val random = Random(42)
        var history = emptyList<Completion>()
        var recorded = 0
        var operations = 0

        while (operations < 20) {
            val undoing = random.nextBoolean() && history.any { it.isLive }

            if (undoing) {
                val victim = history.filter { it.isLive }.random(random)
                history = history.map {
                    if (it.id == victim.id) it.copy(reversedAt = it.recordedAt.plusSeconds(1)) else it
                }
            } else {
                val candidates = plan.plannedTasks.filter { canRecord(history, it) }
                if (candidates.isEmpty()) break
                val task = candidates.random(random)
                history = history + DayFixtures.completion(plan, task, recorded++)
            }
            operations++

            val score = scoreDay(plan, history)
            assertEquals(
                "operation $operations: earned must equal the live records' sum",
                history.filter { it.isLive }.sumOf { it.pointsAwarded },
                score.earned,
            )
            assertTrue("operation $operations: earned exceeded available", score.earned <= score.available)
            assertTrue("operation $operations: earned went negative", score.earned >= 0)
        }

        assertEquals("the sequence must actually run 20 operations", 20, operations)
    }
}
