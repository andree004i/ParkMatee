package com.example.parkmatee.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.VehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * I filtri vivono nello [SavedStateHandle]: sopravvivono a rotazione, cambio tab E morte del
 * processo. L'elenco filtrato arriva direttamente da Room (flatMapLatest sui filtri).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val errorMessage = MutableStateFlow<String?>(null)

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

    private val history = filters.flatMapLatest { f ->
        parkingRepository.getFilteredHistory(
            vehicleId = f.vehicleId,
            type = f.type.type,
            fromTime = f.range.fromMillis(System.currentTimeMillis()),
            toTime = null
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        vehicleRepository.getAllVehicles(),
        history,
        filters,
        errorMessage
    ) { vehicles, sessions, f, error ->
        HistoryUiState(
            isLoading = false,
            vehicles = vehicles,
            parkingHistory = sessions,
            filters = f,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

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

    fun onErrorShown() {
        errorMessage.value = null
    }

    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val parkingRepository: ParkingRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(
                    vehicleRepository = vehicleRepository,
                    parkingRepository = parkingRepository,
                    savedStateHandle = extras.createSavedStateHandle()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val KEY_VEHICLE = "history_vehicle_id"
        const val KEY_TYPE = "history_type"
        const val KEY_RANGE = "history_range"
        const val NO_VEHICLE = -1
    }
}
