package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.mapper.toDomain
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Day plans, written once.
 *
 * An existing plan is returned untouched — never rebuilt, never reconciled
 * against the current catalogue. That is what makes a recorded day report the
 * same figures forever (Principle III, FR-007).
 */
class RoomDayPlanRepository(
    private val database: MizanDatabase,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : DayPlanRepository {

    override suspend fun planFor(date: LocalDate): DayPlan? =
        database.dayPlanDao().planByDate(date.toString())?.toDomain()

    override suspend fun ensurePlanFor(date: LocalDate): EnsureOutcome {
        planFor(date)?.let { return EnsureOutcome.AlreadyExists(it) }

        val version = catalogue.versionEffectiveOn(date)
            ?: return EnsureOutcome.NoCatalogue(date)
        val content = catalogue.catalogueAt(version)
            ?: return EnsureOutcome.NoCatalogue(date)

        val plan = buildDayPlan(
            catalogue = content,
            version = version,
            date = date,
            newId = newId,
        )

        val now = time.now().toEpochMilli()
        database.dayPlanDao().insertPlanWithTasks(
            plan = plan.toEntity(now),
            tasks = plan.plannedTasks.map { it.toEntity(now) },
        )

        return EnsureOutcome.Created(plan)
    }

    override fun observePlan(date: LocalDate): Flow<DayPlan?> =
        database.dayPlanDao().observePlanByDate(date.toString()).map { it?.toDomain() }
}
