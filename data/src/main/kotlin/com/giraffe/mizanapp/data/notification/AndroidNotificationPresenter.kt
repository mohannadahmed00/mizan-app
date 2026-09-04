package com.giraffe.mizanapp.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.notification.anchorKey

class AndroidNotificationPresenter(private val context: Context) : NotificationPresenter {
    private val manager = NotificationManagerCompat.from(context)
    override fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) {
        if (!hasPermission()) return
        createChannel(content.category)
        manager.notify(anchor.anchorKey, 0, NotificationCompat.Builder(context, channelId(content.category)).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(content.titleKey).setContentText(content.bodyArgs.values.joinToString(" ")).setAutoCancel(true).build())
    }
    override suspend fun withdraw(anchorKey: String) { manager.cancel(anchorKey, 0) }
    private fun createChannel(category: NotificationCategory) {
        if (Build.VERSION.SDK_INT >= 26) (context.getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(channelId(category), channelName(category), NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun channelId(category: NotificationCategory) = "mizan_${category.name.lowercase()}"
    private fun channelName(category: NotificationCategory) = when (category) { NotificationCategory.PRAYER_WINDOW -> "Prayer windows"; NotificationCategory.STREAK_AT_RISK -> "Streak reminders"; NotificationCategory.WEEKLY_SUMMARY -> "Weekly summaries" }
}
