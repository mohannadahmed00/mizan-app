package com.giraffe.mizanapp.catalogue.model

import java.time.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which dates a task applies to.
 *
 * Closed for now, open to extension: a `DateAnchored` variant is reserved for
 * Hijri occasions (Ramadan, Ashura) and deliberately NOT implemented in this
 * increment. Adding it later must not redefine [EveryDay] or [DaysOfWeek].
 */
@Serializable
sealed interface ScheduleRule {

    /** True when this rule applies on [day]. */
    fun matches(day: DayOfWeek): Boolean

    /** The set of weekdays this rule applies on. Empty means unreachable. */
    fun matchingDays(): Set<DayOfWeek>

    @Serializable
    @SerialName("everyDay")
    data object EveryDay : ScheduleRule {
        override fun matches(day: DayOfWeek): Boolean = true
        override fun matchingDays(): Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }

    @Serializable
    @SerialName("daysOfWeek")
    data class DaysOfWeek(val days: Set<DayOfWeek>) : ScheduleRule {
        override fun matches(day: DayOfWeek): Boolean = day in days
        override fun matchingDays(): Set<DayOfWeek> = days
    }
}
