package com.example.parkmatee.ui.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ParkingViewModel(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParkingUiState())
    val uiState: StateFlow<ParkingUiState> = _uiState.asStateFlow()

    init {
        observeVehicles()
        observeActiveParkings()
    }

    private fun observeVehicles() {
        viewModelScope.launch {
            vehicleRepository.getAllVehicles().collect { vehicles ->
                _uiState.update { currentState ->
                    val selectedVehicleStillExists =
                        currentState.selectedVehicleId?.let { selectedId ->
                            vehicles.any { vehicle ->
                                vehicle.id == selectedId
                            }
                        } ?: false

                    currentState.copy(
                        vehicles = vehicles,
                        selectedVehicleId =
                            if (selectedVehicleStillExists) {
                                currentState.selectedVehicleId
                            } else {
                                null
                            }
                    )
                }
            }
        }
    }

    private fun observeActiveParkings() {
        viewModelScope.launch {
            parkingRepository.getActiveParkings().collect { activeParkings ->
                _uiState.update {
                    it.copy(activeParkings = activeParkings)
                }
            }
        }
    }

    fun onEndParkingClicked(
        session: ParkingSession
    ) {
        viewModelScope.launch {
            try {
                parkingRepository.endParking(session)

                _uiState.update {
                    it.copy(
                        errorMessage = null,
                        successMessage = "Parcheggio terminato correttamente."
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Errore durante la chiusura del parcheggio.",
                        successMessage = null
                    )
                }
            }
        }
    }

    fun onVehicleSelected(
        vehicleId: Int
    ) {
        _uiState.update {
            it.copy(
                selectedVehicleId = vehicleId,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onParkingTypeSelected(
        type: ParkingType
    ) {
        _uiState.update {
            it.copy(
                selectedType = type,
                hourlyRate =
                    if (type == ParkingType.HOURLY) {
                        it.hourlyRate
                    } else {
                        ""
                    },
                fixedCost =
                    if (type == ParkingType.FIXED) {
                        it.fixedCost
                    } else {
                        ""
                    },
                expiryMinutes =
                    if (type == ParkingType.FIXED) {
                        it.expiryMinutes
                    } else {
                        ""
                    },
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onHourlyRateChanged(
        value: String
    ) {
        _uiState.update {
            it.copy(
                hourlyRate = value,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onFixedCostChanged(
        value: String
    ) {
        _uiState.update {
            it.copy(
                fixedCost = value,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onExpiryMinutesChanged(
        value: String
    ) {
        _uiState.update {
            it.copy(
                expiryMinutes = value,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onLatitudeChanged(
        value: String
    ) {
        _uiState.update {
            it.copy(
                latitude = value,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onLongitudeChanged(
        value: String
    ) {
        _uiState.update {
            it.copy(
                longitude = value,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onCurrentLocationLoaded(
        latitude: Double,
        longitude: Double
    ) {
        _uiState.update {
            it.copy(
                latitude = latitude.toString(),
                longitude = longitude.toString(),
                savedLocationName = null,
                errorMessage = null,
                successMessage = "Posizione attuale caricata."
            )
        }
    }

    fun onLocationError(
        message: String
    ) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                successMessage = null
            )
        }
    }

    fun onNoteChanged(
        value: String
    ) {
        _uiState.update {
            it.copy(
                note = value,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onStartParkingClicked() {
        val currentState = _uiState.value
        val validationError = validate(currentState)

        if (validationError != null) {
            _uiState.update {
                it.copy(
                    errorMessage = validationError,
                    successMessage = null
                )
            }

            return
        }

        val selectedVehicleId =
            currentState.selectedVehicleId ?: return

        val latitude =
            currentState.latitude.toValidDoubleOrNull() ?: return

        val longitude =
            currentState.longitude.toValidDoubleOrNull() ?: return

        val startTime = System.currentTimeMillis()

        val hourlyRate =
            if (currentState.selectedType == ParkingType.HOURLY) {
                currentState.hourlyRate.toValidDoubleOrNull()
            } else {
                null
            }

        val fixedCost =
            if (currentState.selectedType == ParkingType.FIXED) {
                currentState.fixedCost.toValidDoubleOrNull()
            } else {
                null
            }

        val expiryTime =
            if (currentState.selectedType == ParkingType.FIXED) {
                val expiryMinutes =
                    currentState.expiryMinutes.toLongOrNull() ?: return

                startTime + expiryMinutes * MILLIS_PER_MINUTE
            } else {
                null
            }

        val session = ParkingSession(
            vehicleId = selectedVehicleId,
            type = currentState.selectedType.dbValue,
            startTime = startTime,
            latitude = latitude,
            longitude = longitude,
            savedLocationName = currentState.savedLocationName,
            hourlyRate = hourlyRate,
            fixedCost = fixedCost,
            expiryTime = expiryTime,
            photoPath = currentState.photoPath,
            note = currentState.note.trim().ifBlank {
                null
            }
        )

        viewModelScope.launch {
            try {
                parkingRepository.closeActiveSessionIfExists(
                    vehicleId = selectedVehicleId
                )

                parkingRepository.startParking(
                    session = session
                )

                _uiState.update {
                    it.copy(
                        selectedVehicleId = null,
                        selectedType = ParkingType.FREE,
                        hourlyRate = "",
                        fixedCost = "",
                        expiryMinutes = "",
                        latitude = "",
                        longitude = "",
                        savedLocationName = null,
                        photoPath = null,
                        note = "",
                        errorMessage = null,
                        successMessage = "Parcheggio avviato correttamente."
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage =
                            "Errore durante il salvataggio del parcheggio.",
                        successMessage = null
                    )
                }
            }
        }
    }

    private fun validate(
        state: ParkingUiState
    ): String? {
        if (state.selectedVehicleId == null) {
            return "Seleziona un veicolo."
        }

        val latitude =
            state.latitude.toValidDoubleOrNull()
                ?: return "Inserisci una latitudine valida."

        val longitude =
            state.longitude.toValidDoubleOrNull()
                ?: return "Inserisci una longitudine valida."

        if (latitude !in -90.0..90.0) {
            return "La latitudine deve essere compresa tra -90 e 90."
        }

        if (longitude !in -180.0..180.0) {
            return "La longitudine deve essere compresa tra -180 e 180."
        }

        when (state.selectedType) {
            ParkingType.FREE -> Unit

            ParkingType.HOURLY -> {
                val hourlyRate =
                    state.hourlyRate.toValidDoubleOrNull()
                        ?: return "Inserisci una tariffa oraria valida."

                if (hourlyRate <= 0.0) {
                    return "La tariffa oraria deve essere maggiore di zero."
                }
            }

            ParkingType.FIXED -> {
                val fixedCost =
                    state.fixedCost.toValidDoubleOrNull()
                        ?: return "Inserisci un costo fisso valido."

                if (fixedCost < 0.0) {
                    return "Il costo fisso non può essere negativo."
                }

                val expiryMinutes =
                    state.expiryMinutes.toLongOrNull()
                        ?: return "Inserisci una durata valida in minuti."

                if (expiryMinutes <= 0L) {
                    return "La durata deve essere maggiore di zero."
                }
            }
        }

        return null
    }

    private fun String.toValidDoubleOrNull(): Double? {
        return replace(',', '.').toDoubleOrNull()
    }

    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val parkingRepository: ParkingRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (
                modelClass.isAssignableFrom(
                    ParkingViewModel::class.java
                )
            ) {
                @Suppress("UNCHECKED_CAST")
                return ParkingViewModel(
                    vehicleRepository = vehicleRepository,
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