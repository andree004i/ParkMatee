package com.example.parkmatee.data.repository

import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.util.ParkingFormatters
import kotlinx.coroutines.flow.Flow

class ParkingRepository(
    private val parkingDao: ParkingDao
) {

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
        return parkingDao.insertParking(session)
    }

    suspend fun endParking(
        session: ParkingSession
    ) {
        val endTime = System.currentTimeMillis()
        val elapsedMillis = (endTime - session.startTime).coerceAtLeast(0L)
        val calculatedCost = ParkingFormatters.calculateLiveCost(session, elapsedMillis)

        parkingDao.updateParking(
            session.copy(
                endTime = endTime,
                finalCost = calculatedCost
            )
        )
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
        val activeSession = parkingDao.getActiveSessionForVehicle(vehicleId)
        if (activeSession != null) {
            val now = System.currentTimeMillis()
            val elapsed = (now - activeSession.startTime).coerceAtLeast(0L)
            val liveCost = ParkingFormatters.calculateLiveCost(activeSession, elapsed)

            val updatedSession = activeSession.copy(
                endTime = now,
                finalCost = liveCost
            )
            parkingDao.updateParking(updatedSession)
            return updatedSession
        }
        return null
    }
}