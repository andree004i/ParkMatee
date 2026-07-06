package com.example.parkmatee.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.Vehicle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    HistoryContent(
        uiState = uiState,
        onTypeFilterSelected = viewModel::onTypeFilterSelected,
        onVehicleFilterSelected = viewModel::onVehicleFilterSelected
    )
}

@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    onTypeFilterSelected: (HistoryTypeFilter) -> Unit,
    onVehicleFilterSelected: (Int?) -> Unit
) {
    val filteredHistory = uiState.parkingHistory.filter { session ->
        val selectedDbValue = uiState.selectedTypeFilter.dbValue

        val matchesType =
            selectedDbValue == null || session.type == selectedDbValue

        val matchesVehicle =
            uiState.selectedVehicleId == null ||
                    session.vehicleId == uiState.selectedVehicleId

        matchesType && matchesVehicle
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Storico parcheggi")

        TypeFilterChips(
            selectedFilter = uiState.selectedTypeFilter,
            onTypeFilterSelected = onTypeFilterSelected
        )

        VehicleFilterDropdown(
            vehicles = uiState.vehicles,
            selectedVehicleId = uiState.selectedVehicleId,
            onVehicleFilterSelected = onVehicleFilterSelected
        )

        uiState.errorMessage?.let { message ->
            Text(text = message)
        }

        if (uiState.parkingHistory.isEmpty()) {
            Text(text = "Non ci sono parcheggi terminati.")
        } else if (filteredHistory.isEmpty()) {
            Text(text = "Nessun parcheggio trovato per questo filtro.")
        } else {
            filteredHistory.forEach { session ->
                HistoryItem(
                    session = session,
                    vehicle = uiState.vehicles.firstOrNull {
                        it.id == session.vehicleId
                    }
                )
            }
        }
    }
}

@Composable
private fun TypeFilterChips(
    selectedFilter: HistoryTypeFilter,
    onTypeFilterSelected: (HistoryTypeFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryTypeFilter.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = {
                    onTypeFilterSelected(filter)
                },
                label = {
                    Text(text = filter.label)
                }
            )
        }
    }
}

@Composable
private fun VehicleFilterDropdown(
    vehicles: List<com.example.parkmatee.data.entity.Vehicle>,
    selectedVehicleId: Int?,
    onVehicleFilterSelected: (Int?) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    val selectedVehicleName = vehicles.firstOrNull { vehicle ->
        vehicle.id == selectedVehicleId
    }?.name ?: "Tutti i veicoli"

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "Filtro veicolo")

        Box {
            OutlinedButton(
                onClick = {
                    expanded = true
                }
            ) {
                Text(text = selectedVehicleName)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(text = "Tutti i veicoli")
                    },
                    onClick = {
                        onVehicleFilterSelected(null)
                        expanded = false
                    }
                )

                vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = {
                            Text(text = "${vehicle.name} (${vehicle.type})")
                        },
                        onClick = {
                            onVehicleFilterSelected(vehicle.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
@Composable
private fun HistoryItem(
    session: ParkingSession,
    vehicle: Vehicle?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = vehicle?.name ?: "Veicolo sconosciuto")
            Text(text = "Tipo: ${formatParkingType(session.type)}")
            Text(text = "Inizio: ${formatTime(session.startTime)}")
            Text(text = "Fine: ${formatTime(session.endTime)}")
            Text(text = "Posizione: ${session.latitude}, ${session.longitude}")

            session.finalCost?.let { finalCost ->
                Text(text = "Costo finale: ${formatCost(finalCost)} euro")
            }

            session.note?.let { note ->
                Text(text = "Nota: $note")
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

private fun formatTime(
    timestamp: Long?
): String {
    if (timestamp == null) {
        return "-"
    }

    val formatter = SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    )

    return formatter.format(Date(timestamp))
}

private fun formatCost(
    cost: Double
): String {
    return String.format(
        Locale.getDefault(),
        "%.2f",
        cost
    )
}