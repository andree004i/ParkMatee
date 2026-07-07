package com.example.parkmatee.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.Vehicle

private val vehicleTypes = listOf(
    "car",
    "motorcycle",
    "bicycle"
)

@Composable
fun VehicleScreen(viewModel: VehicleViewModel) {
    val vehicles by viewModel.vehicles.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var vehicleToEdit by remember { mutableStateOf<Vehicle?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(vehicles) { vehicle ->
                VehicleItem(
                    vehicle = vehicle,
                    onEdit = { vehicleToEdit = it },
                    onDelete = { viewModel.deleteVehicle(it) }
                )
            }
        }
    }

    if (showAddDialog) {
        VehicleDialog(
            title = "Aggiungi veicolo",
            confirmButtonText = "Aggiungi",
            initialName = "",
            initialType = "car",
            onConfirm = { name, type ->
                viewModel.addVehicle(name, type)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    vehicleToEdit?.let { vehicle ->
        VehicleDialog(
            title = "Modifica veicolo",
            confirmButtonText = "Salva",
            initialName = vehicle.name,
            initialType = vehicle.type,
            onConfirm = { name, type ->
                viewModel.updateVehicle(
                    vehicle = vehicle,
                    name = name,
                    type = type
                )
                vehicleToEdit = null
            },
            onDismiss = { vehicleToEdit = null }
        )
    }
}

@Composable
fun VehicleItem(
    vehicle: Vehicle,
    onEdit: (Vehicle) -> Unit,
    onDelete: (Vehicle) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = vehicle.type,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEdit(vehicle) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Modifica")
                }

                Button(
                    onClick = { onDelete(vehicle) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Elimina")
                }
            }
        }
    }
}

@Composable
fun VehicleDialog(
    title: String,
    confirmButtonText: String,
    initialName: String,
    initialType: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var type by remember(initialType) {
        mutableStateOf(
            if (initialType in vehicleTypes) {
                initialType
            } else {
                "car"
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Tipo")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    vehicleTypes.forEach { vehicleType ->
                        FilterChip(
                            selected = type == vehicleType,
                            onClick = { type = vehicleType },
                            label = { Text(vehicleType) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()

                    if (trimmedName.isNotBlank()) {
                        onConfirm(trimmedName, type)
                    }
                }
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}