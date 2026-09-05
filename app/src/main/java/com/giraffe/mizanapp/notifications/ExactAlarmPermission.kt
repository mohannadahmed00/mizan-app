package com.giraffe.mizanapp.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.giraffe.mizanapp.domain.notification.DeliveryMode

/**
 * FR-007b: exact delivery is only ever requested after notification permission is already
 * granted, and only when the platform is not already offering exact delivery.
 */
fun shouldRequestExactAlarmPermission(notificationPermissionGranted: Boolean, deliveryMode: DeliveryMode): Boolean =
    notificationPermissionGranted && deliveryMode == DeliveryMode.RELAXED

fun requestExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
