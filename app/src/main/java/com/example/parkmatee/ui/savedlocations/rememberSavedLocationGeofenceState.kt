package com.example.parkmatee.ui.savedlocations

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.parkmatee.data.entity.SavedLocation
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.LocationServices

@Composable
fun rememberSavedLocationGeofenceState(
    context: Context,
    savedLocations: List<SavedLocation>,
    permissionRefreshKey: Long
): SavedLocationGeofenceUiState {
    var uiState by remember {
        mutableStateOf(SavedLocationGeofenceUiState())
    }

    val enabledLocations = savedLocations.filter { location ->
        location.geofenceEnabled
    }
    val enabledLocationKey = enabledLocations.joinToString(separator = "|") { location ->
        "${location.id}:${location.name}:${location.latitude}:${location.longitude}:${location.geofenceRadiusMeters}"
    }

    LaunchedEffect(
        enabledLocationKey,
        permissionRefreshKey
    ) {
        val geofencingClient = LocationServices.getGeofencingClient(context)
        val pendingIntent = createSavedLocationGeofencePendingIntent(context)

        if (enabledLocations.isEmpty()) {
            geofencingClient.removeGeofences(pendingIntent)
            uiState = SavedLocationGeofenceUiState(
                active = false,
                monitoredCount = 0,
                message = "Attiva il geofence su uno o piu luoghi salvati."
            )
            return@LaunchedEffect
        }

        if (!hasSavedLocationGeofencePermission(context)) {
            uiState = SavedLocationGeofenceUiState(
                active = false,
                monitoredCount = enabledLocations.size,
                message = "Servono i permessi posizione, inclusa la posizione in background."
            )
            return@LaunchedEffect
        }

        uiState = SavedLocationGeofenceUiState(
            active = true,
            monitoredCount = enabledLocations.size,
            message = "Aggiornamento geofence dei luoghi salvati in corso..."
        )

        registerEnabledSavedLocationGeofences(context, savedLocations)
            ?.addOnSuccessListener {
                uiState = SavedLocationGeofenceUiState(
                    active = true,
                    monitoredCount = enabledLocations.size,
                    message = "Geofencing attivo sui luoghi salvati selezionati."
                )

            }
            ?.addOnFailureListener {
                uiState = SavedLocationGeofenceUiState(
                    active = false,
                    monitoredCount = enabledLocations.size,
                    message = "Errore durante l'attivazione del geofencing."
                )
            }
    }

    return uiState
}

data class SavedLocationGeofenceUiState(
    val active: Boolean = false,
    val monitoredCount: Int = 0,
    val message: String = "Attiva il geofence su uno o piu luoghi salvati."
)

fun hasSavedLocationGeofencePermission(
    context: Context
): Boolean {
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    return hasFineLocation && hasBackgroundLocation
}

fun canShowSavedLocationGeofenceNotifications(
    context: Context
): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

internal fun createSavedLocationGeofence(
    location: SavedLocation
): Geofence {
    return Geofence.Builder()
        .setRequestId(SavedLocationGeofenceRequestId.encode(location))
        .setCircularRegion(
            location.latitude,
            location.longitude,
            location.geofenceRadiusMeters
        )
        // Come richiesto dalla traccia: notifica sia all'ingresso (propone di avviare la sosta)
        // sia all'uscita (propone di terminarla).
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT
        )
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .build()
}

internal fun createSavedLocationGeofencePendingIntent(
    context: Context
): PendingIntent {
    val intent = Intent(
        context,
        SavedLocationGeofenceBroadcastReceiver::class.java
    )

    return PendingIntent.getBroadcast(
        context,
        SAVED_LOCATION_GEOFENCE_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
}

private const val SAVED_LOCATION_GEOFENCE_REQUEST_CODE = 91_000