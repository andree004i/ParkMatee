package com.example.parkmatee.ui.savedlocations

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.parkmatee.MainActivity
import com.example.parkmatee.data.db.DatabaseProvider
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SavedLocationGeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val triggeringGeofences = geofencingEvent.triggeringGeofences
                if (!triggeringGeofences.isNullOrEmpty()) {
                    val transition = geofencingEvent.geofenceTransition

                    for (triggeringGeofence in triggeringGeofences) {
                        val requestId = triggeringGeofence.requestId ?: continue

                        // Estrazione centralizzata tramite SavedLocationGeofenceRequestId
                        val savedLocationId = SavedLocationGeofenceRequestId.decodeId(requestId)
                        val triggeredLocationName = SavedLocationGeofenceRequestId.decodeName(requestId)

                        if (savedLocationId != null) {
                            try {
                                val hasActiveParking = hasActiveParkingForSavedLocation(
                                    context = context,
                                    savedLocationName = triggeredLocationName
                                )

                                val notificationContent = buildGeofenceNotificationContent(
                                    locationName = triggeredLocationName,
                                    transition = transition,
                                    hasActiveParking = hasActiveParking
                                )

                                showGeofenceNotification(
                                    context = context,
                                    title = notificationContent.title,
                                    text = notificationContent.text,
                                    actionLabel = notificationContent.actionLabel,
                                    actionType = notificationContent.actionType,
                                    notificationId = savedLocationId,
                                    savedLocationId = savedLocationId
                                )
                            } catch (dbException: Exception) {
                                Log.e(TAG, "Errore durante il recupero dello stato del parcheggio per geofence", dbException)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun hasActiveParkingForSavedLocation(
        context: Context,
        savedLocationName: String
    ): Boolean {
        return DatabaseProvider.getDatabase(context)
            .parkingDao()
            .getActiveSessionForSavedLocation(savedLocationName) != null
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
        private const val GEOFENCE_NOTIFICATION_CHANNEL_ID = "parking_geofence"
        private const val GEOFENCE_NOTIFICATION_CONTENT_REQUEST_CODE = 50_001
        private const val GEOFENCE_NOTIFICATION_ACTION_REQUEST_CODE = 50_002

        fun showSavedLocationStatusNotification(
            context: Context,
            savedLocationId: Int,
            locationName: String,
            isInside: Boolean,
            distanceMeters: Float,
            hasActiveParking: Boolean
        ) {
            val roundedDistance = distanceMeters.toInt()
            val notificationContent = buildCurrentStatusNotificationContent(
                locationName = locationName,
                isInside = isInside,
                distanceMeters = roundedDistance,
                hasActiveParking = hasActiveParking
            )

            showGeofenceNotification(
                context = context,
                title = notificationContent.title,
                text = notificationContent.text,
                actionLabel = notificationContent.actionLabel,
                actionType = notificationContent.actionType,
                notificationId = savedLocationId,
                savedLocationId = savedLocationId
            )
        }

        private fun buildGeofenceNotificationContent(
            locationName: String,
            transition: Int,
            hasActiveParking: Boolean
        ): GeofenceNotificationContent {
            return when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    if (hasActiveParking) {
                        GeofenceNotificationContent(
                            title = "Parcheggio gia attivo in $locationName",
                            text = "Sei dentro il raggio del luogo salvato e il parcheggio e gia attivo.",
                            actionLabel = "Apri ParkMate",
                            actionType = null
                        )
                    } else {
                        GeofenceNotificationContent(
                            title = "Sei dentro il raggio di $locationName",
                            text = "Vuoi avviare un parcheggio in questo luogo salvato?",
                            actionLabel = "Avvia parcheggio",
                            actionType = "ACTION_START_PARKING"
                        )
                    }
                }

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    if (hasActiveParking) {
                        GeofenceNotificationContent(
                            title = "Ti sei allontanato da $locationName",
                            text = "Vuoi terminare il parcheggio collegato a questo luogo?",
                            actionLabel = "Termina parcheggio",
                            actionType = "ACTION_END_PARKING"
                        )
                    } else {
                        GeofenceNotificationContent(
                            title = "Sei fuori dal raggio di $locationName",
                            text = "Non risultano parcheggi attivi collegati a questo luogo.",
                            actionLabel = "Apri ParkMate",
                            actionType = null
                        )
                    }
                }

                else -> GeofenceNotificationContent(
                    title = "Promemoria parcheggio",
                    text = "Controlla lo stato del parcheggio.",
                    actionLabel = "Apri ParkMate",
                    actionType = null
                )
            }
        }

        private fun buildCurrentStatusNotificationContent(
            locationName: String,
            isInside: Boolean,
            distanceMeters: Int,
            hasActiveParking: Boolean
        ): GeofenceNotificationContent {
            return if (isInside) {
                if (hasActiveParking) {
                    GeofenceNotificationContent(
                        title = "Parcheggio gia attivo in $locationName",
                        text = "Distanza: $distanceMeters m. Sei gia parcheggiato in questo luogo.",
                        actionLabel = "Apri ParkMate",
                        actionType = null
                    )
                } else {
                    GeofenceNotificationContent(
                        title = "Sei dentro il raggio di $locationName",
                        text = "Distanza: $distanceMeters m. Vuoi avviare un parcheggio?",
                        actionLabel = "Avvia parcheggio",
                        actionType = "ACTION_START_PARKING"
                    )
                }
            } else {
                if (hasActiveParking) {
                    GeofenceNotificationContent(
                        title = "Ti sei allontanato da $locationName",
                        text = "Distanza: $distanceMeters m. Vuoi terminare il parcheggio collegato?",
                        actionLabel = "Termina parcheggio",
                        actionType = "ACTION_END_PARKING"
                    )
                } else {
                    GeofenceNotificationContent(
                        title = "Sei fuori dal raggio di $locationName",
                        text = "Distanza: $distanceMeters m. Nessun parcheggio attivo collegato.",
                        actionLabel = "Apri ParkMate",
                        actionType = null
                    )
                }
            }
        }

        private fun showGeofenceNotification(
            context: Context,
            title: String,
            text: String,
            actionLabel: String,
            actionType: String?,
            notificationId: Int,
            savedLocationId: Int
        ) {
            if (!canShowNotifications(context)) {
                return
            }

            ensureGeofenceNotificationChannel(context)

            val contentIntent = createOpenAppPendingIntent(
                context = context,
                requestCode = GEOFENCE_NOTIFICATION_CONTENT_REQUEST_CODE + notificationId,
                savedLocationId = savedLocationId,
                actionType = null
            )

            val actionIntent = createOpenAppPendingIntent(
                context = context,
                requestCode = GEOFENCE_NOTIFICATION_ACTION_REQUEST_CODE + notificationId,
                savedLocationId = savedLocationId,
                actionType = actionType
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
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.notify(notificationId, notification)
        }

        private fun createOpenAppPendingIntent(
            context: Context,
            requestCode: Int,
            savedLocationId: Int,
            actionType: String?
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("EXTRA_SAVED_LOCATION_ID", savedLocationId)
                putExtra("EXTRA_GEOFENCE_ACTION", actionType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
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
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche intelligenti per ingresso, uscita e stato dei luoghi salvati"
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }
}

private data class GeofenceNotificationContent(
    val title: String,
    val text: String,
    val actionLabel: String,
    val actionType: String?
)