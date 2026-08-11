package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** :domain-local fakes for `GetWeekSummary` tests — :domain cannot depend on :app's. */

class FakeWeekCatalogueRepository(
    private val catalogue: Catalogue = DayFixtures.catalogue,
    private val available: Boolean = true,
) : CatalogueRepository {
    override suspend fun seedIfNeeded(): SeedOutcome = SeedOutcome.Seeded(1, catalogue.tasks.size)
    override suspend fun currentVersion(): Int? = if (available) 1 else null
    override suspend fun versionEffectiveOn(date: LocalDate): Int? = if (available) 1 else null
    override suspend fun catalogueAt(version: Int): Catalogue? = if (available) catalogue else null
}

/**
 * A fake mirroring `RoomDayPlanRepository`'s origin rule: a plan for the
 * current date is `OPENED`, anything else is `BACKFILLED`. [failDates]
 * simulates a storage failure (thrown exception) for specific dates, so
 * `GetWeekSummary`'s failure handling can be exercised without a real
 * database.
 */
class FakeWeekDayPlanRepository(
    private val catalogue: Catalogue = DayFixtures.catalogue,
    private val time: TimeProvider,
    private val failDates: Set<LocalDate> = emptySet(),
) : DayPlanRepository {

    private val plans = mutableMapOf<LocalDate, DayPlan>()
    private var counter = 0
    var creationCount = 0
        private set

    override suspend fun planFor(date: LocalDate): DayPlan? = plans[date]

    override suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome {
        plans[date]?.let { return EnsureOutcome.AlreadyExists(it) }
        if (date in failDates) error("simulated storage failure for $date")

        val origin = if (date == time.today()) PlanOrigin.OPENED else PlanOrigin.BACKFILLED
        val plan = buildDayPlan(catalogue, version = 1, date = date, origin = origin) { "id-${counter++}" }
        plans[date] = plan
        creationCount++
        return EnsureOutcome.Created(plan)
    }

    override fun observePlan(date: LocalDate): Flow<DayPlan?> = MutableStateFlow(plans[date])

    override suspend fun plansBetween(start: LocalDate, end: LocalDate): List<DayPlan> =
        plans.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.sortedBy { it.date }

    override suspend fun earliestPlanDate(): LocalDate? = plans.keys.minOrNull()

    /** Test-only seam: seeds a plan directly, bypassing the origin rule. */
    fun seedPlan(plan: DayPlan) {
        plans[plan.date] = plan
    }
}

class FakeWeekCompletionRepository : CompletionRepository {
    private val rows = mutableListOf<Completion>()

    /** Test-only seam: injects completions directly, bypassing `record`. */
    fun seed(vararg completions: Completion) {
        rows += completions
    }

    override suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome =
        throw UnsupportedOperationException("not needed for GetWeekSummary tests")

    override suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome =
        throw UnsupportedOperationException("not needed for GetWeekSummary tests")

    override fun observeCompletions(date: LocalDate): Flow<List<Completion>> =
        MutableStateFlow(rows.filter { it.creditedDate == date && it.isLive })

    override suspend fun liveCount(date: LocalDate, taskSlug: String): Int =
        rows.count { it.creditedDate == date && it.taskSlug == taskSlug && it.isLive }

    override suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion> =
        rows.filter { it.isLive && !it.creditedDate.isBefore(start) && !it.creditedDate.isAfter(end) }
}
