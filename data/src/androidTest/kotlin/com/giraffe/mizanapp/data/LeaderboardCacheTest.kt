package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.db.entity.ParticipationStateEntity
import com.giraffe.mizanapp.data.repository.RoomLeaderboardRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.LeaderboardRefresh
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ranking retrieval writes Room first and degrades to an explicit cache state. */
class LeaderboardCacheTest : DbTestBase() {

    @Test
    fun refresh_replaces_the_deterministic_cache_and_unreachable_states_are_explicit() = runBlocking {
        val userId = "user-1"
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        assertTrue(fake.reportZone("Africa/Cairo") is RemoteResult.Ok)
        assertTrue(fake.setParticipation(true) is RemoteResult.Ok)
        db.participationStateDao().upsert(
            ParticipationStateEntity(
                optedIn = true,
                regionId = REGION_ID,
                regionDisplayName = "Egypt (Cairo)",
                updatedAt = time.now().toEpochMilli(),
            ),
        )
        val accountScope = AccountScope(db.accountScopeDao(), time).also {
            it.set(userId, "person@example.test", "Person")
        }
        val refresh = LeaderboardRefresh(db, fake, time)
        val repository = RoomLeaderboardRepository(db, fake, refresh, accountScope, time)

        fake.seedEntries(
            kind = PeriodKind.WEEKLY,
            regionId = REGION_ID,
            entries = entries(points = 10),
            regionDisplayName = "Egypt (Cairo)",
            regionZone = "Africa/Cairo",
            periodStart = PERIOD_START,
            periodEndInclusive = "2026-03-20",
        )
        repository.refresh(PeriodKind.WEEKLY)

        val cached = requireNotNull(db.leaderboardCacheDao().observeById(CACHE_ID).first())
        assertEquals(time.now().toEpochMilli(), cached.retrievedAt)
        assertTrue(repository.observeRanking(PeriodKind.WEEKLY).first() is RankingState.Live)

        fake.seedEntries(
            kind = PeriodKind.WEEKLY,
            regionId = REGION_ID,
            entries = entries(points = 20),
            regionDisplayName = "Egypt (Cairo)",
            regionZone = "Africa/Cairo",
            periodStart = PERIOD_START,
            periodEndInclusive = "2026-03-20",
        )
        repository.refresh(PeriodKind.WEEKLY)

        assertEquals(1, rowCount("leaderboard_cache"))
        val replaced = repository.observeRanking(PeriodKind.WEEKLY).first() as RankingState.Live
        assertEquals(20, replaced.ranking.entries.single().points)

        fake.unreachable = true
        repository.refresh(PeriodKind.WEEKLY)
        assertTrue(repository.observeRanking(PeriodKind.WEEKLY).first() is RankingState.Cached)

        db.leaderboardCacheDao().deleteAll()
        repository.refresh(PeriodKind.WEEKLY)
        assertTrue(repository.observeRanking(PeriodKind.WEEKLY).first() is RankingState.Unavailable)
    }

    private fun entries(points: Int) = listOf(
        RemoteRankingEntry(
            userId = "user-1",
            displayName = "Person",
            points = points,
            position = 1,
        ),
    )

    private fun rowCount(table: String): Int = db.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private companion object {
        const val REGION_ID = "egypt-cairo"
        const val PERIOD_START = "2026-03-14"
        const val CACHE_ID = "WEEKLY:$PERIOD_START:$REGION_ID"
    }
}
