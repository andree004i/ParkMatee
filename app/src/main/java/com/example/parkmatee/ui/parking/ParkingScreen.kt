package com.example.parkmatee.ui.parking

import android.Manifest
import android.content.Context
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.DragState
import androidx.core.app.ActivityCompat
import androidx.compose.material3.AlertDialog
import android.provider.Settings
import android.content.Intent
import android.content.ContextWrapper
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.util.decodePhoto
import com.example.parkmatee.util.deletePhotoQuietly
import com.example.parkmatee.util.fetchCurrentLocation
import com.example.parkmatee.util.isDeviceLocationEnabled
import com.example.parkmatee.notification.canShowParkingNotifications
import com.example.parkmatee.notification.ensureParkingNotificationChannel
import com.example.parkmatee.util.ParkingFormatters
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ParkingScreen(
    viewModel: ParkingViewModel,
    savedLocationId: Int = -1
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Stato dello scatto: rememberSaveable, cosi sopravvive alla rotazione mentre la fotocamera e aperta.
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    // Id della sosta a cui assegnare la foto (null = foto del form di nuova sosta)
    var photoTargetId by rememberSaveable { mutableStateOf<Int?>(null) }
    var notificationPermissionRefresh by remember { mutableLongStateOf(0L) }

    // Osserva il ciclo di vita per aggiornare lo stato del permesso allarmi esatti al ritorno dalle Impostazioni
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshExactAlarmPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showLocationRationale by rememberSaveable { mutableStateOf(false) }

    fun loadGpsLocation() {
        if (!isDeviceLocationEnabled(context)) {
            viewModel.onLocationProblem(
                "Localizzazione disattivata. Attivala per usare il GPS, oppure scegli il punto sulla mappa.",
                LocationHelp.OPEN_LOCATION_SETTINGS
            )
            return
        }
        viewModel.onLocationRequested()
        fetchCurrentLocation(
            context = context,
            onLocationLoaded = viewModel::onCurrentLocationLoaded,
            onLocationError = viewModel::onLocationError
        )
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                loadGpsLocation()
            } else if (!shouldShowLocationRationale(context)) {
                // Dopo un rifiuto, "false" significa "non chiedere piu": serve passare dalle impostazioni.
                viewModel.onLocationProblem(
                    "Permesso posizione negato in modo permanente. Abilitalo dalle impostazioni dell'app, oppure scegli il punto sulla mappa.",
                    LocationHelp.OPEN_APP_SETTINGS
                )
            } else {
                viewModel.onLocationProblem(
                    "Permesso posizione non concesso. Serve per registrare dove hai parcheggiato; puoi comunque scegliere il punto sulla mappa.",
                    LocationHelp.NONE
                )
            }
        }

    fun onUseGpsClicked() {
        when {
            hasFineLocationPermission(context) -> loadGpsLocation()
            shouldShowLocationRationale(context) -> showLocationRationale = true
            else -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // GPS come posizione predefinita: all'apertura, se il permesso c'e gia e non c'e ancora un punto.
    LaunchedEffect(Unit) {
        if (savedLocationId == -1 &&
            uiState.latitude.isBlank() &&
            hasFineLocationPermission(context) &&
            isDeviceLocationEnabled(context)
        ) {
            loadGpsLocation()
        }
    }

    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            title = { Text("Perche serve la posizione") },
            text = { Text("ParkMate usa il GPS per salvare dove hai lasciato il veicolo. Puoi sempre scegliere il punto a mano sulla mappa.") },
            confirmButton = {
                Button(onClick = {
                    showLocationRationale = false
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Text("Continua") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLocationRationale = false }) { Text("Annulla") }
            }
        )
    }

    val takePictureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            val photoPath = pendingPhotoPath
            val target = uiState.activeParkings.firstOrNull { it.id == photoTargetId }
            if (success && photoPath != null) {
                if (target != null) {
                    viewModel.onSessionPhotoChanged(target, photoPath)
                } else {
                    viewModel.onPhotoCaptured(photoPath)
                }
            } else {
                deletePhotoQuietly(photoPath)
                viewModel.onPhotoCaptureError("Foto non scattata.")
            }
            pendingPhotoPath = null
            pendingPhotoUriString = null
            photoTargetId = null
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            val photoUri = pendingPhotoUriString?.let(Uri::parse)
            if (isGranted && photoUri != null) {
                takePictureLauncher.launch(photoUri)
            } else {
                pendingPhotoPath = null
                pendingPhotoUriString = null
                photoTargetId = null
                viewModel.onPhotoCaptureError("Permesso fotocamera non concesso.")
            }
        }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            notificationPermissionRefresh = System.currentTimeMillis()
        }

    LaunchedEffect(Unit) {
        ensureParkingNotificationChannel(context)
    }

    val parkingNotificationsEnabled = remember(notificationPermissionRefresh) {
        canShowParkingNotifications(context)
    }

    fun launchPhotoCapture(target: ParkingSession?) {
        photoTargetId = target?.id
        try {
            val photoFile = createParkingPhotoFile(context)
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )

            pendingPhotoPath = photoFile.absolutePath
            pendingPhotoUriString = photoUri.toString()

            if (hasCameraPermission(context)) {
                takePictureLauncher.launch(photoUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (exception: Exception) {
            pendingPhotoPath = null
            pendingPhotoUriString = null
            photoTargetId = null
            viewModel.onPhotoCaptureError("Errore durante la preparazione della fotocamera.")
        }
    }

    fun startPhotoCapture() = launchPhotoCapture(null)

    ParkingContent(
        uiState = uiState,
        parkingNotificationsEnabled = parkingNotificationsEnabled,
        onRequestExactAlarmPermission = viewModel::onRequestExactAlarmPermission,
        onVehicleSelected = viewModel::onVehicleSelected,
        onParkingTypeSelected = viewModel::onParkingTypeSelected,
        onHourlyRateChanged = viewModel::onHourlyRateChanged,
        onFixedCostChanged = viewModel::onFixedCostChanged,
        onExpiryMinutesChanged = viewModel::onExpiryMinutesChanged,
        onWarningMinutesChanged = viewModel::onWarningMinutesChanged,
        onLatitudeChanged = viewModel::onLatitudeChanged,
        onLongitudeChanged = viewModel::onLongitudeChanged,
        onSavedLocationSelected = viewModel::onSavedLocationSelected,
        onUseCurrentLocationClicked = ::onUseGpsClicked,
        onMapPositionSelected = { position ->
            viewModel.onMapPositionSelected(position.latitude, position.longitude)
        },
        onLocationHelpClicked = { help ->
            val intent = when (help) {
                LocationHelp.OPEN_LOCATION_SETTINGS -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                LocationHelp.OPEN_APP_SETTINGS -> Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                LocationHelp.NONE -> null
            }
            intent?.let { context.startActivity(it) }
        },
        onTakePhotoClicked = ::startPhotoCapture,
        onEditSessionNote = viewModel::onSessionNoteChanged,
        onChangeSessionPhoto = { session -> launchPhotoCapture(session) },
        onRemovePhotoClicked = viewModel::onRemovePhotoClicked,
        onEnableNotificationsClicked = {
            ensureParkingNotificationChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPermissionRefresh = System.currentTimeMillis()
            }
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
    onRequestExactAlarmPermission: () -> Unit,
    onVehicleSelected: (Int) -> Unit,
    onParkingTypeSelected: (ParkingType) -> Unit,
    onHourlyRateChanged: (String) -> Unit,
    onFixedCostChanged: (String) -> Unit,
    onExpiryMinutesChanged: (String) -> Unit,
    onWarningMinutesChanged: (Int) -> Unit,
    onLatitudeChanged: (String) -> Unit,
    onLongitudeChanged: (String) -> Unit,
    onSavedLocationSelected: (Int?) -> Unit,
    onUseCurrentLocationClicked: () -> Unit,
    onMapPositionSelected: (LatLng) -> Unit,
    onLocationHelpClicked: (LocationHelp) -> Unit,
    onTakePhotoClicked: () -> Unit,
    onEditSessionNote: (ParkingSession, String) -> Unit,
    onChangeSessionPhoto: (ParkingSession) -> Unit,
    onRemovePhotoClicked: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
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
            ParkingType.entries.forEach { type ->
                FilterChip(
                    selected = uiState.selectedType == type,
                    onClick = { onParkingTypeSelected(type) },
                    label = { Text(text = type.label) }
                )
            }
        }

        when (uiState.selectedType) {
            ParkingType.FREE -> Unit

            ParkingType.HOURLY -> {
                OutlinedTextField(
                    value = uiState.hourlyRate,
                    onValueChange = onHourlyRateChanged,
                    label = { Text(text = "Tariffa oraria") },
                    supportingText = { Text(text = "Esempio: 1.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParkingType.FIXED -> {
                OutlinedTextField(
                    value = uiState.fixedCost,
                    onValueChange = onFixedCostChanged,
                    label = { Text(text = "Costo fisso") },
                    supportingText = { Text(text = "Esempio: 3.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.expiryMinutes,
                    onValueChange = onExpiryMinutesChanged,
                    label = { Text(text = "Durata ticket in minuti") },
                    supportingText = { Text(text = "Esempio: 120") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Preavviso di scadenza")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = uiState.warningMinutes == minutes,
                            onClick = { onWarningMinutesChanged(minutes) },
                            label = { Text(text = "$minutes min prima") }
                        )
                    }
                }
            }
        }

        if (!uiState.canScheduleExactAlarms && uiState.selectedType == ParkingType.FIXED) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Permesso allarmi esatti disattivato")
                    Text(text = "Senza questo permesso l'avviso di scadenza usa una sveglia di sistema (icona della sveglia nella barra di stato) e il preavviso puo arrivare con qualche minuto di ritardo. Per il comportamento ideale abilita 'Allarmi e promemoria' nelle impostazioni.")
                    Button(
                        onClick = onRequestExactAlarmPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Apri Impostazioni Permesso")
                    }
                }
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
            enabled = !uiState.isLoadingLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (uiState.isLoadingLocation) "Recupero posizione..." else "Usa posizione attuale (GPS)")
        }

        if (uiState.locationHelp != LocationHelp.NONE) {
            OutlinedButton(
                onClick = { onLocationHelpClicked(uiState.locationHelp) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = uiState.locationHelp.label)
            }
        }

        ParkingPositionPickerMap(
            latitude = uiState.latitude,
            longitude = uiState.longitude,
            onPositionSelected = onMapPositionSelected
        )

        Text(text = "Origine posizione: ${uiState.locationSource.label}")

        OutlinedTextField(
            value = uiState.latitude,
            onValueChange = onLatitudeChanged,
            label = { Text(text = "Latitudine") },
            supportingText = { Text(text = "Compilata da luogo salvato, GPS, mappa o a mano") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.longitude,
            onValueChange = onLongitudeChanged,
            label = { Text(text = "Longitudine") },
            supportingText = { Text(text = "Compilata da luogo salvato, GPS, mappa o a mano") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            label = { Text(text = "Nota") },
            supportingText = { Text(text = "${uiState.note.length}/${ParkingViewModel.MAX_NOTE_LENGTH}") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        uiState.errorMessage?.let { message -> Text(text = message) }
        uiState.successMessage?.let { message -> Text(text = message) }

        NotificationReminderSection(
            notificationsEnabled = parkingNotificationsEnabled,
            activeParkings = uiState.activeParkings,
            onEnableNotificationsClicked = onEnableNotificationsClicked
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
            onEndParkingClicked = onEndParkingClicked,
            onEditSessionNote = onEditSessionNote,
            onChangeSessionPhoto = onChangeSessionPhoto
        )
    }
}

@Composable
private fun NotificationReminderSection(
    notificationsEnabled: Boolean,
    activeParkings: List<ParkingSession>,
    onEnableNotificationsClicked: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Notifiche parcheggio")
            Text(text = if (notificationsEnabled) "Stato: attive" else "Stato: non attive")
            Text(text = "Reminder sosta attiva: una notifica ogni 15 minuti (WorkManager).")
            Text(text = "Ticket in scadenza: avviso all'orario di scadenza (AlarmManager).")
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
internal fun ParkingPhotoPreview(photoPath: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, photoPath) {
        value = withContext(Dispatchers.IO) {
            decodePhoto(photoPath)
        }
    }

    bitmap?.let { loadedBitmap ->
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = "Foto parcheggio",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentScale = ContentScale.Crop
        )
    } ?: Text(text = "Caricamento anteprima foto...")
}

@Composable
private fun SavedLocationSelector(
    savedLocations: List<SavedLocation>,
    selectedSavedLocationId: Int?,
    onSavedLocationSelected: (Int?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedLocation = savedLocations.firstOrNull { it.id == selectedSavedLocationId }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Luogo salvato")

        if (savedLocations.isEmpty()) {
            Text(text = "Nessun luogo salvato. Aggiungilo dalla schermata Luoghi.")
        } else {
            Box {
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = selectedLocation?.name ?: "Seleziona un luogo salvato")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Nessun luogo salvato") },
                        onClick = {
                            onSavedLocationSelected(null)
                            menuExpanded = false
                        }
                    )

                    savedLocations.forEach { location ->
                        DropdownMenuItem(
                            text = { Text(text = "${location.name} (${location.latitude}, ${location.longitude})") },
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
    val defaultPosition = LatLng(44.4949, 11.3426)
    val selectedPosition = parseLatLng(latitude = latitude, longitude = longitude)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedPosition ?: defaultPosition, 14f)
    }
    val markerState = rememberMarkerState(position = selectedPosition ?: defaultPosition)

    // La mappa segue la posizione scelta da GPS, luogo salvato o campi di testo.
    LaunchedEffect(selectedPosition) {
        selectedPosition?.let { position ->
            markerState.position = position
            // Niente CameraUpdateFactory: richiede la mappa gia inizializzata e fa crashare se la
            // posizione arriva prima del caricamento. Impostare la posizione e sempre sicuro.
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Tocca la mappa o trascina il marker per regolare il punto del parcheggio")

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
            onMapClick = onPositionSelected
        ) {
            if (selectedPosition != null) {
                Marker(
                    state = markerState,
                    draggable = true,
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
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedVehicle = uiState.vehicles.firstOrNull { it.id == uiState.selectedVehicleId }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Veicolo")

        if (uiState.vehicles.isEmpty()) {
            Text(text = "Non ci sono veicoli disponibili. Aggiungi prima un veicolo dalla schermata dedicata.")
        } else {
            Box {
                OutlinedButton(onClick = { menuExpanded = true }) {
                    Text(text = selectedVehicle?.name ?: "Seleziona un veicolo")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    uiState.vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = { Text(text = "${vehicle.name} (${vehicle.type.label})") },
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
    onEndParkingClicked: (ParkingSession) -> Unit,
    onEditSessionNote: (ParkingSession, String) -> Unit,
    onChangeSessionPhoto: (ParkingSession) -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val hasActiveParkings = uiState.activeParkings.isNotEmpty()

    // Il ticker gira solo con l'app visibile e solo se c'e una sosta attiva.
    // Ogni tick rilegge l'orologio: nessun drift.
    LaunchedEffect(lifecycleOwner, hasActiveParkings) {
        if (!hasActiveParkings) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    Text(text = "Parcheggi attivi")

    if (uiState.activeParkings.isEmpty()) {
        Text(text = "Nessun parcheggio attivo.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.activeParkings.forEach { session ->
                ActiveParkingItem(
                    session = session,
                    vehicleName = uiState.vehicles.firstOrNull { it.id == session.vehicleId }?.name ?: "Veicolo sconosciuto",
                    nowMillis = nowMillis,
                    onEditSessionNote = onEditSessionNote,
                    onChangeSessionPhoto = onChangeSessionPhoto,
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
    onEditSessionNote: (ParkingSession, String) -> Unit,
    onChangeSessionPhoto: (ParkingSession) -> Unit,
    onEndParkingClicked: (ParkingSession) -> Unit
) {
    var showNoteDialog by remember(session.id) { mutableStateOf(false) }
    var noteDraft by remember(session.id, session.note) { mutableStateOf(session.note ?: "") }
    val elapsedMillis = (nowMillis - session.startTime).coerceAtLeast(0L)
    val liveCost = ParkingFormatters.calculateLiveCost(session = session, elapsedMillis = elapsedMillis)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = vehicleName)
            Text(text = "Tipo: ${ParkingFormatters.formatParkingType(session.type)}")
            Text(text = "Inizio: ${ParkingFormatters.formatTime(session.startTime, "dd/MM/yyyy HH:mm")}")
            Text(text = "Tempo trascorso: ${ParkingFormatters.formatDuration(elapsedMillis)}")
            Text(text = "Costo attuale: ${ParkingFormatters.formatCurrency(liveCost)}")
            Text(text = "Posizione: ${session.latitude}, ${session.longitude}")

            session.savedLocationName?.let { Text(text = "Luogo: $it") }
            session.photoPath?.let { ParkingPhotoPreview(photoPath = it) }
            session.hourlyRate?.let { Text(text = "Tariffa: ${ParkingFormatters.formatCurrency(it)} / ora") }
            session.fixedCost?.let { Text(text = "Costo fisso: ${ParkingFormatters.formatCurrency(it)}") }

            session.expiryTime?.let { expiryTime ->
                Text(text = "Scadenza: ${ParkingFormatters.formatTime(expiryTime, "dd/MM/yyyy HH:mm")}")
                Text(text = "Countdown ticket: ${formatTicketCountdown(expiryTime - nowMillis)}")
                Text(text = "Preavviso configurato: ${session.warningMinutes} min prima")
            }

            session.note?.let { Text(text = "Nota: $it") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showNoteDialog = true }) {
                    Text(text = if (session.note == null) "Aggiungi nota" else "Modifica nota")
                }
                OutlinedButton(onClick = { onChangeSessionPhoto(session) }) {
                    Text(text = if (session.photoPath == null) "Aggiungi foto" else "Cambia foto")
                }
            }

            if (showNoteDialog) {
                AlertDialog(
                    onDismissRequest = { showNoteDialog = false },
                    title = { Text("Nota della sosta") },
                    text = {
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it.take(ParkingViewModel.MAX_NOTE_LENGTH) },
                            supportingText = { Text("${noteDraft.length}/${ParkingViewModel.MAX_NOTE_LENGTH}") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            onEditSessionNote(session, noteDraft)
                            showNoteDialog = false
                        }) { Text("Salva") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showNoteDialog = false }) { Text("Annulla") }
                    }
                )
            }

            Button(
                onClick = { onEndParkingClicked(session) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Termina parcheggio")
            }
        }
    }
}

private fun formatTicketCountdown(remainingMillis: Long): String {
    return if (remainingMillis <= 0L) {
        "Scaduto da ${ParkingFormatters.formatDuration(-remainingMillis)}"
    } else {
        ParkingFormatters.formatDuration(remainingMillis)
    }
}

private fun parseLatLng(latitude: String, longitude: String): LatLng? {
    val parsedLatitude = latitude.replace(',', '.').toDoubleOrNull()
    val parsedLongitude = longitude.replace(',', '.').toDoubleOrNull()
    if (parsedLatitude == null || parsedLongitude == null) return null
    if (parsedLatitude !in -90.0..90.0 || parsedLongitude !in -180.0..180.0) return null
    return LatLng(parsedLatitude, parsedLongitude)
}

private fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun shouldShowLocationRationale(context: Context): Boolean {
    var current: Context = context
    while (current is ContextWrapper) {
        if (current is Activity) {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                current,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        current = current.baseContext
    }
    return false
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun createParkingPhotoFile(context: Context): File {
    val photoDirectory = File(context.filesDir, "parking_photos").apply { mkdirs() }
    return File(photoDirectory, "parking_${System.currentTimeMillis()}.jpg")
}

