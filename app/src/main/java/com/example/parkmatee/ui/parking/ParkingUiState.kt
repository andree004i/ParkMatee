package com.example.parkmatee.ui.parking

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.entity.Vehicle

enum class ParkingType(
    val dbValue: String,
    val label: String
) {
    FREE("free", "Gratuito"),
    HOURLY("hourly", "A ore"),
    FIXED("fixed", "Ticket fisso")
}

data class ParkingUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val activeParkings: List<ParkingSession> = emptyList(),
    val savedLocations: List<SavedLocation> = emptyList(),

    val selectedVehicleId: Int? = null,
    val selectedType: ParkingType = ParkingType.FREE,
    val selectedSavedLocationId: Int? = null,

    val hourlyRate: String = "",
    val fixedCost: String = "",
    val expiryMinutes: String = "",

    val latitude: String = "",
    val longitude: String = "",

    val savedLocationName: String? = null,
    val photoPath: String? = null,
    val note: String = "",

    val errorMessage: String? = null,
    val successMessage: String? = null
)