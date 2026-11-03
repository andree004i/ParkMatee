package com.example.parkmatee.ui.parking

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.SavedLocationRepository
import com.example.parkmatee.data.repository.VehicleRepository
import com.example.parkmatee.notification.NotificationConstants
import com.example.parkmatee.notification.ParkingAlarmScheduler
import com.example.parkmatee.notification.ParkingWorkScheduler
import com.example.parkmatee.notification.cancelSessionNotifications
import com.example.parkmatee.notification.showParkingNotification
import com.example.parkmatee.util.deletePhotoQuietly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ParkingViewModel(
    private val application: Application,
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val savedLocationRepository: SavedLocationRepository,
    private val alarmScheduler: ParkingAlarmScheduler,
    private val workScheduler: ParkingWorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParkingUiState())
    val uiState: StateFlow<ParkingUiState> = _uiState.asStateFlow()

    init {
        observeVehicles()
        observeActiveParkings()
        observeSavedLocations()
        refreshExactAlarmPermission()
    }

    fun refreshExactAlarmPermission() {
        val canSchedule = alarmScheduler.canScheduleExact()
        _uiState.update { it.copy(canScheduleExactAlarms = canSchedule) }
    }

    fun onRequestExactAlarmPermission() {
        alarmScheduler.openExactAlarmPermissionSettings()
    }

    private fun observeVehicles() {
        viewModelScope.launch {
            vehicleRepository.getAllVehicles().collect { vehicles ->
                _uiState.update { currentState ->
                    val selectedVehicleStillExists = currentState.selectedVehicleId?.let { selectedId ->
                        vehicles.any { vehicle -> vehicle.id == selectedId }
                    } ?: false

                    val defaultVehicleId = if (selectedVehicleStillExists) {
                        currentState.selectedVehicleId
                    } else {
                        vehicles.firstOrNull()?.id
                    }

                    currentState.copy(
                        vehicles = vehicles,
                        selectedVehicleId = defaultVehicleId
                    )
                }
            }
        }
    }

    private fun observeActiveParkings() {
        viewModelScope.launch {
            parkingRepository.getActiveParkings().collect { activeParkings ->
                _uiState.update { it.copy(activeParkings = activeParkings) }
            }
        }
    }

    private fun observeSavedLocations() {
        viewModelScope.launch {
            savedLocationRepository.getAllLocations().collect { savedLocations ->
                _uiState.update { currentState ->
                    val selectedLocation = savedLocations.firstOrNull { location ->
                        location.id == currentState.selectedSavedLocationId
                    }

                    currentState.copy(
                        savedLocations = savedLocations,
                        selectedSavedLocationId = selectedLocation?.id,
                        savedLocationName = selectedLocation?.name
                            ?: currentState.savedLocationName?.takeIf { currentState.selectedSavedLocationId == null }
                    )
                }
            }
        }
    }

    fun onEndParkingClicked(session: ParkingSession) {
        viewModelScope.launch {
            try {
                parkingRepository.endParking(session)
                cancelSessionJobs(session.id)

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

    /** Worker, allarmi e notifiche gia mostrate di una sosta chiusa o sostituita. */
    private fun cancelSessionJobs(sessionId: Int) {
        workScheduler.cancelPeriodicReminder(sessionId)
        alarmScheduler.cancelTicketAlarms(sessionId)
        cancelSessionNotifications(application, sessionId)
    }

    fun onVehicleSelected(vehicleId: Int) {
        _uiState.update { it.copy(selectedVehicleId = vehicleId, errorMessage = null, successMessage = null) }
    }

    fun onParkingTypeSelected(type: ParkingType) {
        _uiState.update {
            it.copy(
                selectedType = type,
                hourlyRate = if (type == ParkingType.HOURLY) it.hourlyRate else "",
                fixedCost = if (type == ParkingType.FIXED) it.fixedCost else "",
                expiryMinutes = if (type == ParkingType.FIXED) it.expiryMinutes else "",
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onHourlyRateChanged(value: String) {
        _uiState.update { it.copy(hourlyRate = value, errorMessage = null, successMessage = null) }
    }

    fun onFixedCostChanged(value: String) {
        _uiState.update { it.copy(fixedCost = value, errorMessage = null, successMessage = null) }
    }

    fun onExpiryMinutesChanged(value: String) {
        _uiState.update { it.copy(expiryMinutes = value, errorMessage = null, successMessage = null) }
    }

    fun onWarningMinutesChanged(minutes: Int) {
        _uiState.update { it.copy(warningMinutes = minutes, errorMessage = null, successMessage = null) }
    }

    fun onLatitudeChanged(value: String) {
        _uiState.update {
            it.copy(latitude = value, locationSource = LocationSource.MANUAL, selectedSavedLocationId = null, savedLocationName = null, errorMessage = null, successMessage = null)
        }
    }

    fun onLongitudeChanged(value: String) {
        _uiState.update {
            it.copy(longitude = value, locationSource = LocationSource.MANUAL, selectedSavedLocationId = null, savedLocationName = null, errorMessage = null, successMessage = null)
        }
    }

    /** Punto scelto toccando o trascinando il marker sulla mappa. */
    fun onMapPositionSelected(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                latitude = latitude.toString(),
                longitude = longitude.toString(),
                locationSource = LocationSource.MAP,
                selectedSavedLocationId = null,
                savedLocationName = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onLocationRequested() {
        _uiState.update { it.copy(isLoadingLocation = true, locationHelp = LocationHelp.NONE, errorMessage = null, successMessage = null) }
    }

    fun onSavedLocationSelected(locationId: Int?) {
        val selectedLocation = _uiState.value.savedLocations.firstOrNull { it.id == locationId }
        _uiState.update {
            if (selectedLocation == null) {
                it.copy(selectedSavedLocationId = null, savedLocationName = null, errorMessage = null, successMessage = null)
            } else {
                it.copy(
                    selectedSavedLocationId = selectedLocation.id,
                    latitude = selectedLocation.latitude.toString(),
                    longitude = selectedLocation.longitude.toString(),
                    savedLocationName = selectedLocation.name,
                    locationSource = LocationSource.SAVED_PLACE,
                    errorMessage = null,
                    successMessage = "Luogo salvato selezionato."
                )
            }
        }
    }

    fun onCurrentLocationLoaded(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(latitude = latitude.toString(), longitude = longitude.toString(), locationSource = LocationSource.GPS, isLoadingLocation = false, locationHelp = LocationHelp.NONE, selectedSavedLocationId = null, savedLocationName = null, errorMessage = null, successMessage = "Posizione attuale caricata.")
        }
    }

    fun onLocationError(message: String) {
        _uiState.update { it.copy(isLoadingLocation = false, errorMessage = message, successMessage = null) }
    }

    fun onLocationProblem(message: String, help: LocationHelp) {
        _uiState.update { it.copy(isLoadingLocation = false, locationHelp = help, errorMessage = message, successMessage = null) }
    }

    fun onPhotoCaptured(photoPath: String) {
        // La foto precedente (non ancora salvata in una sosta) diventerebbe un file orfano.
        _uiState.value.photoPath?.takeIf { it != photoPath }?.let { deletePhotoQuietly(it) }
        _uiState.update { it.copy(photoPath = photoPath, errorMessage = null, successMessage = "Foto parcheggio aggiunta.") }
    }

    fun onPhotoCaptureError(message: String) {
        _uiState.update { it.copy(errorMessage = message, successMessage = null) }
    }

    fun onRemovePhotoClicked() {
        deletePhotoQuietly(_uiState.value.photoPath)
        _uiState.update { it.copy(photoPath = null, errorMessage = null, successMessage = null) }
    }

    fun onNoteChanged(value: String) {
        _uiState.update { it.copy(note = value.take(MAX_NOTE_LENGTH), errorMessage = null, successMessage = null) }
    }

    /** Modifica della nota di una sosta gia avviata. */
    fun onSessionNoteChanged(session: ParkingSession, note: String) {
        viewModelScope.launch {
            val current = parkingRepository.getParkingById(session.id) ?: return@launch
            parkingRepository.updateParking(
                current.copy(note = note.trim().take(MAX_NOTE_LENGTH).ifBlank { null })
            )
        }
    }

    /** Aggiunta o sostituzione della foto di una sosta gia avviata. */
    fun onSessionPhotoChanged(session: ParkingSession, newPhotoPath: String) {
        viewModelScope.launch {
            val current = parkingRepository.getParkingById(session.id)
            if (current == null) {
                withContext(Dispatchers.IO) { deletePhotoQuietly(newPhotoPath) }
                return@launch
            }
            parkingRepository.updateParking(current.copy(photoPath = newPhotoPath))
            if (current.photoPath != null && current.photoPath != newPhotoPath) {
                withContext(Dispatchers.IO) { deletePhotoQuietly(current.photoPath) }
            }
        }
    }

    override fun onCleared() {
        // Foto scattata nel form ma mai salvata in una sosta: non lasciare file orfani.
        deletePhotoQuietly(_uiState.value.photoPath)
        super.onCleared()
    }

    fun onStartParkingClicked() {
        val currentState = _uiState.value
        val selectedVehicleId = currentState.selectedVehicleId ?: currentState.vehicles.firstOrNull()?.id
        val effectiveState = if (currentState.selectedVehicleId == null && selectedVehicleId != null) {
            currentState.copy(selectedVehicleId = selectedVehicleId)
        } else {
            currentState
        }

        val validationError = validate(effectiveState)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, successMessage = null) }
            return
        }

        val finalVehicleId = effectiveState.selectedVehicleId ?: return
        val latitude = effectiveState.latitude.toValidDoubleOrNull() ?: return
        val longitude = effectiveState.longitude.toValidDoubleOrNull() ?: return
        val startTime = System.currentTimeMillis()

        val hourlyRate = if (effectiveState.selectedType == ParkingType.HOURLY) effectiveState.hourlyRate.toValidDoubleOrNull() else null
        val fixedCost = if (effectiveState.selectedType == ParkingType.FIXED) effectiveState.fixedCost.toValidDoubleOrNull() else null
        val expiryTime = if (effectiveState.selectedType == ParkingType.FIXED) {
            val expiryMinutes = effectiveState.expiryMinutes.toLongOrNull() ?: return
            startTime + expiryMinutes * MILLIS_PER_MINUTE
        } else {
            null
        }

        val session = ParkingSession(
            vehicleId = finalVehicleId,
            type = effectiveState.selectedType,
            startTime = startTime,
            latitude = latitude,
            longitude = longitude,
            savedLocationName = effectiveState.savedLocationName,
            savedLocationId = effectiveState.selectedSavedLocationId,
            hourlyRate = hourlyRate,
            fixedCost = fixedCost,
            expiryTime = expiryTime,
            warningMinutes = effectiveState.warningMinutes,
            photoPath = effectiveState.photoPath,
            note = effectiveState.note.trim().ifBlank { null }
        )

        viewModelScope.launch {
            try {
                // Chiusura automatica delle soste attive del veicolo + inserimento: una sola transazione.
                val result = parkingRepository.startParkingReplacingActive(session)
                result.closedSessions.forEach { cancelSessionJobs(it.id) }
                val newSessionId = result.newSessionId

                showParkingNotification(
                    context = application,
                    notificationId = NotificationConstants.START_NOTIFICATION_BASE_ID + newSessionId,
                    title = "Parcheggio attivo",
                    text = "Hai avviato un nuovo parcheggio. Ricordati di terminarlo quando riparti."
                )

                workScheduler.schedulePeriodicReminder(newSessionId, startTime)

                if (expiryTime != null) {
                    alarmScheduler.scheduleTicketAlarms(
                        sessionId = newSessionId,
                        expiryTimeMillis = expiryTime,
                        warningMinutes = effectiveState.warningMinutes
                    )
                }

                _uiState.update {
                    it.copy(
                        selectedVehicleId = it.vehicles.firstOrNull()?.id,
                        selectedType = ParkingType.FREE,
                        selectedSavedLocationId = null,
                        hourlyRate = "",
                        fixedCost = "",
                        expiryMinutes = "",
                        warningMinutes = 5,
                        latitude = "",
                        longitude = "",
                        locationSource = LocationSource.NONE,
                        savedLocationName = null,
                        photoPath = null,
                        note = "",
                        errorMessage = null,
                        successMessage = "Parcheggio avviato correttamente."
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore durante il salvataggio del parcheggio.", successMessage = null)
                }
            }
        }
    }

    private fun validate(state: ParkingUiState): String? {
        if (state.selectedVehicleId == null) return "Seleziona un veicolo."

        val latitude = state.latitude.toValidDoubleOrNull() ?: return "Inserisci una latitudine valida."
        val longitude = state.longitude.toValidDoubleOrNull() ?: return "Inserisci una longitudine valida."

        if (latitude !in -90.0..90.0) return "La latitudine deve essere compresa tra -90 e 90."
        if (longitude !in -180.0..180.0) return "La longitudine deve essere compresa tra -180 e 180."

        when (state.selectedType) {
            ParkingType.FREE -> Unit
            ParkingType.HOURLY -> {
                val hourlyRate = state.hourlyRate.toValidDoubleOrNull() ?: return "Inserisci una tariffa oraria valida."
                if (hourlyRate <= 0.0) return "La tariffa oraria deve essere maggiore di zero."
            }
            ParkingType.FIXED -> {
                val fixedCost = state.fixedCost.toValidDoubleOrNull() ?: return "Inserisci un costo fisso valido."
                if (fixedCost < 0.0) return "Il costo fisso non può essere negativo."
                val expiryMinutes = state.expiryMinutes.toLongOrNull() ?: return "Inserisci una durata valida in minuti."
                if (expiryMinutes <= 0L) return "La durata deve essere maggiore di zero."
            }
        }
        return null
    }

    private fun String.toValidDoubleOrNull(): Double? = replace(',', '.').toDoubleOrNull()

    class Factory(
        private val application: Application,
        private val vehicleRepository: VehicleRepository,
        private val parkingRepository: ParkingRepository,
        private val savedLocationRepository: SavedLocationRepository,
        private val alarmScheduler: ParkingAlarmScheduler,
        private val workScheduler: ParkingWorkScheduler
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ParkingViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ParkingViewModel(
                    application = application,
                    vehicleRepository = vehicleRepository,
                    parkingRepository = parkingRepository,
                    savedLocationRepository = savedLocationRepository,
                    alarmScheduler = alarmScheduler,
                    workScheduler = workScheduler
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        const val MAX_NOTE_LENGTH = 500
    }
}