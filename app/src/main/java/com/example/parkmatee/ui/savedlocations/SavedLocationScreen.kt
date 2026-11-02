package com.example.parkmatee.ui.savedlocations

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import com.example.parkmatee.util.fetchCurrentLocation
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.rememberMarkerState
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.parkmatee.data.entity.SavedLocation
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun SavedLocationScreen(
    viewModel: SavedLocationViewModel,
    savedLocationId: Int = -1
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
    var formMode by remember { mutableStateOf<SavedLocationFormMode?>(null) }
    var pendingGeofenceLocation by remember { mutableStateOf<SavedLocation?>(null) }
    var permissionRefreshKey by remember { mutableLongStateOf(0L) }
    var geofencePermissionWarning by remember { mutableStateOf<String?>(null) }
    val deleteRequest by viewModel.deleteRequest.collectAsState()

    // Flag per garantire che il deep-link/notifica venga gestito UNA SOLA VOLTA
    // e non si riattivi a ogni aggiornamento della lista 'locations' da Room
    var hasHandledDeepLink by remember(savedLocationId) { mutableStateOf(false) }

    // Intercetta savedLocationId arrivato dalla navigazione/notifica
    LaunchedEffect(savedLocationId, locations) {
        if (savedLocationId != -1 && !hasHandledDeepLink) {
            val locationToEdit = locations.firstOrNull { it.id == savedLocationId }
            if (locationToEdit != null) {
                formMode = SavedLocationFormMode.Edit(locationToEdit)
                hasHandledDeepLink = true
            }
        }
    }

    val geofenceUiState = rememberSavedLocationGeofenceState(
        context = context,
        savedLocations = locations,
        permissionRefreshKey = permissionRefreshKey
    )

    fun refreshPermissions() {
        permissionRefreshKey = System.currentTimeMillis()
    }

    fun enableGeofenceIfPossible(location: SavedLocation) {
        if (hasSavedLocationGeofencePermission(context)) {
            viewModel.setGeofenceEnabled(
                location = location,
                enabled = true
            )
            refreshPermissions()
        } else {
            pendingGeofenceLocation = location
        }
    }

    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val location = pendingGeofenceLocation
        if (location != null && hasSavedLocationGeofencePermission(context)) {
            viewModel.setGeofenceEnabled(
                location = location,
                enabled = true
            )
            geofencePermissionWarning = null
        } else if (location != null) {
            geofencePermissionWarning = GEOFENCE_PERMISSION_WARNING
        }
        pendingGeofenceLocation = null
        refreshPermissions()
    }

    val fineLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val location = pendingGeofenceLocation
        if (location != null && !hasFineLocationPermission(context)) {
            geofencePermissionWarning = GEOFENCE_PERMISSION_WARNING
            pendingGeofenceLocation = null
        } else if (location != null) {
            if (needsBackgroundLocationPermission(context)) {
                backgroundLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            } else {
                enableGeofenceIfPossible(location)
            }
        }
        refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    fun requestGeofencePermissionsFor(location: SavedLocation) {
        pendingGeofenceLocation = location

        if (!hasFineLocationPermission(context)) {
            fineLocationPermissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            return
        }

        if (needsBackgroundLocationPermission(context)) {
            backgroundLocationPermissionLauncher.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            return
        }

        enableGeofenceIfPossible(location)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            refreshPermissions()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    formMode = SavedLocationFormMode.Add
                }
            ) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Luoghi salvati",
                style = MaterialTheme.typography.titleLarge
            )

            SavedLocationGeofenceSection(
                geofenceUiState = geofenceUiState,
                notificationsEnabled = canShowSavedLocationGeofenceNotifications(context),
                onEnableNotificationsClicked = ::requestNotificationPermission,
                permissionWarning = geofencePermissionWarning,
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            )

            formMode?.let { mode ->
                SavedLocationForm(
                    mode = mode,
                    onSave = { name, latitude, longitude, radius, onError ->
                        viewModel.saveLocation(
                            editing = (mode as? SavedLocationFormMode.Edit)?.location,
                            name = name,
                            latitude = latitude,
                            longitude = longitude,
                            radiusMeters = radius
                        ) { error ->
                            if (error == null) formMode = null else onError(error)
                        }
                    },
                    onCancel = {
                        formMode = null
                    }
                )
            }

            if (locations.isEmpty()) {
                Text(text = "Non ci sono ancora luoghi salvati.")
            } else {
                locations.forEach { location ->
                    SavedLocationItem(
                        location = location,
                        onEdit = {
                            formMode = SavedLocationFormMode.Edit(it)
                        },
                        onDelete = viewModel::onDeleteRequested,
                        onToggleGeofence = { selectedLocation ->
                            if (selectedLocation.geofenceEnabled) {
                                viewModel.setGeofenceEnabled(
                                    location = selectedLocation,
                                    enabled = false
                                )
                                refreshPermissions()
                            } else {
                                requestGeofencePermissionsFor(selectedLocation)
                            }
                        }
                    )
                }
            }
        }
    }

    deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text("Eliminare ${request.location.name}?") },
            text = {
                Text(
                    if (request.hasActiveParking) {
                        "Hai una sosta attiva in questo luogo: resta attiva e dovrai chiuderla a mano. Le soste gia registrate restano nello storico."
                    } else {
                        "Le soste gia registrate restano nello storico. Il geofence di questo luogo verra rimosso."
                    }
                )
            },
            confirmButton = {
                Button(onClick = viewModel::onDeleteConfirmed) { Text("Elimina") }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::onDeleteDismissed) { Text("Annulla") }
            }
        )
    }
}

