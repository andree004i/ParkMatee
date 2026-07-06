package com.example.parkmatee.ui.map

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.Vehicle

data class MapUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val activeParkings: List<ParkingSession> = emptyList(),
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val errorMessage: String? = null
)