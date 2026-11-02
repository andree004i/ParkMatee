package com.example.parkmatee.ui.history

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.Vehicle

enum class HistoryTypeFilter(
    val type: ParkingType?,
    val label: String
) {
    ALL(null, "Tutti"),
    FREE(ParkingType.FREE, ParkingType.FREE.label),
    HOURLY(ParkingType.HOURLY, ParkingType.HOURLY.label),
    FIXED(ParkingType.FIXED, ParkingType.FIXED.label)
}

data class HistoryUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val parkingHistory: List<ParkingSession> = emptyList(),
    val selectedTypeFilter: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val selectedVehicleId: Int? = null,
    val errorMessage: String? = null
)