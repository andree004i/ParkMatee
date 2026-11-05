package com.example.parkmatee.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.Vehicle
import com.example.parkmatee.data.entity.VehicleType
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.VehicleRepository
import com.example.parkmatee.util.deletePhotoQuietly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Veicolo + sosta attiva. Lo stato "parcheggiato" e' derivato, mai salvato. */
data class VehicleWithStatus(
    val vehicle: Vehicle,
    val activeSession: ParkingSession?
) {
    val isParked: Boolean get() = activeSession != null
}

/** Richiesta di eliminazione in attesa di conferma. */
data class DeleteRequest(val vehicle: Vehicle, val sessionCount: Int)

class VehicleViewModel(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository
) : ViewModel() {

    companion object {
        const val MAX_NAME_LENGTH = 40
    }

    val vehicles: StateFlow<List<VehicleWithStatus>> = combine(
        vehicleRepository.getAllVehicles(),
        parkingRepository.getActiveParkings()
    ) { vehicles, activeSessions ->
        vehicles.map { vehicle ->
            VehicleWithStatus(
                vehicle = vehicle,
                activeSession = activeSessions.firstOrNull { it.vehicleId == vehicle.id }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deleteRequest = MutableStateFlow<DeleteRequest?>(null)
    val deleteRequest: StateFlow<DeleteRequest?> = _deleteRequest

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * Valida e salva (inserimento se [editing] e' null, altrimenti modifica).
     * [onResult] riceve null se tutto ok, altrimenti il messaggio di errore da mostrare.
     */
    fun saveVehicle(
        editing: Vehicle?,
        name: String,
        type: VehicleType,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val trimmed = name.trim()
            val error = when {
                trimmed.isEmpty() -> "Inserisci un nome"
                trimmed.length > MAX_NAME_LENGTH -> "Massimo $MAX_NAME_LENGTH caratteri"
                vehicleRepository.isNameTaken(trimmed, editing?.id ?: 0) -> "Esiste gia un veicolo con questo nome"
                else -> null
            }
            if (error == null) {
                if (editing == null) {
                    vehicleRepository.insertVehicle(Vehicle(name = trimmed, type = type))
                } else {
                    // Il tipo di un veicolo parcheggiato non puo cambiare durante la sosta.
                    val isParked = parkingRepository.getActiveSessionForVehicle(editing.id) != null
                    val newType = if (isParked) editing.type else type
                    vehicleRepository.updateVehicle(editing.copy(name = trimmed, type = newType))
                }
            }
            onResult(error)
        }
    }

    fun onDeleteRequested(item: VehicleWithStatus) {
        if (item.isParked) {
            _message.value = "Termina prima la sosta attiva di ${item.vehicle.name}."
            return
        }
        viewModelScope.launch {
            val count = parkingRepository.getParkingsByVehicle(item.vehicle.id).first().size
            _deleteRequest.value = DeleteRequest(item.vehicle, count)
        }
    }

    fun onDeleteDismissed() {
        _deleteRequest.value = null
    }

    fun onDeleteConfirmed() {
        val request = _deleteRequest.value ?: return
        _deleteRequest.value = null
        viewModelScope.launch {
            // Ricontrollo: nel frattempo la sosta potrebbe essere stata avviata (es. da geofence).
            if (parkingRepository.getActiveSessionForVehicle(request.vehicle.id) != null) {
                _message.value = "Il veicolo e ora parcheggiato: termina prima la sosta."
            } else {
                // Le soste vengono eliminate a cascata: cancella anche i loro file di foto.
                val photoPaths = parkingRepository.getParkingsByVehicle(request.vehicle.id)
                    .first()
                    .mapNotNull { it.photoPath }
                vehicleRepository.deleteVehicle(request.vehicle)
                withContext(Dispatchers.IO) { photoPaths.forEach { deletePhotoQuietly(it) } }
            }
        }
    }

    // Factory per creare il ViewModel senza Hilt
    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val parkingRepository: ParkingRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VehicleViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return VehicleViewModel(vehicleRepository, parkingRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
