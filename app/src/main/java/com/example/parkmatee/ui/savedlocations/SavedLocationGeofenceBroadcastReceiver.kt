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
import com.example.parkmatee.R
import com.example.parkmatee.appContainer
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Riceve le transizioni dei geofence anche con l'app chiusa.
 * - ENTER (ingresso nell'area): propone di avviare la sosta per il veicolo suggerito.
 * - EXIT (uscita): propone di terminare la sosta collegata al luogo.
 * Le azioni della notifica sono eseguite da [GeofenceActionReceiver] senza aprire l'app.
 * Se non c'e nulla da proporre (sosta gia attiva all'arrivo, nessuna sosta all'uscita) non
 * viene mostrata alcuna notifica.
 */
class SavedLocationGeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) return

        val transition = geofencingEvent.geofenceTransition
        val inside = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }

        val triggering = geofencingEvent.triggeringGeofences
        if (triggering.isNullOrEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (geofence in triggering) {
                    val requestId = geofence.requestId ?: continue
                    val savedLocationId = SavedLocationGeofenceRequestId.decodeId(requestId) ?: continue
                    try {
                        notifyForLocation(
                            context = context,
                            savedLocationId = savedLocationId,
                            locationName = SavedLocationGeofenceRequestId.decodeName(requestId),
                            inside = inside
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore durante la preparazione della notifica del geofence", e)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
        private const val GEOFENCE_NOTIFICATION_CHANNEL_ID = "parking_geofence"
        private const val CONTENT_REQUEST_CODE = 50_001
        private const val ACTION_REQUEST_CODE = 52_000

        private suspend fun notifyForLocation(
            context: Context,
            savedLocationId: Int,
            locationName: String,
            inside: Boolean
        ) {
            val container = context.appContainer
            val activeHere = container.parkingRepository.getActiveSessionsForSavedLocationId(savedLocationId)

            if (inside) {
                // Sosta gia attiva qui: nulla da proporre.
                if (activeHere.isNotEmpty()) return
                val vehicle = container.sessionService.suggestVehicleToPark()
                // Tutti i veicoli sono gia parcheggiati: nulla da proporre.
                if (vehicle == null) return
                show(
                    context, savedLocationId,
                    title = "Sei in $locationName",
                    text = "Vuoi avviare la sosta per ${vehicle.name}?",
                    actionLabel = "Avvia sosta",
                    actionIntent = actionBroadcast(
                        context = context,
                        savedLocationId = savedLocationId,
                        action = GeofenceActionReceiver.ACTION_START
                    ) { putExtra(GeofenceActionReceiver.EXTRA_VEHICLE_ID, vehicle.id) }
                )
            } else {
                when (activeHere.size) {
                    0 -> Unit // nessuna sosta collegata a questo luogo: nulla da proporre

                    1 -> {
                        val session = activeHere.first()
                        val vehicleName = container.vehicleRepository.getAllVehicles().first()
                            .firstOrNull { it.id == session.vehicleId }?.name ?: "il veicolo"
                        show(
                            context, savedLocationId,
                            title = "Ti sei allontanato da $locationName",
                            text = "Vuoi terminare la sosta di $vehicleName?",
                            actionLabel = "Termina sosta",
                            actionIntent = actionBroadcast(
                                context = context,
                                savedLocationId = savedLocationId,
                                action = GeofenceActionReceiver.ACTION_END
                            ) { putExtra(GeofenceActionReceiver.EXTRA_SESSION_ID, session.id) }
                        )
                    }

                    else -> show(
                        context, savedLocationId,
                        title = "Ti sei allontanato da $locationName",
                        text = "Ci sono ${activeHere.size} veicoli parcheggiati qui: apri l'app per scegliere quale terminare.",
                        actionLabel = "Apri ParkMate",
                        actionIntent = openAppPendingIntent(context, ACTION_REQUEST_CODE + savedLocationId, savedLocationId)
                    )
                }
            }
        }

        private fun actionBroadcast(
            context: Context,
            savedLocationId: Int,
            action: String,
            extras: Intent.() -> Unit
        ): PendingIntent {
            val intent = Intent(context, GeofenceActionReceiver::class.java).apply {
                this.action = action
                putExtra(GeofenceActionReceiver.EXTRA_SAVED_LOCATION_ID, savedLocationId)
                extras()
            }
            return PendingIntent.getBroadcast(
                context,
                ACTION_REQUEST_CODE + savedLocationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun show(
            context: Context,
            savedLocationId: Int,
            title: String,
            text: String,
            actionLabel: String?,
            actionIntent: PendingIntent?
        ) {
            if (!canShowNotifications(context)) return

            ensureGeofenceNotificationChannel(context)

            val builder = NotificationCompat.Builder(context, GEOFENCE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openAppPendingIntent(context, CONTENT_REQUEST_CODE + savedLocationId, savedLocationId))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            if (actionLabel != null && actionIntent != null) {
                builder.addAction(0, actionLabel, actionIntent)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(savedLocationId, builder.build())
        }

        /** Tocco sulla notifica: apre l'app sul luogo salvato. */
        private fun openAppPendingIntent(
            context: Context,
            requestCode: Int,
            savedLocationId: Int
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("EXTRA_SAVED_LOCATION_ID", savedLocationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun canShowNotifications(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        }

        private fun ensureGeofenceNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val channel = NotificationChannel(
                GEOFENCE_NOTIFICATION_CHANNEL_ID,
                "Geofencing luoghi salvati",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Proposte di avvio e chiusura sosta quando arrivi o esci da un luogo salvato"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
