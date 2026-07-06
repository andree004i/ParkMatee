package com.example.parkmatee.ui.parking

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.ParkingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ParkingScreen(
    viewModel: ParkingViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    ParkingContent(
        uiState = uiState,
        onVehicleSelected = viewModel::onVehicleSelected,
        onParkingTypeSelected = viewModel::onParkingTypeSelected,
        onHourlyRateChanged = viewModel::onHourlyRateChanged,
        onFixedCostChanged = viewModel::onFixedCostChanged,
        onExpiryMinutesChanged = viewModel::onExpiryMinutesChanged,
        onLatitudeChanged = viewModel::onLatitudeChanged,
        onLongitudeChanged = viewModel::onLongitudeChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onStartParkingClicked = viewModel::onStartParkingClicked,
        onEndParkingClicked = viewModel::onEndParkingClicked
    )
}

@Composable
private fun ParkingContent(
    uiState: ParkingUiState,
    onVehicleSelected: (Int) -> Unit,
    onParkingTypeSelected: (ParkingType) -> Unit,
    onHourlyRateChanged: (String) -> Unit,
    onFixedCostChanged: (String) -> Unit,
    onExpiryMinutesChanged: (String) -> Unit,
    onLatitudeChanged: (String) -> Unit,
    onLongitudeChanged: (String) -> Unit,
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

        Text(text = "Posizione temporanea")

        OutlinedTextField(
            value = uiState.latitude,
            onValueChange = onLatitudeChanged,
            label = {
                Text(text = "Latitudine")
            },
            supportingText = {
                Text(text = "Esempio: 44.4949")
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
                Text(text = "Esempio: 11.3426")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
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
    onEndParkingClicked: (ParkingSession) -> Unit
) {
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
            Text(text = "Posizione: ${session.latitude}, ${session.longitude}")

            session.hourlyRate?.let { hourlyRate ->
                Text(text = "Tariffa: $hourlyRate euro / ora")
            }

            session.fixedCost?.let { fixedCost ->
                Text(text = "Costo fisso: $fixedCost euro")
            }

            session.expiryTime?.let { expiryTime ->
                Text(text = "Scadenza: ${formatTime(expiryTime)}")
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

private fun formatTime(
    timestamp: Long
): String {
    val formatter = SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    )

    return formatter.format(Date(timestamp))
}