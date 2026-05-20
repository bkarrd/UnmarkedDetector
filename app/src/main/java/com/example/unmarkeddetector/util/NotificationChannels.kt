package com.example.unmarkeddetector.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.unmarkeddetector.R
import com.example.unmarkeddetector.service.DetectionForegroundService

const val ALERT_NOTIFICATION_CHANNEL_ID = "alert_events"

fun Context.createDetectionNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        DetectionForegroundService.NOTIFICATION_CHANNEL_ID,
        getString(R.string.service_channel_name),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = getString(R.string.service_channel_description)
    }
    manager.createNotificationChannel(channel)
}

fun Context.createAlertNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        ALERT_NOTIFICATION_CHANNEL_ID,
        getString(R.string.alert_channel_name),
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = getString(R.string.alert_channel_description)
        enableVibration(true)
        setShowBadge(true)
    }
    manager.createNotificationChannel(channel)
}
