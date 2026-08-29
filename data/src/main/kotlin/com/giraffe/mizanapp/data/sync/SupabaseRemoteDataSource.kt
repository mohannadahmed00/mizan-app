package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoard
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoardMember
import com.giraffe.mizanapp.data.sync.dto.RemoteOwnRank
import com.giraffe.mizanapp.data.sync.dto.RemoteParticipation
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingPage
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.RegionId
import com.giraffe.mizanapp.domain.leaderboard.periodFor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The production [RemoteDataSource], reached only through Postgrest. This file
 * and [SupabaseClientFactory] are the only two files in the repository allowed
 * to import `io.github.jan.*` or `io.ktor.*`.
 *
 * **No method throws.** Every exception is mapped to a [RemoteResult]:
 * connection failures and 5xx become [RemoteResult.Unreachable], a 401/403
 * becomes [RemoteResult.NotAuthenticated], and any other 4xx becomes
 * [RemoteResult.Rejected] — the engine decides what to do with each, this
 * class never decides for it.
 */
class SupabaseRemoteDataSource(private val client: SupabaseClient) : RemoteDataSource {

    private suspend fun <T> guarded(block: suspend () -> T): RemoteResult<T> = try {
        RemoteResult.Ok(block())
    } catch (e: RemoteSessionException) {
        RemoteResult.NotAuthenticated
    } catch (e: HttpRequestException) {
        RemoteResult.Unreachable
    } catch (e: RestException) {
        when {
            e.statusCode in 500..599 -> RemoteResult.Unreachable
            e.statusCode == 401 || e.statusCode == 403 -> RemoteResult.NotAuthenticated
            else -> RemoteResult.Rejected(e.message ?: e.error, emptyList())
        }
    } catch (e: Exception) {
        RemoteResult.Unreachable
    }

    override suspend fun upsertDayRecords(rows: List<RemoteDayRecord>): RemoteResult<Unit> = guarded {
        client.postgrest.from("day_records").upsert(rows) { onConflict = "user_id,date" }
        Unit
    }

    override suspend fun upsertCompletions(rows: List<RemoteCompletion>): RemoteResult<Unit> = guarded {
        client.postgrest.from("completions").upsert(rows) { onConflict = "id" }
        Unit
    }

    override suspend fun upsertProfile(row: RemoteProfile): RemoteResult<Unit> = guarded {
        client.postgrest.from("profiles").upsert(row) { onConflict = "id" }
        Unit
    }

    override suspend fun changedSince(since: Instant?, limit: Int): RemoteResult<RemoteChanges> = guarded {
        val dayRecords = client.postgrest.from("day_records").select {
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
            if (since != null) filter { gte("updated_at", since.toString()) }
        }.decodeList<RemoteDayRecord>()

        val completions = client.postgrest.from("completions").select {
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
            if (since != null) filter { gte("updated_at", since.toString()) }
        }.decodeList<RemoteCompletion>()

        val watermark = (dayRecords.mapNotNull { it.updatedAt } + completions.mapNotNull { it.updatedAt })
            .maxOfOrNull { Instant.parse(it) } ?: since

        RemoteChanges(dayRecords, completions, watermark)
    }

    override suspend fun recordsBetween(from: LocalDate, to: LocalDate): RemoteResult<RemoteChanges> = guarded {
        val dayRecords = client.postgrest.from("day_records").select {
            order("date", Order.DESCENDING)
            filter {
                gte("date", from.toString())
                lte("date", to.toString())
            }
        }.decodeList<RemoteDayRecord>()

        val completions = client.postgrest.from("completions").select {
            order("credited_date", Order.DESCENDING)
            filter {
                gte("credited_date", from.toString())
                lte("credited_date", to.toString())
            }
        }.decodeList<RemoteCompletion>()

        RemoteChanges(dayRecords = dayRecords, completions = completions, watermark = null)
    }

