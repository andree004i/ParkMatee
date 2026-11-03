package com.example.parkmatee.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.parkmatee.MainActivity

object NotificationConstants {
    const val PARKING_NOTIFICATION_CHANNEL_ID = "parking_reminders"
    const val START_NOTIFICATION_BASE_ID = 10_000
    const val ACTIVE_REMINDER_NOTIFICATION_BASE_ID = 20_000
    const val EXPIRING_NOTIFICATION_BASE_ID = 30_000
    const val EXPIRED_NOTIFICATION_BASE_ID = 40_000
}

fun ensureParkingNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val channel = NotificationChannel(
        NotificationConstants.PARKING_NOTIFICATION_CHANNEL_ID,
        "Parcheggi e ticket",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Reminder per parcheggi attivi e ticket in scadenza"
    }

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}

fun canShowParkingNotifications(context: Context): Boolean {
    val runtimePermissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    return runtimePermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}

@SuppressLint("MissingPermission")
fun showParkingNotification(
    context: Context,
    notificationId: Int,
    title: String,
    text: String
): Boolean {
    if (!canShowParkingNotifications(context)) return false

    ensureParkingNotificationChannel(context)

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(
        context,
        NotificationConstants.PARKING_NOTIFICATION_CHANNEL_ID
    )
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
    return true
}

/** Rimuove dalla barra le notifiche gia mostrate per una sosta (chiusa o sostituita). */
fun cancelSessionNotifications(context: Context, sessionId: Int) {
    val manager = NotificationManagerCompat.from(context)
    listOf(
        NotificationConstants.START_NOTIFICATION_BASE_ID,
        NotificationConstants.ACTIVE_REMINDER_NOTIFICATION_BASE_ID,
        NotificationConstants.EXPIRING_NOTIFICATION_BASE_ID,
        NotificationConstants.EXPIRED_NOTIFICATION_BASE_ID
    ).forEach { base -> manager.cancel(base + sessionId) }
}
