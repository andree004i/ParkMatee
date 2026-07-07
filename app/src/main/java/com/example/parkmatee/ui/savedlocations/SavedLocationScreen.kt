package com.example.parkmatee.ui.savedlocations

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.SavedLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun SavedLocationScreen(
    viewModel: SavedLocationViewModel
) {
    val locations by viewModel.locations.collectAsState()
    var formMode by remember { mutableStateOf<SavedLocationFormMode?>(null) }

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

            formMode?.let { mode ->
                SavedLocationForm(
                    mode = mode,
                    onSave = { name, latitude, longitude ->
                        when (mode) {
                            SavedLocationFormMode.Add -> {
                                viewModel.addLocation(
                                    name = name,
                                    latitude = latitude,
                                    longitude = longitude
                                )
                            }

                            is SavedLocationFormMode.Edit -> {
                                viewModel.updateLocation(
                                    location = mode.location,
                                    name = name,
                                    latitude = latitude,
                                    longitude = longitude
                                )
                            }
                        }

                        formMode = null
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
                        onDelete = viewModel::deleteLocation
                    )
                }
            }
        }
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
private fun SavedLocationForm(
    mode: SavedLocationFormMode,
    onSave: (String, Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    val editingLocation = (mode as? SavedLocationFormMode.Edit)?.location

    var name by remember(editingLocation?.id) {
        mutableStateOf(editingLocation?.name ?: "")
    }
    var latitude by remember(editingLocation?.id) {
        mutableStateOf(editingLocation?.latitude?.toString() ?: "44.4949")
    }
    var longitude by remember(editingLocation?.id) {
        mutableStateOf(editingLocation?.longitude?.toString() ?: "11.3426")
    }
    var errorMessage by remember(editingLocation?.id) {
        mutableStateOf<String?>(null)
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
                modifier = Modifier.fillMaxWidth()
            )

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
                    Text(text = "Compilata dalla mappa o modificabile a mano")
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
                    Text(text = "Compilata dalla mappa o modificabile a mano")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )

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
                                onSave(
                                    parsedName,
                                    parsedLatitude,
                                    parsedLongitude
                                )
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

    LaunchedEffect(selectedPosition) {
        selectedPosition?.let { position ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    position,
                    16f
                )
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Tocca la mappa per scegliere le coordinate")

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
            selectedPosition?.let { position ->
                Marker(
                    state = MarkerState(position = position),
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

private fun String.toCoordinateOrNull(): Double? {
    return replace(',', '.').toDoubleOrNull()
}