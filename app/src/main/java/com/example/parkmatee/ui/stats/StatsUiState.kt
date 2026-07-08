package com.example.parkmatee.ui.stats

data class StatsUiState(
    val totalCompletedParkings: Int = 0,
    val totalCost: Double = 0.0,
    val freeCount: Int = 0,
    val hourlyCount: Int = 0,
    val fixedCount: Int = 0,
    val averageDurationMinutes: Long = 0L,
    val longestDurationMinutes: Long = 0L,
    val selectedPeriod: StatsPeriodFilter = StatsPeriodFilter.ALL,
    val typeCountEntries: List<StatsChartEntry> = emptyList(),
    val costByTypeEntries: List<StatsChartEntry> = emptyList(),
    val durationByTypeEntries: List<StatsChartEntry> = emptyList(),
    val dailyParkingEntries: List<StatsChartEntry> = emptyList(),
    val errorMessage: String? = null
)

enum class StatsPeriodFilter(
    val label: String,
    val days: Int?
) {
    ALL(
        label = "Tutto",
        days = null
    ),
    LAST_7_DAYS(
        label = "7 giorni",
        days = 7
    ),
    LAST_30_DAYS(
        label = "30 giorni",
        days = 30
    )
}

data class StatsChartEntry(
    val label: String,
    val value: Double,
    val displayValue: String
)