private const val GEOFENCE_PERMISSION_WARNING =
    "Per il geofence servono la posizione precisa e la posizione in background (\"Sempre\"). Abilitale dalle impostazioni dell'app."

@Composable
private fun SavedLocationGeofenceSection(
    geofenceUiState: SavedLocationGeofenceUiState,
    notificationsEnabled: Boolean,
    onEnableNotificationsClicked: () -> Unit,
    permissionWarning: String?,
    onOpenAppSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Geofencing luoghi salvati",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (geofenceUiState.active) {
                    "Stato: attivo"
                } else {
                    "Stato: non attivo"
                }
            )
            Text(text = geofenceUiState.message)
            Text(text = "Luoghi monitorati: ${geofenceUiState.monitoredCount}")
            Text(text = "Entrata: propone di avviare un parcheggio.")
            Text(text = "Uscita: propone di terminare il parcheggio rilevante.")

            permissionWarning?.let { warning ->
                Text(text = warning)
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Apri impostazioni app")
                }
            }

            if (!notificationsEnabled) {
                Button(
                    onClick = onEnableNotificationsClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Abilita notifiche geofence")
                }
            }
        }
    }
}

@Composable
private fun SavedLocationItem(
    location: SavedLocation,
    onEdit: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit,
    onToggleGeofence: (SavedLocation) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "${location.latitude}, ${location.longitude}")
            Text(
                text = if (location.geofenceEnabled) {
                    "Geofence: attivo (${location.geofenceRadiusMeters.toInt()} m)"
                } else {
                    "Geofence: non attivo"
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEdit(location) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Modifica")
                }

                Button(
                    onClick = { onToggleGeofence(location) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (location.geofenceEnabled) {
                            "Disattiva geofence"
                        } else {
                            "Attiva geofence"
                        }
                    )
                }
            }

            Button(
                onClick = { onDelete(location) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Elimina")
            }
        }
    }
}

