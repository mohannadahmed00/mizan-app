package com.giraffe.mizanapp.domain.catalogue

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic the whole project rests on. These numbers come from
 * docs/PLAN.md and are never the thing that is wrong: if a change makes these
 * fail, the change is wrong.
 */
class CatalogueArithmeticTest {

    private val catalogue = parseCatalogue(Fixtures.good()).getOrThrow()

    @Test
    fun `base days are 69 points`() {
        listOf(
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
        ).forEach { day ->
            assertEquals("$day", 69, catalogue.availablePointsOn(day))
        }
    }

    @Test
    fun `fast days are 74 points`() {
        assertEquals(74, catalogue.availablePointsOn(DayOfWeek.MONDAY))
        assertEquals(74, catalogue.availablePointsOn(DayOfWeek.THURSDAY))
    }

    @Test
    fun `friday is 76 points`() {
        assertEquals(76, catalogue.availablePointsOn(DayOfWeek.FRIDAY))
    }

    @Test
    fun `the week totals 500`() {
        assertEquals(500, catalogue.weeklyAvailablePoints())
    }

    @Test
    fun `week total is the sum of its seven days`() {
        val summed = DayOfWeek.entries.sumOf { catalogue.availablePointsOn(it) }
        assertEquals(69 * 4 + 74 * 2 + 76, summed)
        assertEquals(500, summed)
    }

    @Test
    fun `section composition on a base day`() {
        val expected = mapOf(
            "fajr" to 12,
            "dhuhr" to 8,
            "asr" to 6,
            "maghrib" to 6,
            "isha" to 6,
            "qiyam-witr" to 9,
            "quran" to 4,
            "adhkar" to 18,
        )
        expected.forEach { (sectionId, points) ->
            assertEquals(sectionId, points, catalogue.sectionPointsOn(sectionId, DayOfWeek.SATURDAY))
        }
        assertEquals("sections must sum to the base day", 69, expected.values.sum())
    }

    @Test
    fun `prayer sections subtotal 38`() {
        val prayers = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        val subtotal = prayers.sumOf { catalogue.sectionPointsOn(it, DayOfWeek.SATURDAY) }
        assertEquals(38, subtotal)
    }

    @Test
    fun `weekday-scheduled sections do not apply on a base day`() {
        assertEquals(0, catalogue.sectionPointsOn("fasting", DayOfWeek.SATURDAY))
        assertEquals(0, catalogue.sectionPointsOn("friday", DayOfWeek.SATURDAY))
        assertEquals(5, catalogue.sectionPointsOn("fasting", DayOfWeek.MONDAY))
        assertEquals(7, catalogue.sectionPointsOn("friday", DayOfWeek.FRIDAY))
    }
}
