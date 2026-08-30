package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity
import com.giraffe.mizanapp.domain.streak.buildStreakSummary
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Discharges Principle III for this increment even though it touches no
 * schema (research.md R7): the only way a streak figure could move under a
 * catalogue change is the streak reading the catalogue, which FR-005
 * forbids and which nothing else would catch — the wrong number would still
 * look plausible.
 */
@RunWith(AndroidJUnit4::class)
class StreakImmutabilityTest : DbTestBase() {

    @Test
    fun changing_the_catalogue_moves_no_streak_figure() = runTest {
        catalogue.seedIfNeeded()

        val run = listOf("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19").map(LocalDate::parse)
        run.forEach { date ->
            time.setDate(date)
            dayPlans.ensurePlanFor(date)
            completions.record(date, "fajr-1")
        }

        val recordStart = dayPlans.earliestPlanDate()
        val datesBefore = completions.observeConsistencyDates().first()
        val summaryBefore = buildStreakSummary(datesBefore, time.today(), time.now(), time.today().plusDays(1).atStartOfDay(time.zone()).toInstant(), recordStart)

        // Bump the catalogue: different points, a changed schedule.
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
                scheduleType = if (v1.taskSlug == "fajr-1") "daysOfWeek" else v1.scheduleType,
                scheduleDays = if (v1.taskSlug == "fajr-1") "FRIDAY" else v1.scheduleDays,
                updatedAt = 1L,
            )
        }
        dao.insertTaskVersions(v2TaskVersions)

        val datesAfter = completions.observeConsistencyDates().first()
        val summaryAfter = buildStreakSummary(datesAfter, time.today(), time.now(), time.today().plusDays(1).atStartOfDay(time.zone()).toInstant(), recordStart)

        assertEquals(datesBefore, datesAfter)
        assertEquals(summaryBefore, summaryAfter)
    }
}
