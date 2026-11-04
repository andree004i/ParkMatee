package com.example.parkmatee.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.util.ParkingFormatters
import com.example.parkmatee.util.fetchCurrentLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay

private val DEFAULT_POSITION = LatLng(44.4949, 11.3426) // Bologna

// Colori dei marker delle soste attive, per tipo; i luoghi salvati sono sempre azzurri + cerchio.
private fun parkingHue(type: ParkingType): Float = when (type) {
    ParkingType.FREE -> BitmapDescriptorFactory.HUE_GREEN
    ParkingType.HOURLY -> BitmapDescriptorFactory.HUE_ORANGE
    ParkingType.FIXED -> BitmapDescriptorFactory.HUE_VIOLET
}

private fun parkingColor(type: ParkingType): Color = when (type) {
    ParkingType.FREE -> Color(0xFF4CAF50)
    ParkingType.HOURLY -> Color(0xFFFF9800)
    ParkingType.FIXED -> Color(0xFF9C27B0)
}

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenParking: () -> Unit = {}
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
                viewModel.onLocationError("Permesso posizione non concesso.")
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
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        },
        onEndParking = viewModel::onEndParkingClicked,
        onOpenParking = onOpenParking
    )
}

@Composable
private fun MapContent(
    uiState: MapUiState,
    hasLocationPermission: Boolean,
    onRequestCurrentLocation: () -> Unit,
    onEndParking: (ParkingSession) -> Unit,
    onOpenParking: () -> Unit
) {
    val userPosition = if (uiState.userLatitude != null && uiState.userLongitude != null) {
        LatLng(uiState.userLatitude, uiState.userLongitude)
    } else null

    val parkingPoints = uiState.activeParkings.map { LatLng(it.latitude, it.longitude) }
    val firstSaved = uiState.savedLocations.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            parkingPoints.firstOrNull() ?: userPosition ?: firstSaved ?: DEFAULT_POSITION,
            13f
        )
    }

    var isMapLoaded by remember { mutableStateOf(false) }
    // Filtri e selezione sopravvivono alla rotazione dello schermo.
    var showParkings by rememberSaveable { mutableStateOf(true) }
    var showSavedPlaces by rememberSaveable { mutableStateOf(true) }
    var selectedSessionId by rememberSaveable { mutableStateOf<Int?>(null) }
    // Incrementato da "Centra" per rifare l'inquadratura.
    var recenterTick by remember { mutableLongStateOf(0L) }

    // Sosta selezionata: sparisce da sola se nel frattempo viene chiusa
    val selectedSession = uiState.activeParkings.firstOrNull { it.id == selectedSessionId }

    // Inquadra tutte le soste attive (o la posizione utente / primo luogo salvato / Bologna)
    val parkingIdsKey = uiState.activeParkings.map { it.id }
    LaunchedEffect(isMapLoaded, parkingIdsKey, recenterTick) {
        if (!isMapLoaded) return@LaunchedEffect
        when {
            parkingPoints.size >= 2 -> {
                val bounds = LatLngBounds.builder().apply { parkingPoints.forEach { include(it) } }.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
            parkingPoints.size == 1 -> cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(parkingPoints.first(), 15f)
            )
            else -> cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(userPosition ?: firstSaved ?: DEFAULT_POSITION, 15f)
            )
        }
    }

    // Quando l'utente carica la sua posizione la mappa si sposta li (azione esplicita)
    LaunchedEffect(uiState.userLatitude, uiState.userLongitude) {
        if (isMapLoaded && userPosition != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(userPosition, 15f))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Mappa", style = MaterialTheme.typography.titleLarge)

        uiState.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showParkings,
                onClick = { showParkings = !showParkings },
                label = { Text("Soste attive (${uiState.activeParkings.size})") }
            )
            FilterChip(
                selected = showSavedPlaces,
                onClick = { showSavedPlaces = !showSavedPlaces },
                label = { Text("Luoghi salvati (${uiState.savedLocations.size})") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRequestCurrentLocation) { Text("Usa posizione attuale") }
            OutlinedButton(onClick = { recenterTick += 1 }) { Text("Centra") }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = hasLocationPermission
                ),
                onMapLoaded = { isMapLoaded = true },
                onMapClick = { selectedSessionId = null }
            ) {
                if (showSavedPlaces) {
                    uiState.savedLocations.forEach { location ->
                        key("saved-${location.id}") {
                            val pos = LatLng(location.latitude, location.longitude)
                            val markerState = rememberMarkerState(
                                key = "saved-${location.id}-${location.latitude}-${location.longitude}",
                                position = pos
                            )
                            Circle(
                                center = pos,
                                radius = location.geofenceRadiusMeters.toDouble(),
                                strokeWidth = if (location.geofenceEnabled) 4f else 2f,
                                strokeColor = if (location.geofenceEnabled) Color(0xFF1976D2) else Color(0xFF9E9E9E),
                                fillColor = if (location.geofenceEnabled) Color(0x331976D2) else Color(0x229E9E9E)
                            )
                            Marker(
                                state = markerState,
                                title = "Luogo salvato: ${location.name}",
                                snippet = if (location.geofenceEnabled) {
                                    "Geofence attivo - raggio ${location.geofenceRadiusMeters.toInt()} m"
                                } else {
                                    "Geofence non attivo - raggio ${location.geofenceRadiusMeters.toInt()} m"
                                },
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )
                        }
                    }
                }

                if (showParkings) {
                    // Un solo marker per sosta attiva; i veicoli non parcheggiati non compaiono.
                    uiState.activeParkings.forEach { session ->
                        key(session.id) {
                            val vehicleName = uiState.vehicles.firstOrNull { it.id == session.vehicleId }?.name
                                ?: "Veicolo"
                            val markerState = rememberMarkerState(
                                key = "parking-${session.id}",
                                position = LatLng(session.latitude, session.longitude)
                            )
                            Marker(
                                state = markerState,
                                title = vehicleName,
                                snippet = ParkingFormatters.formatParkingType(session.type),
                                icon = BitmapDescriptorFactory.defaultMarker(parkingHue(session.type)),
                                onClick = {
                                    selectedSessionId = session.id
                                    false
                                }
                            )
                        }
                    }
                }
            }
        }

        MapLegend()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val session = selectedSession
            if (session != null) {
                val vehicleName = uiState.vehicles.firstOrNull { it.id == session.vehicleId }?.name ?: "Veicolo"
                SelectedParkingCard(
                    session = session,
                    vehicleName = vehicleName,
                    onEnd = {
                        selectedSessionId = null
                        onEndParking(session)
                    },
                    onOpen = onOpenParking
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (uiState.activeParkings.isEmpty()) {
                            "Nessun veicolo parcheggiato al momento."
                        } else {
                            "Tocca un marker per vedere i dettagli della sosta."
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParkingType.entries.forEach { type ->
            LegendDot(parkingColor(type), type.label)
        }
        LegendDot(Color(0xFF1976D2), "Luogo salvato")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SelectedParkingCard(
    session: ParkingSession,
    vehicleName: String,
    onEnd: () -> Unit,
    onOpen: () -> Unit
) {
    // Ticker confinato alla card e legato al ciclo di vita: la mappa non si ricompone ogni secondo
    // e il timer si ferma quando l'app va in background.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val elapsed = (nowMillis - session.startTime).coerceAtLeast(0L)
    val cost = ParkingFormatters.calculateLiveCost(session, elapsed)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = vehicleName, style = MaterialTheme.typography.titleMedium)
            Text(text = "Tipo di sosta: ${ParkingFormatters.formatParkingType(session.type)}")
            Text(text = "Inizio: ${ParkingFormatters.formatTime(session.startTime, "dd/MM HH:mm")}")
            Text(text = "Tempo trascorso: ${ParkingFormatters.formatDuration(elapsed)}")
            Text(text = "Costo attuale: ${ParkingFormatters.formatCurrency(cost)}")

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onEnd) { Text("Termina") }
                OutlinedButton(onClick = onOpen) { Text("Dettaglio") }
            }
        }
    }
}

private fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
