package com.example.parkmatee.data.repository

import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.dao.StartResult
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.costAt
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

    suspend fun getActiveSessionForSavedLocationId(
        savedLocationId: Int
    ): ParkingSession? =
        parkingDao.getActiveSessionForSavedLocationId(savedLocationId)

    suspend fun getActiveSessionForSavedLocation(
        savedLocationName: String
    ): ParkingSession? =
        parkingDao.getActiveSessionForSavedLocation(savedLocationName)

    suspend fun startParking(
        session: ParkingSession
    ): Long {
        // Validazione di dominio: nessun punto d'ingresso puo salvare una sosta incoerente.
        session.validationError()?.let { throw IllegalArgumentException(it) }
        return parkingDao.insertParking(session)
    }

    private fun ParkingSession.closedAt(endTime: Long): ParkingSession =
        copy(endTime = endTime, finalCost = costAt(endTime))

    suspend fun endParking(
        session: ParkingSession
    ) {
        parkingDao.updateParking(session.closedAt(System.currentTimeMillis()))
    }

    suspend fun updateParking(
        session: ParkingSession
    ) {
        parkingDao.updateParking(session)
    }

    suspend fun deleteParking(
        session: ParkingSession
    ) {
        parkingDao.deleteParking(session)
    }

    /** Avvio atomico con chiusura automatica delle soste attive dello stesso veicolo. */
    suspend fun startParkingReplacingActive(session: ParkingSession): StartResult {
        session.validationError()?.let { throw IllegalArgumentException(it) }
        return parkingDao.startReplacingActive(session, System.currentTimeMillis())
    }
}
