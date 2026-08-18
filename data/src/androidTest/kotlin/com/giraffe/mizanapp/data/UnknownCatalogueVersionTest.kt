package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.RemoteCataloguePublicationRepository
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import com.giraffe.mizanapp.domain.repository.PullOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-025, FR-027, FR-028: a publication this app cannot read, cannot admit,
 * or cannot reach is never allowed to disturb the catalogue already in force.
 */
class UnknownCatalogueVersionTest : DbTestBase() {

    @Test
    fun an_unreadable_format_version_is_skipped_in_favour_of_the_last_understood_one() = runBlocking {
        catalogue.seedIfNeeded()
        val fake = FakeRemoteDataSource()
        fake.publish(
            RemotePublication(version = 2, effectiveFrom = "2099-01-01", formatVersion = 99, payload = "irrelevant"),
        )
        val publications = RemoteCataloguePublicationRepository(fake, db, time)

        val outcome = publications.pullIfNewer()

        assertEquals(PullOutcome.Skipped(listOf(2)), outcome)
        assertEquals(1, catalogue.currentVersion())
        dayPlans.ensurePlanFor(time.today())
        assertEquals(1, requireNotNull(dayPlans.planFor(time.today())).catalogueVersion)
    }

    @Test
    fun a_payload_carrying_a_user_authoring_field_is_rejected_wholesale() = runBlocking {
        catalogue.seedIfNeeded()
        val fake = FakeRemoteDataSource()
        fake.publish(
            RemotePublication(
                version = 2,
                effectiveFrom = "2099-01-01",
                formatVersion = 1,
                payload = """{"editable": true}""",
            ),
        )
        val publications = RemoteCataloguePublicationRepository(fake, db, time)

        val outcome = publications.pullIfNewer()

        assertTrue(outcome is PullOutcome.Rejected)
        assertTrue((outcome as PullOutcome.Rejected).defects.any { it is CatalogueDefect.UserAuthoringAffordance })
        assertEquals(listOf(1), db.catalogueDao().versions().map { it.version })
    }

    @Test
    fun a_malformed_payload_is_rejected_wholesale_exactly_like_an_authoring_affordance() = runBlocking {
        catalogue.seedIfNeeded()
        val fake = FakeRemoteDataSource()
        fake.publish(
            RemotePublication(
                version = 2,
                effectiveFrom = "2099-01-01",
                formatVersion = 1,
                payload = "not valid json {{{",
            ),
        )
        val publications = RemoteCataloguePublicationRepository(fake, db, time)

        val outcome = publications.pullIfNewer()

        assertTrue(outcome is PullOutcome.Rejected)
        assertTrue((outcome as PullOutcome.Rejected).defects.any { it is CatalogueDefect.MalformedCatalogue })
        assertEquals(listOf(1), db.catalogueDao().versions().map { it.version })
    }

    @Test
    fun an_unreachable_pull_leaves_the_built_in_seed_fully_in_force() = runBlocking {
        catalogue.seedIfNeeded()
        val fake = FakeRemoteDataSource().apply { unreachable = true }
        val publications = RemoteCataloguePublicationRepository(fake, db, time)

        val outcome = publications.pullIfNewer()

        assertEquals(PullOutcome.Unreachable, outcome)
        assertEquals(1, catalogue.currentVersion())
        dayPlans.ensurePlanFor(time.today())
        assertEquals(1, requireNotNull(dayPlans.planFor(time.today())).catalogueVersion)
    }
}
