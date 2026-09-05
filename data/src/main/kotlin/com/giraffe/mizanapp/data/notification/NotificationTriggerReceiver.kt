package com.giraffe.mizanapp.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class NotificationTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(AlarmNotificationScheduler.EXTRA_ANCHOR_KEY) ?: return
        val request = OneTimeWorkRequestBuilder<NotificationWorker>().setInputData(Data.Builder().putString(NotificationWorker.INPUT_ANCHOR_KEY, key).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, request)
    }
}
