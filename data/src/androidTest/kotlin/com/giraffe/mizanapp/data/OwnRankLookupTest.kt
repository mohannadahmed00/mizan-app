package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC-009, FR-023: locating your own row is a direct lookup, never a page scan. */
class OwnRankLookupTest {

    @Test
    fun own_rank_returns_the_viewers_row_and_neighbours_without_scanning_every_page() = runBlocking {
        val fake = FakeRemoteDataSource()
        fake.currentUserId = VIEWER
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)

        val entries = (1..ENTRY_COUNT).map { position ->
            val userId = if (position == VIEWER_POSITION) VIEWER else "user-$position"
            RemoteRankingEntry(userId = userId, displayName = userId, points = ENTRY_COUNT - position, position = position)
        }
        fake.seedEntries(PeriodKind.WEEKLY, REGION, entries)

        val before = fake.readCount
        val result = (fake.ownRank(PeriodKind.WEEKLY) as RemoteResult.Ok).value
        val readsUsed = fake.readCount - before

        assertNotNull("the viewer's own entry must be present", result.entry)
        assertEquals(VIEWER, result.entry!!.userId)
        assertEquals(VIEWER_POSITION, result.entry.position)
        assertTrue("neighbours either side of the viewer must be present", result.neighbours.isNotEmpty())
        assertEquals(ENTRY_COUNT, result.totalParticipants)
        assertTrue("must not fetch intervening pages to find one row among $ENTRY_COUNT", readsUsed <= 1)
    }

    private companion object {
        const val ENTRY_COUNT = 10_000
        const val VIEWER_POSITION = 7_531
        const val VIEWER = "viewer"
        const val REGION = "egypt-cairo"
    }
}
