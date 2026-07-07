package com.example.parkmatee.ui.parking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
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

        val title = when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Sei vicino al parcheggio"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Ti stai allontanando dal parcheggio"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "Sei ancora vicino al parcheggio"
            else -> "Promemoria parcheggio"
        }

        val text = when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Hai raggiunto l'area del parcheggio salvato."
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Ti sei allontanato dall'area del parcheggio attivo."
            Geofence.GEOFENCE_TRANSITION_DWELL -> "Il parcheggio attivo e ancora vicino alla tua posizione."
            else -> "Controlla il parcheggio attivo."
        }

        showGeofenceNotification(
            context = context,
            title = title,
            text = text
        )
    }

    private fun showGeofenceNotification(
        context: Context,
        title: String,
        text: String
    ) {
        ensureGeofenceNotificationChannel(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            GEOFENCE_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
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

    private fun ensureGeofenceNotificationChannel(
        context: Context
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            GEOFENCE_NOTIFICATION_CHANNEL_ID,
            "Geofencing parcheggio",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifiche quando entri o esci dall'area del parcheggio"
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val GEOFENCE_NOTIFICATION_CHANNEL_ID = "parking_geofence"
        private const val GEOFENCE_NOTIFICATION_ID = 50_000
    }
}
