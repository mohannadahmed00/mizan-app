package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.day.DayFixtures.task
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildDayPlanTest {

    @Test
    fun `a base day is worth 69`() {
        assertEquals(69, DayFixtures.planFor(DayOfWeek.SATURDAY).availablePoints)
    }

    @Test
    fun `a fast day is worth 74 and friday is worth 76`() {
        assertEquals(74, DayFixtures.planFor(DayOfWeek.MONDAY).availablePoints)
        assertEquals(76, DayFixtures.planFor(DayOfWeek.FRIDAY).availablePoints)
    }

    @Test
    fun `planned tasks snapshot label points and limit from the catalogue`() {
        val plan = DayFixtures.planFor()
        val fajr = plan.task("fajr-1")

        assertEquals("Fajr 1", fajr.label)
        assertEquals(2, fajr.points)
        assertEquals(1, fajr.maxOccurrencesPerDay)
        assertEquals("fajr", fajr.sectionId)
        assertEquals("Fajr", fajr.sectionLabel)
    }

    @Test
    fun `adhkar is one task with a limit of nine contributing eighteen`() {
        val adhkar = DayFixtures.planFor().task("adhkar")

        assertEquals(9, adhkar.maxOccurrencesPerDay)
        assertEquals(2, adhkar.points)
        assertEquals(18, adhkar.availablePoints)
        assertEquals(
            "adhkar must be a single row, not nine",
            1,
            DayFixtures.planFor().plannedTasks.count { it.sectionId == "adhkar" },
        )
    }

    @Test
    fun `available points equal the sum over planned tasks`() {
        val plan = DayFixtures.planFor(DayOfWeek.FRIDAY)

        assertEquals(plan.plannedTasks.sumOf { it.availablePoints }, plan.availablePoints)
    }

    @Test
    fun `the plan carries a hijri label`() {
        assertTrue(DayFixtures.planFor().hijriLabel.isNotBlank())
    }

    @Test
    fun `ids are non-blank and unique`() {
        val plan = DayFixtures.planFor()
        val ids = plan.plannedTasks.map { it.id }

        assertTrue(plan.id.isNotBlank())
        assertTrue(ids.none { it.isBlank() })
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(plan.plannedTasks.all { it.dayPlanId == plan.id })
    }

    @Test
    fun `sections come back in catalogue order with tasks in display order`() {
        val sections = DayFixtures.planFor().sectionsInOrder()

        assertEquals("fajr", sections.first().first)
        val orders = sections.map { (_, tasks) -> tasks.first().sectionOrder }
        assertEquals(orders.sorted(), orders)
        sections.forEach { (_, tasks) ->
            val positions = tasks.map { it.displayPosition }
            assertEquals(positions.sorted(), positions)
        }
    }
}
