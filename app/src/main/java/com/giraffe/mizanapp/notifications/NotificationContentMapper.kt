package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent

data class RenderedNotification(val title: String, val body: String)

fun NotificationContent.render(): RenderedNotification = when (category) {
    NotificationCategory.PRAYER_WINDOW -> RenderedNotification("A moment for ${bodyArgs["section"] ?: "this prayer"}", "There is still an opportunity in this section.")
    NotificationCategory.STREAK_AT_RISK -> RenderedNotification("Keep your rhythm going", "One task keeps your established streak going.")
    NotificationCategory.WEEKLY_SUMMARY -> RenderedNotification(
        "Your weekly summary",
        "${bodyArgs["daysEngaged"] ?: "0"} days engaged, ${bodyArgs["tasksRecorded"] ?: "0"} tasks recorded, ${bodyArgs["pointsEarned"] ?: "0"} points earned.",
    )
}
