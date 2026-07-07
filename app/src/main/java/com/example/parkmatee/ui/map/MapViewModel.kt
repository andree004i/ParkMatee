package com.example.parkmatee.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    private val savedLocationRepository: SavedLocationRepository
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
        private val savedLocationRepository: SavedLocationRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MapViewModel(
                    vehicleRepository = vehicleRepository,
                    parkingRepository = parkingRepository,
                    savedLocationRepository = savedLocationRepository
                ) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}