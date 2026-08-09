package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.seed.CatalogueSeeder
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueSeederTest : DbTestBase() {

    @Test
    fun seeding_an_empty_database_loads_thirty_two_tasks() = runTest {
        val outcome = catalogue.seedIfNeeded()

        assertTrue("expected Seeded, got $outcome", outcome is SeedOutcome.Seeded)
        assertEquals(32, (outcome as SeedOutcome.Seeded).taskCount)
        assertEquals(1, outcome.version)
    }

    @Test
    fun seeding_twice_changes_nothing() = runTest {
        catalogue.seedIfNeeded()
        val dao = db.catalogueDao()
        val before = Triple(dao.countTasks(), dao.countVersions(), dao.allTaskVersions().size)

        val second = catalogue.seedIfNeeded()

        assertTrue("expected AlreadyPresent, got $second", second is SeedOutcome.AlreadyPresent)
        assertEquals(before, Triple(dao.countTasks(), dao.countVersions(), dao.allTaskVersions().size))
    }

    @Test
    fun seeding_twice_leaves_plans_and_completions_untouched() = runTest {
        seedAndPlanToday()
        completions.record(time.today(), "fajr-1")
        val plansBefore = db.dayPlanDao().countPlans()
        val rowsBefore = db.completionDao().countAllRows(time.today().toString())

        catalogue.seedIfNeeded()

        assertEquals(plansBefore, db.dayPlanDao().countPlans())
        assertEquals(rowsBefore, db.completionDao().countAllRows(time.today().toString()))
    }

    @Test
    fun a_defective_catalogue_writes_nothing_at_all() = runTest {
        val defective = CatalogueSeeder(db, time, resourcePath = "/catalogue/bad/zero-points.json")

        val outcome = defective.seedIfNeeded()

        assertTrue("expected Failed, got $outcome", outcome is SeedOutcome.Failed)
        assertTrue((outcome as SeedOutcome.Failed).defects.isNotEmpty())
        assertEquals("nothing may be written", 0, db.catalogueDao().countTasks())
        assertEquals(0, db.catalogueDao().countVersions())
    }

    @Test
    fun an_absent_catalogue_is_reported_not_treated_as_empty() = runTest {
        val missing = CatalogueSeeder(db, time, resourcePath = "/catalogue/does-not-exist.json")

        val outcome = missing.seedIfNeeded()

        assertTrue("absence must never read as success", outcome is SeedOutcome.Failed)
        assertEquals(0, db.catalogueDao().countTasks())
    }
}
