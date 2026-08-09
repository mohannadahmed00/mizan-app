package com.giraffe.mizanapp.today

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import com.giraffe.mizanapp.domain.catalogue.parseCatalogue
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
) : DayPlanRepository {

    private val plans = mutableMapOf<LocalDate, MutableStateFlow<DayPlan?>>()
    private var counter = 0

    private fun flowFor(date: LocalDate) = plans.getOrPut(date) { MutableStateFlow(null) }

    override suspend fun planFor(date: LocalDate): DayPlan? = flowFor(date).value

    override suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome {
        flowFor(date).value?.let { return EnsureOutcome.AlreadyExists(it) }
        val plan = buildDayPlan(catalogue, 1, date) { "id-${counter++}" }
        flowFor(date).value = plan
        return EnsureOutcome.Created(plan)
    }

    override fun observePlan(date: LocalDate): Flow<DayPlan?> = flowFor(date)
}

class FakeCompletionRepository(
    private val dayPlans: FakeDayPlanRepository,
    private val policy: DayWritePolicy,
    private val time: TimeProvider,
) : CompletionRepository {

    private val rows = MutableStateFlow<List<Completion>>(emptyList())
    private var counter = 0

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
    override fun today(): LocalDate = com.giraffe.mizanapp.domain.time.DayBoundary.dateAt(instant, zone)
    override fun zone(): java.time.ZoneId = zone
    fun setDate(date: LocalDate) { instant = date.atTime(9, 0).atZone(zone).toInstant() }
}
