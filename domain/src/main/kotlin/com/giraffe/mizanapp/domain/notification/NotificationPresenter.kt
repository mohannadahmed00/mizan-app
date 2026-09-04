package com.giraffe.mizanapp.domain.notification
interface NotificationPresenter { suspend fun post(anchor: NotificationAnchor, content: NotificationContent); suspend fun withdraw(anchorKey: String); fun hasPermission(): Boolean }
