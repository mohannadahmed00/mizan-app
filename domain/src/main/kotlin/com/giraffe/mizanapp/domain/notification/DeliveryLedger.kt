package com.giraffe.mizanapp.domain.notification

import java.time.Instant
data class DeliveryRecord(val anchorKey: String, val category: NotificationCategory, val state: DeliveryState, val reason: DiscardReason?, val decidedAt: Instant, val heldUntil: Instant?)
enum class DeliveryState { DELIVERED, DISCARDED, HELD }
fun List<DeliveryRecord>.terminalFor(anchorKey: String): DeliveryRecord? = firstOrNull { it.anchorKey == anchorKey && it.state != DeliveryState.HELD }
