package com.example.parkmatee.ui.savedlocations

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.parkmatee.data.db.DatabaseProvider
import com.example.parkmatee.data.entity.SavedLocation
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Volatile
private var lastNotifiedLocationKey: String? = null

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

                // La notifica di stato parte solo se l'insieme dei luoghi attivi e cambiato,
                // non a ogni riapertura della schermata.
                if (lastNotifiedLocationKey != enabledLocationKey) {
                    lastNotifiedLocationKey = enabledLocationKey
                    notifyCurrentSavedLocationStatus(
                        context = context,
                        enabledLocations = enabledLocations
                    )
                }
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

@SuppressLint("MissingPermission")
private fun notifyCurrentSavedLocationStatus(
    context: Context,
    enabledLocations: List<SavedLocation>
) {
    if (enabledLocations.isEmpty()) {
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()

    fun notifyWith(location: Location) {
        notifyClosestSavedLocationStatus(
            context = context,
            currentLocation = location,
            enabledLocations = enabledLocations
        )
    }

    // Posizione FRESCA: lastLocation puo essere quella precedente (es. sull'emulatore non si
    // aggiorna finche nessuno richiede la posizione) e darebbe una distanza sbagliata.
    // lastLocation resta solo come ripiego, e solo se recente.
    fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationTokenSource.token
    ).addOnSuccessListener { freshLocation ->
        if (freshLocation != null) {
            notifyWith(freshLocation)
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                val ageMillis = last?.let {
                    (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000L
                }
                if (last != null && ageMillis != null && ageMillis < MAX_LAST_LOCATION_AGE_MILLIS) {
                    notifyWith(last)
                }
            }
        }
    }
}

private const val MAX_LAST_LOCATION_AGE_MILLIS = 60_000L

private fun notifyClosestSavedLocationStatus(
    context: Context,
    currentLocation: Location,
    enabledLocations: List<SavedLocation>
) {
    val closestLocationStatus = enabledLocations
        .map { savedLocation ->
            val distanceMeters = distanceBetweenMeters(
                currentLatitude = currentLocation.latitude,
                currentLongitude = currentLocation.longitude,
                savedLocation = savedLocation
            )

            SavedLocationDistanceStatus(
                savedLocation = savedLocation,
                distanceMeters = distanceMeters,
                isInside = distanceMeters <= savedLocation.geofenceRadiusMeters
            )
        }
        .minByOrNull { status -> status.distanceMeters }
        ?: return

    CoroutineScope(Dispatchers.IO).launch {
        val hasActiveParking = try {
            DatabaseProvider.getDatabase(context)
                .parkingDao()
                .getActiveSessionForSavedLocationId(closestLocationStatus.savedLocation.id) != null
        } catch (_: Exception) {
            false
        }

        SavedLocationGeofenceBroadcastReceiver.showSavedLocationStatusNotification(
            context = context,
            savedLocationId = closestLocationStatus.savedLocation.id,
            locationName = closestLocationStatus.savedLocation.name,
            isInside = closestLocationStatus.isInside,
            distanceMeters = closestLocationStatus.distanceMeters,
            hasActiveParking = hasActiveParking
        )
    }
}

private fun distanceBetweenMeters(
    currentLatitude: Double,
    currentLongitude: Double,
    savedLocation: SavedLocation
): Float {
    val results = FloatArray(1)

    Location.distanceBetween(
        currentLatitude,
        currentLongitude,
        savedLocation.latitude,
        savedLocation.longitude,
        results
    )

    return results[0]
}

private data class SavedLocationDistanceStatus(
    val savedLocation: SavedLocation,
    val distanceMeters: Float,
    val isInside: Boolean
)

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
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT
        )
        // Risposta piu rapida possibile (a scapito della batteria): utile per il demo.
        .setNotificationResponsiveness(0)
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