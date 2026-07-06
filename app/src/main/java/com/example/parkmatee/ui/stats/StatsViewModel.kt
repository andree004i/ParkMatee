package com.example.parkmatee.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.repository.ParkingRepository
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

    init {
        observeParkingHistory()
    }

    private fun observeParkingHistory() {
        viewModelScope.launch {
            parkingRepository.getParkingHistory().collect { history ->
                _uiState.update {
                    buildStats(history)
                }
            }
        }
    }

    private fun buildStats(
        history: List<ParkingSession>
    ): StatsUiState {
        val completedParkings = history.filter { session ->
            session.endTime != null
        }

        val durations = completedParkings.mapNotNull { session ->
            val endTime = session.endTime

            if (endTime != null) {
                ((endTime - session.startTime) / MILLIS_PER_MINUTE)
                    .coerceAtLeast(0L)
            } else {
                null
            }
        }

        return StatsUiState(
            totalCompletedParkings = completedParkings.size,
            totalCost = completedParkings.sumOf { session ->
                session.finalCost ?: 0.0
            },
            freeCount = completedParkings.count { session ->
                session.type == "free"
            },
            hourlyCount = completedParkings.count { session ->
                session.type == "hourly"
            },
            fixedCount = completedParkings.count { session ->
                session.type == "fixed"
            },
            averageDurationMinutes =
                if (durations.isEmpty()) {
                    0L
                } else {
                    durations.sum() / durations.size
                },
            longestDurationMinutes = durations.maxOrNull() ?: 0L
        )
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
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}