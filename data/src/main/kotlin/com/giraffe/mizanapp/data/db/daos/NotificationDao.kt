package com.giraffe.mizanapp.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entities.NotificationDeliveryEntity
import com.giraffe.mizanapp.data.db.entities.NotificationPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao interface NotificationDao {
 @Query("SELECT * FROM notification_preferences WHERE id = 0") suspend fun preferences(): NotificationPreferencesEntity?
 @Query("SELECT * FROM notification_preferences WHERE id = 0") fun observePreferences(): Flow<NotificationPreferencesEntity?>
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPreferences(e: NotificationPreferencesEntity)
 @Query("SELECT * FROM notification_deliveries") suspend fun deliveries(): List<NotificationDeliveryEntity>
 @Query("SELECT * FROM notification_deliveries WHERE anchorKey = :anchorKey") suspend fun delivery(anchorKey: String): NotificationDeliveryEntity?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDelivery(e: NotificationDeliveryEntity)
 @Query("DELETE FROM notification_deliveries WHERE decidedAt < :before") suspend fun pruneBefore(before: Long)
}
