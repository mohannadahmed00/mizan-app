package com.giraffe.mizanapp.domain.notification

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class QuietHours(val start: LocalTime, val end: LocalTime) {
    fun contains(instant: Instant, zone: ZoneId): Boolean {
        if (start == end) return true
        val time = instant.atZone(zone).toLocalTime()
        return if (start < end) time >= start && time < end else time >= start || time < end
    }

    fun endAfter(instant: Instant, zone: ZoneId): Instant {
        val local = instant.atZone(zone)
        val endDate = when {
            start == end -> local.toLocalDate().plusDays(1)
            start < end -> local.toLocalDate()
            local.toLocalTime() >= start -> local.toLocalDate().plusDays(1)
            else -> local.toLocalDate()
        }
        return endDate.atTime(end).atZone(zone).toInstant()
    }
}
