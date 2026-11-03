package com.example.parkmatee.ui.history

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.Vehicle
import java.util.Calendar

enum class HistoryTypeFilter(
    val type: ParkingType?,
    val label: String
) {
    ALL(null, "Tutti"),
    FREE(ParkingType.FREE, ParkingType.FREE.label),
    HOURLY(ParkingType.HOURLY, ParkingType.HOURLY.label),
    FIXED(ParkingType.FIXED, ParkingType.FIXED.label);

    companion object {
        fun fromName(name: String?): HistoryTypeFilter =
            entries.firstOrNull { it.name == name } ?: ALL
    }
}

/** Filtro sull'intervallo di inizio sosta (preset). */
enum class HistoryRange(val label: String) {
    ALL("Sempre"),
    LAST_7_DAYS("7 giorni"),
    LAST_30_DAYS("30 giorni"),
    THIS_MONTH("Mese corrente");

    /** Istante di inizio dell'intervallo, null se non c'e limite inferiore. */
    fun fromMillis(now: Long): Long? = when (this) {
        ALL -> null
        LAST_7_DAYS -> now - 7L * DAY_MILLIS
        LAST_30_DAYS -> now - 30L * DAY_MILLIS
        THIS_MONTH -> Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        fun fromName(name: String?): HistoryRange =
            entries.firstOrNull { it.name == name } ?: ALL
    }
}

data class HistoryFilters(
    val vehicleId: Int? = null,
    val type: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val range: HistoryRange = HistoryRange.ALL
) {
    val isActive: Boolean
        get() = vehicleId != null || type != HistoryTypeFilter.ALL || range != HistoryRange.ALL
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val parkingHistory: List<ParkingSession> = emptyList(),
    val filters: HistoryFilters = HistoryFilters(),
    val errorMessage: String? = null
)
