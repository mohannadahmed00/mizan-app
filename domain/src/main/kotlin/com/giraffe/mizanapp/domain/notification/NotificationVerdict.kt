package com.giraffe.mizanapp.domain.notification

import java.time.Instant
data class NotificationContent(val category: NotificationCategory, val titleKey: String, val bodyArgs: Map<String, String>, val destination: String)
sealed interface NotificationVerdict { data class Post(val content: NotificationContent) : NotificationVerdict; data class Discard(val reason: DiscardReason) : NotificationVerdict; data class Hold(val until: Instant) : NotificationVerdict }
enum class DiscardReason { CATEGORY_OFF, ALL_SILENCED, ALREADY_DELIVERED, SECTION_COMPLETE, DAY_ALREADY_COUNTED, NO_LIVE_STREAK, WINDOW_PASSED, DAY_ROLLED_OVER, QUIET_HOURS, SUMMARY_DORMANT, NO_PERMISSION }
