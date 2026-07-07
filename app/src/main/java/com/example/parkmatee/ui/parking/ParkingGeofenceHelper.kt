package com.example.parkmatee.ui.parking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.parkmatee.data.entity.ParkingSession
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

@Composable
fun rememberParkingGeofenceState(
    context: Context,
    activeParkings: List<ParkingSession>,
    enabled: Boolean,
    permissionRefreshKey: Long
): ParkingGeofenceUiState {
    var uiState by remember {
        mutableStateOf(ParkingGeofenceUiState())
    }
    var registeredIds by remember {
        mutableStateOf(emptySet<String>())
    }

    val activeIds = activeParkings.map { session -> session.id }.toSet()

    LaunchedEffect(
        enabled,
        activeIds,
        permissionRefreshKey
    ) {
        val geofencingClient = LocationServices.getGeofencingClient(context)
        val pendingIntent = createGeofencePendingIntent(context)

        if (registeredIds.isNotEmpty()) {
            geofencingClient.removeGeofences(registeredIds.toList())
            registeredIds = emptySet()
        }

        if (!enabled) {
            uiState = ParkingGeofenceUiState(
                enabled = false,
                monitoredCount = 0,
                message = "Geofencing non attivo."
            )
            return@LaunchedEffect
        }

        if (!hasGeofenceLocationPermission(context)) {
            uiState = ParkingGeofenceUiState(
                enabled = false,
                monitoredCount = 0,
                message = "Permesso posizione necessario per attivare il geofencing."
            )
            return@LaunchedEffect
        }

        if (activeParkings.isEmpty()) {
            uiState = ParkingGeofenceUiState(
                enabled = true,
                monitoredCount = 0,
                message = "Geofencing pronto: avvia un parcheggio per monitorarlo."
            )
            return@LaunchedEffect
        }

        val geofences = activeParkings.map { session ->
            createParkingGeofence(session)
        }

        uiState = ParkingGeofenceUiState(
            enabled = true,
            monitoredCount = activeParkings.size,
            message = "Registrazione geofence in corso..."
        )

        registerParkingGeofences(
            context = context,
            geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build(),
            pendingIntent = pendingIntent,
            onSuccess = {
                registeredIds = geofences.map { geofence -> geofence.requestId }.toSet()
                uiState = ParkingGeofenceUiState(
                    enabled = true,
                    monitoredCount = activeParkings.size,
                    message = "Geofencing attivo sui parcheggi correnti."
                )
            },
            onError = {
                registeredIds = emptySet()
                uiState = ParkingGeofenceUiState(
                    enabled = false,
                    monitoredCount = 0,
                    message = "Errore durante l'attivazione del geofencing."
                )
            }
        )
    }

    return uiState
}

data class ParkingGeofenceUiState(
    val enabled: Boolean = false,
    val monitoredCount: Int = 0,
    val message: String = "Geofencing non attivo."
)

fun hasGeofenceLocationPermission(
    context: Context
): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private fun registerParkingGeofences(
    context: Context,
    geofencingRequest: GeofencingRequest,
    pendingIntent: PendingIntent,
    onSuccess: () -> Unit,
    onError: () -> Unit
) {
    LocationServices.getGeofencingClient(context)
        .addGeofences(
            geofencingRequest,
            pendingIntent
        )
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener {
            onError()
        }
}

private fun createParkingGeofence(
    session: ParkingSession
): Geofence {
    return Geofence.Builder()
        .setRequestId("parking_${session.id}")
        .setCircularRegion(
            session.latitude,
            session.longitude,
            GEOFENCE_RADIUS_METERS
        )
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT or
                Geofence.GEOFENCE_TRANSITION_DWELL
        )
        .setLoiteringDelay(GEOFENCE_DWELL_DELAY_MILLIS)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .build()
}

private fun createGeofencePendingIntent(
    context: Context
): PendingIntent {
    val intent = Intent(
        context,
        ParkingGeofenceBroadcastReceiver::class.java
    )

    return PendingIntent.getBroadcast(
        context,
        GEOFENCE_PENDING_INTENT_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
}

private const val GEOFENCE_RADIUS_METERS = 120f
private const val GEOFENCE_DWELL_DELAY_MILLIS = 2 * 60 * 1_000
private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 90_000