    override suspend fun earliestRecordedDate(): RemoteResult<LocalDate?> = guarded {
        val earliestPlan = client.postgrest.from("day_records").select {
            order("date", Order.ASCENDING)
            limit(1)
        }.decodeList<RemoteDayRecord>().firstOrNull()?.date

        val earliestCompletion = client.postgrest.from("completions").select {
            order("credited_date", Order.ASCENDING)
            limit(1)
        }.decodeList<RemoteCompletion>().firstOrNull()?.creditedDate

        listOfNotNull(earliestPlan, earliestCompletion).minOrNull()?.let(LocalDate::parse)
    }

    override suspend fun catalogues(knownFormatVersions: Set<Int>): RemoteResult<List<RemotePublication>> = guarded {
        client.postgrest.from("catalogue_publications").select {
            order("version", Order.ASCENDING)
        }.decodeList<RemotePublication>()
    }

    override suspend fun rankingPage(kind: PeriodKind, cursor: Int?): RemoteResult<RemoteRankingPage> = guarded {
        val participation = requireParticipation()
        val region = requireRegion(participation.regionId)
        val period = requirePeriod(kind, region.id)
        val rows = client.postgrest.from("leaderboard_entries").select {
            order("position", Order.ASCENDING)
            limit((PAGE_SIZE + 1).toLong())
            filter {
                eq("period_kind", kind.name)
                eq("period_start", period.periodStart)
                eq("region_id", region.id)
                if (cursor != null) gt("position", cursor)
            }
        }.decodeList<RemoteRankingEntry>()
        val page = rows.take(PAGE_SIZE)
        val boundary = periodFor(
            kind = kind,
            date = LocalDate.parse(period.periodStart),
            zone = ZoneId.of(region.zone),
            regionId = RegionId(region.id),
        )
        RemoteRankingPage(
            periodKind = kind.name,
            periodStart = period.periodStart,
            periodEndInclusive = boundary.endInclusive.toString(),
            regionId = region.id,
            regionDisplayName = region.displayName,
            regionZone = region.zone,
            entries = page,
            hasMore = rows.size > page.size,
            isFinal = period.state == PERIOD_CLOSED,
        )
    }

    override suspend fun ownRank(kind: PeriodKind): RemoteResult<RemoteOwnRank> = guarded {
        val userId = requireUserId()
        val participation = requireParticipation()
        val region = requireRegion(participation.regionId)
        val period = requirePeriod(kind, region.id)
        val ownEntry = client.postgrest.from("leaderboard_entries").select {
            limit(1)
            filter {
                eq("period_kind", kind.name)
                eq("period_start", period.periodStart)
                eq("region_id", region.id)
                eq("user_id", userId)
            }
        }.decodeSingleOrNull<RemoteRankingEntry>()
        val neighbours = ownEntry?.let { entry ->
            client.postgrest.from("leaderboard_entries").select {
                order("position", Order.ASCENDING)
                filter {
                    eq("period_kind", kind.name)
                    eq("period_start", period.periodStart)
                    eq("region_id", region.id)
                    gte("position", maxOf(1, entry.position - 1))
                    lte("position", entry.position + 1)
                }
            }.decodeList<RemoteRankingEntry>().filterNot { it.userId == userId }
        }.orEmpty()
        val totalParticipants = client.postgrest.from("leaderboard_entries").select {
            count(Count.EXACT)
            head = true
            filter {
                eq("period_kind", kind.name)
                eq("period_start", period.periodStart)
                eq("region_id", region.id)
            }
        }.countOrNull()?.toInt() ?: 0
        RemoteOwnRank(ownEntry, neighbours, totalParticipants)
    }

    override suspend fun honorBoard(kind: PeriodKind): RemoteResult<RemoteHonorBoard> = guarded {
        require(kind != PeriodKind.DAILY) { "DAILY has no Honor Board" }
        val userId = requireUserId()
        val participation = requireParticipation()
        val region = requireRegion(participation.regionId)
        val period = requirePeriod(kind, region.id, state = PERIOD_CLOSED)
        val rows = client.postgrest.from("honor_board_closed").select {
            filter {
                eq("period_kind", kind.name)
                eq("period_start", period.periodStart)
                eq("region_id", region.id)
            }
        }.decodeList<RemoteHonorBoardRow>()
        val members = rows.map { row ->
            RemoteHonorBoardMember(
                displayName = row.displayName,
                isViewer = row.userId == userId,
            )
        }
        val boundary = periodFor(
            kind = kind,
            date = LocalDate.parse(period.periodStart),
            zone = ZoneId.of(region.zone),
            regionId = RegionId(region.id),
        )
        RemoteHonorBoard(
            periodKind = kind.name,
            periodStart = period.periodStart,
            periodEndInclusive = boundary.endInclusive.toString(),
            regionId = region.id,
            regionDisplayName = region.displayName,
            regionZone = region.zone,
            members = members,
            viewerQualified = members.any(RemoteHonorBoardMember::isViewer),
        )
    }

