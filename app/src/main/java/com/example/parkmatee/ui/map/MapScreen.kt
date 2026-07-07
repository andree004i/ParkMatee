package com.example.parkmatee.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen(
    viewModel: MapViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val hasLocationPermission = hasFineLocationPermission(context)

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                fetchCurrentLocation(
                    context = context,
                    onLocationLoaded = viewModel::onUserLocationLoaded,
                    onLocationError = viewModel::onLocationError
                )
            } else {
                viewModel.onLocationError(
                    "Permesso posizione non concesso."
                )
            }
        }

    MapContent(
        uiState = uiState,
        hasLocationPermission = hasLocationPermission,
        onRequestCurrentLocation = {
            if (hasFineLocationPermission(context)) {
                fetchCurrentLocation(
                    context = context,
                    onLocationLoaded = viewModel::onUserLocationLoaded,
                    onLocationError = viewModel::onLocationError
                )
            } else {
                locationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    )
}

@Composable
private fun MapContent(
    uiState: MapUiState,
    hasLocationPermission: Boolean,
    onRequestCurrentLocation: () -> Unit
) {
    val defaultPosition = LatLng(
        44.4949,
        11.3426
    )

    val firstParkingPosition = uiState.activeParkings.firstOrNull()?.let { session ->
        LatLng(
            session.latitude,
            session.longitude
        )
    }

    val firstSavedLocationPosition = uiState.savedLocations.firstOrNull()?.let { location ->
        LatLng(
            location.latitude,
            location.longitude
        )
    }

    val userPosition =
        if (
            uiState.userLatitude != null &&
            uiState.userLongitude != null
        ) {
            LatLng(
                uiState.userLatitude,
                uiState.userLongitude
            )
        } else {
            null
        }

    val initialPosition =
        userPosition
            ?: firstParkingPosition
            ?: firstSavedLocationPosition
            ?: defaultPosition

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            initialPosition,
            13f
        )
    }

    var isMapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(
        isMapLoaded,
        uiState.userLatitude,
        uiState.userLongitude,
        uiState.activeParkings,
        uiState.savedLocations
    ) {
        if (!isMapLoaded) {
            return@LaunchedEffect
        }

        val target =
            userPosition
                ?: firstParkingPosition
                ?: firstSavedLocationPosition
                ?: defaultPosition

        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                target,
                15f
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Mappa")

        uiState.errorMessage?.let { message ->
            Text(text = message)
        }

        Button(
            onClick = onRequestCurrentLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Usa posizione attuale")
        }

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = hasLocationPermission
            ),
            onMapLoaded = {
                isMapLoaded = true
            }
        ) {
            userPosition?.let { position ->
                Marker(
                    state = MarkerState(position = position),
                    title = "Posizione attuale",
                    snippet = "Sei qui"
                )
            }

            uiState.savedLocations.forEach { location ->
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            location.latitude,
                            location.longitude
                        )
                    ),
                    title = location.name,
                    snippet = "Luogo salvato",
                    icon = BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_AZURE
                    )
                )
            }

            uiState.activeParkings.forEach { session ->
                val vehicleName = uiState.vehicles.firstOrNull { vehicle ->
                    vehicle.id == session.vehicleId
                }?.name ?: "Veicolo"

                Marker(
                    state = MarkerState(
                        position = LatLng(
                            session.latitude,
                            session.longitude
                        )
                    ),
                    title = vehicleName,
                    snippet = "Parcheggio attivo"
                )
            }
        }

        ActiveParkingMapSummary(
            uiState = uiState
        )
    }
}

@Composable
private fun ActiveParkingMapSummary(
    uiState: MapUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Parcheggi attivi: ${uiState.activeParkings.size}"
            )
            Text(
                text = "Luoghi salvati: ${uiState.savedLocations.size}"
            )

            if (uiState.activeParkings.isEmpty()) {
                Text(text = "Nessun parcheggio attivo da mostrare sulla mappa.")
            } else {
                uiState.activeParkings.forEach { session ->
                    val vehicleName = uiState.vehicles.firstOrNull { vehicle ->
                        vehicle.id == session.vehicleId
                    }?.name ?: "Veicolo sconosciuto"

                    Text(
                        text = "$vehicleName: ${session.latitude}, ${session.longitude}"
                    )
                }
            }
        }
    }
}

private fun hasFineLocationPermission(
    context: Context
): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    context: Context,
    onLocationLoaded: (Double, Double) -> Unit,
    onLocationError: (String) -> Unit
) {
    val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    val cancellationTokenSource = CancellationTokenSource()

    fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationTokenSource.token
    ).addOnSuccessListener { location ->
        if (location != null) {
            onLocationLoaded(
                location.latitude,
                location.longitude
            )
        } else {
            onLocationError(
                "Posizione non disponibile. Controlla GPS/emulatore."
            )
        }
    }.addOnFailureListener {
        onLocationError(
            "Errore durante il recupero della posizione."
        )
    }
}