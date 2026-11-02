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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.Vehicle
import com.example.parkmatee.data.entity.VehicleType

@Composable
fun VehicleScreen(viewModel: VehicleViewModel) {
    val vehicles by viewModel.vehicles.collectAsState()
    val deleteRequest by viewModel.deleteRequest.collectAsState()
    val message by viewModel.message.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var vehicleToEdit by remember { mutableStateOf<VehicleWithStatus?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        if (vehicles.isEmpty()) {
            Text(
                text = "Nessun veicolo. Tocca + per aggiungerne uno.",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        }
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(vehicles, key = { it.vehicle.id }) { item ->
                VehicleItem(
                    item = item,
                    onEdit = { vehicleToEdit = it },
                    onDelete = { viewModel.onDeleteRequested(it) }
                )
            }
        }
    }

    if (showAddDialog) {
        VehicleDialog(
            title = "Aggiungi veicolo",
            confirmButtonText = "Aggiungi",
            initialName = "",
            initialType = VehicleType.CAR,
            typeEditable = true,
            onConfirm = { name, type, onError ->
                viewModel.saveVehicle(null, name, type) { error ->
                    if (error == null) showAddDialog = false else onError(error)
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    vehicleToEdit?.let { item ->
        VehicleDialog(
            title = "Modifica veicolo",
            confirmButtonText = "Salva",
            initialName = item.vehicle.name,
            initialType = item.vehicle.type,
            typeEditable = !item.isParked,
            onConfirm = { name, type, onError ->
                viewModel.saveVehicle(item.vehicle, name, type) { error ->
                    if (error == null) vehicleToEdit = null else onError(error)
                }
            },
            onDismiss = { vehicleToEdit = null }
        )
    }

    deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text("Eliminare ${request.vehicle.name}?") },
            text = {
                Text(
                    if (request.sessionCount > 0) {
                        "Verranno eliminate definitivamente anche ${request.sessionCount} soste dalla cronologia e dalle statistiche."
                    } else {
                        "Il veicolo non ha soste registrate."
                    }
                )
            },
            confirmButton = {
                Button(onClick = viewModel::onDeleteConfirmed) { Text("Elimina") }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::onDeleteDismissed) { Text("Annulla") }
            }
        )
    }
}

@Composable
fun VehicleItem(
    item: VehicleWithStatus,
    onEdit: (VehicleWithStatus) -> Unit,
    onDelete: (VehicleWithStatus) -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = item.vehicle.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = item.vehicle.type.label,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (item.isParked) "Parcheggiato" else "Libero") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEdit(item) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Modifica")
                }

                OutlinedButton(
                    onClick = { onDelete(item) },
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
    initialType: VehicleType,
    typeEditable: Boolean,
    onConfirm: (name: String, type: VehicleType, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var type by remember(initialType) { mutableStateOf(initialType) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Nome") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Tipo")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VehicleType.entries.forEach { vehicleType ->
                        FilterChip(
                            selected = type == vehicleType,
                            onClick = { type = vehicleType },
                            enabled = typeEditable,
                            label = { Text(vehicleType.label) }
                        )
                    }
                }

                if (!typeEditable) {
                    Text(
                        text = "Il tipo non si puo cambiare mentre il veicolo e parcheggiato.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, type) { message -> error = message } }) {
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
