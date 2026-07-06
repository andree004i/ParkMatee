package com.example.parkmatee.ui.history

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.Vehicle

enum class HistoryTypeFilter(
    val dbValue: String?,
    val label: String
) {
    ALL(null, "Tutti"),
    FREE("free", "Gratuito"),
    HOURLY("hourly", "A ore"),
    FIXED("fixed", "Ticket fisso")
}

data class HistoryUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val parkingHistory: List<ParkingSession> = emptyList(),
    val selectedTypeFilter: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val selectedVehicleId: Int? = null,
    val errorMessage: String? = null
)