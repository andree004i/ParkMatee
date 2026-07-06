package com.example.parkmatee.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeVehicles()
        observeParkingHistory()
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

    private fun observeParkingHistory() {
        viewModelScope.launch {
            parkingRepository.getParkingHistory().collect { parkingHistory ->
                _uiState.update {
                    it.copy(parkingHistory = parkingHistory)
                }
            }
        }
    }

    fun onTypeFilterSelected(
        filter: HistoryTypeFilter
    ) {
        _uiState.update {
            it.copy(selectedTypeFilter = filter)
        }
    }

    fun onVehicleFilterSelected(
        vehicleId: Int?
    ) {
        _uiState.update {
            it.copy(selectedVehicleId = vehicleId)
        }
    }

    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val parkingRepository: ParkingRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(
                    vehicleRepository = vehicleRepository,
                    parkingRepository = parkingRepository
                ) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}