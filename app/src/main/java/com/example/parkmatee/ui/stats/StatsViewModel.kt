package com.example.parkmatee.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.VehicleRepository
import com.example.parkmatee.ui.history.HistoryFilters
import com.example.parkmatee.ui.history.HistoryRange
import com.example.parkmatee.ui.history.HistoryTypeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Le soste arrivano gia filtrate da Room (veicolo, tipo, intervallo di inizio) con la stessa
 * query dello storico, quindi i numeri delle due schermate coincidono. I filtri stanno nello
 * [SavedStateHandle] e sopravvivono anche alla morte del processo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filters = combine(
        savedStateHandle.getStateFlow(KEY_VEHICLE, NO_VEHICLE),
        savedStateHandle.getStateFlow(KEY_TYPE, HistoryTypeFilter.ALL.name),
        savedStateHandle.getStateFlow(KEY_RANGE, HistoryRange.ALL.name)
    ) { vehicleId, type, range ->
        HistoryFilters(
            vehicleId = vehicleId.takeIf { it != NO_VEHICLE },
            type = HistoryTypeFilter.fromName(type),
            range = HistoryRange.fromName(range)
        )
    }

    private val sessions = filters.flatMapLatest { f ->
        parkingRepository.getFilteredHistory(
            vehicleId = f.vehicleId,
            type = f.type.type,
            fromTime = f.range.fromMillis(System.currentTimeMillis()),
            toTime = null
        )
    }

    val uiState: StateFlow<StatsUiState> = combine(
        vehicleRepository.getAllVehicles(),
        sessions,
        filters
    ) { vehicles, list, f -> buildState(vehicles, list, f) }
        .flowOn(Dispatchers.Default)
        .catch { e ->
            emit(
                StatsUiState(
                    isLoading = false,
                    errorMessage = "Errore nel caricamento delle statistiche: ${e.localizedMessage}"
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    init {
        // Se il veicolo filtrato viene eliminato, il filtro torna su "Tutti".
        viewModelScope.launch {
            vehicleRepository.getAllVehicles().collect { vehicles ->
                val selected = savedStateHandle.get<Int>(KEY_VEHICLE) ?: NO_VEHICLE
                if (selected != NO_VEHICLE && vehicles.none { it.id == selected }) {
                    savedStateHandle[KEY_VEHICLE] = NO_VEHICLE
                }
            }
        }
    }

    fun onTypeFilterSelected(filter: HistoryTypeFilter) {
        savedStateHandle[KEY_TYPE] = filter.name
    }

    fun onVehicleFilterSelected(vehicleId: Int?) {
        savedStateHandle[KEY_VEHICLE] = vehicleId ?: NO_VEHICLE
    }

    fun onRangeSelected(range: HistoryRange) {
        savedStateHandle[KEY_RANGE] = range.name
    }

    fun onClearFilters() {
        savedStateHandle[KEY_VEHICLE] = NO_VEHICLE
        savedStateHandle[KEY_TYPE] = HistoryTypeFilter.ALL.name
        savedStateHandle[KEY_RANGE] = HistoryRange.ALL.name
    }

    private fun buildState(
        vehicles: List<com.example.parkmatee.data.entity.Vehicle>,
        list: List<ParkingSession>,
        f: HistoryFilters
    ): StatsUiState {
        val durations = list.map { session ->
            val end = session.endTime ?: session.startTime
            ((end - session.startTime) / MILLIS_PER_MINUTE).coerceAtLeast(0L)
        }

        return StatsUiState(
            isLoading = false,
            vehicles = vehicles,
            filters = f,
            sessions = list,
            totalCost = list.sumOf { it.statsCost() }.roundCents(),
            averageDurationMinutes = if (durations.isEmpty()) 0L else durations.sum() / durations.size,
            longestDurationMinutes = durations.maxOrNull() ?: 0L
        )
    }

    private fun Double.roundCents(): Double = Math.round(this * 100.0) / 100.0

    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val parkingRepository: ParkingRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StatsViewModel(
                    vehicleRepository = vehicleRepository,
                    parkingRepository = parkingRepository,
                    savedStateHandle = extras.createSavedStateHandle()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val KEY_VEHICLE = "stats_vehicle_id"
        const val KEY_TYPE = "stats_type"
        const val KEY_RANGE = "stats_range"
        const val NO_VEHICLE = -1
    }
}
