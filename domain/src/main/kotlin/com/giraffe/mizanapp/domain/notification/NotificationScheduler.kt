package com.giraffe.mizanapp.domain.notification
import java.time.Instant
interface NotificationScheduler { suspend fun replaceAll(anchors: List<NotificationAnchor>); suspend fun cancelAll(); fun deliveryMode(): DeliveryMode; suspend fun scheduleRefresh(at: Instant); suspend fun scheduleAt(anchorKey: String, at: Instant) }
enum class DeliveryMode { EXACT, RELAXED }
