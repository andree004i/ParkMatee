package com.example.parkmatee.ui.stats

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.Vehicle
import com.example.parkmatee.ui.history.HistoryFilters

data class StatsUiState(
    val isLoading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val filters: HistoryFilters = HistoryFilters(),
    /** Soste terminate che rispettano i filtri: sorgente di tutte le visualizzazioni. */
    val sessions: List<ParkingSession> = emptyList(),
    val totalCost: Double = 0.0,
    val averageDurationMinutes: Long = 0L,
    val longestDurationMinutes: Long = 0L,
    val costByType: Map<ParkingType, Double> = emptyMap(),
    val countByType: Map<ParkingType, Int> = emptyMap(),
    val errorMessage: String? = null
)
