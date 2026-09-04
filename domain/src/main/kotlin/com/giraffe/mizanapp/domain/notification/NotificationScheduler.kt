package com.giraffe.mizanapp.domain.notification
interface NotificationScheduler { suspend fun replaceAll(anchors: List<NotificationAnchor>); suspend fun cancelAll(); fun deliveryMode(): DeliveryMode }
enum class DeliveryMode { EXACT, RELAXED }
