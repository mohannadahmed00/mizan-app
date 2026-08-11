package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.catalogue.Fixtures
import com.giraffe.mizanapp.domain.catalogue.parseCatalogue
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Shared helpers for the day-layer tests. Deterministic ids, no clock. */
object DayFixtures {

    val catalogue = parseCatalogue(Fixtures.good()).getOrThrow()

    /** 2026-03-14 is a Saturday. */
    fun dateFor(day: DayOfWeek): LocalDate {
        var date = LocalDate.of(2026, 3, 14)
        while (date.dayOfWeek != day) date = date.plusDays(1)
        return date
    }

    fun sequentialIds(): () -> String {
        var n = 0
        return { "id-${n++}" }
    }

    fun planFor(day: DayOfWeek = DayOfWeek.SATURDAY, origin: PlanOrigin = PlanOrigin.OPENED): DayPlan =
        buildDayPlan(catalogue, version = 1, date = dateFor(day), origin = origin, newId = sequentialIds())

    fun completion(
        plan: DayPlan,
        task: PlannedTask,
        index: Int,
        reversed: Boolean = false,
    ): Completion = Completion(
        id = "c-${task.taskSlug}-$index",
        dayPlanId = plan.id,
        taskSlug = task.taskSlug,
        creditedDate = plan.date,
        pointsAwarded = task.points,
        recordedAt = Instant.parse("2026-03-14T06:00:00Z").plusSeconds(index.toLong()),
        reversedAt = if (reversed) Instant.parse("2026-03-14T07:00:00Z") else null,
    )

    fun DayPlan.task(slug: String): PlannedTask = plannedTasks.first { it.taskSlug == slug }
}
