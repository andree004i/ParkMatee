package com.example.parkmatee.ui.savedlocations

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.ui.parking.ParkingGeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

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

        val geofences = enabledLocations.map { location ->
            createSavedLocationGeofence(location)
        }

        uiState = SavedLocationGeofenceUiState(
            active = true,
            monitoredCount = enabledLocations.size,
            message = "Aggiornamento geofence dei luoghi salvati in corso..."
        )

        geofencingClient.removeGeofences(pendingIntent)
            .addOnCompleteListener {
                registerSavedLocationGeofences(
                    context = context,
                    geofencingRequest = GeofencingRequest.Builder()
                        .addGeofences(geofences)
                        .build(),
                    pendingIntent = pendingIntent,
                    onSuccess = {
                        uiState = SavedLocationGeofenceUiState(
                            active = true,
                            monitoredCount = enabledLocations.size,
                            message = "Geofencing attivo sui luoghi salvati selezionati."
                        )

                        notifyCurrentSavedLocationStatus(
                            context = context,
                            enabledLocations = enabledLocations
                        )
                    },
                    onError = {
                        uiState = SavedLocationGeofenceUiState(
                            active = false,
                            monitoredCount = enabledLocations.size,
                            message = "Errore durante l'attivazione del geofencing."
                        )
                    }
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
private fun registerSavedLocationGeofences(
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

    fusedLocationClient.lastLocation
        .addOnSuccessListener { lastKnownLocation ->
            lastKnownLocation?.let { location ->
                notifyClosestSavedLocationStatus(
                    context = context,
                    currentLocation = location,
                    enabledLocations = enabledLocations
                )
            }
        }

    fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationTokenSource.token
    ).addOnSuccessListener { freshLocation ->
        if (freshLocation == null) {
            return@addOnSuccessListener
        }

        notifyClosestSavedLocationStatus(
            context = context,
            currentLocation = freshLocation,
            enabledLocations = enabledLocations
        )
    }
}

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

    val hasActiveParking = DatabaseProviderAccess.hasActiveParkingForSavedLocation(
        context = context,
        savedLocationName = closestLocationStatus.savedLocation.name
    )

    ParkingGeofenceBroadcastReceiver.showSavedLocationStatusNotification(
        context = context,
        locationName = closestLocationStatus.savedLocation.name,
        isInside = closestLocationStatus.isInside,
        distanceMeters = closestLocationStatus.distanceMeters,
        hasActiveParking = hasActiveParking
    )
}

private object DatabaseProviderAccess {
    fun hasActiveParkingForSavedLocation(
        context: Context,
        savedLocationName: String
    ): Boolean {
        return try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                com.example.parkmatee.data.db.DatabaseProvider.getDatabase(context)
                    .parkingDao()
                    .getActiveSessionForSavedLocation(savedLocationName) != null
            }
        } catch (exception: Exception) {
            false
        }
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

private fun createSavedLocationGeofence(
    location: SavedLocation
): Geofence {
    return Geofence.Builder()
        .setRequestId(createSavedLocationRequestId(location))
        .setCircularRegion(
            location.latitude,
            location.longitude,
            location.geofenceRadiusMeters
        )
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT
        )
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .build()
}

private fun createSavedLocationRequestId(location: SavedLocation): String {
    val safeName = location.name
        .replace("_", " ")
        .take(40)

    return "saved_location_${location.id}_$safeName"
}

private fun createSavedLocationGeofencePendingIntent(
    context: Context
): PendingIntent {
    val intent = Intent(
        context,
        ParkingGeofenceBroadcastReceiver::class.java
    )

    return PendingIntent.getBroadcast(
        context,
        SAVED_LOCATION_GEOFENCE_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
}

private const val SAVED_LOCATION_GEOFENCE_REQUEST_CODE = 91_000
