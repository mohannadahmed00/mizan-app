package com.giraffe.mizanapp.domain.day

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.time.HijriLabel
import java.time.LocalDate

/**
 * Materialises the frozen record of a date.
 *
 * Pure by design: no clock, no I/O, and ids supplied by [newId] so tests are
 * deterministic. Keeping construction pure is what makes the whole of Principle
 * III testable without a database — "does a catalogue change alter a recorded
 * day" runs as a unit test in milliseconds.
 *
 * The Hijri label is computed here rather than passed in, because it is derived
 * from the date and nothing else (research.md R4). A plan therefore always has
 * one, from the moment it exists.
 */
fun buildDayPlan(
    catalogue: Catalogue,
    version: Int,
    date: LocalDate,
    newId: () -> String,
): DayPlan {
    val planId = newId()
    val sectionsById = catalogue.sections.associateBy { it.id }
    val definitionsBySlug = catalogue.tasks.associateBy { it.slug }

    val planned = resolveApplicableTasks(catalogue, version, date).mapNotNull { taskVersion ->
        val definition = definitionsBySlug[taskVersion.taskSlug] ?: return@mapNotNull null
        val section = sectionsById[definition.sectionId] ?: return@mapNotNull null

        PlannedTask(
            id = newId(),
            dayPlanId = planId,
            taskSlug = definition.slug,
            sectionId = section.id,
            sectionLabel = section.label,
            sectionOrder = section.order,
            displayPosition = definition.displayPosition,
            label = definition.label,
            points = taskVersion.points,
            maxOccurrencesPerDay = taskVersion.maxOccurrencesPerDay,
        )
    }

    return DayPlan(
        id = planId,
        date = date,
        catalogueVersion = version,
        hijriLabel = HijriLabel.forDate(date),
        availablePoints = planned.sumOf { it.availablePoints },
        plannedTasks = planned,
    )
}
