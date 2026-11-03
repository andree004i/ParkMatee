package com.example.parkmatee.ui.savedlocations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.parkmatee.appContainer
import com.example.parkmatee.notification.NotificationConstants
import com.example.parkmatee.notification.showParkingNotification
import com.example.parkmatee.util.ParkingFormatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Esegue le azioni "Avvia sosta" / "Termina sosta" delle notifiche del geofence SENZA aprire
 * l'app: il lavoro e fatto qui, con le stesse funzioni usate dal resto dell'applicazione.
 */
class GeofenceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val savedLocationId = intent.getIntExtra(EXTRA_SAVED_LOCATION_ID, -1)
        if (savedLocationId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                when (action) {
                    ACTION_START -> {
                        val location = container.savedLocationRepository.getById(savedLocationId)
                        val requestedVehicleId = intent.getIntExtra(EXTRA_VEHICLE_ID, -1)
                        val vehicles = container.vehicleRepository.getAllVehicles().first()
                        val parkedIds = container.parkingRepository.getActiveParkings().first()
                            .map { it.vehicleId }.toSet()
                        // Il veicolo scelto al momento della notifica, se e ancora libero;
                        // altrimenti si ricalcola il suggerimento.
                        val vehicle = vehicles.firstOrNull { it.id == requestedVehicleId && it.id !in parkedIds }
                            ?: container.sessionService.suggestVehicleToPark()
                        if (location != null && vehicle != null) {
                            container.sessionService.startFreeSessionAtSavedLocation(location, vehicle)
                        }
                    }

                    ACTION_END -> {
                        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                        val session = container.parkingRepository.getParkingById(sessionId)
                        if (session != null && session.endTime == null) {
                            container.sessionService.endSession(session)
                            val closed = container.parkingRepository.getParkingById(sessionId)
                            val vehicleName = container.vehicleRepository.getAllVehicles().first()
                                .firstOrNull { it.id == session.vehicleId }?.name ?: "Veicolo"
                            val cost = closed?.finalCost
                            showParkingNotification(
                                context = context,
                                notificationId = NotificationConstants.SESSION_ENDED_NOTIFICATION_BASE_ID + sessionId,
                                title = "Sosta terminata",
                                text = if (cost != null && cost > 0.0) {
                                    "$vehicleName: costo finale ${ParkingFormatters.formatCurrency(cost)}."
                                } else {
                                    "$vehicleName: sosta terminata."
                                }
                            )
                        }
                    }
                }
                // L'azione e stata eseguita: la notifica del geofence non serve piu.
                NotificationManagerCompat.from(context).cancel(savedLocationId)
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'esecuzione dell'azione del geofence", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceActionReceiver"
        const val ACTION_START = "com.example.parkmatee.action.GEOFENCE_START_PARKING"
        const val ACTION_END = "com.example.parkmatee.action.GEOFENCE_END_PARKING"
        const val EXTRA_SAVED_LOCATION_ID = "extra_saved_location_id"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
