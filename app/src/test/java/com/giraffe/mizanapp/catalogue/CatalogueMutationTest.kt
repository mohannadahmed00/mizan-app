package com.giraffe.mizanapp.catalogue

import com.giraffe.mizanapp.catalogue.CatalogueDefect.WeekTotalMismatch
import com.giraffe.mizanapp.catalogue.CatalogueDefect.WeekdayTotalMismatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC-002. Proves the suite can actually fail.
 *
 * A contract that passes a corrupted catalogue is not a contract, so this test
 * corrupts a good one in memory and insists the validator notices. Without it,
 * every other test here could be vacuously green.
 */
class CatalogueMutationTest {

    private val validator = CatalogueValidator()
    private val good = parseCatalogue(Fixtures.good()).getOrThrow()

    @Test
    fun `the unmutated fixture is clean`() {
        assertEquals(emptyList<CatalogueDefect>(), validator.validate(good))
    }

    @Test
    fun `raising one point value breaks both the day and the week`() {
        val mutated = good.copy(
            taskVersions = good.taskVersions.map { version ->
                if (version.taskSlug == "fajr-1") version.copy(points = 3) else version
            },
        )

        val defects = validator.validate(mutated)

        assertTrue("expected a weekday mismatch, got $defects", defects.any { it is WeekdayTotalMismatch })
        assertTrue("expected a week mismatch, got $defects", defects.any { it is WeekTotalMismatch })

        val week = defects.filterIsInstance<WeekTotalMismatch>().single()
        assertEquals(500, week.expected)
        assertEquals(507, week.actual) // +1 point on all seven days
    }

    @Test
    fun `lowering one point value is caught too`() {
        val mutated = good.copy(
            taskVersions = good.taskVersions.map { version ->
                if (version.taskSlug == "adhkar-5") version.copy(points = 1) else version
            },
        )

        val defects = validator.validate(mutated)

        val saturday = defects.filterIsInstance<WeekdayTotalMismatch>()
            .single { it.dayOfWeek == "SATURDAY" }
        assertEquals(69, saturday.expected)
        assertEquals(68, saturday.actual)
    }

    @Test
    fun `removing a task is caught`() {
        val mutated = good.copy(
            tasks = good.tasks.filterNot { it.slug == "friday-7" },
            taskVersions = good.taskVersions.filterNot { it.taskSlug == "friday-7" },
        )

        val defects = validator.validate(mutated)

        val friday = defects.filterIsInstance<WeekdayTotalMismatch>()
            .single { it.dayOfWeek == "FRIDAY" }
        assertEquals(76, friday.expected)
        assertEquals(75, friday.actual)
    }
}