@Composable
private fun SavedLocationForm(
    mode: SavedLocationFormMode,
    onSave: (String, Double, Double, Float, (String) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val editingLocation = (mode as? SavedLocationFormMode.Edit)?.location

    var name by remember(editingLocation?.id) {
        mutableStateOf(editingLocation?.name ?: "")
    }
    // In creazione i campi partono vuoti: niente luogo "Bologna" salvato per sbaglio.
    var latitude by remember(editingLocation?.id) {
        mutableStateOf(editingLocation?.latitude?.toString() ?: "")
    }
    var longitude by remember(editingLocation?.id) {
        mutableStateOf(editingLocation?.longitude?.toString() ?: "")
    }
    var radius by remember(editingLocation?.id) {
        mutableStateOf(editingLocation?.geofenceRadiusMeters ?: DEFAULT_RADIUS_METERS)
    }
    var errorMessage by remember(editingLocation?.id) {
        mutableStateOf<String?>(null)
    }
    var isLoadingGps by remember { mutableStateOf(false) }

    fun loadGpsPosition() {
        isLoadingGps = true
        fetchCurrentLocation(
            context = context,
            onLocationLoaded = { lat, lng ->
                latitude = lat.toString()
                longitude = lng.toString()
                errorMessage = null
                isLoadingGps = false
            },
            onLocationError = { message ->
                errorMessage = message
                isLoadingGps = false
            }
        )
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadGpsPosition()
        } else {
            errorMessage = "Permesso posizione non concesso: scegli il punto sulla mappa o inserisci le coordinate."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = when (mode) {
                    SavedLocationFormMode.Add -> "Aggiungi luogo"
                    is SavedLocationFormMode.Edit -> "Modifica luogo"
                },
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = { Text(text = "Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (hasFineLocationPermission(context)) {
                        loadGpsPosition()
                    } else {
                        gpsPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                enabled = !isLoadingGps,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (isLoadingGps) "Recupero posizione..." else "Usa posizione attuale (GPS)")
            }

            SavedLocationPickerMap(
                latitude = latitude,
                longitude = longitude,
                onPositionSelected = { position ->
                    latitude = position.latitude.toString()
                    longitude = position.longitude.toString()
                    errorMessage = null
                }
            )

            OutlinedTextField(
                value = latitude,
                onValueChange = {
                    latitude = it
                    errorMessage = null
                },
                label = { Text(text = "Latitudine") },
                supportingText = {
                    Text(text = "Dal GPS, dalla mappa o a mano")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = longitude,
                onValueChange = {
                    longitude = it
                    errorMessage = null
                },
                label = { Text(text = "Longitudine") },
                supportingText = {
                    Text(text = "Dal GPS, dalla mappa o a mano")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(text = "Raggio del geofence")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RADIUS_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = radius == option,
                        onClick = { radius = option },
                        label = { Text(text = "${option.toInt()} m") }
                    )
                }
            }

            errorMessage?.let { message ->
                Text(text = message)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Annulla")
                }

                Button(
                    onClick = {
                        val parsedLatitude = latitude.toCoordinateOrNull()
                        val parsedLongitude = longitude.toCoordinateOrNull()

                        when {
                            name.isBlank() -> {
                                errorMessage = "Inserisci un nome."
                            }

                            parsedLatitude == null || parsedLatitude !in -90.0..90.0 -> {
                                errorMessage = "Scegli un punto (GPS, mappa) o inserisci una latitudine valida."
                            }

                            parsedLongitude == null || parsedLongitude !in -180.0..180.0 -> {
                                errorMessage = "Scegli un punto (GPS, mappa) o inserisci una longitudine valida."
                            }

                            else -> {
                                onSave(name, parsedLatitude, parsedLongitude, radius) { message ->
                                    errorMessage = message
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when (mode) {
                            SavedLocationFormMode.Add -> "Aggiungi"
                            is SavedLocationFormMode.Edit -> "Salva"
                        }
                    )
                }
            }
        }
    }
}

private const val DEFAULT_RADIUS_METERS = 120f
private val RADIUS_OPTIONS = listOf(100f, 120f, 150f, 200f, 300f)

@Composable
private fun SavedLocationPickerMap(
    latitude: String,
    longitude: String,
    onPositionSelected: (LatLng) -> Unit
) {
    val defaultPosition = LatLng(
        44.4949,
        11.3426
    )

    val selectedPosition = parseLatLng(
        latitude = latitude,
        longitude = longitude
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            selectedPosition ?: defaultPosition,
            14f
        )
    }
    val markerState = rememberMarkerState(position = selectedPosition ?: defaultPosition)

    // La mappa segue la posizione scelta da GPS o campi di testo.
    // (Niente CameraUpdateFactory: richiede la mappa gia inizializzata.)
    LaunchedEffect(selectedPosition) {
        selectedPosition?.let { position ->
            markerState.position = position
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                position,
                cameraPositionState.position.zoom
            )
        }
    }

    // Fine del trascinamento: la posizione del marker diventa quella salvata.
    LaunchedEffect(markerState.dragState) {
        if (markerState.dragState == DragState.END) {
            onPositionSelected(markerState.position)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Tocca la mappa o trascina il marker per scegliere le coordinate")

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
            ),
            onMapClick = onPositionSelected
        ) {
            if (selectedPosition != null) {
                Marker(
                    state = markerState,
                    draggable = true,
                    title = "Luogo salvato"
                )
            }
        }
    }
}

private sealed class SavedLocationFormMode {
    data object Add : SavedLocationFormMode()
    data class Edit(val location: SavedLocation) : SavedLocationFormMode()
}

private fun parseLatLng(
    latitude: String,
    longitude: String
): LatLng? {
    val parsedLatitude = latitude.toCoordinateOrNull()
    val parsedLongitude = longitude.toCoordinateOrNull()

    if (parsedLatitude == null || parsedLongitude == null) {
        return null
    }

    if (parsedLatitude !in -90.0..90.0 || parsedLongitude !in -180.0..180.0) {
        return null
    }

    return LatLng(
        parsedLatitude,
        parsedLongitude
    )
}

private fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun needsBackgroundLocationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
}

private fun String.toCoordinateOrNull(): Double? {
    return replace(',', '.').toDoubleOrNull()
}