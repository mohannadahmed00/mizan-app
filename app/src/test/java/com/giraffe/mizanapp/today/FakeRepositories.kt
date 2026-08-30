package com.giraffe.mizanapp.today

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import com.giraffe.mizanapp.domain.catalogue.parseCatalogue
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.FallbackReason
import com.giraffe.mizanapp.domain.time.LocationRequestOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** The real seed, read from the domain module's resources. */
fun loadSeedCatalogue(): Catalogue {
    val raw = checkNotNull(
        object {}.javaClass.getResourceAsStream("/catalogue/valid-catalogue.json"),
    ) { "seed resource missing from the classpath" }.bufferedReader().use { it.readText() }
    return parseCatalogue(raw).getOrThrow()
}

class FakeCatalogueRepository(
    private val catalogue: Catalogue = loadSeedCatalogue(),
    private val failWith: List<CatalogueDefect>? = null,
) : CatalogueRepository {
    override suspend fun seedIfNeeded(): SeedOutcome =
        failWith?.let { SeedOutcome.Failed(it) } ?: SeedOutcome.Seeded(1, catalogue.tasks.size)

    override suspend fun currentVersion(): Int = 1
    override suspend fun versionEffectiveOn(date: LocalDate): Int = 1
    override suspend fun catalogueAt(version: Int): Catalogue = catalogue
}

class FakeDayPlanRepository(
    private val catalogue: Catalogue = loadSeedCatalogue(),
    private val time: TimeProvider? = null,
    private val failDates: Set<LocalDate> = emptySet(),
) : DayPlanRepository {

    private val plans = mutableMapOf<LocalDate, MutableStateFlow<DayPlan?>>()
    private var counter = 0
    var creationCount = 0
        private set

    private fun flowFor(date: LocalDate) = plans.getOrPut(date) { MutableStateFlow(null) }

    override suspend fun planFor(date: LocalDate): DayPlan? = flowFor(date).value

    override suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome {
        flowFor(date).value?.let { return EnsureOutcome.AlreadyExists(it) }
        if (date in failDates) error("simulated storage failure for $date")
        // Mirrors RoomDayPlanRepository: the caller never chooses the origin.
        val origin = if (time == null || date == time.today()) PlanOrigin.OPENED else PlanOrigin.BACKFILLED
        val plan = buildDayPlan(catalogue, 1, date, origin) { "id-${counter++}" }
        flowFor(date).value = plan
        creationCount++
        return EnsureOutcome.Created(plan)
    }

    override fun observePlan(date: LocalDate): Flow<DayPlan?> = flowFor(date)

    override suspend fun plansBetween(start: LocalDate, end: LocalDate): List<DayPlan> =
        plans.entries
            .filter { (date, flow) -> !date.isBefore(start) && !date.isAfter(end) && flow.value != null }
            .sortedBy { it.key }
            .map { it.value.value!! }

    override suspend fun earliestPlanDate(): LocalDate? =
        plans.entries.filter { it.value.value != null }.minOfOrNull { it.key }

    /** Test-only seam: seeds a plan directly, bypassing the origin rule. */
    fun seedPlan(plan: DayPlan) {
        flowFor(plan.date).value = plan
    }
}

