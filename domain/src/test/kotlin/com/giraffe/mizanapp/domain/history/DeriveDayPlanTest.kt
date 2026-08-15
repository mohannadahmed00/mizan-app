package com.giraffe.mizanapp.domain.history

import com.giraffe.mizanapp.domain.catalogue.CatalogueVersion
import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [deriveDayPlan] must be indistinguishable from a stored plan on every field
 * except identity (FR-020b). This is the property the whole history feature's
 * "derived equals stored" guarantee rests on (research.md R1).
 */
class DeriveDayPlanTest {

    private val catalogue = DayFixtures.catalogue
    private val date = DayFixtures.dateFor(java.time.DayOfWeek.SATURDAY)

    @Test
    fun `derived plan equals the plan ensurePlanFor would store, field by field`() {
        val stored = buildDayPlan(catalogue, version = 1, date = date, origin = PlanOrigin.BACKFILLED) { "stored-id" }
        val derived = deriveDayPlan(catalogue, version = 1, date = date)

        assertEquals(stored.date, derived.date)
        assertEquals(stored.catalogueVersion, derived.catalogueVersion)
        assertEquals(stored.hijriLabel, derived.hijriLabel)
        assertEquals(stored.availablePoints, derived.availablePoints)
        assertEquals(stored.origin, derived.origin)

        assertEquals(stored.plannedTasks.size, derived.plannedTasks.size)
        val storedBySlug = stored.plannedTasks.associateBy { it.taskSlug }
        val derivedBySlug = derived.plannedTasks.associateBy { it.taskSlug }
        assertEquals(storedBySlug.keys, derivedBySlug.keys)
        for (slug in storedBySlug.keys) {
            val s = storedBySlug.getValue(slug)
            val d = derivedBySlug.getValue(slug)
            assertEquals("$slug sectionId", s.sectionId, d.sectionId)
            assertEquals("$slug sectionLabel", s.sectionLabel, d.sectionLabel)
            assertEquals("$slug sectionOrder", s.sectionOrder, d.sectionOrder)
            assertEquals("$slug displayPosition", s.displayPosition, d.displayPosition)
            assertEquals("$slug label", s.label, d.label)
            assertEquals("$slug points", s.points, d.points)
            assertEquals("$slug maxOccurrencesPerDay", s.maxOccurrencesPerDay, d.maxOccurrencesPerDay)
        }

        // Identity is deliberately NOT equal — a derived plan is never confused with a stored one internally.
        assertNotEquals(stored.id, derived.id)
    }

    @Test
    fun `derived plan is always marked BACKFILLED`() {
        // Even when the date passed in happens to equal "today" by coincidence in a
        // caller's context, deriveDayPlan has no clock and no opinion about "today" -
        // it always marks BACKFILLED because it is never evidence the app was open.
        val derived = deriveDayPlan(catalogue, version = 1, date = date)
        assertEquals(PlanOrigin.BACKFILLED, derived.origin)
    }

    @Test
    fun `changing the catalogue does not change a derived plan for an earlier version`() {
        val derivedV1Before = deriveDayPlan(catalogue, version = 1, date = date)

        // A hypothetical v2 catalogue with different points for the same tasks.
        val v2TaskVersions = catalogue.taskVersions.map { tv ->
            if (tv.catalogueVersion == 1) tv.copy(catalogueVersion = 2, points = tv.points + 100) else tv
        }
        val catalogueWithV2 = catalogue.copy(
            versions = catalogue.versions + CatalogueVersion(2, LocalDate.parse("2099-01-01")),
            taskVersions = catalogue.taskVersions + v2TaskVersions,
        )

        val derivedV1After = deriveDayPlan(catalogueWithV2, version = 1, date = date)

        assertEquals(derivedV1Before.availablePoints, derivedV1After.availablePoints)
        assertEquals(
            derivedV1Before.plannedTasks.map { it.points },
            derivedV1After.plannedTasks.map { it.points },
        )
    }
}
