package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entity.ParticipationStateEntity
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteParticipation
import com.giraffe.mizanapp.domain.leaderboard.Participation
import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import com.giraffe.mizanapp.domain.leaderboard.Region
import com.giraffe.mizanapp.domain.leaderboard.RegionId
import com.giraffe.mizanapp.domain.repository.ParticipationRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists only the disposable local mirror of server-owned participation. */
class RoomParticipationRepository(
    private val db: MizanDatabase,
    private val remote: RemoteDataSource,
) : ParticipationRepository {
    private var assignedZone: ZoneId? = null

    override fun observe(): Flow<Participation> = db.participationStateDao().observe().map { entity ->
        val region = if (entity?.optedIn == true && entity.regionId != null && entity.regionDisplayName != null) {
            Region(
                id = RegionId(entity.regionId),
                displayName = entity.regionDisplayName,
                zone = assignedZone ?: ZoneId.of("UTC"),
            )
        } else {
            null
        }
        Participation(optedIn = entity?.optedIn == true, region = region)
    }

    override suspend fun optIn(reportedZone: ZoneId): ParticipationResult {
        val assigned = when (val result = remote.reportZone(reportedZone.id)) {
            is RemoteResult.Ok -> result.value
            else -> return result.toParticipationResult()
        }
        assignedZone = assigned.regionZone?.let(ZoneId::of) ?: reportedZone
        return when (val result = remote.setParticipation(true)) {
            is RemoteResult.Ok -> {
                store(result.value)
                ParticipationResult.Applied
            }
            else -> result.toParticipationResult()
        }
    }

    override suspend fun optOut(): ParticipationResult = when (val result = remote.setParticipation(false)) {
        is RemoteResult.Ok -> {
            assignedZone = null
            db.participationStateDao().deleteAll()
            db.leaderboardCacheDao().deleteAll()
            ParticipationResult.Applied
        }
        else -> result.toParticipationResult()
    }

    override suspend fun reportZone(zone: ZoneId): ParticipationResult = ParticipationResult.Unreachable

    private suspend fun store(value: RemoteParticipation) {
        db.participationStateDao().upsert(
            ParticipationStateEntity(
                optedIn = value.optedIn,
                regionId = value.regionId,
                regionDisplayName = value.regionDisplayName,
                updatedAt = 0L,
            ),
        )
    }

    private fun RemoteResult<*>.toParticipationResult(): ParticipationResult = when (this) {
        RemoteResult.NotAuthenticated -> ParticipationResult.SessionExpired
        RemoteResult.Unreachable, is RemoteResult.Rejected -> ParticipationResult.Unreachable
        is RemoteResult.Ok -> ParticipationResult.Applied
    }
}
