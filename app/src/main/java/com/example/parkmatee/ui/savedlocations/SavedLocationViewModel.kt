package com.example.parkmatee.ui.savedlocations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.repository.SavedLocationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedLocationViewModel(
    private val repository: SavedLocationRepository
) : ViewModel() {

    val locations: StateFlow<List<SavedLocation>> = repository.getAllLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLocation(
        name: String,
        latitude: Double,
        longitude: Double
    ) {
        viewModelScope.launch {
            repository.insert(
                SavedLocation(
                    name = name,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
    }

    fun updateLocation(
        location: SavedLocation,
        name: String,
        latitude: Double,
        longitude: Double
    ) {
        viewModelScope.launch {
            repository.update(
                location.copy(
                    name = name,
                    latitude = latitude,
                    longitude = longitude
                )
            )
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

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.delete(location)
        }
    }

    class Factory(
        private val repository: SavedLocationRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SavedLocationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SavedLocationViewModel(repository) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}
