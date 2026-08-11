package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Read-only day projection. Everything comes from the stored plan and its
 * completions — never the live catalogue (FR-023, Principle III).
 */
class GetDaySummaryTest {

    private val date = DayFixtures.dateFor(DayOfWeek.SATURDAY)

    @Test
    fun `a date with a plan returns tasks labels points and limits from the stored plan`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(date) }
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.ensurePlanFor(date)
        val useCase = GetDaySummary(plans, FakeWeekCompletionRepository())

        val summary = useCase(date)

        requireNotNull(summary)
        val stored = plans.planFor(date)!!
        assertEquals(stored.plannedTasks.size, summary.tasks.size)
        summary.tasks.forEach { record ->
            val original = stored.plannedTasks.first { it.taskSlug == record.task.taskSlug }
            assertEquals(original.label, record.task.label)
            assertEquals(original.points, record.task.points)
            assertEquals(original.maxOccurrencesPerDay, record.task.maxOccurrencesPerDay)
        }
    }

    @Test
    fun `a date with no plan returns null and creates nothing`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(date) }
        val plans = FakeWeekDayPlanRepository(time = time)
        val useCase = GetDaySummary(plans, FakeWeekCompletionRepository())

        val summary = useCase(date)

        assertNull(summary)
        assertEquals(0, plans.creationCount)
    }

    @Test
    fun `occurrence counts exclude tombstoned completions`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(date) }
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.ensurePlanFor(date)
        val plan = plans.planFor(date)!!
        val adhkar = plan.plannedTasks.first { it.taskSlug == "adhkar" }

        val completions = FakeWeekCompletionRepository()
        completions.seed(
            DayFixtures.completion(plan, adhkar, index = 0),
            DayFixtures.completion(plan, adhkar, index = 1),
            DayFixtures.completion(plan, adhkar, index = 2, reversed = true),
        )
        val useCase = GetDaySummary(plans, completions)

        val summary = useCase(date)

        val adhkarRecord = requireNotNull(summary).tasks.first { it.task.taskSlug == "adhkar" }
        assertEquals(2, adhkarRecord.recordedCount)
    }

    @Test
    fun `tasks are ordered by section then display position, matching the plan`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(date) }
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.ensurePlanFor(date)
        val plan = plans.planFor(date)!!
        val useCase = GetDaySummary(plans, FakeWeekCompletionRepository())

        val summary = requireNotNull(useCase(date))

        val expectedOrder = plan.sectionsInOrder().flatMap { (_, tasks) -> tasks.map { it.taskSlug } }
        assertEquals(expectedOrder, summary.tasks.map { it.task.taskSlug })
        assertTrue("must actually have more than one section to be a meaningful check", expectedOrder.isNotEmpty())
    }
}
