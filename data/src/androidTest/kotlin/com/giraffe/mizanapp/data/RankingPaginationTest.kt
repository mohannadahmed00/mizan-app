package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.db.entity.ParticipationStateEntity
import com.giraffe.mizanapp.data.repository.RoomLeaderboardRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.LeaderboardRefresh
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.domain.leaderboard.LoadMoreResult
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-024: a page is bounded at 50; loadMore extends it; hasMore is false at the end. */
class RankingPaginationTest : DbTestBase() {

    @Test
    fun a_page_is_bounded_at_fifty_and_loadMore_extends_it_to_the_end() = runBlocking {
        val userId = "user-1"
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
        db.participationStateDao().upsert(
            ParticipationStateEntity(
                optedIn = true,
                regionId = REGION_ID,
                regionDisplayName = "Egypt (Cairo)",
                updatedAt = time.now().toEpochMilli(),
            ),
        )
        val accountScope = AccountScope(db.accountScopeDao(), time)
            .also { it.set(userId, "person@example.test", "Person") }
        val refresh = LeaderboardRefresh(db, fake, time)
        val repository = RoomLeaderboardRepository(db, fake, refresh, accountScope, time)

        val entries = (1..62).map { position ->
            RemoteRankingEntry(
                userId = "user-$position",
                displayName = "Person $position",
                points = 100 - position,
                position = position,
            )
        }
        fake.seedEntries(
            kind = PeriodKind.WEEKLY,
            regionId = REGION_ID,
            entries = entries,
            regionDisplayName = "Egypt (Cairo)",
            regionZone = "Africa/Cairo",
            periodStart = PERIOD_START,
            periodEndInclusive = "2026-03-20",
        )

        repository.refresh(PeriodKind.WEEKLY)
        val firstPage = repository.observeRanking(PeriodKind.WEEKLY).first() as RankingState.Live
        assertEquals(50, firstPage.ranking.entries.size)
        assertTrue("more than 50 entries exist", firstPage.ranking.hasMore)

        val outcome = repository.loadMore(PeriodKind.WEEKLY)
        assertTrue("$outcome", outcome is LoadMoreResult.Applied)

        val extended = repository.observeRanking(PeriodKind.WEEKLY).first() as RankingState.Live
        assertEquals(62, extended.ranking.entries.size)
        assertFalse("all entries are now loaded", extended.ranking.hasMore)
    }

    private companion object {
        const val REGION_ID = "egypt-cairo"
        const val PERIOD_START = "2026-03-14"
    }
}
