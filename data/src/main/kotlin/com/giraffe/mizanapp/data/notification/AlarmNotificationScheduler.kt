package com.giraffe.mizanapp.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationScheduler
import com.giraffe.mizanapp.domain.notification.anchorKey

class AlarmNotificationScheduler(private val context: Context) : NotificationScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("notification_alarms", Context.MODE_PRIVATE)
    override suspend fun replaceAll(anchors: List<NotificationAnchor>) {
        anchors(prefs).forEach { cancel(it) }
        anchors.forEach { anchor ->
            val key = anchor.anchorKey; val intent = pendingIntent(key)
            schedule(intent, anchor.firesAt)
            prefs.edit().putBoolean(key, true).apply()
        }
    }
    override suspend fun cancelAll() { anchors(prefs).forEach(::cancel) }
    override fun deliveryMode(): DeliveryMode = if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) DeliveryMode.EXACT else DeliveryMode.RELAXED
    override suspend fun scheduleRefresh(at: Instant) {
        schedule(pendingIntent(REFRESH_KEY), at)
        prefs.edit().putBoolean(REFRESH_KEY, true).apply()
    }
    private fun schedule(intent: PendingIntent, at: Instant) {
        if (deliveryMode() == DeliveryMode.EXACT) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
    }
    private fun cancel(key: String) { alarms.cancel(pendingIntent(key)); prefs.edit().remove(key).apply() }
    private fun pendingIntent(key: String) = PendingIntent.getBroadcast(context, key.hashCode(), Intent(context, NotificationTriggerReceiver::class.java).putExtra(EXTRA_ANCHOR_KEY, key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun anchors(prefs: android.content.SharedPreferences) = prefs.all.keys.filter { it != REFRESH_KEY }
    companion object { const val EXTRA_ANCHOR_KEY = "anchorKey"; const val REFRESH_KEY = "__REFRESH__" }
}
