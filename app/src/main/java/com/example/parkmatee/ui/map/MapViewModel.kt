package com.example.parkmatee.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.ParkingSessionService
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.SavedLocationRepository
import com.example.parkmatee.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val savedLocationRepository: SavedLocationRepository,
    private val sessionService: ParkingSessionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observeVehicles()
        observeActiveParkings()
        observeSavedLocations()
    }

    private fun observeVehicles() {
        viewModelScope.launch {
            vehicleRepository.getAllVehicles().collect { vehicles ->
                _uiState.update {
                    it.copy(vehicles = vehicles)
                }
            }
        }
    }

    private fun observeActiveParkings() {
        viewModelScope.launch {
            parkingRepository.getActiveParkings().collect { activeParkings ->
                _uiState.update {
                    it.copy(activeParkings = activeParkings)
                }
            }
        }
    }

    private fun observeSavedLocations() {
        viewModelScope.launch {
            savedLocationRepository.getAllLocations().collect { savedLocations ->
                _uiState.update {
                    it.copy(savedLocations = savedLocations)
                }
            }
        }
    }

    /** Chiude la sosta dalla mappa: stessa logica della schermata Parcheggio. */
    fun onEndParkingClicked(session: ParkingSession) {
        viewModelScope.launch {
            try {
                sessionService.endSession(session)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore durante la chiusura del parcheggio.") }
            }
        }
    }

    fun onUserLocationLoaded(
        latitude: Double,
        longitude: Double
    ) {
        _uiState.update {
            it.copy(
                userLatitude = latitude,
                userLongitude = longitude,
                errorMessage = null
            )
        }
    }

    fun onLocationError(
        message: String
    ) {
        _uiState.update {
            it.copy(errorMessage = message)
        }
    }

    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val parkingRepository: ParkingRepository,
        private val savedLocationRepository: SavedLocationRepository,
        private val sessionService: ParkingSessionService
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MapViewModel(
                    vehicleRepository = vehicleRepository,
                    parkingRepository = parkingRepository,
                    savedLocationRepository = savedLocationRepository,
                    sessionService = sessionService
                ) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}