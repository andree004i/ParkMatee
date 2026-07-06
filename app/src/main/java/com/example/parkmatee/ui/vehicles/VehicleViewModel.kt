package com.example.parkmatee.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.Vehicle
import com.example.parkmatee.data.repository.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VehicleViewModel(
    private val repository: VehicleRepository
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> = repository.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addVehicle(name: String, type: String) {
        viewModelScope.launch {
            repository.insertVehicle(Vehicle(name = name, type = type))
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }

    // Factory per creare il ViewModel senza Hilt
    class Factory(private val repository: VehicleRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VehicleViewModel(repository) as T
        }
    }
}