class FakeCompletionRepository(
    private val dayPlans: FakeDayPlanRepository,
    private val policy: DayWritePolicy,
    private val time: TimeProvider,
) : CompletionRepository {

    private val rows = MutableStateFlow<List<Completion>>(emptyList())
    private var counter = 0

    /** Test-only seam: injects a completion directly, bypassing [DayWritePolicy]. */
    fun seed(vararg completions: Completion) {
        rows.value = rows.value + completions
    }

    override suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome {
        if (!policy.isWritable(date)) return RecordOutcome.NotWritable(policy.refusalReason(date))
        val plan = dayPlans.planFor(date) ?: return RecordOutcome.NotWritable("no plan")
        val task = plan.plannedTasks.firstOrNull { it.taskSlug == taskSlug }
            ?: return RecordOutcome.NotWritable("not applicable")

        val live = rows.value.count { it.taskSlug == taskSlug && it.creditedDate == date && it.isLive }
        if (live >= task.maxOccurrencesPerDay) return RecordOutcome.AtLimit(task.maxOccurrencesPerDay)

        val completion = Completion(
            id = "c-${counter++}",
            dayPlanId = plan.id,
            taskSlug = taskSlug,
            creditedDate = date,
            pointsAwarded = task.points,
            recordedAt = time.now().plusSeconds(counter.toLong()),
        )
        rows.value = rows.value + completion
        return RecordOutcome.Recorded(completion, live + 1)
    }

    override suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome {
        if (!policy.isWritable(date)) return UndoOutcome.NotWritable(policy.refusalReason(date))
        val latest = rows.value
            .filter { it.taskSlug == taskSlug && it.creditedDate == date && it.isLive }
            .maxByOrNull { it.recordedAt } ?: return UndoOutcome.NothingToUndo

        val reversed = latest.copy(reversedAt = time.now())
        rows.value = rows.value.map { if (it.id == latest.id) reversed else it }
        val live = rows.value.count { it.taskSlug == taskSlug && it.creditedDate == date && it.isLive }
        return UndoOutcome.Reversed(reversed, live)
    }

    override fun observeCompletions(date: LocalDate): Flow<List<Completion>> =
        rows.map { all -> all.filter { it.creditedDate == date && it.isLive } }

    override suspend fun liveCount(date: LocalDate, taskSlug: String): Int =
        rows.value.count { it.taskSlug == taskSlug && it.creditedDate == date && it.isLive }

    override suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion> =
        rows.value
            .filter { it.isLive && !it.creditedDate.isBefore(start) && !it.creditedDate.isAfter(end) }
            .sortedWith(compareBy({ it.creditedDate }, { it.recordedAt }))

    override fun observeConsistencyDates(): Flow<List<LocalDate>> =
        rows.map { all -> all.filter { it.isLive }.map { it.creditedDate }.distinct().sorted() }
}

/**
 * A clock these tests control. `:domain`'s own FakeTimeProvider lives in its
 * test source set and is not visible here, so this is the app-side twin.
 */
class FakeClock(
    private var instant: Instant = Instant.parse("2026-03-14T09:00:00Z"),
    private val zone: java.time.ZoneId = java.time.ZoneId.of("Africa/Cairo"),
) : TimeProvider {
    override fun now(): Instant = instant
    override fun today(): LocalDate = com.giraffe.mizanapp.domain.time.DayBoundary.dateAt(instant, zone, null)
    override fun zone(): java.time.ZoneId = zone
    fun setDate(date: LocalDate) { instant = date.atTime(9, 0).atZone(zone).toInstant() }
    fun advanceBy(duration: java.time.Duration) { instant = instant.plus(duration) }
}

/**
 * Test double for [BoundaryStatus]. Duplicated in every consuming source set (see
 * contracts/prayer-times-provider.md's precedent for test doubles); keep behaviour identical.
 */
class FakeBoundaryStatus(
    regime: BoundaryRegime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION),
    resolvedDate: LocalDate = LocalDate.parse("2026-03-14"),
) : BoundaryStatus {
    private val stateFlow = MutableStateFlow(
        BoundaryState(
            regime = regime,
            coordinates = null,
            zoneIdWhenObtained = null,
            resolvedDate = resolvedDate,
            expiresAt = Instant.parse("2026-03-15T00:00:00Z"),
            lastResolvedDate = null,
            lastResolvedRegime = null,
        ),
    )
    private var shown = false
    var requestLocationOutcome: LocationRequestOutcome = LocationRequestOutcome.Obtained
    var refreshCallCount = 0
        private set

    override fun current(): BoundaryState = stateFlow.value
    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()
    override suspend fun refresh(now: Instant, zone: ZoneId) { refreshCallCount++ }
    override suspend fun requestLocation(): LocationRequestOutcome = requestLocationOutcome
    override suspend fun eraseLocation() {
        stateFlow.value = stateFlow.value.copy(
            regime = BoundaryRegime.Fallback(FallbackReason.ERASED),
            coordinates = null,
            zoneIdWhenObtained = null,
        )
    }
    override fun promptShown(): Boolean = shown
    override suspend fun markPromptShown() { shown = true }

    fun setRegime(newRegime: BoundaryRegime) {
        stateFlow.value = stateFlow.value.copy(regime = newRegime)
    }

    fun setPromptShown(value: Boolean) { shown = value }
}

/** Complete by default — matches the signed-out / backfill-finished product unchanged. */
class FakeRecordCoverageRepository(private var value: RecordCoverage = RecordCoverage.completeFrom(null)) :
    RecordCoverageRepository {
    fun setCoverage(coverage: RecordCoverage) {
        value = coverage
    }
    override fun observeCoverage(): Flow<RecordCoverage> = MutableStateFlow(value)
    override suspend fun coverage(): RecordCoverage = value
}
