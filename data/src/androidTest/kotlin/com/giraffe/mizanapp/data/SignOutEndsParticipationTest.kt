package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.SupabaseAccountRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-008: signing out must not leave an opted-in account published server-side.
 * Clearing the local tables (T013) is not sufficient — a signed-out account left
 * opted-in server-side stays visible to every other participant.
 */
class SignOutEndsParticipationTest : DbTestBase() {

    @Test
    fun signing_out_removes_the_account_from_every_participant_visible_ranking() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, VIEWER)
        optIn(fake, OBSERVER)
        fake.seedEntries(PeriodKind.DAILY, REGION, listOf(entry(VIEWER, 12, 1), entry(OBSERVER, 8, 2)))

        fake.currentUserId = OBSERVER
        assertTrue(page(fake).any { it.userId == VIEWER })

        fake.currentUserId = VIEWER
        val repository = SupabaseAccountRepository(
            client = null,
            accountScope = AccountScope(db.accountScopeDao(), time),
            db = db,
            outbox = Outbox(db, time),
            time = time,
            remote = fake,
        )
        repository.signOut(SignOutMode.KEEP_LOCAL_RECORDS)

        fake.currentUserId = OBSERVER
        assertFalse(page(fake).any { it.userId == VIEWER })
    }

    private suspend fun optIn(fake: FakeRemoteDataSource, userId: String) {
        fake.currentUserId = userId
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
    }

    private suspend fun page(fake: FakeRemoteDataSource) =
        (fake.rankingPage(PeriodKind.DAILY, null) as RemoteResult.Ok).value.entries

    private fun entry(userId: String, points: Int, position: Int) = RemoteRankingEntry(
        userId = userId,
        displayName = if (userId == VIEWER) "Viewer" else "Observer",
        points = points,
        position = position,
    )

    private companion object {
        const val VIEWER = "viewer"
        const val OBSERVER = "observer"
        const val REGION = "egypt-cairo"
    }
}
