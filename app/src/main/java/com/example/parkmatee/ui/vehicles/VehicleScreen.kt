package com.example.parkmatee.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.Vehicle

@Composable
fun VehicleScreen(viewModel: VehicleViewModel) {
    val vehicles by viewModel.vehicles.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(vehicles) { vehicle ->
                VehicleItem(vehicle = vehicle, onDelete = { viewModel.deleteVehicle(it) })
            }
        }
    }

    if (showDialog) {
        AddVehicleDialog(
            onConfirm = { name, type ->
                viewModel.addVehicle(name, type)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun VehicleItem(vehicle: Vehicle, onDelete: (Vehicle) -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = vehicle.name, style = MaterialTheme.typography.titleMedium)
                Text(text = vehicle.type, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { onDelete(vehicle) }) {
                Text("Elimina")
            }
        }
    }
}

@Composable
fun AddVehicleDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("car") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi veicolo") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Selezione tipo
                Row {
                    listOf("car", "motorcycle", "bicycle").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, type) }) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}