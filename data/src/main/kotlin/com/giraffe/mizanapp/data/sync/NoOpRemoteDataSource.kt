package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoard
import com.giraffe.mizanapp.data.sync.dto.RemoteOwnRank
import com.giraffe.mizanapp.data.sync.dto.RemoteParticipation
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingPage
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import java.time.Instant
import java.time.LocalDate

/**
 * Bound whenever the build carries no Supabase configuration, so the app is
 * the offline MVP and **no caller ever handles a null data source** — the Koin
 * binding for [RemoteDataSource] stays non-nullable no matter how the build
 * was configured (FR-003).
 */
class NoOpRemoteDataSource : RemoteDataSource {

    override suspend fun upsertDayRecords(rows: List<RemoteDayRecord>): RemoteResult<Unit> =
        RemoteResult.Unreachable

    override suspend fun upsertCompletions(rows: List<RemoteCompletion>): RemoteResult<Unit> =
        RemoteResult.Unreachable

    override suspend fun upsertProfile(row: RemoteProfile): RemoteResult<Unit> =
        RemoteResult.Unreachable

    override suspend fun changedSince(since: Instant?, limit: Int): RemoteResult<RemoteChanges> =
        RemoteResult.Unreachable

    override suspend fun recordsBetween(from: LocalDate, to: LocalDate): RemoteResult<RemoteChanges> =
        RemoteResult.Unreachable

    override suspend fun earliestRecordedDate(): RemoteResult<LocalDate?> =
        RemoteResult.Unreachable

    override suspend fun catalogues(knownFormatVersions: Set<Int>): RemoteResult<List<RemotePublication>> =
        RemoteResult.Unreachable

    override suspend fun rankingPage(kind: PeriodKind, cursor: Int?): RemoteResult<RemoteRankingPage> =
        RemoteResult.Unreachable

    override suspend fun ownRank(kind: PeriodKind): RemoteResult<RemoteOwnRank> =
        RemoteResult.Unreachable

    override suspend fun honorBoard(kind: PeriodKind): RemoteResult<RemoteHonorBoard> =
        RemoteResult.Unreachable

    override suspend fun setParticipation(optedIn: Boolean): RemoteResult<RemoteParticipation> =
        RemoteResult.Unreachable

    override suspend fun reportZone(zoneId: String): RemoteResult<RemoteParticipation> =
        RemoteResult.Unreachable
}
