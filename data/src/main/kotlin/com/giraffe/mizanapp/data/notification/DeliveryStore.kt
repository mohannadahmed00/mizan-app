package com.giraffe.mizanapp.data.notification

import com.giraffe.mizanapp.data.db.daos.NotificationDao
import com.giraffe.mizanapp.data.db.entities.NotificationDeliveryEntity
import com.giraffe.mizanapp.domain.notification.DeliveryRecord
import com.giraffe.mizanapp.domain.notification.DeliveryState
import com.giraffe.mizanapp.domain.notification.DiscardReason
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import java.time.Instant
import java.time.temporal.ChronoUnit

class DeliveryStore(private val dao: NotificationDao) {
    suspend fun records(): List<DeliveryRecord> = dao.deliveries().map { it.toDomain() }
    suspend fun record(value: DeliveryRecord) = dao.upsertDelivery(value.toEntity())
    suspend fun prune(now: Instant) = dao.pruneBefore(now.minus(90, ChronoUnit.DAYS).toEpochMilli())
}
private fun NotificationDeliveryEntity.toDomain() = DeliveryRecord(anchorKey, NotificationCategory.valueOf(category), DeliveryState.valueOf(state), reason?.let(DiscardReason::valueOf), Instant.ofEpochMilli(decidedAt), heldUntil?.let(Instant::ofEpochMilli))
private fun DeliveryRecord.toEntity() = NotificationDeliveryEntity(anchorKey, category.name, state.name, reason?.name, decidedAt.toEpochMilli(), heldUntil?.toEpochMilli())
