package com.example.parkmatee.data.repository

import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.dao.StartResult
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.validationError
import kotlinx.coroutines.flow.Flow

class ParkingRepository(
    private val parkingDao: ParkingDao
) {

    suspend fun getParkingById(id: Int): ParkingSession? =
        parkingDao.getParkingById(id)

    fun getActiveParkings(): Flow<List<ParkingSession>> =
        parkingDao.getActiveParkings()

    fun getParkingHistory(): Flow<List<ParkingSession>> =
        parkingDao.getParkingHistory()

    fun getParkingsByVehicle(vehicleId: Int): Flow<List<ParkingSession>> =
        parkingDao.getParkingsByVehicle(vehicleId)

    /** Storico filtrato in Room: veicolo, tipo e intervallo di inizio sono tutti opzionali. */
    fun getFilteredHistory(
        vehicleId: Int?,
        type: ParkingType?,
        fromTime: Long?,
        toTime: Long?
    ): Flow<List<ParkingSession>> =
        parkingDao.getFilteredHistory(vehicleId, type?.dbValue, fromTime, toTime)

    suspend fun getActiveSessionForVehicle(
        vehicleId: Int
    ): ParkingSession? =
        parkingDao.getActiveSessionForVehicle(vehicleId)

    suspend fun getActiveSessionsForSavedLocationId(savedLocationId: Int): List<ParkingSession> =
        parkingDao.getActiveSessionsForSavedLocationId(savedLocationId)

    /** Chiude la sosta (se ancora attiva) con orario e costo finali. */
    suspend fun endParking(session: ParkingSession) {
        parkingDao.endActive(session.id, System.currentTimeMillis())
    }

    suspend fun updateParking(
        session: ParkingSession
    ) {
        parkingDao.updateParking(session)
    }

    /** Avvio atomico con chiusura automatica delle soste attive dello stesso veicolo. */
    suspend fun startParkingReplacingActive(session: ParkingSession): StartResult {
        session.validationError()?.let { throw IllegalArgumentException(it) }
        return parkingDao.startReplacingActive(session, System.currentTimeMillis())
    }
}
