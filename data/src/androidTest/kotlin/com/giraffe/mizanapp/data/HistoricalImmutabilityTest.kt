package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity
import com.giraffe.mizanapp.domain.day.PlanOrigin
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one test constitution Principle III requires for any increment
 * touching persistence and the catalogue: a stored day — opened or
 * backfilled — must report its original figures forever, even after the
 * catalogue that produced it is superseded.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalImmutabilityTest : DbTestBase() {

    @Test
    fun stored_plans_survive_a_catalogue_change_while_a_new_plan_reflects_it() = runTest {
        // 1. Catalogue v1, effective from 2026-01-01 (the shipped seed).
        catalogue.seedIfNeeded()

        // 2. An opened plan and a backfilled plan, both under v1.
        time.setDate(LocalDate.parse("2026-08-08"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-08"))
        time.setDate(LocalDate.parse("2026-08-13"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-10"))

        val openedBefore = dayPlans.planFor(LocalDate.parse("2026-08-08"))!!
        val backfilledBefore = dayPlans.planFor(LocalDate.parse("2026-08-10"))!!
        assertEquals(PlanOrigin.OPENED, openedBefore.origin)
        assertEquals(PlanOrigin.BACKFILLED, backfilledBefore.origin)

        // 3. Catalogue v2: different points, a changed schedule, effective
        //    2026-09-01. Two inserts — the version row alone changes
        //    nothing, because points and schedules live on task_versions.
        val dao = db.catalogueDao()
        dao.insertVersions(listOf(CatalogueVersionEntity(version = 2, effectiveFrom = "2026-09-01")))
        val v1TaskVersions = dao.taskVersionsFor(1)
        val v2TaskVersions = v1TaskVersions.map { v1 ->
            TaskVersionEntity(
                id = UUID.randomUUID().toString(),
                taskSlug = v1.taskSlug,
                catalogueVersion = 2,
                points = v1.points + 5,
                maxOccurrencesPerDay = v1.maxOccurrencesPerDay,
                // fajr-1 moves from every day to Friday-only; every other
                // task keeps its schedule but not its points.
                scheduleType = if (v1.taskSlug == "fajr-1") "daysOfWeek" else v1.scheduleType,
                scheduleDays = if (v1.taskSlug == "fajr-1") "FRIDAY" else v1.scheduleDays,
                updatedAt = 1L,
            )
        }
        dao.insertTaskVersions(v2TaskVersions)

        // 4. Both stored plans report their original tasks, points, and totals.
        val openedAfter = dayPlans.planFor(LocalDate.parse("2026-08-08"))!!
        val backfilledAfter = dayPlans.planFor(LocalDate.parse("2026-08-10"))!!

        assertEquals("an opened day must not silently re-score", openedBefore, openedAfter)
        assertEquals("a backfilled day must not silently re-score", backfilledBefore, backfilledAfter)
        assertEquals(1, openedAfter.catalogueVersion)
        assertEquals(1, backfilledAfter.catalogueVersion)

        // 5. A new plan on or after v2's effective-from reflects v2.
        time.setDate(LocalDate.parse("2026-09-05"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-09-05"))
        val newPlan = dayPlans.planFor(LocalDate.parse("2026-09-05"))!!

        assertEquals(2, newPlan.catalogueVersion)
        assertNotEquals(
            "the new plan's points must differ from v1's, proving it actually used v2",
            openedBefore.availablePoints,
            newPlan.availablePoints,
        )
        // Saturday, 2026-09-05: fajr-1 no longer applies under v2's Friday-only rule.
        assertEquals(false, newPlan.plannedTasks.any { it.taskSlug == "fajr-1" })
    }
}
