package com.example.parkmatee.ui.savedlocations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.SavedLocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Richiesta di eliminazione in attesa di conferma. */
data class LocationDeleteRequest(
    val location: SavedLocation,
    val hasActiveParking: Boolean
)

class SavedLocationViewModel(
    private val repository: SavedLocationRepository,
    private val parkingRepository: ParkingRepository
) : ViewModel() {

    companion object {
        const val MAX_NAME_LENGTH = 40
    }

    val locations: StateFlow<List<SavedLocation>> = repository.getAllLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deleteRequest = MutableStateFlow<LocationDeleteRequest?>(null)
    val deleteRequest: StateFlow<LocationDeleteRequest?> = _deleteRequest

    /**
     * Valida e salva (inserimento se [editing] e null, altrimenti modifica).
     * [onResult] riceve null se tutto ok, altrimenti il messaggio di errore.
     */
    fun saveLocation(
        editing: SavedLocation?,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val trimmed = name.trim()
            val error = when {
                trimmed.isEmpty() -> "Inserisci un nome."
                trimmed.length > MAX_NAME_LENGTH -> "Massimo $MAX_NAME_LENGTH caratteri."
                latitude !in -90.0..90.0 -> "Latitudine non valida."
                longitude !in -180.0..180.0 -> "Longitudine non valida."
                repository.isNameTaken(trimmed, editing?.id ?: 0) -> "Esiste gia un luogo con questo nome."
                else -> null
            }
            if (error == null) {
                if (editing == null) {
                    repository.insert(
                        SavedLocation(
                            name = trimmed,
                            latitude = latitude,
                            longitude = longitude,
                            geofenceRadiusMeters = radiusMeters
                        )
                    )
                } else {
                    repository.update(
                        editing.copy(
                            name = trimmed,
                            latitude = latitude,
                            longitude = longitude,
                            geofenceRadiusMeters = radiusMeters
                        )
                    )
                }
            }
            onResult(error)
        }
    }

    fun setGeofenceEnabled(
        location: SavedLocation,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            repository.update(
                location.copy(
                    geofenceEnabled = enabled
                )
            )
        }
    }

    fun onDeleteRequested(location: SavedLocation) {
        viewModelScope.launch {
            val hasActive = parkingRepository.getActiveSessionsForSavedLocationId(location.id).isNotEmpty()
            _deleteRequest.value = LocationDeleteRequest(location, hasActive)
        }
    }

    fun onDeleteDismissed() {
        _deleteRequest.value = null
    }

    fun onDeleteConfirmed() {
        val request = _deleteRequest.value ?: return
        _deleteRequest.value = null
        viewModelScope.launch {
            // Le soste gia registrate non vengono toccate: hanno copiato le coordinate.
            repository.delete(request.location)
        }
    }

    class Factory(
        private val repository: SavedLocationRepository,
        private val parkingRepository: ParkingRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SavedLocationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SavedLocationViewModel(repository, parkingRepository) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}
