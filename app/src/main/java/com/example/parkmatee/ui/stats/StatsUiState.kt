package com.example.parkmatee.ui.stats

data class StatsUiState(
    val totalCompletedParkings: Int = 0,
    val totalCost: Double = 0.0,
    val freeCount: Int = 0,
    val hourlyCount: Int = 0,
    val fixedCount: Int = 0,
    val averageDurationMinutes: Long = 0L,
    val longestDurationMinutes: Long = 0L,
    val errorMessage: String? = null
)