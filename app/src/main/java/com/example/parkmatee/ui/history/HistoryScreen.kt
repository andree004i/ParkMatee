package com.example.parkmatee.ui.history

import android.location.Geocoder
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.Vehicle
import com.example.parkmatee.ui.parking.ParkingPhotoPreview
import com.example.parkmatee.util.ParkingFormatters
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    HistoryContent(
        uiState = uiState,
        onTypeFilterSelected = viewModel::onTypeFilterSelected,
        onVehicleFilterSelected = viewModel::onVehicleFilterSelected,
        onRangeSelected = viewModel::onRangeSelected,
        onClearFilters = viewModel::onClearFilters,
        onDeleteSession = viewModel::onDeleteSession,
        onErrorShown = viewModel::onErrorShown
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    onTypeFilterSelected: (HistoryTypeFilter) -> Unit,
    onVehicleFilterSelected: (Int?) -> Unit,
    onRangeSelected: (HistoryRange) -> Unit,
    onClearFilters: () -> Unit,
    onDeleteSession: (ParkingSession) -> Unit,
    onErrorShown: () -> Unit
) {
    var selectedSessionId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedSession = uiState.parkingHistory.firstOrNull { it.id == selectedSessionId }

    // Raggruppamento per mese: la lista arriva gia ordinata per startTime DESC, quindi
    // groupBy (che mantiene l'ordine di inserimento) produce mesi dal piu recente.
    val groups = remember(uiState.parkingHistory) {
        uiState.parkingHistory.groupBy { monthKey(it.startTime) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Storico parcheggi", style = MaterialTheme.typography.titleLarge)

        HistoryFiltersSection(
            vehicles = uiState.vehicles,
            filters = uiState.filters,
            onTypeFilterSelected = onTypeFilterSelected,
            onVehicleFilterSelected = onVehicleFilterSelected,
            onRangeSelected = onRangeSelected,
            onClearFilters = onClearFilters
        )

        uiState.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        when {
            uiState.isLoading -> Unit

            uiState.parkingHistory.isEmpty() -> EmptyState(
                filtersActive = uiState.filters.isActive,
                onClearFilters = onClearFilters
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { (month, sessions) ->
                    stickyHeader(key = "month-$month", contentType = "header") {
                        MonthHeader(
                            label = monthLabel(sessions.first().startTime),
                            sessions = sessions
                        )
                    }
                    items(
                        items = sessions,
                        key = { it.id },
                        contentType = { "session" }
                    ) { session ->
                        HistoryItem(
                            session = session,
                            vehicle = uiState.vehicles.firstOrNull { it.id == session.vehicleId },
                            onClick = { selectedSessionId = session.id }
                        )
                    }
                }
            }
        }
    }

    selectedSession?.let { session ->
        SessionDetailSheet(
            session = session,
            vehicle = uiState.vehicles.firstOrNull { it.id == session.vehicleId },
            onDismiss = { selectedSessionId = null },
            onDelete = {
                selectedSessionId = null
                onDeleteSession(session)
            }
        )
    }

    // Se la lista cambia e l'errore resta, l'utente lo vede una volta e poi sparisce al prossimo filtro.
    if (uiState.errorMessage != null) {
        androidx.compose.runtime.LaunchedEffect(uiState.errorMessage) {
            kotlinx.coroutines.delay(4_000L)
            onErrorShown()
        }
    }
}

/** Sezione filtri (tipo, intervallo, veicolo): condivisa tra Storico e Statistiche. */
@Composable
internal fun HistoryFiltersSection(
    vehicles: List<Vehicle>,
    filters: HistoryFilters,
    onTypeFilterSelected: (HistoryTypeFilter) -> Unit,
    onVehicleFilterSelected: (Int?) -> Unit,
    onRangeSelected: (HistoryRange) -> Unit,
    onClearFilters: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryTypeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filters.type == filter,
                    onClick = { onTypeFilterSelected(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryRange.entries.forEach { range ->
                FilterChip(
                    selected = filters.range == range,
                    onClick = { onRangeSelected(range) },
                    label = { Text(range.label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VehicleFilterDropdown(
                vehicles = vehicles,
                selectedVehicleId = filters.vehicleId,
                onVehicleFilterSelected = onVehicleFilterSelected
            )
            if (filters.isActive) {
                TextButton(onClick = onClearFilters) { Text("Azzera filtri") }
            }
        }
    }
}

@Composable
private fun VehicleFilterDropdown(
    vehicles: List<Vehicle>,
    selectedVehicleId: Int?,
    onVehicleFilterSelected: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedVehicleName = vehicles.firstOrNull { it.id == selectedVehicleId }?.name
        ?: "Tutti i veicoli"

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(text = "Veicolo: $selectedVehicleName")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Tutti i veicoli") },
                onClick = {
                    onVehicleFilterSelected(null)
                    expanded = false
                }
            )
            vehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text("${vehicle.name} (${vehicle.type.label})") },
                    onClick = {
                        onVehicleFilterSelected(vehicle.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(filtersActive: Boolean, onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filtersActive) {
            Text("Nessuna sosta corrisponde ai filtri scelti.")
            OutlinedButton(onClick = onClearFilters) { Text("Azzera filtri") }
        } else {
            Text("Nessuna sosta terminata.")
            Text(
                text = "Le soste in corso si vedono in Park e nella Mappa; compaiono qui quando le termini.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MonthHeader(label: String, sessions: List<ParkingSession>) {
    val total = sessions.sumOf { it.finalCost ?: 0.0 }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${sessions.size} soste - ${ParkingFormatters.formatCurrency(total)}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun HistoryItem(
    session: ParkingSession,
    vehicle: Vehicle?,
    onClick: () -> Unit
) {
    val end = session.endTime
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = vehicle?.name ?: "Veicolo sconosciuto",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = costLabel(session), style = MaterialTheme.typography.titleMedium)
            }
            Text(text = session.type.label)
            Text(
                text = "${formatDateTime(session.startTime)} -> ${formatDateTime(end)}",
                style = MaterialTheme.typography.bodySmall
            )
            if (end != null) {
                Text(
                    text = "Durata ${ParkingFormatters.formatDuration((end - session.startTime).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            session.savedLocationName?.let {
                Text(text = "Luogo: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailSheet(
    session: ParkingSession,
    vehicle: Vehicle?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val end = session.endTime
    val address = rememberAddress(session.latitude, session.longitude)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = vehicle?.name ?: "Veicolo sconosciuto",
                style = MaterialTheme.typography.titleLarge
            )
            Text("Tipo di sosta: ${session.type.label}")
            Text("Inizio: ${formatDateTime(session.startTime)}")
            Text("Fine: ${formatDateTime(end)}")
            if (end != null) {
                Text("Durata: ${ParkingFormatters.formatDuration((end - session.startTime).coerceAtLeast(0L))}")
            }

            when (session.type) {
                ParkingType.HOURLY -> session.hourlyRate?.let {
                    Text("Tariffa oraria: ${ParkingFormatters.formatCurrency(it)}/h")
                }
                ParkingType.FIXED -> {
                    session.fixedCost?.let { Text("Costo ticket: ${ParkingFormatters.formatCurrency(it)}") }
                    session.expiryTime?.let { Text("Scadenza ticket: ${formatDateTime(it)}") }
                }
                ParkingType.FREE -> Unit
            }
            Text("Costo finale: ${costLabel(session)}", style = MaterialTheme.typography.titleMedium)

            Text("Posizione", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            session.savedLocationName?.let { Text("Luogo salvato: $it") }
            Text("Indirizzo: $address")
            Text("Coordinate: %.5f, %.5f".format(Locale.US, session.latitude, session.longitude))

            ParkingLocationMiniMap(latitude = session.latitude, longitude = session.longitude)

            session.note?.takeIf { it.isNotBlank() }?.let {
                Text("Nota", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text(it)
            }

            session.photoPath?.let {
                Text("Foto", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ParkingPhotoPreview(photoPath = it)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onDismiss) { Text("Chiudi") }
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Elimina") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare questa sosta?") },
            text = { Text("La sosta verra rimossa definitivamente dallo storico e dalle statistiche, insieme alla sua foto.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            }
        )
    }
}

/** Mini-mappa non interattiva (lite mode) con il punto in cui era parcheggiato il veicolo. */
@Composable
private fun ParkingLocationMiniMap(latitude: Double, longitude: Double) {
    val position = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState(key = "$latitude,$longitude") {
        this.position = CameraPosition.fromLatLngZoom(position, 16f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        cameraPositionState = cameraPositionState,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        Marker(state = rememberMarkerState(key = "history-$latitude,$longitude", position = position))
    }
}

/** Indirizzo da Geocoder, caricato fuori dal main thread; se non disponibile resta il testo di ripiego. */
@Composable
private fun rememberAddress(latitude: Double, longitude: Double): String {
    val context = LocalContext.current
    val address by produceState("Ricerca indirizzo...", latitude, longitude) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) {
                    null
                } else {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault())
                        .getFromLocation(latitude, longitude, 1)
                        ?.firstOrNull()
                        ?.getAddressLine(0)
                }
            }.getOrNull()
        } ?: "non disponibile"
    }
    return address
}

private fun costLabel(session: ParkingSession): String = when {
    session.finalCost != null -> ParkingFormatters.formatCurrency(session.finalCost)
    session.type == ParkingType.FREE -> "Gratuita"
    else -> "-"
}

private fun formatDateTime(timestamp: Long?): String =
    if (timestamp == null) "-" else ParkingFormatters.formatTime(timestamp, "dd/MM/yyyy HH:mm")

private fun monthKey(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp))

private fun monthLabel(timestamp: Long): String =
    SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(timestamp))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
