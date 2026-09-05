package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.QuietHours

data class NotificationSettings(val prayerWindowEnabled: Boolean, val streakAtRiskEnabled: Boolean, val weeklySummaryEnabled: Boolean, val allSilenced: Boolean, val quietHours: QuietHours?, val systemPermission: PermissionState, val deliveryMode: DeliveryMode, val statements: List<String>)
enum class PermissionState { GRANTED, DENIED, NOT_YET_ASKED }
fun notificationStatements(permission: PermissionState, deliveryMode: DeliveryMode, nudgesNeedLocation: Boolean, summaryDormant: Boolean, allSilenced: Boolean): List<String> = buildList {
    if (permission != PermissionState.GRANTED) add("System notification permission is off. Nothing is delivered; you can enable it in system settings.")
    if (deliveryMode == DeliveryMode.RELAXED) add("Timing may drift; anything arriving outside its own window is not shown late.")
    if (nudgesNeedLocation) add("Prayer nudges need location and are not scheduled until it is available.")
    if (summaryDormant) add("Weekly notifications are paused; summaries remain on their screen and recording anything resumes them.")
    if (allSilenced) add("Everything is silenced. Your category choices are remembered.")
    if (isEmpty()) add("Notifications are ready when you choose to use them.")
}