    override suspend fun setParticipation(optedIn: Boolean): RemoteResult<RemoteParticipation> = guarded {
        val userId = requireUserId()
        val existing = participationRow()
        client.postgrest.from("leaderboard_participation").upsert(
            RemoteParticipationWrite(
                userId = userId,
                optedIn = optedIn,
                reportedZone = existing?.reportedZone,
            ),
        ) { onConflict = "user_id" }
        participationResult()
    }

    override suspend fun reportZone(zoneId: String): RemoteResult<RemoteParticipation> = guarded {
        val userId = requireUserId()
        val existing = participationRow()
        client.postgrest.from("leaderboard_participation").upsert(
            RemoteParticipationWrite(
                userId = userId,
                optedIn = existing?.optedIn ?: false,
                reportedZone = zoneId,
            ),
        ) { onConflict = "user_id" }
        participationResult()
    }

    private fun requireUserId(): String = client.auth.currentUserOrNull()?.id ?: throw RemoteSessionException()

    private suspend fun participationRow(): RemoteParticipationRow? =
        client.postgrest.from("leaderboard_participation").select {
            limit(1)
        }.decodeSingleOrNull()

    private suspend fun requireParticipation(): RemoteParticipationRow {
        val row = requireNotNull(participationRow()) { "Participation is unavailable" }
        require(row.optedIn) { "Participation is off" }
        return row
    }

    private suspend fun participationResult(): RemoteParticipation {
        val row = requireNotNull(participationRow()) { "Participation is unavailable" }
        val regionId = row.regionId
        val region = regionId?.let { requireRegion(it) }
        return RemoteParticipation(
            optedIn = row.optedIn,
            regionId = region?.id,
            regionDisplayName = region?.displayName,
            regionZone = region?.zone,
        )
    }

    private suspend fun requireRegion(regionId: String?): RemoteRegion = requireNotNull(
        regionId?.let { id ->
            client.postgrest.from("regions").select {
                limit(1)
                filter { eq("id", id) }
            }.decodeSingleOrNull<RemoteRegion>()
        },
    ) { "Assigned region is unavailable" }

    private suspend fun requirePeriod(
        kind: PeriodKind,
        regionId: String,
        state: String? = null,
    ): RemoteLeaderboardPeriod = requireNotNull(
        client.postgrest.from("leaderboard_periods").select {
            order("period_start", Order.DESCENDING)
            limit(1)
            filter {
                eq("period_kind", kind.name)
                eq("region_id", regionId)
                if (state != null) eq("state", state)
            }
        }.decodeSingleOrNull<RemoteLeaderboardPeriod>(),
    ) { "Leaderboard period is unavailable" }

    private companion object {
        const val PAGE_SIZE = 50
        const val PERIOD_CLOSED = "CLOSED"
    }
}

@Serializable
private data class RemoteParticipationRow(
    @SerialName("user_id") val userId: String,
    @SerialName("opted_in") val optedIn: Boolean,
    @SerialName("region_id") val regionId: String? = null,
    @SerialName("reported_zone") val reportedZone: String? = null,
)

@Serializable
private data class RemoteParticipationWrite(
    @SerialName("user_id") val userId: String,
    @SerialName("opted_in") val optedIn: Boolean,
    @SerialName("reported_zone") val reportedZone: String? = null,
)

@Serializable
private data class RemoteRegion(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val zone: String,
)

@Serializable
private data class RemoteLeaderboardPeriod(
    @SerialName("period_start") val periodStart: String,
    val state: String,
)

@Serializable
private data class RemoteHonorBoardRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
)

private class RemoteSessionException : Exception()
