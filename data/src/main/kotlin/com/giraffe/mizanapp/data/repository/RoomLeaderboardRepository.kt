package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entity.LeaderboardCacheEntity
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.LeaderboardRefresh
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingPage
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.leaderboard.LeaderboardPeriod
import com.giraffe.mizanapp.domain.leaderboard.LoadMoreResult
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.Ranking
import com.giraffe.mizanapp.domain.leaderboard.RankingEntry
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import com.giraffe.mizanapp.domain.leaderboard.Region
import com.giraffe.mizanapp.domain.leaderboard.RegionId
import com.giraffe.mizanapp.domain.leaderboard.OwnRank
import com.giraffe.mizanapp.domain.leaderboard.markViewer
import com.giraffe.mizanapp.domain.leaderboard.periodFor
import com.giraffe.mizanapp.domain.repository.LeaderboardRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Maps server-ranked snapshots without deriving, sorting, or assigning positions. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomLeaderboardRepository(
    private val db: MizanDatabase,
    private val remote: RemoteDataSource,
    private val refreshBoundary: LeaderboardRefresh,
    private val accountScope: AccountScope,
    private val time: TimeProvider,
) : LeaderboardRepository {

    override fun observeRanking(kind: PeriodKind): Flow<RankingState> =
        db.participationStateDao().observe().flatMapLatest { participation ->
            val regionId = participation?.regionId?.takeIf { participation.optedIn }
                ?: return@flatMapLatest flowOf(RankingState.Unavailable)
            val start = periodFor(kind, time.today(), time.zone(), RegionId(regionId)).start
            val id = "${kind.name}:$start:$regionId"
            combine(
                db.leaderboardCacheDao().observeById(id),
                accountScope.observe(),
                refreshBoundary.reachable,
            ) { entity, session, reachable ->
                entity?.toRankingState(session, reachable) ?: RankingState.Unavailable
            }
        }

    override fun observeOwnRank(kind: PeriodKind): Flow<OwnRankState> =
        db.participationStateDao().observe().flatMapLatest { participation ->
            val regionId = participation?.regionId?.takeIf { participation.optedIn }
                ?: return@flatMapLatest flowOf(OwnRankState.Unavailable)
            val start = periodFor(kind, time.today(), time.zone(), RegionId(regionId)).start
            val id = "${kind.name}:$start:$regionId"
            db.leaderboardCacheDao().observeById(id).map { entity ->
                if (entity == null) return@map OwnRankState.Unavailable
                when (val result = remote.ownRank(kind)) {
                    is RemoteResult.Ok -> {
                        val own = result.value.entry ?: return@map OwnRankState.Unavailable
                        OwnRankState.Available(
                            OwnRank(
                                entry = RankingEntry(own.userId, own.displayName, own.points, own.position, isViewer = true),
                                neighbours = result.value.neighbours.map {
                                    RankingEntry(it.userId, it.displayName, it.points, it.position, isViewer = false)
                                },
                                totalParticipants = result.value.totalParticipants,
                            ),
                        )
                    }
                    RemoteResult.NotAuthenticated, RemoteResult.Unreachable, is RemoteResult.Rejected -> OwnRankState.Unavailable
                }
            }
        }

    override suspend fun loadMore(kind: PeriodKind): LoadMoreResult {
        val participation = db.participationStateDao().observe().first()
        val regionId = participation?.regionId?.takeIf { participation.optedIn } ?: return LoadMoreResult.Unreachable
        val start = periodFor(kind, time.today(), time.zone(), RegionId(regionId)).start
        val id = "${kind.name}:$start:$regionId"
        val cached = db.leaderboardCacheDao().observeById(id).first() ?: return LoadMoreResult.Unreachable
        val current = Json.decodeFromString<RemoteRankingPage>(cached.payload)
        val cursor = current.entries.lastOrNull()?.position

        return when (val result = remote.rankingPage(kind, cursor)) {
            is RemoteResult.Ok -> {
                val merged = current.copy(
                    entries = current.entries + result.value.entries,
                    hasMore = result.value.hasMore,
                    isFinal = result.value.isFinal,
                )
                db.leaderboardCacheDao().upsert(
                    cached.copy(payload = Json.encodeToString(merged), retrievedAt = time.now().toEpochMilli()),
                )
                LoadMoreResult.Applied
            }
            RemoteResult.NotAuthenticated -> LoadMoreResult.SessionExpired
            RemoteResult.Unreachable, is RemoteResult.Rejected -> LoadMoreResult.Unreachable
        }
    }

    override suspend fun refresh(kind: PeriodKind) = refreshBoundary.refresh(kind)

    private fun LeaderboardCacheEntity.toRankingState(
        session: AccountSession,
        reachable: Boolean?,
    ): RankingState {
        val page = Json.decodeFromString<RemoteRankingPage>(payload)
        val viewerId = (session as? AccountSession.SignedIn)?.userId
        val region = Region(RegionId(page.regionId), page.regionDisplayName, ZoneId.of(page.regionZone))
        val ranking = Ranking(
            period = LeaderboardPeriod(
                kind = PeriodKind.valueOf(page.periodKind),
                start = LocalDate.parse(page.periodStart),
                endInclusive = LocalDate.parse(page.periodEndInclusive),
                regionId = region.id,
            ),
            region = region,
            entries = markViewer(
                page.entries.map { entry ->
                    RankingEntry(
                        userId = entry.userId,
                        displayName = entry.displayName,
                        points = entry.points,
                        position = entry.position,
                        isViewer = false,
                    )
                },
                viewerId,
            ),
            hasMore = page.hasMore,
            retrievedAt = Instant.ofEpochMilli(retrievedAt),
            isProvisional = !page.isFinal,
        )
        return if (reachable == false) RankingState.Cached(ranking) else RankingState.Live(ranking)
    }
}
