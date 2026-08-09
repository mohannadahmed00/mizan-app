package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.day.DayFixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LandingSectionTest {

    private val plan = DayFixtures.planFor()

    private fun completeSections(vararg sectionIds: String): List<Completion> {
        var index = 0
        return plan.plannedTasks
            .filter { it.sectionId in sectionIds }
            .flatMap { task ->
                (0 until task.maxOccurrencesPerDay).map { DayFixtures.completion(plan, task, index++) }
            }
    }

    @Test
    fun `nothing complete lands on the first section`() {
        assertEquals(0, landingSectionIndex(sectionProgress(plan, emptyList())))
    }

    @Test
    fun `the first three sections complete lands on the fourth`() {
        val done = completeSections("fajr", "dhuhr", "asr")

        assertEquals(3, landingSectionIndex(sectionProgress(plan, done)))
    }

    @Test
    fun `everything complete lands back on the first section`() {
        val all = plan.plannedTasks.flatMapIndexed { i, task ->
            (0 until task.maxOccurrencesPerDay).map { DayFixtures.completion(plan, task, i * 100 + it) }
        }

        assertEquals(0, landingSectionIndex(sectionProgress(plan, all)))
    }

    @Test
    fun `a partly recorded multi-occurrence task leaves its section incomplete`() {
        val adhkar = plan.task("adhkar")
        val threeOfNine = (0 until 3).map { DayFixtures.completion(plan, adhkar, it) }

        val progress = sectionProgress(plan, threeOfNine).first { it.sectionId == "adhkar" }

        assertFalse("three of nine is not done", progress.isComplete)
    }

    @Test
    fun `reversed records do not make a section complete`() {
        val fajr = plan.plannedTasks.filter { it.sectionId == "fajr" }
        val reversed = fajr.mapIndexed { i, t -> DayFixtures.completion(plan, t, i, reversed = true) }

        val progress = sectionProgress(plan, reversed).first { it.sectionId == "fajr" }

        assertFalse(progress.isComplete)
        assertEquals(0, landingSectionIndex(sectionProgress(plan, reversed)))
    }

    @Test
    fun `an empty plan lands on zero rather than failing`() {
        assertEquals(0, landingSectionIndex(emptyList()))
    }
}
