package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.RemoteCataloguePublicationRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import com.giraffe.mizanapp.domain.repository.PullOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A catalogue problem must never block a user's own record from reaching the
 * account — the two pulls are wired independently in `SyncWorker`, and this
 * proves the independence holds even while the catalogue side is actively
 * failing (unreachable, then rejected).
 */
class CataloguePullFailureIsolationTest : DbTestBase() {

    private val userId = "user-1"

    @Test
    fun a_failing_catalogue_pull_never_blocks_a_healthy_record_drain() = runBlocking {
        catalogue.seedIfNeeded()
        dayPlans.ensurePlanFor(time.today())
        val slug = requireNotNull(dayPlans.planFor(time.today())).plannedTasks.first().taskSlug
        completions.record(time.today(), slug)

        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        val engine = SyncEngine(db, outbox, accountScope, fake, catalogue, time)
        val publications = RemoteCataloguePublicationRepository(fake, db, time)

        fake.unreachable = true
        assertEquals(PullOutcome.Unreachable, publications.pullIfNewer())

        fake.unreachable = false
        fake.publish(
            RemotePublication(
                version = 2,
                effectiveFrom = "2099-01-01",
                formatVersion = 1,
                payload = """{"editable": true}""",
            ),
        )
        assertTrue(publications.pullIfNewer() is PullOutcome.Rejected)

        engine.migrateOnSignIn(userId)

        val completion = db.completionDao().liveByDate(time.today().toString()).single()
        assertNotNull(completion.syncedAt)
        assertTrue(db.completionDao().unsynced().isEmpty())
    }
}
