package com.example.parkmatee.ui.savedlocations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.parkmatee.data.entity.SavedLocation

@Composable
fun SavedLocationScreen(
    viewModel: SavedLocationViewModel
) {
    val locations by viewModel.locations.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var locationToEdit by remember { mutableStateOf<SavedLocation?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Luoghi salvati",
                style = MaterialTheme.typography.titleLarge
            )

            if (locations.isEmpty()) {
                Text(text = "Non ci sono ancora luoghi salvati.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(locations) { location ->
                        SavedLocationItem(
                            location = location,
                            onEdit = { locationToEdit = it },
                            onDelete = viewModel::deleteLocation
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SavedLocationDialog(
            title = "Aggiungi luogo",
            confirmButtonText = "Aggiungi",
            initialName = "",
            initialLatitude = "44.4949",
            initialLongitude = "11.3426",
            onConfirm = { name, latitude, longitude ->
                viewModel.addLocation(
                    name = name,
                    latitude = latitude,
                    longitude = longitude
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    locationToEdit?.let { location ->
        SavedLocationDialog(
            title = "Modifica luogo",
            confirmButtonText = "Salva",
            initialName = location.name,
            initialLatitude = location.latitude.toString(),
            initialLongitude = location.longitude.toString(),
            onConfirm = { name, latitude, longitude ->
                viewModel.updateLocation(
                    location = location,
                    name = name,
                    latitude = latitude,
                    longitude = longitude
                )
                locationToEdit = null
            },
            onDismiss = { locationToEdit = null }
        )
    }
}

@Composable
private fun SavedLocationItem(
    location: SavedLocation,
    onEdit: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit
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
                    onClick = { onDelete(location) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Elimina")
                }
            }
        }
    }
}

@Composable
private fun SavedLocationDialog(
    title: String,
    confirmButtonText: String,
    initialName: String,
    initialLatitude: String,
    initialLongitude: String,
    onConfirm: (String, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var latitude by remember(initialLatitude) { mutableStateOf(initialLatitude) }
    var longitude by remember(initialLongitude) { mutableStateOf(initialLongitude) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(text = "Nome") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = latitude,
                    onValueChange = {
                        latitude = it
                        errorMessage = null
                    },
                    label = { Text(text = "Latitudine") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = longitude,
                    onValueChange = {
                        longitude = it
                        errorMessage = null
                    },
                    label = { Text(text = "Longitudine") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = message)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedName = name.trim()
                    val parsedLatitude = latitude.toCoordinateOrNull()
                    val parsedLongitude = longitude.toCoordinateOrNull()

                    when {
                        parsedName.isBlank() -> {
                            errorMessage = "Inserisci un nome."
                        }

                        parsedLatitude == null || parsedLatitude !in -90.0..90.0 -> {
                            errorMessage = "Inserisci una latitudine valida."
                        }

                        parsedLongitude == null || parsedLongitude !in -180.0..180.0 -> {
                            errorMessage = "Inserisci una longitudine valida."
                        }

                        else -> {
                            onConfirm(
                                parsedName,
                                parsedLatitude,
                                parsedLongitude
                            )
                        }
                    }
                }
            ) {
                Text(text = confirmButtonText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Annulla")
            }
        }
    )
}

private fun String.toCoordinateOrNull(): Double? {
    return replace(',', '.').toDoubleOrNull()
}