package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate

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
}
