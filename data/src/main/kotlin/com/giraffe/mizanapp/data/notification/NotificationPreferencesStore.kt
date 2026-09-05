package com.giraffe.mizanapp.data.notification

import com.giraffe.mizanapp.data.db.daos.NotificationDao
import com.giraffe.mizanapp.data.db.entities.NotificationPreferencesEntity
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationPreferences
import com.giraffe.mizanapp.domain.notification.QuietHours
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationPreferencesStore(private val dao: NotificationDao) {
    suspend fun preferences(): NotificationPreferences = dao.preferences()?.toDomain() ?: NotificationPreferences.DEFAULT
    fun observePreferences(): Flow<NotificationPreferences> = dao.observePreferences().map { it?.toDomain() ?: NotificationPreferences.DEFAULT }
    suspend fun save(value: NotificationPreferences) = dao.upsertPreferences(value.toEntity())
}
private fun NotificationPreferencesEntity.toDomain() = NotificationPreferences(buildSet { if (prayerWindowEnabled) add(NotificationCategory.PRAYER_WINDOW); if (streakAtRiskEnabled) add(NotificationCategory.STREAK_AT_RISK); if (weeklySummaryEnabled) add(NotificationCategory.WEEKLY_SUMMARY) }, allSilenced, quietStart?.let { QuietHours(LocalTime.parse(it), LocalTime.parse(requireNotNull(quietEnd))) })
private fun NotificationPreferences.toEntity() = NotificationPreferencesEntity(prayerWindowEnabled = NotificationCategory.PRAYER_WINDOW in enabled, streakAtRiskEnabled = NotificationCategory.STREAK_AT_RISK in enabled, weeklySummaryEnabled = NotificationCategory.WEEKLY_SUMMARY in enabled, allSilenced = allSilenced, quietStart = quietHours?.start?.toString(), quietEnd = quietHours?.end?.toString())
