package com.giraffe.mizanapp.catalogue.model

import java.time.DayOfWeek
import kotlinx.serialization.Serializable

/** Root aggregate. What a catalogue file parses into. */
@Serializable
data class Catalogue(
    val versions: List<CatalogueVersion>,
    val sections: List<Section>,
    val tasks: List<TaskDefinition>,
    val taskVersions: List<TaskVersion>,
) {

    /**
     * Points available on [day], summed over every task whose schedule matches.
     *
     * availablePoints(day) = sum of (points x maxOccurrencesPerDay)
     */
    fun availablePointsOn(day: DayOfWeek): Int =
        taskVersions.filter { it.scheduleRule.matches(day) }.sumOf { it.availablePoints }

    /** Points available across a Saturday-to-Friday week. */
    fun weeklyAvailablePoints(): Int =
        DayOfWeek.entries.sumOf { availablePointsOn(it) }

    /** Points a section contributes on [day]. */
    fun sectionPointsOn(sectionId: String, day: DayOfWeek): Int {
        val slugsInSection = tasks.filter { it.sectionId == sectionId }.map { it.slug }.toSet()
        return taskVersions
            .filter { it.taskSlug in slugsInSection && it.scheduleRule.matches(day) }
            .sumOf { it.availablePoints }
    }
}
