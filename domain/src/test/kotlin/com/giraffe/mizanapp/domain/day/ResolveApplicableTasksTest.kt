package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.catalogue.Fixtures
import com.giraffe.mizanapp.domain.catalogue.parseCatalogue
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveApplicableTasksTest {

    private val catalogue = parseCatalogue(Fixtures.good()).getOrThrow()

    /** 2026-03-14 is a Saturday; the following days line up from there. */
    private fun dateFor(day: DayOfWeek): LocalDate {
        var date = LocalDate.of(2026, 3, 14)
        while (date.dayOfWeek != day) date = date.plusDays(1)
        return date
    }

    private fun slugsOn(day: DayOfWeek) =
        resolveApplicableTasks(catalogue, version = 1, date = dateFor(day)).map { it.taskSlug }

    private fun availableOn(day: DayOfWeek) =
        resolveApplicableTasks(catalogue, version = 1, date = dateFor(day))
            .sumOf { it.availablePoints }

    @Test
    fun `a base day excludes the fast and the friday practices`() {
        val slugs = slugsOn(DayOfWeek.SATURDAY)

        assertFalse(slugs.contains("fast-voluntary"))
        assertTrue(slugs.none { it.startsWith("friday-") })
        assertEquals(69, availableOn(DayOfWeek.SATURDAY))
    }

    @Test
    fun `monday and thursday include the fast`() {
        listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY).forEach { day ->
            assertTrue("$day", slugsOn(day).contains("fast-voluntary"))
            assertTrue("$day", slugsOn(day).none { it.startsWith("friday-") })
            assertEquals("$day", 74, availableOn(day))
        }
    }

    @Test
    fun `friday includes the seven friday practices but not the fast`() {
        val slugs = slugsOn(DayOfWeek.FRIDAY)

        assertEquals(7, slugs.count { it.startsWith("friday-") })
        assertFalse(slugs.contains("fast-voluntary"))
        assertEquals(76, availableOn(DayOfWeek.FRIDAY))
    }

    @Test
    fun `every weekday totals the expected figure and the week totals 500`() {
        val expected = mapOf(
            DayOfWeek.SATURDAY to 69, DayOfWeek.SUNDAY to 69,
            DayOfWeek.MONDAY to 74, DayOfWeek.TUESDAY to 69,
            DayOfWeek.WEDNESDAY to 69, DayOfWeek.THURSDAY to 74,
            DayOfWeek.FRIDAY to 76,
        )
        expected.forEach { (day, points) -> assertEquals("$day", points, availableOn(day)) }
        assertEquals(500, expected.values.sum())
    }

    @Test
    fun `a version with no task versions resolves to nothing`() {
        assertTrue(resolveApplicableTasks(catalogue, version = 99, date = dateFor(DayOfWeek.SATURDAY)).isEmpty())
    }
}
