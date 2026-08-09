package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **The test the whole storage design exists for** (Principle III).
 *
 * A recorded day must report the same figures forever. This is the one category
 * of bug that cannot be repaired after the fact — the original truth is gone.
 */
@RunWith(AndroidJUnit4::class)
class DayPlanImmutabilityTest : DbTestBase() {

    /** Publishes a v2 in which every task is worth 10 and the day would total far more. */
    private suspend fun publishInflatedVersion2(effectiveFrom: String) {
        val dao = db.catalogueDao()
        dao.insertVersions(listOf(CatalogueVersionEntity(2, effectiveFrom)))
        dao.insertTaskVersions(
            dao.taskVersionsFor(1).map {
                TaskVersionEntity(
                    id = UUID.randomUUID().toString(),
                    taskSlug = it.taskSlug,
                    catalogueVersion = 2,
                    points = 10,
                    maxOccurrencesPerDay = it.maxOccurrencesPerDay,
                    scheduleType = it.scheduleType,
                    scheduleDays = it.scheduleDays,
                    updatedAt = time.now().toEpochMilli(),
                )
            },
        )
    }

    @Test
    fun a_recorded_day_is_unchanged_by_a_later_catalogue_version() = runTest {
        seedAndPlanToday()
        val recordedDate = time.today()
        val before = dayPlans.planFor(recordedDate)!!
        assertEquals(69, before.availablePoints)

        publishInflatedVersion2(effectiveFrom = recordedDate.plusDays(1).toString())

        val after = dayPlans.planFor(recordedDate)!!
        assertEquals("available points must not move", before.availablePoints, after.availablePoints)
        assertEquals("planned tasks must not move", before.plannedTasks, after.plannedTasks)
        assertEquals(1, after.catalogueVersion)
    }

    @Test
    fun a_later_day_reflects_the_new_version() = runTest {
        seedAndPlanToday()
        val today = time.today()
        publishInflatedVersion2(effectiveFrom = today.plusDays(1).toString())

        time.setDate(today.plusDays(1))
        val outcome = dayPlans.ensurePlanFor(time.today())

        assertTrue("$outcome", outcome is EnsureOutcome.Created)
        val tomorrow = (outcome as EnsureOutcome.Created).plan
        assertEquals(2, tomorrow.catalogueVersion)
        assertTrue(
            "the new version must actually change the total",
            tomorrow.availablePoints != 69,
        )
    }

    @Test
    fun a_completion_keeps_the_points_it_was_awarded() = runTest {
        seedAndPlanToday()
        val today = time.today()
        completions.record(today, "fajr-1")
        val awarded = db.completionDao().liveByDate(today.toString()).single().pointsAwarded
        assertEquals(2, awarded)

        publishInflatedVersion2(effectiveFrom = today.plusDays(1).toString())

        assertEquals(
            "a recorded completion carries its own points forever",
            2,
            db.completionDao().liveByDate(today.toString()).single().pointsAwarded,
        )
    }

    /** US4: the label is computed at creation, stored, and never recomputed. */
    @Test
    fun a_plan_carries_a_stable_hijri_label() = runTest {
        seedAndPlanToday()
        val today = time.today()
        val label = dayPlans.planFor(today)!!.hijriLabel

        assertTrue("a label must always be present", label.isNotBlank())
        assertTrue("it must name a month, not a number", label.any { it.isLetter() })

        db.close()
        openDatabase(keepTime = true)
        catalogue.seedIfNeeded()

        assertEquals("the stored label must survive unchanged", label, dayPlans.planFor(today)!!.hijriLabel)
    }

    @Test
    fun ensure_plan_returns_the_existing_plan_untouched() = runTest {
        seedAndPlanToday()
        val first = dayPlans.planFor(time.today())!!

        val second = dayPlans.ensurePlanFor(time.today())

        assertTrue("$second", second is EnsureOutcome.AlreadyExists)
        assertEquals(first, (second as EnsureOutcome.AlreadyExists).plan)
    }

    /** SC-005: records survive process death. Reopening the database is the cheap proxy. */
    @Test
    fun records_survive_a_database_reopen() = runTest {
        seedAndPlanToday()
        val today = time.today()
        repeat(3) { completions.record(today, "adhkar") }
        completions.record(today, "fajr-1")
        val planBefore = dayPlans.planFor(today)!!
        val liveBefore = completions.liveCount(today, "adhkar")

        db.close()
        openDatabase(keepTime = true)
        catalogue.seedIfNeeded()

        assertEquals("plan must be identical", planBefore, dayPlans.planFor(today))
        assertEquals("live count must be identical", liveBefore, completions.liveCount(today, "adhkar"))
        assertEquals(1, completions.liveCount(today, "fajr-1"))
    }
}
