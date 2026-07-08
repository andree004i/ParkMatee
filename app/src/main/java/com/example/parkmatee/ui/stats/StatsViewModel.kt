package com.example.parkmatee.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.repository.ParkingRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StatsViewModel(
    private val parkingRepository: ParkingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var fullHistory: List<ParkingSession> = emptyList()

    init {
        observeParkingHistory()
    }

    fun onPeriodSelected(
        period: StatsPeriodFilter
    ) {
        _uiState.update { currentState ->
            buildStats(
                history = fullHistory,
                selectedPeriod = period,
                errorMessage = currentState.errorMessage
            )
        }
    }

    private fun observeParkingHistory() {
        viewModelScope.launch {
            parkingRepository.getParkingHistory().collect { history ->
                fullHistory = history

                _uiState.update { currentState ->
                    buildStats(
                        history = history,
                        selectedPeriod = currentState.selectedPeriod,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun buildStats(
        history: List<ParkingSession>,
        selectedPeriod: StatsPeriodFilter,
        errorMessage: String?
    ): StatsUiState {
        val completedParkings = history
            .filter { session -> session.endTime != null }
            .filterByPeriod(selectedPeriod)
            .sortedBy { session -> session.endTime ?: session.startTime }

        val durations = completedParkings.map { session ->
            session.durationMinutes()
        }

        val freeSessions = completedParkings.filter { session -> session.type == FREE_TYPE }
        val hourlySessions = completedParkings.filter { session -> session.type == HOURLY_TYPE }
        val fixedSessions = completedParkings.filter { session -> session.type == FIXED_TYPE }

        val freeCost = freeSessions.sumOf { session -> session.finalOrEstimatedCost() }
        val hourlyCost = hourlySessions.sumOf { session -> session.finalOrEstimatedCost() }
        val fixedCost = fixedSessions.sumOf { session -> session.finalOrEstimatedCost() }

        val typeCountEntries = listOf(
            StatsChartEntry(
                label = "Gratis",
                value = freeSessions.size.toDouble(),
                displayValue = freeSessions.size.toString()
            ),
            StatsChartEntry(
                label = "Ore",
                value = hourlySessions.size.toDouble(),
                displayValue = hourlySessions.size.toString()
            ),
            StatsChartEntry(
                label = "Ticket",
                value = fixedSessions.size.toDouble(),
                displayValue = fixedSessions.size.toString()
            )
        )

        val costByTypeEntries = listOf(
            StatsChartEntry(
                label = "Gratis",
                value = freeCost,
                displayValue = formatCost(freeCost)
            ),
            StatsChartEntry(
                label = "Ore",
                value = hourlyCost,
                displayValue = formatCost(hourlyCost)
            ),
            StatsChartEntry(
                label = "Ticket",
                value = fixedCost,
                displayValue = formatCost(fixedCost)
            )
        )

        val durationByTypeEntries = listOf(
            buildAverageDurationEntry(
                label = "Gratis",
                sessions = freeSessions
            ),
            buildAverageDurationEntry(
                label = "Ore",
                sessions = hourlySessions
            ),
            buildAverageDurationEntry(
                label = "Ticket",
                sessions = fixedSessions
            )
        )

        return StatsUiState(
            totalCompletedParkings = completedParkings.size,
            totalCost = completedParkings.sumOf { session -> session.finalOrEstimatedCost() },
            freeCount = freeSessions.size,
            hourlyCount = hourlySessions.size,
            fixedCount = fixedSessions.size,
            averageDurationMinutes =
                if (durations.isEmpty()) {
                    0L
                } else {
                    durations.sum() / durations.size
                },
            longestDurationMinutes = durations.maxOrNull() ?: 0L,
            selectedPeriod = selectedPeriod,
            typeCountEntries = typeCountEntries,
            costByTypeEntries = costByTypeEntries,
            durationByTypeEntries = durationByTypeEntries,
            dailyParkingEntries = buildDailyEntries(completedParkings),
            errorMessage = errorMessage
        )
    }

    private fun List<ParkingSession>.filterByPeriod(
        selectedPeriod: StatsPeriodFilter
    ): List<ParkingSession> {
        val days = selectedPeriod.days ?: return this
        val startMillis = System.currentTimeMillis() - days.toLong() * MILLIS_PER_DAY

        return filter { session ->
            val endTime = session.endTime ?: return@filter false
            endTime >= startMillis
        }
    }

    private fun buildAverageDurationEntry(
        label: String,
        sessions: List<ParkingSession>
    ): StatsChartEntry {
        val averageDuration = if (sessions.isEmpty()) {
            0L
        } else {
            sessions.sumOf { session -> session.durationMinutes() } / sessions.size
        }

        return StatsChartEntry(
            label = label,
            value = averageDuration.toDouble(),
            displayValue = formatDuration(averageDuration)
        )
    }

    private fun buildDailyEntries(
        completedParkings: List<ParkingSession>
    ): List<StatsChartEntry> {
        val formatter = SimpleDateFormat(
            "dd/MM",
            Locale.getDefault()
        )

        val groupedEntries: List<Map.Entry<String, List<ParkingSession>>> = completedParkings
            .groupBy { session ->
                formatter.format(Date(session.endTime ?: session.startTime))
            }
            .entries
            .toList()

        val startIndex = (groupedEntries.size - MAX_DAILY_ENTRIES).coerceAtLeast(0)
        val visibleEntries = groupedEntries.subList(
            startIndex,
            groupedEntries.size
        )

        return visibleEntries.map { entry: Map.Entry<String, List<ParkingSession>> ->
            StatsChartEntry(
                label = entry.key,
                value = entry.value.size.toDouble(),
                displayValue = entry.value.size.toString()
            )
        }
    }

    private fun ParkingSession.durationMinutes(): Long {
        val endTime = endTime ?: return 0L

        return ((endTime - startTime) / MILLIS_PER_MINUTE)
            .coerceAtLeast(0L)
    }

    private fun ParkingSession.finalOrEstimatedCost(): Double {
        finalCost?.let { cost ->
            return cost
        }

        return when (type) {
            HOURLY_TYPE -> {
                val durationHours = durationMinutes().toDouble() / MINUTES_PER_HOUR
                (hourlyRate ?: 0.0) * durationHours
            }
            FIXED_TYPE -> fixedCost ?: 0.0
            else -> 0.0
        }
    }

    class Factory(
        private val parkingRepository: ParkingRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StatsViewModel(
                    parkingRepository = parkingRepository
                ) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }

    companion object {
        private const val FREE_TYPE = "free"
        private const val HOURLY_TYPE = "hourly"
        private const val FIXED_TYPE = "fixed"
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L
        private const val MINUTES_PER_HOUR = 60.0
        private const val MAX_DAILY_ENTRIES = 7
    }
}

private fun formatCost(
    cost: Double
): String {
    return String.format(
        Locale.getDefault(),
        "%.2f euro",
        cost
    )
}

private fun formatDuration(
    minutes: Long
): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (hours > 0) {
        "${hours}h ${remainingMinutes}min"
    } else {
        "${remainingMinutes}min"
    }
}
