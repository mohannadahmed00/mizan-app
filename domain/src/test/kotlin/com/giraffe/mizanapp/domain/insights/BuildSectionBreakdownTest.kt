package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.PlannedTask
import com.giraffe.mizanapp.domain.day.buildDayPlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildSectionBreakdown` - every section's own completion rate, listed in
 * catalogue order, never sorted by rate and never singling out a lowest
 * section (Clarification Q2, FR-003, FR-010).
 */
class BuildSectionBreakdownTest {

    private val catalogue = DayFixtures.catalogue

    private var idCounter = 0
    private fun newId(): String = "id-${idCounter++}"

    private fun planFor(date: LocalDate): DayPlan =
        buildDayPlan(catalogue, version = 1, date = date, origin = PlanOrigin.OPENED, newId = ::newId)

    private fun completionsFor(plan: DayPlan, task: PlannedTask, times: Int): List<Completion> =
        (0 until times).map { i ->
            Completion(
                id = newId(),
                dayPlanId = plan.id,
                taskSlug = task.taskSlug,
                creditedDate = plan.date,
                pointsAwarded = task.points,
                recordedAt = plan.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(i.toLong()),
            )
        }

    @Test
    fun `each section reports its own completed and available occurrence counts`() {
        val date = LocalDate.parse("2026-08-05")
        val plan = planFor(date)
        val fajrTasks = plan.plannedTasks.filter { it.sectionId == "fajr" }
        val adhkarTask = plan.plannedTasks.first { it.sectionId == "adhkar" }

        val completions = fajrTasks.flatMap { completionsFor(plan, it, it.maxOccurrencesPerDay) }

        val breakdown = buildSectionBreakdown(listOf(plan), completions)

        val fajr = breakdown.single { it.sectionId == "fajr" }
        val adhkar = breakdown.single { it.sectionId == "adhkar" }
        assertEquals(fajr.available, fajr.completed)
        assertEquals(1f, fajr.rate, 0.0001f)
        assertEquals(0, adhkar.completed)
        assertTrue(adhkar.available > 0)
    }

    @Test
    fun `output order matches catalogue section order, never sorted by rate`() {
        val date = LocalDate.parse("2026-08-05")
        val plan = planFor(date)

        val breakdown = buildSectionBreakdown(listOf(plan), emptyList())

        val expectedOrder = plan.plannedTasks.map { it.sectionId }.distinct().sortedBy { sectionId ->
            plan.plannedTasks.first { it.sectionId == sectionId }.sectionOrder
        }
        assertEquals(expectedOrder, breakdown.map { it.sectionId })
    }

    @Test
    fun `a section relabelled between two plans resolves to the most recent label`() {
        val earlyDate = LocalDate.parse("2026-08-01")
        val laterDate = LocalDate.parse("2026-08-08")
        val earlyPlan = planFor(earlyDate)
        val laterPlanOriginal = planFor(laterDate)
        val relabelledTasks = laterPlanOriginal.plannedTasks.map { task ->
            if (task.sectionId == "fajr") task.copy(sectionLabel = "Fajr (renamed)") else task
        }
        val laterPlan = laterPlanOriginal.copy(plannedTasks = relabelledTasks)

        val breakdown = buildSectionBreakdown(listOf(earlyPlan, laterPlan), emptyList())

        assertEquals("Fajr (renamed)", breakdown.single { it.sectionId == "fajr" }.sectionLabel)
    }

    @Test
    fun `a section with zero occurrences anywhere in the range does not appear`() {
        val breakdown = buildSectionBreakdown(emptyList(), emptyList())

        assertTrue(breakdown.isEmpty())
    }

    @Test
    fun `a single elapsed day still computes correct rates with no crash`() {
        val date = LocalDate.parse("2026-08-05")
        val plan = planFor(date)
        val firstTask = plan.plannedTasks.first()
        val completions = completionsFor(plan, firstTask, 1)

        val breakdown = buildSectionBreakdown(listOf(plan), completions)

        assertTrue(breakdown.isNotEmpty())
        breakdown.forEach { assertTrue(it.rate in 0f..1f) }
    }
}
