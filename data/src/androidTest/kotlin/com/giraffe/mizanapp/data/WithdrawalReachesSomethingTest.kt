package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves withdrawal targets rows created by the real aggregation path, not hand-seeded rows. */
class WithdrawalReachesSomethingTest {
    @Test
    fun aggregation_materialises_period_rows_that_withdrawal_can_reach() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, VIEWER)
        optIn(fake, OBSERVER)
        fake.upsertCompletions(
            listOf(
                RemoteCompletion(
                    id = "completion-1",
                    userId = VIEWER,
                    creditedDate = "2026-03-14",
                    taskSlug = "task",
                    pointsAwarded = 12,
                    recordedAt = "2026-03-14T09:00:00Z",
                ),
            ),
        )

        fake.recomputeOpenPeriods()
        assertTrue("the aggregation must create period rows before folding", fake.materializedPeriodCount > 0)

        fake.currentUserId = OBSERVER
        assertTrue(page(fake).any { it.userId == VIEWER })

        fake.currentUserId = VIEWER
        fake.setParticipation(false)
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

    private companion object {
        const val VIEWER = "viewer"
        const val OBSERVER = "observer"
    }
}
