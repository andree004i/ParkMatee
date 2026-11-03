package com.example.parkmatee.ui.savedlocations

import android.annotation.SuppressLint
import android.content.Context
import com.example.parkmatee.data.entity.SavedLocation
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task

/** Limite di geofence registrabili da una app. */
const val MAX_SAVED_LOCATION_GEOFENCES = 100

/**
 * Unico punto che (ri)registra i geofence dei luoghi salvati attivi: rimuove quelli esistenti
 * e registra l'insieme corrente. Usato dalla schermata Luoghi e dal BootReceiver (Play Services
 * cancella i geofence al riavvio del telefono e dopo un aggiornamento dell'app).
 * Restituisce il Task finale, oppure null se non c'e nulla da attendere.
 */
@SuppressLint("MissingPermission")
fun registerEnabledSavedLocationGeofences(
    context: Context,
    locations: List<SavedLocation>
): Task<Void>? {
    // Android consente al massimo 100 geofence per app: oltre il limite la registrazione fallirebbe.
    val enabled = locations.filter { it.geofenceEnabled }.take(MAX_SAVED_LOCATION_GEOFENCES)
    val client = LocationServices.getGeofencingClient(context)
    val pendingIntent = createSavedLocationGeofencePendingIntent(context)

    val removal = client.removeGeofences(pendingIntent)
    if (enabled.isEmpty() || !hasSavedLocationGeofencePermission(context)) return removal

    val responsiveness = geofenceResponsivenessMillis(context)
    val request = GeofencingRequest.Builder()
        // Nessuna transizione iniziale: registrare i geofence mentre si e gia dentro l'area
        // (modifica, riavvio, aggiornamento) non deve far scattare una proposta.
        .setInitialTrigger(0)
        .addGeofences(enabled.map { createSavedLocationGeofence(it, responsiveness) })
        .build()
    return removal.continueWithTask { client.addGeofences(request, pendingIntent) }
}
