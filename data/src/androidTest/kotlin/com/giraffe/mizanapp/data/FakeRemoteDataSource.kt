package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteChanges
import com.giraffe.mizanapp.data.sync.RemoteDataSource
import com.giraffe.mizanapp.data.sync.RemoteResult
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    private val rankingPages = LinkedHashMap<Pair<PeriodKind, String>, FakeRankingSnapshot>()
    private val honorBoards = LinkedHashMap<Pair<PeriodKind, String>, RemoteHonorBoard>()
    private val closedPeriods = mutableSetOf<Pair<PeriodKind, String>>()
    private val participation = LinkedHashMap<String, RemoteParticipation>()
    private val regionsByZone = LinkedHashMap<String, FakeRegion>().apply {
        put("Asia/Riyadh", FakeRegion("arabia-riyadh", "Arabia (Riyadh)", "Asia/Riyadh"))
        put("Africa/Cairo", FakeRegion("egypt-cairo", "Egypt (Cairo)", "Africa/Cairo"))
        put("Asia/Karachi", FakeRegion("pakistan-karachi", "Pakistan (Karachi)", "Asia/Karachi"))
        put("Pacific/Honolulu", FakeRegion("hawaii-honolulu", "Hawaii (Honolulu)", "Pacific/Honolulu"))
        put("UTC", FakeRegion("fallback-utc", "UTC", "UTC"))
    }

    val reportedZones: MutableList<String> = mutableListOf()
    var materializedPeriodCount: Int = 0
        private set

    private val daysEngagedStore = HashMap<Triple<PeriodKind, String, String>, Int>()

    /**
     * The fake's mirror of `recompute_open_periods()`'s fold (T062). Never
     * auto-closes a period — that stays test-driven via [markPeriodClosed],
     * since only the real SQL function has a wall clock to check a boundary
     * against (Rule B: every already-closed key here is skipped, never
     * touched).
     */
    fun recomputeOpenPeriods() {
        val regionIds = regionsByZone.values.map { it.id }.distinct()
        for (kind in PeriodKind.entries) {
            for (regionId in regionIds) {
                val key = kind to regionId
                if (key in closedPeriods) continue
                val region = regionsByZone.values.first { it.id == regionId }
                val zone = ZoneId.of(region.zone)
                val participantIds = participation.filterValues { it.optedIn && it.regionId == regionId }.keys
                if (participantIds.isEmpty()) continue

                val liveByUser = participantIds.associateWith { uid ->
                    completions.values.filter { it.userId == uid && it.reversedAt == null }
                }
                val latestStart = liveByUser.values.flatten()
                    .map { periodFor(kind, LocalDate.parse(it.creditedDate), zone, RegionId(regionId)).start }
                    .maxOrNull() ?: continue
                val period = periodFor(kind, latestStart, zone, RegionId(regionId))

                val summaries = liveByUser.mapNotNull { (uid, rows) ->
                    val inPeriod = rows.filter { row ->
                        val date = LocalDate.parse(row.creditedDate)
                        !date.isBefore(period.start) && !date.isAfter(period.endInclusive)
                    }
                    if (inPeriod.isEmpty()) return@mapNotNull null
                    FoldSummary(
                        userId = uid,
                        points = inPeriod.sumOf { it.pointsAwarded },
                        daysEngaged = inPeriod.map { it.creditedDate }.distinct().size,
                        // FR-022's tie-break: who reached the total earliest.
                        lastRecordedAt = inPeriod.maxOf { Instant.parse(it.recordedAt) },
                    )
                }
                // Position = rank over (points desc, lastRecordedAt asc) — FR-022a bounds
                // a forged clock to reordering this tie alone.
                val ordered = summaries.sortedWith(compareByDescending<FoldSummary> { it.points }.thenBy { it.lastRecordedAt })

                ordered.forEach { daysEngagedStore[Triple(kind, regionId, it.userId)] = it.daysEngaged }
                rankingPages[key] = FakeRankingSnapshot(
                    periodStart = period.start.toString(),
                    periodEndInclusive = period.endInclusive.toString(),
                    regionDisplayName = region.displayName,
                    regionZone = region.zone,
                    entries = ordered.mapIndexed { index, s ->
                        RemoteRankingEntry(
                            userId = s.userId,
                            displayName = profiles[s.userId]?.displayName ?: "Participant",
                            points = s.points,
                            position = index + 1,
                        )
                    },
                )
                materializedPeriodCount++
            }
        }
    }

    private data class FoldSummary(val userId: String, val points: Int, val daysEngaged: Int, val lastRecordedAt: Instant)

    /**
     * Test-only mirror of `leaderboard_entries.days_engaged` (T057, T069/T070) —
     * never exposed through [RemoteDataSource] or any client-facing DTO (Rule D).
     */
    fun daysEngagedFor(kind: PeriodKind, regionId: String, userId: String): Int =
        daysEngagedStore[Triple(kind, regionId, userId)] ?: 0

    /** Adds or replaces one zone mapping without giving production code a region-selection path. */
    fun seedRegion(zoneId: String, regionId: String, displayName: String) {
        regionsByZone[zoneId] = FakeRegion(regionId, displayName, zoneId)
    }

    /**
     * Seeds one server-ranked period. Marking the key closed first makes this
     * throw, so tests cannot accidentally mutate immutable historical output.
     */
    fun seedEntries(
        kind: PeriodKind,
        regionId: String,
        entries: List<RemoteRankingEntry>,
        regionDisplayName: String = regionId,
        regionZone: String = "UTC",
        periodStart: String = "2026-08-29",
        periodEndInclusive: String = periodStart,
    ) {
        val key = kind to regionId
        check(key !in closedPeriods) { "A closed period cannot be changed" }
        rankingPages[key] = FakeRankingSnapshot(
            periodStart = periodStart,
            periodEndInclusive = periodEndInclusive,
            regionDisplayName = regionDisplayName,
            regionZone = regionZone,
            entries = entries.toList(),
        )
    }

    fun seedHonorBoard(
        kind: PeriodKind,
        regionId: String,
        members: List<RemoteHonorBoardMember>,
        regionDisplayName: String = regionId,
        regionZone: String = "UTC",
        periodStart: String = "2026-08-29",
        periodEndInclusive: String = periodStart,
    ) {
        honorBoards[kind to regionId] = RemoteHonorBoard(
            periodKind = kind.name,
            periodStart = periodStart,
            periodEndInclusive = periodEndInclusive,
            regionId = regionId,
            regionDisplayName = regionDisplayName,
            regionZone = regionZone,
            members = members.toList(),
            viewerQualified = members.any(RemoteHonorBoardMember::isViewer),
        )
    }

    fun markPeriodClosed(kind: PeriodKind, regionId: String) {
        closedPeriods += kind to regionId
    }

    /** Materialises a newly opened period from consent as the server fold does. */
    fun seedNewOpenPeriod(
        kind: PeriodKind,
        regionId: String,
        candidateEntries: List<RemoteRankingEntry>,
        periodStart: String,
    ) {
        seedEntries(
            kind = kind,
            regionId = regionId,
            entries = candidateEntries.filter { participation[it.userId]?.optedIn == true },
            periodStart = periodStart,
        )
    }

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

    override suspend fun rankingPage(kind: PeriodKind, cursor: Int?): RemoteResult<RemoteRankingPage> {
        unavailableOrExpired()?.let { return it }
        readCounter.incrementAndGet()
        val region = currentParticipation() ?: return RemoteResult.Rejected("not participating", emptyList())
        val regionId = region.regionId ?: return RemoteResult.Rejected("region unavailable", emptyList())
        val snapshot = rankingPages[kind to regionId] ?: FakeRankingSnapshot(
            periodStart = "2026-08-29",
            periodEndInclusive = "2026-08-29",
            regionDisplayName = region.regionDisplayName ?: regionId,
            regionZone = region.regionZone ?: "UTC",
            entries = emptyList(),
        )
        val remaining = snapshot.entries.dropWhile { entry -> cursor != null && entry.position <= cursor }
        val entries = remaining.take(PAGE_SIZE)
        return RemoteResult.Ok(
            RemoteRankingPage(
                periodKind = kind.name,
                periodStart = snapshot.periodStart,
                periodEndInclusive = snapshot.periodEndInclusive,
                regionId = regionId,
                regionDisplayName = snapshot.regionDisplayName,
                regionZone = snapshot.regionZone,
                entries = entries,
                hasMore = remaining.size > entries.size,
                isFinal = kind to regionId in closedPeriods,
            ),
        )
    }

    override suspend fun ownRank(kind: PeriodKind): RemoteResult<RemoteOwnRank> {
        unavailableOrExpired()?.let { return it }
        readCounter.incrementAndGet()
        val userId = currentUserId ?: return RemoteResult.NotAuthenticated
        val regionId = currentParticipation()?.regionId
            ?: return RemoteResult.Rejected("not participating", emptyList())
        val entries = rankingPages[kind to regionId]?.entries.orEmpty()
        val index = entries.indexOfFirst { it.userId == userId }
        val ownEntry = entries.getOrNull(index)
        val neighbours = if (index < 0) {
            emptyList()
        } else {
            entries.subList(maxOf(0, index - 1), minOf(entries.size, index + 2))
                .filterNot { it.userId == userId }
        }
        return RemoteResult.Ok(RemoteOwnRank(ownEntry, neighbours, entries.size))
    }

    override suspend fun honorBoard(kind: PeriodKind): RemoteResult<RemoteHonorBoard> {
        unavailableOrExpired()?.let { return it }
        readCounter.incrementAndGet()
        if (kind == PeriodKind.DAILY) return RemoteResult.Rejected("daily has no Honor Board", emptyList())
        val region = currentParticipation() ?: return RemoteResult.Rejected("not participating", emptyList())
        val regionId = region.regionId ?: return RemoteResult.Rejected("region unavailable", emptyList())
        return RemoteResult.Ok(
            honorBoards[kind to regionId] ?: RemoteHonorBoard(
                periodKind = kind.name,
                periodStart = "2026-08-29",
                periodEndInclusive = "2026-08-29",
                regionId = regionId,
                regionDisplayName = region.regionDisplayName ?: regionId,
                regionZone = region.regionZone ?: "UTC",
                members = emptyList(),
                viewerQualified = false,
            ),
        )
    }

    override suspend fun setParticipation(optedIn: Boolean): RemoteResult<RemoteParticipation> {
        unavailableOrExpired()?.let { return it }
        val userId = currentUserId ?: return RemoteResult.NotAuthenticated
        val previous = participation[userId] ?: participationFor(regionsByZone.getValue("UTC"), false)
        val updated = previous.copy(optedIn = optedIn)
        participation[userId] = updated
        if (!optedIn) {
            rankingPages.forEach { (key, snapshot) ->
                if (key !in closedPeriods) {
                    snapshot.entries = snapshot.entries.filterNot { it.userId == userId }
                }
            }
        }
        return RemoteResult.Ok(updated)
    }

    override suspend fun reportZone(zoneId: String): RemoteResult<RemoteParticipation> {
        unavailableOrExpired()?.let { return it }
        val userId = currentUserId ?: return RemoteResult.NotAuthenticated
        reportedZones += zoneId
        val assigned = regionsByZone[zoneId] ?: regionsByZone.getValue("UTC")
        val updated = participationFor(assigned, participation[userId]?.optedIn ?: false)
        participation[userId] = updated
        return RemoteResult.Ok(updated)
    }

    private fun currentParticipation(): RemoteParticipation? = currentUserId
        ?.let(participation::get)
        ?.takeIf(RemoteParticipation::optedIn)

    private fun participationFor(region: FakeRegion, optedIn: Boolean) = RemoteParticipation(
        optedIn = optedIn,
        regionId = region.id,
        regionDisplayName = region.displayName,
        regionZone = region.zone,
    )

    private fun unavailableOrExpired(): RemoteResult<Nothing>? = when {
        unreachable -> RemoteResult.Unreachable
        forceNotAuthenticated -> RemoteResult.NotAuthenticated
        else -> null
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

    private data class FakeRegion(
        val id: String,
        val displayName: String,
        val zone: String,
    )

    private data class FakeRankingSnapshot(
        val periodStart: String,
        val periodEndInclusive: String,
        val regionDisplayName: String,
        val regionZone: String,
        var entries: List<RemoteRankingEntry>,
    )

    private companion object {
        const val PAGE_SIZE = 50
    }
}
