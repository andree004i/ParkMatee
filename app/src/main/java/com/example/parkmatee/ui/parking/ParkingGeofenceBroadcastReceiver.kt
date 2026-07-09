package com.example.parkmatee.ui.parking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.parkmatee.MainActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class ParkingGeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            return
        }

        val triggeredLocationName = geofencingEvent.triggeringGeofences
            ?.firstOrNull()
            ?.requestId
            ?.toSavedLocationName()
            ?: "luogo salvato"

        val transition = geofencingEvent.geofenceTransition
        val title = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Sei vicino a $triggeredLocationName"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Ti stai allontanando da $triggeredLocationName"
            else -> "Promemoria parcheggio"
        }

        val text = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER ->
                "Apri ParkMate per avviare un parcheggio in questo luogo salvato."

            Geofence.GEOFENCE_TRANSITION_EXIT ->
                "Apri ParkMate per terminare il parcheggio collegato a questo luogo."

            else -> "Controlla il parcheggio attivo."
        }

        showGeofenceNotification(
            context = context,
            title = title,
            text = text,
            transition = transition
        )
    }

    private fun showGeofenceNotification(
        context: Context,
        title: String,
        text: String,
        transition: Int
    ) {
        if (!canShowNotifications(context)) {
            return
        }

        ensureGeofenceNotificationChannel(context)

        val contentIntent = createOpenAppPendingIntent(
            context = context,
            requestCode = GEOFENCE_NOTIFICATION_CONTENT_REQUEST_CODE
        )

        val actionLabel = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Avvia parcheggio"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Termina parcheggio"
            else -> "Apri ParkMate"
        }

        val actionIntent = createOpenAppPendingIntent(
            context = context,
            requestCode = GEOFENCE_NOTIFICATION_ACTION_REQUEST_CODE
        )

        val notification = NotificationCompat.Builder(
            context,
            GEOFENCE_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_dialog_map,
                actionLabel,
                actionIntent
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(
            GEOFENCE_NOTIFICATION_ID,
            notification
        )
    }

    private fun createOpenAppPendingIntent(
        context: Context,
        requestCode: Int
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canShowNotifications(
        context: Context
    ): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureGeofenceNotificationChannel(
        context: Context
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            GEOFENCE_NOTIFICATION_CHANNEL_ID,
            "Geofencing luoghi salvati",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifiche quando entri o esci dai luoghi salvati"
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    private fun String.toSavedLocationName(): String? {
        if (!startsWith(SAVED_LOCATION_REQUEST_ID_PREFIX)) {
            return null
        }

        return substringAfter(SAVED_LOCATION_REQUEST_ID_PREFIX)
            .substringAfter("_", "luogo salvato")
            .ifBlank { "luogo salvato" }
    }

    companion object {
        private const val SAVED_LOCATION_REQUEST_ID_PREFIX = "saved_location_"
        private const val GEOFENCE_NOTIFICATION_CHANNEL_ID = "parking_geofence"
        private const val GEOFENCE_NOTIFICATION_ID = 50_000
        private const val GEOFENCE_NOTIFICATION_CONTENT_REQUEST_CODE = 50_001
        private const val GEOFENCE_NOTIFICATION_ACTION_REQUEST_CODE = 50_002
    }
}
