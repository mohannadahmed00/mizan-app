package com.giraffe.mizanapp.domain.notification

data class NotificationPreferences(val enabled: Set<NotificationCategory>, val allSilenced: Boolean, val quietHours: QuietHours?) {
    companion object { val DEFAULT = NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, null) }
}
