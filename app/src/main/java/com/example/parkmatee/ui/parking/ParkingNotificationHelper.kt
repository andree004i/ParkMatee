package com.example.parkmatee.ui.parking

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.parkmatee.MainActivity
import com.example.parkmatee.data.entity.ParkingSession
import kotlinx.coroutines.delay

@Composable
fun ParkingNotificationEffect(
    context: Context,
    activeParkings: List<ParkingSession>
) {
    val latestActiveParkings = rememberUpdatedState(activeParkings)
    val notifiedStarted = remember { mutableStateMapOf<Int, Boolean>() }
    val notifiedExpiring = remember { mutableStateMapOf<Int, Boolean>() }
    val notifiedExpired = remember { mutableStateMapOf<Int, Boolean>() }
    val lastActiveReminder = remember { mutableStateMapOf<Int, Long>() }
    val activeIds = activeParkings.map { session -> session.id }.toSet()

    LaunchedEffect(Unit) {
        ensureParkingNotificationChannel(context)
    }

    LaunchedEffect(activeIds) {
        listOf(
            notifiedStarted,
            notifiedExpiring,
            notifiedExpired,
            lastActiveReminder
        ).forEach { stateMap ->
            stateMap.keys.toList()
                .filter { id -> id !in activeIds }
                .forEach { id -> stateMap.remove(id) }
        }
    }

    LaunchedEffect(activeParkings) {
        activeParkings.forEach { session ->
            if (notifiedStarted[session.id] != true) {
                showParkingNotification(
                    context = context,
                    notificationId = START_NOTIFICATION_BASE_ID + session.id,
                    title = "Parcheggio attivo",
                    text = "Hai un parcheggio attivo. Ricordati di terminarlo quando riparti."
                )
                notifiedStarted[session.id] = true
                lastActiveReminder[session.id] = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()

            latestActiveParkings.value.forEach { session ->
                val lastReminder = lastActiveReminder[session.id] ?: session.startTime
                if (now - lastReminder >= ACTIVE_REMINDER_INTERVAL_MILLIS) {
                    showParkingNotification(
                        context = context,
                        notificationId = ACTIVE_REMINDER_NOTIFICATION_BASE_ID + session.id,
                        title = "Reminder parcheggio attivo",
                        text = "Il parcheggio e attivo da ${formatNotificationDuration(now - session.startTime)}."
                    )
                    lastActiveReminder[session.id] = now
                }

                val expiryTime = session.expiryTime
                if (expiryTime != null) {
                    val remainingMillis = expiryTime - now

                    if (
                        remainingMillis in 1L..TICKET_EXPIRING_WARNING_MILLIS &&
                        notifiedExpiring[session.id] != true
                    ) {
                        showParkingNotification(
                            context = context,
                            notificationId = EXPIRING_NOTIFICATION_BASE_ID + session.id,
                            title = "Ticket in scadenza",
                            text = "Il ticket scade tra ${formatNotificationDuration(remainingMillis)}."
                        )
                        notifiedExpiring[session.id] = true
                    }

                    if (remainingMillis <= 0L && notifiedExpired[session.id] != true) {
                        showParkingNotification(
                            context = context,
                            notificationId = EXPIRED_NOTIFICATION_BASE_ID + session.id,
                            title = "Ticket scaduto",
                            text = "Il ticket del parcheggio e scaduto."
                        )
                        notifiedExpired[session.id] = true
                    }
                }
            }

            delay(NOTIFICATION_CHECK_INTERVAL_MILLIS)
        }
    }
}

fun ensureParkingNotificationChannel(
    context: Context
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val channel = NotificationChannel(
        PARKING_NOTIFICATION_CHANNEL_ID,
        "Parcheggi e ticket",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Reminder per parcheggi attivi e ticket in scadenza"
    }

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.createNotificationChannel(channel)
}

fun canShowParkingNotifications(
    context: Context
): Boolean {
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
private fun showParkingNotification(
    context: Context,
    notificationId: Int,
    title: String,
    text: String
) {
    if (!canShowParkingNotifications(context)) {
        return
    }

    ensureParkingNotificationChannel(context)

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(
        context,
        PARKING_NOTIFICATION_CHANNEL_ID
    )
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(
        notificationId,
        notification
    )
}

private fun formatNotificationDuration(
    durationMillis: Long
): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private const val PARKING_NOTIFICATION_CHANNEL_ID = "parking_reminders"
private const val START_NOTIFICATION_BASE_ID = 10_000
private const val ACTIVE_REMINDER_NOTIFICATION_BASE_ID = 20_000
private const val EXPIRING_NOTIFICATION_BASE_ID = 30_000
private const val EXPIRED_NOTIFICATION_BASE_ID = 40_000
private const val TICKET_EXPIRING_WARNING_MILLIS = 5 * 60 * 1_000L
private const val ACTIVE_REMINDER_INTERVAL_MILLIS = 15 * 60 * 1_000L
private const val NOTIFICATION_CHECK_INTERVAL_MILLIS = 30 * 1_000L
