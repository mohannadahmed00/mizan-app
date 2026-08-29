package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entity.LeaderboardCacheEntity
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.time.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Refreshes the bounded first page into Room. Its only callers are
 * `RoomLeaderboardRepository` and the background sync coordinator; ViewModels
 * observe the repository and never invoke this network boundary directly.
 */
class LeaderboardRefresh(
    private val db: MizanDatabase,
    private val remote: RemoteDataSource,
    private val time: TimeProvider,
) {
    private val _reachable = MutableStateFlow<Boolean?>(null)
    internal val reachable: StateFlow<Boolean?> = _reachable

    suspend fun refresh(kind: PeriodKind) {
        when (val result = remote.rankingPage(kind, cursor = null)) {
            is RemoteResult.Ok -> {
                val page = result.value
                db.leaderboardCacheDao().upsert(
                    LeaderboardCacheEntity(
                        id = "${kind.name}:${page.periodStart}:${page.regionId}",
                        periodKind = page.periodKind,
                        periodStart = page.periodStart,
                        regionId = page.regionId,
                        regionDisplayName = page.regionDisplayName,
                        payload = Json.encodeToString(page),
                        retrievedAt = time.now().toEpochMilli(),
                    ),
                )
                _reachable.value = true
            }
            RemoteResult.Unreachable, RemoteResult.NotAuthenticated, is RemoteResult.Rejected -> {
                _reachable.value = false
            }
        }
    }
}
