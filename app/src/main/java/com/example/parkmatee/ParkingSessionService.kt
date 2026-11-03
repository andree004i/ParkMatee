package com.example.parkmatee

import android.app.Application
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.notification.ParkingAlarmScheduler
import com.example.parkmatee.notification.ParkingWorkScheduler
import com.example.parkmatee.notification.cancelSessionNotifications

/**
 * Unico punto in cui una sosta viene chiusa e i suoi lavori in background vengono annullati.
 * Usato dalla schermata Parcheggio e dalla Mappa, cosi la logica non e duplicata.
 */
class ParkingSessionService(
    private val application: Application,
    private val parkingRepository: ParkingRepository,
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
}
