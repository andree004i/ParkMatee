package com.example.parkmatee

import android.app.Application
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.entity.Vehicle
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.VehicleRepository
import com.example.parkmatee.notification.NotificationConstants
import com.example.parkmatee.notification.ParkingAlarmScheduler
import com.example.parkmatee.notification.ParkingWorkScheduler
import com.example.parkmatee.notification.cancelSessionNotifications
import com.example.parkmatee.notification.showParkingNotification
import kotlinx.coroutines.flow.first

/**
 * Unico punto in cui una sosta viene avviata/chiusa e i suoi lavori in background vengono
 * gestiti. Usato dalla schermata Parcheggio, dalla Mappa e dalle azioni delle notifiche
 * (geofence, "Termina sosta"), cosi la logica non e duplicata.
 */
class ParkingSessionService(
    private val application: Application,
    private val parkingRepository: ParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val alarmScheduler: ParkingAlarmScheduler,
    private val workScheduler: ParkingWorkScheduler
) {
    /** Chiude la sosta (endTime + costo finale) e annulla Worker, allarmi e notifiche. */
    suspend fun endSession(session: ParkingSession) {
        parkingRepository.endParking(session)
        cancelJobs(session.id)
    }

    /** Worker periodico, allarmi del ticket e notifiche gia mostrate di una sosta. */
    fun cancelJobs(sessionId: Int) {
        workScheduler.cancelPeriodicReminder(sessionId)
        alarmScheduler.cancelTicketAlarms(sessionId)
        cancelSessionNotifications(application, sessionId)
    }

    /**
     * Veicolo da proporre quando si arriva in un luogo salvato: tra quelli NON parcheggiati,
     * il piu recentemente usato; se non ha mai parcheggiato nessuno, il primo. Null se tutti
     * i veicoli sono gia parcheggiati (o non ce ne sono).
     */
    suspend fun suggestVehicleToPark(): Vehicle? {
        val vehicles = vehicleRepository.getAllVehicles().first()
        val parkedIds = parkingRepository.getActiveParkings().first().map { it.vehicleId }.toSet()
        val available = vehicles.filter { it.id !in parkedIds }
        if (available.isEmpty()) return null

        val lastUsedId = parkingRepository.getParkingHistory().first()
            .firstOrNull { session -> available.any { it.id == session.vehicleId } }
            ?.vehicleId
        return available.firstOrNull { it.id == lastUsedId } ?: available.first()
    }

    /**
     * Avvia una sosta gratuita nella posizione del luogo salvato, per il veicolo indicato.
     * Chiude in modo atomico un'eventuale sosta attiva dello stesso veicolo.
     * Restituisce l'id della nuova sosta.
     */
    suspend fun startFreeSessionAtSavedLocation(location: SavedLocation, vehicle: Vehicle): Int {
        val now = System.currentTimeMillis()
        val session = ParkingSession(
            vehicleId = vehicle.id,
            type = ParkingType.FREE,
            startTime = now,
            latitude = location.latitude,
            longitude = location.longitude,
            savedLocationName = location.name,
            savedLocationId = location.id
        )
        val result = parkingRepository.startParkingReplacingActive(session)
        result.closedSessions.forEach { cancelJobs(it.id) }

        workScheduler.schedulePeriodicReminder(result.newSessionId)
        showParkingNotification(
            context = application,
            notificationId = NotificationConstants.START_NOTIFICATION_BASE_ID + result.newSessionId,
            title = "Parcheggio attivo",
            text = "${vehicle.name} parcheggiato in ${location.name}. Ricordati di terminare la sosta quando riparti.",
            sessionId = result.newSessionId,
            withEndAction = true
        )
        return result.newSessionId
    }
}
