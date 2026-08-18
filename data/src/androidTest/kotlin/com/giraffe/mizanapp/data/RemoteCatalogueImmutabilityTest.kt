package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.RemoteCataloguePublicationRepository
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.repository.PullOutcome
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Principle III's structural test for this increment (FR-024a): a newer,
 * wholly different catalogue must never re-derive, re-version, or reprice a
 * day already recorded on this device.
 */
class RemoteCatalogueImmutabilityTest : DbTestBase() {

    private data class Snapshot(
        val version: Int,
        val available: Int,
        val earned: Int,
        val taskPoints: Map<String, Int>,
    )

    private val startDate: LocalDate = LocalDate.of(2026, 7, 1)

    @Test
    fun a_newer_catalogue_never_touches_a_day_already_recorded() = runBlocking {
        catalogue.seedIfNeeded()

        val before = LinkedHashMap<LocalDate, Snapshot>()
        for (i in 0 until 14) {
            val date = startDate.plusDays(i.toLong())
            time.setDate(date)
            dayPlans.ensurePlanFor(date)
            val plan = requireNotNull(dayPlans.planFor(date))
            plan.plannedTasks.forEachIndexed { index, task ->
                if (index % 3 == 0) completions.record(date, task.taskSlug)
            }
            val score = scoreDay(plan, completions.liveBetween(date, date))
            before[date] = Snapshot(
                version = plan.catalogueVersion,
                available = score.available,
                earned = score.earned,
                taskPoints = plan.plannedTasks.associate { it.taskSlug to it.points },
            )
        }

        val versionOneRowsBefore = db.catalogueDao().taskVersionsFor(1)

        val tomorrow = time.today().plusDays(1)
        val fake = FakeRemoteDataSource()
        fake.publish(
            RemotePublication(
                version = 2,
                effectiveFrom = tomorrow.toString(),
                formatVersion = 1,
                payload = catalogueVersion2Payload(tomorrow),
            ),
        )
        val publications = RemoteCataloguePublicationRepository(fake, db, time)

        assertEquals(PullOutcome.Added(listOf(2)), publications.pullIfNewer())

        for ((date, expected) in before) {
            val plan = requireNotNull(dayPlans.planFor(date))
            val score = scoreDay(plan, completions.liveBetween(date, date))
            assertEquals("catalogue version changed for $date", expected.version, plan.catalogueVersion)
            assertEquals("available points changed for $date", expected.available, score.available)
            assertEquals("earned points changed for $date", expected.earned, score.earned)
            assertEquals(
                "per-task points changed for $date",
                expected.taskPoints,
                plan.plannedTasks.associate { it.taskSlug to it.points },
            )
        }

        time.setDate(tomorrow)
        dayPlans.ensurePlanFor(tomorrow)
        assertEquals(2, requireNotNull(dayPlans.planFor(tomorrow)).catalogueVersion)

        assertEquals(
            "version 1's task_versions rows must be untouched by a version 2 pull",
            versionOneRowsBefore,
            db.catalogueDao().taskVersionsFor(1),
        )
    }
}
