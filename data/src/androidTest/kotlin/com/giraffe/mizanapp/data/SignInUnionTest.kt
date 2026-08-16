package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The account already holds history from another device, for dates this one
 * has never seen. Signing in must not discard either side — the account ends
 * up with the union (US1 AS5, FR-011).
 */
class SignInUnionTest : DbTestBase() {

    private val userId = "user-1"

    @Test
    fun the_account_ends_up_holding_the_union_of_both_sides() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        val engine = SyncEngine(db, outbox, accountScope, fake, catalogue, time)

        // The account already holds these — dates this device has never opened.
        val remoteOnlyDate = LocalDate.of(2026, 5, 1)
        fake.upsertDayRecords(
            listOf(RemoteDayRecord(userId = userId, date = remoteOnlyDate.toString(), catalogueVersion = 1)),
        )
        fake.upsertCompletions(
            listOf(
                RemoteCompletion(
                    id = "remote-only-completion",
                    userId = userId,
                    creditedDate = remoteOnlyDate.toString(),
                    taskSlug = "fajr-1",
                    pointsAwarded = 2,
                    recordedAt = "2026-05-01T05:00:00Z",
                ),
            ),
        )

        // This device has its own local-only history for a different date.
        val localOnlyDate = LocalDate.of(2026, 8, 16)
        catalogue.seedIfNeeded()
        time.setDate(localOnlyDate)
        dayPlans.ensurePlanFor(localOnlyDate)
        val plan = requireNotNull(dayPlans.planFor(localOnlyDate))
        completions.record(localOnlyDate, plan.plannedTasks.first().taskSlug)

        engine.migrateOnSignIn(userId)
        engine.pull()

        val (dayRecords, remoteCompletions) = fake.rows()
        val dates = dayRecords.map { it.date }.toSet()

        assertTrue("remote-only date discarded", remoteOnlyDate.toString() in dates)
        assertTrue("local-only date discarded", localOnlyDate.toString() in dates)
        assertEquals(2, dates.size)

        val completionIds = remoteCompletions.map { it.id }.toSet()
        assertTrue("remote-only completion discarded", "remote-only-completion" in completionIds)
        assertTrue(remoteCompletions.size >= 2)
    }
}
