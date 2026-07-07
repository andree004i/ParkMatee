package com.example.parkmatee.ui.parking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.SavedLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ParkingScreen(
    viewModel: ParkingViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var notificationPermissionRefresh by remember { mutableLongStateOf(0L) }
    var geofenceEnabled by remember { mutableStateOf(false) }
    var geofencePermissionRefresh by remember { mutableLongStateOf(0L) }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                fetchCurrentLocation(
                    context = context,
                    onLocationLoaded = viewModel::onCurrentLocationLoaded,
                    onLocationError = viewModel::onLocationError
                )
            } else {
                viewModel.onLocationError(
                    "Permesso posizione non concesso."
                )
            }
        }

    val takePictureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            val photoPath = pendingPhotoPath

            if (success && photoPath != null) {
                viewModel.onPhotoCaptured(photoPath)
            } else {
                viewModel.onPhotoCaptureError(
                    "Foto non scattata."
                )
            }

            pendingPhotoPath = null
            pendingPhotoUri = null
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            val photoUri = pendingPhotoUri

            if (isGranted && photoUri != null) {
                takePictureLauncher.launch(photoUri)
            } else {
                pendingPhotoPath = null
                pendingPhotoUri = null
                viewModel.onPhotoCaptureError(
                    "Permesso fotocamera non concesso."
                )
            }
        }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            notificationPermissionRefresh = System.currentTimeMillis()
        }

    val geofenceLocationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            geofencePermissionRefresh = System.currentTimeMillis()
            geofenceEnabled = isGranted
        }

    LaunchedEffect(Unit) {
        ensureParkingNotificationChannel(context)
    }

    ParkingNotificationEffect(
        context = context,
        activeParkings = uiState.activeParkings
    )

    val geofenceUiState = rememberParkingGeofenceState(
        context = context,
        activeParkings = uiState.activeParkings,
        enabled = geofenceEnabled,
        permissionRefreshKey = geofencePermissionRefresh
    )

    val parkingNotificationsEnabled = remember(notificationPermissionRefresh) {
        canShowParkingNotifications(context)
    }

    fun startPhotoCapture() {
        try {
            val photoFile = createParkingPhotoFile(context)
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )

            pendingPhotoPath = photoFile.absolutePath
            pendingPhotoUri = photoUri

            if (hasCameraPermission(context)) {
                takePictureLauncher.launch(photoUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (exception: Exception) {
            pendingPhotoPath = null
            pendingPhotoUri = null
            viewModel.onPhotoCaptureError(
                "Errore durante la preparazione della fotocamera."
            )
        }
    }

    ParkingContent(
        uiState = uiState,
        parkingNotificationsEnabled = parkingNotificationsEnabled,
        geofenceUiState = geofenceUiState,
        onVehicleSelected = viewModel::onVehicleSelected,
        onParkingTypeSelected = viewModel::onParkingTypeSelected,
        onHourlyRateChanged = viewModel::onHourlyRateChanged,
        onFixedCostChanged = viewModel::onFixedCostChanged,
        onExpiryMinutesChanged = viewModel::onExpiryMinutesChanged,
        onLatitudeChanged = viewModel::onLatitudeChanged,
        onLongitudeChanged = viewModel::onLongitudeChanged,
        onSavedLocationSelected = viewModel::onSavedLocationSelected,
        onUseCurrentLocationClicked = {
            if (hasFineLocationPermission(context)) {
                fetchCurrentLocation(
                    context = context,
                    onLocationLoaded = viewModel::onCurrentLocationLoaded,
                    onLocationError = viewModel::onLocationError
                )
            } else {
                locationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        },
        onTakePhotoClicked = ::startPhotoCapture,
        onRemovePhotoClicked = viewModel::onRemovePhotoClicked,
        onEnableNotificationsClicked = {
            ensureParkingNotificationChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPermissionRefresh = System.currentTimeMillis()
            }
        },
        onEnableGeofenceClicked = {
            if (hasGeofenceLocationPermission(context)) {
                geofencePermissionRefresh = System.currentTimeMillis()
                geofenceEnabled = true
            } else {
                geofenceLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        },
        onDisableGeofenceClicked = {
            geofenceEnabled = false
            geofencePermissionRefresh = System.currentTimeMillis()
        },
        onNoteChanged = viewModel::onNoteChanged,
        onStartParkingClicked = viewModel::onStartParkingClicked,
        onEndParkingClicked = viewModel::onEndParkingClicked
    )
}

@Composable
private fun ParkingContent(
    uiState: ParkingUiState,
    parkingNotificationsEnabled: Boolean,
    geofenceUiState: ParkingGeofenceUiState,
    onVehicleSelected: (Int) -> Unit,
    onParkingTypeSelected: (ParkingType) -> Unit,
    onHourlyRateChanged: (String) -> Unit,
    onFixedCostChanged: (String) -> Unit,
    onExpiryMinutesChanged: (String) -> Unit,
    onLatitudeChanged: (String) -> Unit,
    onLongitudeChanged: (String) -> Unit,
    onSavedLocationSelected: (Int?) -> Unit,
    onUseCurrentLocationClicked: () -> Unit,
    onTakePhotoClicked: () -> Unit,
    onRemovePhotoClicked: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableGeofenceClicked: () -> Unit,
    onDisableGeofenceClicked: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onStartParkingClicked: () -> Unit,
    onEndParkingClicked: (ParkingSession) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Nuovo parcheggio")

        VehicleSelector(
            uiState = uiState,
            onVehicleSelected = onVehicleSelected
        )

        Text(text = "Tipo di parcheggio")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ParkingType.values().forEach { type ->
                FilterChip(
                    selected = uiState.selectedType == type,
                    onClick = {
                        onParkingTypeSelected(type)
                    },
                    label = {
                        Text(text = type.label)
                    }
                )
            }
        }

        when (uiState.selectedType) {
            ParkingType.FREE -> Unit

            ParkingType.HOURLY -> {
                OutlinedTextField(
                    value = uiState.hourlyRate,
                    onValueChange = onHourlyRateChanged,
                    label = {
                        Text(text = "Tariffa oraria")
                    },
                    supportingText = {
                        Text(text = "Esempio: 1.50")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParkingType.FIXED -> {
                OutlinedTextField(
                    value = uiState.fixedCost,
                    onValueChange = onFixedCostChanged,
                    label = {
                        Text(text = "Costo fisso")
                    },
                    supportingText = {
                        Text(text = "Esempio: 3.00")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.expiryMinutes,
                    onValueChange = onExpiryMinutesChanged,
                    label = {
                        Text(text = "Durata ticket in minuti")
                    },
                    supportingText = {
                        Text(text = "Esempio: 120")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(text = "Posizione parcheggio")

        SavedLocationSelector(
            savedLocations = uiState.savedLocations,
            selectedSavedLocationId = uiState.selectedSavedLocationId,
            onSavedLocationSelected = onSavedLocationSelected
        )

        Button(
            onClick = onUseCurrentLocationClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Usa posizione attuale")
        }

        ParkingPositionPickerMap(
            latitude = uiState.latitude,
            longitude = uiState.longitude,
            onPositionSelected = { position ->
                onLatitudeChanged(position.latitude.toString())
                onLongitudeChanged(position.longitude.toString())
            }
        )

        OutlinedTextField(
            value = uiState.latitude,
            onValueChange = onLatitudeChanged,
            label = {
                Text(text = "Latitudine")
            },
            supportingText = {
                Text(text = "Compilata da luogo salvato, GPS, mappa o a mano")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.longitude,
            onValueChange = onLongitudeChanged,
            label = {
                Text(text = "Longitudine")
            },
            supportingText = {
                Text(text = "Compilata da luogo salvato, GPS, mappa o a mano")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
        )

        ParkingPhotoSection(
            photoPath = uiState.photoPath,
            onTakePhotoClicked = onTakePhotoClicked,
            onRemovePhotoClicked = onRemovePhotoClicked
        )

        OutlinedTextField(
            value = uiState.note,
            onValueChange = onNoteChanged,
            label = {
                Text(text = "Nota")
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        uiState.errorMessage?.let { message ->
            Text(text = message)
        }

        uiState.successMessage?.let { message ->
            Text(text = message)
        }

        NotificationReminderSection(
            notificationsEnabled = parkingNotificationsEnabled,
            activeParkings = uiState.activeParkings,
            onEnableNotificationsClicked = onEnableNotificationsClicked
        )

        ParkingGeofenceSection(
            geofenceUiState = geofenceUiState,
            onEnableGeofenceClicked = onEnableGeofenceClicked,
            onDisableGeofenceClicked = onDisableGeofenceClicked
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onStartParkingClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Avvia parcheggio")
        }

        Spacer(modifier = Modifier.height(12.dp))

        ActiveParkingsSection(
            uiState = uiState,
            onEndParkingClicked = onEndParkingClicked
        )
    }
}

@Composable
private fun ParkingGeofenceSection(
    geofenceUiState: ParkingGeofenceUiState,
    onEnableGeofenceClicked: () -> Unit,
    onDisableGeofenceClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Geofencing parcheggio")
            Text(
                text = if (geofenceUiState.enabled) {
                    "Stato: attivo"
                } else {
                    "Stato: non attivo"
                }
            )
            Text(text = geofenceUiState.message)
            Text(text = "Raggio monitorato: 120 metri")
            Text(text = "Parcheggi monitorati: ${geofenceUiState.monitoredCount}")

            if (geofenceUiState.enabled) {
                OutlinedButton(
                    onClick = onDisableGeofenceClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Disattiva geofencing")
                }
            } else {
                Button(
                    onClick = onEnableGeofenceClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Attiva geofencing")
                }
            }
        }
    }
}

@Composable
private fun NotificationReminderSection(
    notificationsEnabled: Boolean,
    activeParkings: List<ParkingSession>,
    onEnableNotificationsClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Notifiche parcheggio")
            Text(
                text = if (notificationsEnabled) {
                    "Stato: attive"
                } else {
                    "Stato: non attive"
                }
            )
            Text(text = "Reminder parcheggio attivo: notifica subito e poi ogni 15 minuti.")
            Text(text = "Ticket in scadenza: avviso quando mancano 5 minuti e alla scadenza.")
            Text(text = "Parcheggi monitorati: ${activeParkings.size}")

            if (!notificationsEnabled) {
                Button(
                    onClick = onEnableNotificationsClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Abilita notifiche")
                }
            }
        }
    }
}

@Composable
private fun ParkingPhotoSection(
    photoPath: String?,
    onTakePhotoClicked: () -> Unit,
    onRemovePhotoClicked: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Foto parcheggio")

        Button(
            onClick = onTakePhotoClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (photoPath == null) "Scatta foto" else "Scatta nuova foto")
        }

        photoPath?.let { path ->
            ParkingPhotoPreview(photoPath = path)

            OutlinedButton(
                onClick = onRemovePhotoClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Rimuovi foto")
            }
        }
    }
}

@Composable
private fun ParkingPhotoPreview(
    photoPath: String
) {
    val imageBitmap = remember(photoPath) {
        BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Foto parcheggio",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Text(text = "Anteprima foto non disponibile.")
    }
}

@Composable
private fun SavedLocationSelector(
    savedLocations: List<SavedLocation>,
    selectedSavedLocationId: Int?,
    onSavedLocationSelected: (Int?) -> Unit
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    val selectedLocation = savedLocations.firstOrNull { location ->
        location.id == selectedSavedLocationId
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "Luogo salvato")

        if (savedLocations.isEmpty()) {
            Text(text = "Nessun luogo salvato. Aggiungilo dalla schermata Luoghi.")
        } else {
            Box {
                OutlinedButton(
                    onClick = {
                        menuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedLocation?.name
                            ?: "Seleziona un luogo salvato"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                    }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(text = "Nessun luogo salvato")
                        },
                        onClick = {
                            onSavedLocationSelected(null)
                            menuExpanded = false
                        }
                    )

                    savedLocations.forEach { location ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${location.name} (${location.latitude}, ${location.longitude})"
                                )
                            },
                            onClick = {
                                onSavedLocationSelected(location.id)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkingPositionPickerMap(
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

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Tocca la mappa per scegliere il punto del parcheggio")

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
            ),
            onMapClick = onPositionSelected
        ) {
            selectedPosition?.let { position ->
                Marker(
                    state = MarkerState(position = position),
                    title = "Posizione parcheggio"
                )
            }
        }
    }
}

@Composable
private fun VehicleSelector(
    uiState: ParkingUiState,
    onVehicleSelected: (Int) -> Unit
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    val selectedVehicle = uiState.vehicles.firstOrNull { vehicle ->
        vehicle.id == uiState.selectedVehicleId
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "Veicolo")

        if (uiState.vehicles.isEmpty()) {
            Text(
                text = "Non ci sono veicoli disponibili. Aggiungi prima un veicolo dalla schermata dedicata."
            )
        } else {
            Box {
                OutlinedButton(
                    onClick = {
                        menuExpanded = true
                    }
                ) {
                    Text(
                        text = selectedVehicle?.name
                            ?: "Seleziona un veicolo"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                    }
                ) {
                    uiState.vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${vehicle.name} (${vehicle.type})"
                                )
                            },
                            onClick = {
                                onVehicleSelected(vehicle.id)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveParkingsSection(
    uiState: ParkingUiState,
    onEndParkingClicked: (ParkingSession) -> Unit
) {
    var nowMillis by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Text(text = "Parcheggi attivi")

    if (uiState.activeParkings.isEmpty()) {
        Text(text = "Nessun parcheggio attivo.")
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.activeParkings.forEach { session ->
                ActiveParkingItem(
                    session = session,
                    vehicleName = uiState.vehicles.firstOrNull { vehicle ->
                        vehicle.id == session.vehicleId
                    }?.name ?: "Veicolo sconosciuto",
                    nowMillis = nowMillis,
                    onEndParkingClicked = onEndParkingClicked
                )
            }
        }
    }
}

@Composable
private fun ActiveParkingItem(
    session: ParkingSession,
    vehicleName: String,
    nowMillis: Long,
    onEndParkingClicked: (ParkingSession) -> Unit
) {
    val elapsedMillis = (nowMillis - session.startTime).coerceAtLeast(0L)
    val liveCost = calculateLiveCost(
        session = session,
        elapsedMillis = elapsedMillis
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = vehicleName)
            Text(text = "Tipo: ${formatParkingType(session.type)}")
            Text(text = "Inizio: ${formatTime(session.startTime)}")
            Text(text = "Tempo trascorso: ${formatDuration(elapsedMillis)}")
            Text(text = "Costo attuale: ${formatCurrency(liveCost)}")
            Text(text = "Posizione: ${session.latitude}, ${session.longitude}")

            session.savedLocationName?.let { savedLocationName ->
                Text(text = "Luogo: $savedLocationName")
            }

            session.photoPath?.let {
                ParkingPhotoPreview(photoPath = it)
            }

            session.hourlyRate?.let { hourlyRate ->
                Text(text = "Tariffa: ${formatCurrency(hourlyRate)} / ora")
            }

            session.fixedCost?.let { fixedCost ->
                Text(text = "Costo fisso: ${formatCurrency(fixedCost)}")
            }

            session.expiryTime?.let { expiryTime ->
                Text(text = "Scadenza: ${formatTime(expiryTime)}")
                Text(
                    text = "Countdown ticket: ${formatTicketCountdown(expiryTime - nowMillis)}"
                )
            }

            session.note?.let { note ->
                Text(text = "Nota: $note")
            }

            Button(
                onClick = {
                    onEndParkingClicked(session)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Termina parcheggio")
            }
        }
    }
}

private fun formatParkingType(
    type: String
): String {
    return when (type) {
        "free" -> "Gratuito"
        "hourly" -> "A ore"
        "fixed" -> "Ticket fisso"
        else -> type
    }
}

private fun calculateLiveCost(
    session: ParkingSession,
    elapsedMillis: Long
): Double {
    return when (session.type) {
        "hourly" -> {
            val hourlyRate = session.hourlyRate ?: 0.0
            hourlyRate * elapsedMillis.toDouble() / MILLIS_PER_HOUR
        }
        "fixed" -> session.fixedCost ?: 0.0
        else -> 0.0
    }
}

private fun formatCurrency(
    value: Double
): String {
    return String.format(
        Locale.getDefault(),
        "%.2f euro",
        value
    )
}

private fun formatDuration(
    durationMillis: Long
): String {
    val totalSeconds = durationMillis / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE

    return if (hours > 0) {
        String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    } else {
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}

private fun formatTicketCountdown(
    remainingMillis: Long
): String {
    return if (remainingMillis <= 0L) {
        "Scaduto da ${formatDuration(-remainingMillis)}"
    } else {
        formatDuration(remainingMillis)
    }
}

private fun formatTime(
    timestamp: Long
): String {
    val formatter = SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    )

    return formatter.format(Date(timestamp))
}

private fun parseLatLng(
    latitude: String,
    longitude: String
): LatLng? {
    val parsedLatitude = latitude.replace(',', '.').toDoubleOrNull()
    val parsedLongitude = longitude.replace(',', '.').toDoubleOrNull()

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

private fun hasFineLocationPermission(
    context: Context
): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasCameraPermission(
    context: Context
): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

private fun createParkingPhotoFile(
    context: Context
): File {
    val photoDirectory = File(
        context.filesDir,
        "parking_photos"
    ).apply {
        mkdirs()
    }

    return File(
        photoDirectory,
        "parking_${System.currentTimeMillis()}.jpg"
    )
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

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val MILLIS_PER_HOUR = 3_600_000.0
