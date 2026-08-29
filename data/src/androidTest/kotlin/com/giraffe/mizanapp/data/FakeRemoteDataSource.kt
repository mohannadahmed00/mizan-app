package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteChanges
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * An in-memory stand-in for the account backend, implementing exactly the
 * Postgres semantics the sync engine depends on: upsert on the declared
 * conflict target, `LEAST()` on `catalogue_version`, `COALESCE()` on
 * `reversed_at` with every other field frozen after first write, and a
 * monotonic `updated_at`.
 *
 * The only remote any test in this increment touches apart from the RLS SQL
 * and the one thin live contract test (research R14).
 */
class FakeRemoteDataSource : RemoteDataSource {

    /** Set true to make every method return [RemoteResult.Unreachable]. */
    var unreachable: Boolean = false

    /** Entity ids (dates or completion UUIDs) that are rejected rather than written. */
    var rejectIds: Set<String> = emptySet()

    /**
     * Once the cumulative count of rows accepted by this fake would reach this
     * value, the write in progress stops accepting further rows and returns
     * [RemoteResult.Unreachable] — simulating a connection dropped mid-batch
     * after some rows had already landed server-side. Fires once, then clears,
     * so a retry of the same (idempotent) batch completes normally.
     */
    var dropAfter: Int? = null

    /**
     * When true, a write reports [RemoteResult.Ok] without persisting anything —
     * the ambiguous case where the client cannot tell whether the server kept
     * the change (US2 AS4).
     */
    var acknowledgeButDiscard: Boolean = false

    /** Scopes reads the way row-level security would; writes carry their own userId. */
    var currentUserId: String? = null

    /** When true, every write reports [RemoteResult.NotAuthenticated] — a token that could not be renewed. */
    var forceNotAuthenticated: Boolean = false

    private val readCounter = AtomicLong(0)
    private val writeClock = AtomicLong(0)

    val readCount: Long get() = readCounter.get()

    private val dayRecords = LinkedHashMap<Pair<String, String>, RemoteDayRecord>()
    private val completions = LinkedHashMap<String, RemoteCompletion>()
    private val profiles = LinkedHashMap<String, RemoteProfile>()
    private val publications = mutableListOf<RemotePublication>()

    fun publish(publication: RemotePublication) {
        publications += publication
    }

    fun rows(): Pair<List<RemoteDayRecord>, List<RemoteCompletion>> =
        dayRecords.values.toList() to completions.values.toList()

    private fun nextUpdatedAt(): String = Instant.ofEpochMilli(writeClock.incrementAndGet()).toString()

    override suspend fun upsertDayRecords(rows: List<RemoteDayRecord>): RemoteResult<Unit> {
        if (unreachable) return RemoteResult.Unreachable
        if (forceNotAuthenticated) return RemoteResult.NotAuthenticated
        val rejected = rows.filter { it.date in rejectIds }
        if (rejected.isNotEmpty()) {
            return RemoteResult.Rejected("rejected", rejected.map { it.date })
        }
        for (row in rows) {
            if (!admitWrite()) return RemoteResult.Unreachable
            if (acknowledgeButDiscard) continue
            val key = row.userId to row.date
            val existing = dayRecords[key]
            val settledVersion = if (existing == null) {
                row.catalogueVersion
            } else {
                minOf(existing.catalogueVersion, row.catalogueVersion)
            }
            dayRecords[key] = row.copy(catalogueVersion = settledVersion, updatedAt = nextUpdatedAt())
        }
        return RemoteResult.Ok(Unit)
    }

    override suspend fun upsertCompletions(rows: List<RemoteCompletion>): RemoteResult<Unit> {
        if (unreachable) return RemoteResult.Unreachable
        if (forceNotAuthenticated) return RemoteResult.NotAuthenticated
        val rejected = rows.filter { it.id in rejectIds }
        if (rejected.isNotEmpty()) {
            return RemoteResult.Rejected("rejected", rejected.map { it.id })
        }
        for (row in rows) {
            if (!admitWrite()) return RemoteResult.Unreachable
            if (acknowledgeButDiscard) continue
            val existing = completions[row.id]
            val merged = if (existing == null) {
                row
            } else {
                existing.copy(reversedAt = existing.reversedAt ?: row.reversedAt)
            }
            completions[row.id] = merged.copy(updatedAt = nextUpdatedAt())
        }
        return RemoteResult.Ok(Unit)
    }

    override suspend fun upsertProfile(row: RemoteProfile): RemoteResult<Unit> {
        if (unreachable) return RemoteResult.Unreachable
        if (forceNotAuthenticated) return RemoteResult.NotAuthenticated
        if (!admitWrite()) return RemoteResult.Unreachable
        if (!acknowledgeButDiscard) profiles[row.id] = row
        return RemoteResult.Ok(Unit)
    }

    override suspend fun changedSince(since: Instant?, limit: Int): RemoteResult<RemoteChanges> {
        if (unreachable) return RemoteResult.Unreachable
        if (forceNotAuthenticated) return RemoteResult.NotAuthenticated
        readCounter.incrementAndGet()
        val userId = currentUserId
        val dr = dayRecords.values
            .filter { it.userId == userId && (since == null || Instant.parse(it.updatedAt!!) > since) }
            .sortedBy { it.updatedAt }
            .take(limit)
        val cr = completions.values
            .filter { it.userId == userId && (since == null || Instant.parse(it.updatedAt!!) > since) }
            .sortedBy { it.updatedAt }
            .take(limit)
        val watermark = (dr.map { it.updatedAt } + cr.map { it.updatedAt })
            .filterNotNull()
            .maxOfOrNull { Instant.parse(it) }
            ?: since
        return RemoteResult.Ok(RemoteChanges(dr, cr, watermark))
    }

    override suspend fun recordsBetween(from: LocalDate, to: LocalDate): RemoteResult<RemoteChanges> {
        if (unreachable) return RemoteResult.Unreachable
        if (forceNotAuthenticated) return RemoteResult.NotAuthenticated
        readCounter.incrementAndGet()
        val userId = currentUserId
        val dr = dayRecords.values
            .filter { it.userId == userId && it.date >= from.toString() && it.date <= to.toString() }
            .sortedByDescending { it.date }
        val cr = completions.values
            .filter { it.userId == userId && it.creditedDate >= from.toString() && it.creditedDate <= to.toString() }
            .sortedByDescending { it.creditedDate }
        return RemoteResult.Ok(RemoteChanges(dr, cr, watermark = null))
    }

    override suspend fun earliestRecordedDate(): RemoteResult<LocalDate?> {
        if (unreachable) return RemoteResult.Unreachable
        if (forceNotAuthenticated) return RemoteResult.NotAuthenticated
        readCounter.incrementAndGet()
        val userId = currentUserId
        val earliest = (dayRecords.values.filter { it.userId == userId }.map { it.date } +
            completions.values.filter { it.userId == userId }.map { it.creditedDate })
            .minOrNull()
        return RemoteResult.Ok(earliest?.let(LocalDate::parse))
    }

    override suspend fun catalogues(knownFormatVersions: Set<Int>): RemoteResult<List<RemotePublication>> {
        if (unreachable) return RemoteResult.Unreachable
        if (forceNotAuthenticated) return RemoteResult.NotAuthenticated
        readCounter.incrementAndGet()
        return RemoteResult.Ok(publications.toList())
    }

    /** Decrements the drop budget; returns false once it is exhausted, then clears it. */
    private fun admitWrite(): Boolean {
        val budget = dropAfter ?: return true
        if (budget <= 0) {
            dropAfter = null
            return false
        }
        dropAfter = budget - 1
        return true
    }
}
