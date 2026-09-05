package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.domain.notification.DeliveryMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmPermissionTest {

    @Test fun neverIssuedWhileNotificationPermissionIsAbsent() {
        assertFalse(shouldRequestExactAlarmPermission(notificationPermissionGranted = false, deliveryMode = DeliveryMode.RELAXED))
    }

    @Test fun issuedOnceNotificationPermissionIsGrantedAndExactDeliveryIsUnavailable() {
        assertTrue(shouldRequestExactAlarmPermission(notificationPermissionGranted = true, deliveryMode = DeliveryMode.RELAXED))
    }

    @Test fun notIssuedWhenExactDeliveryIsAlreadyAvailable() {
        assertFalse(shouldRequestExactAlarmPermission(notificationPermissionGranted = true, deliveryMode = DeliveryMode.EXACT))
    }
}
