package com.example.parkmatee.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.Vehicle
import com.example.parkmatee.ui.parking.ParkingPhotoPreview
import com.example.parkmatee.util.ParkingFormatters
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
        onErrorShown = viewModel::onErrorShown
    )
}

@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    onTypeFilterSelected: (HistoryTypeFilter) -> Unit,
    onVehicleFilterSelected: (Int?) -> Unit,
    onRangeSelected: (HistoryRange) -> Unit,
    onClearFilters: () -> Unit,
    onErrorShown: () -> Unit
) {
    var selectedSessionId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedSession = uiState.parkingHistory.firstOrNull { it.id == selectedSessionId }

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
                items(
                    items = uiState.parkingHistory,
                    key = { it.id }
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

    selectedSession?.let { session ->
        SessionDetailSheet(
            session = session,
            vehicle = uiState.vehicles.firstOrNull { it.id == session.vehicleId },
            onDismiss = { selectedSessionId = null }
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
    onDismiss: () -> Unit
) {
    val end = session.endTime

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
            Text("Coordinate: %.5f, %.5f".format(Locale.US, session.latitude, session.longitude))

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
            }
        }
    }
}

private fun costLabel(session: ParkingSession): String = when {
    session.finalCost != null -> ParkingFormatters.formatCurrency(session.finalCost)
    session.type == ParkingType.FREE -> "Gratuita"
    else -> "-"
}

private fun formatDateTime(timestamp: Long?): String =
    if (timestamp == null) "-" else ParkingFormatters.formatTime(timestamp, "dd/MM/yyyy HH:mm")
