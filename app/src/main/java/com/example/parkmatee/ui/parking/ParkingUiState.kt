package com.example.parkmatee.ui.parking

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.entity.Vehicle

/** Da dove arriva la posizione corrente del form. */
enum class LocationSource(val label: String) {
    NONE("Nessuna posizione scelta"),
    GPS("GPS"),
    SAVED_PLACE("Luogo salvato"),
    MAP("Scelta sulla mappa"),
    MANUAL("Inserita a mano")
}

/** Azione di aiuto proposta all'utente dopo un problema di posizione. */
enum class LocationHelp(val label: String) {
    NONE(""),
    OPEN_LOCATION_SETTINGS("Apri impostazioni localizzazione"),
    OPEN_APP_SETTINGS("Apri impostazioni app")
}

data class ParkingUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicleId: Int? = null,
    val selectedType: ParkingType = ParkingType.FREE,
    val hourlyRate: String = "",
    val fixedCost: String = "",
    val expiryMinutes: String = "",
    val warningMinutes: Int = 5,
    val canScheduleExactAlarms: Boolean = true,
    val latitude: String = "",
    val longitude: String = "",
    val locationSource: LocationSource = LocationSource.NONE,
    val isLoadingLocation: Boolean = false,
    val locationHelp: LocationHelp = LocationHelp.NONE,
    val savedLocations: List<SavedLocation> = emptyList(),
    val selectedSavedLocationId: Int? = null,
    val savedLocationName: String? = null,
    val photoPath: String? = null,
    val note: String = "",
    val activeParkings: List<ParkingSession> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)