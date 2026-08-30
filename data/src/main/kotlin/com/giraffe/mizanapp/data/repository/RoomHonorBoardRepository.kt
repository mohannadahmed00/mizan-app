package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.domain.leaderboard.HonorBoard
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardMember
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.LeaderboardPeriod
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RegionId
import com.giraffe.mizanapp.domain.repository.HonorBoardRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Reads Honor Board membership straight from the service — WEEKLY/MONTHLY only (FR-027a). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomHonorBoardRepository(
    private val db: MizanDatabase,
    private val remote: RemoteDataSource,
) : HonorBoardRepository {

    override fun observe(kind: PeriodKind): Flow<HonorBoardState> {
        require(kind != PeriodKind.DAILY) { "DAILY has no Honor Board (FR-027a)" }
        return db.participationStateDao().observe().flatMapLatest { participation ->
            if (participation?.optedIn != true) flowOf<HonorBoardState>(HonorBoardState.Unavailable) else flow { emit(fetch(kind)) }
        }
    }

    override suspend fun refresh(kind: PeriodKind) = Unit

    private suspend fun fetch(kind: PeriodKind): HonorBoardState = when (val result = remote.honorBoard(kind)) {
        is RemoteResult.Ok -> {
            val page = result.value
            HonorBoardState.Available(
                HonorBoard(
                    period = LeaderboardPeriod(
                        kind = PeriodKind.valueOf(page.periodKind),
                        start = LocalDate.parse(page.periodStart),
                        endInclusive = LocalDate.parse(page.periodEndInclusive),
                        regionId = RegionId(page.regionId),
                    ),
                    members = page.members.map { HonorBoardMember(it.displayName, it.isViewer) },
                    viewerQualified = page.viewerQualified,
                ),
            )
        }
        else -> HonorBoardState.Unavailable
    }
}
