package com.example.parkmatee.data.repository

import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.entity.ParkingSession
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

    fun getParkingsByType(type: String): Flow<List<ParkingSession>> =
        parkingDao.getParkingsByType(type)

    fun getParkingsByTimeRange(
        from: Long,
        to: Long
    ): Flow<List<ParkingSession>> =
        parkingDao.getParkingsByTimeRange(from, to)

    suspend fun getActiveSessionForVehicle(
        vehicleId: Int
    ): ParkingSession? =
        parkingDao.getActiveSessionForVehicle(vehicleId)

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

    suspend fun closeActiveSessionIfExists(vehicleId: Int): ParkingSession? {
        val activeSession = parkingDao.getActiveSessionForVehicle(vehicleId) ?: return null
        val updatedSession = activeSession.closedAt(System.currentTimeMillis())
        parkingDao.updateParking(updatedSession)
        return updatedSession
    }
}
