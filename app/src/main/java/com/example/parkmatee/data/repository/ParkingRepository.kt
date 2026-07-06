package com.example.parkmatee.data.repository

import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.entity.ParkingSession
import kotlinx.coroutines.flow.Flow

class ParkingRepository(
    private val dao: ParkingDao
) {

    fun getActiveParkings(): Flow<List<ParkingSession>> =
        dao.getActiveParkings()

    fun getParkingHistory(): Flow<List<ParkingSession>> =
        dao.getParkingHistory()

    fun getParkingsByVehicle(vehicleId: Int): Flow<List<ParkingSession>> =
        dao.getParkingsByVehicle(vehicleId)

    fun getParkingsByType(type: String): Flow<List<ParkingSession>> =
        dao.getParkingsByType(type)

    fun getParkingsByTimeRange(
        from: Long,
        to: Long
    ): Flow<List<ParkingSession>> =
        dao.getParkingsByTimeRange(from, to)

    suspend fun getActiveSessionForVehicle(
        vehicleId: Int
    ): ParkingSession? =
        dao.getActiveSessionForVehicle(vehicleId)

    suspend fun startParking(
        session: ParkingSession
    ) {
        dao.insertParking(session)
    }

    suspend fun endParking(
        session: ParkingSession
    ) {
        val endTime = System.currentTimeMillis()

        dao.updateParking(
            session.copy(
                endTime = endTime,
                finalCost = calculateFinalCost(
                    session = session,
                    endTime = endTime
                )
            )
        )
    }



    suspend fun updateParking(
        session: ParkingSession
    ) {
        dao.updateParking(session)
    }

    suspend fun deleteParking(
        session: ParkingSession
    ) {
        dao.deleteParking(session)
    }

    suspend fun closeActiveSessionIfExists(
        vehicleId: Int
    ) {
        val activeSession = dao.getActiveSessionForVehicle(vehicleId)

        if (activeSession != null) {
            endParking(activeSession)
        }
    }

    private fun calculateFinalCost(
        session: ParkingSession,
        endTime: Long
    ): Double? {
        return when (session.type) {
            "hourly" -> {
                val hourlyRate = session.hourlyRate ?: return null
                val elapsedMillis =
                    (endTime - session.startTime).coerceAtLeast(0L)

                val elapsedHours =
                    elapsedMillis / MILLIS_PER_HOUR.toDouble()

                elapsedHours * hourlyRate
            }

            "fixed" -> session.fixedCost

            else -> null
        }
    }

    companion object {
        private const val MILLIS_PER_HOUR = 3_600_000L
    }
}