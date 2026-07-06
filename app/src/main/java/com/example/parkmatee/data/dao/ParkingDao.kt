package com.example.parkmatee.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.parkmatee.data.entity.ParkingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingDao {

    @Insert
    suspend fun insertParking(parking: ParkingSession)

    @Update
    suspend fun updateParking(parking: ParkingSession)

    @Delete
    suspend fun deleteParking(parking: ParkingSession)

    // Sessioni attive (endTime null) — Flow per aggiornamento automatico UI
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NULL")
    fun getActiveParkings(): Flow<List<ParkingSession>>

    // Sessione attiva per un veicolo specifico
    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND endTime IS NULL LIMIT 1")
    suspend fun getActiveSessionForVehicle(vehicleId: Int): ParkingSession?

    // Storico completo
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun getParkingHistory(): Flow<List<ParkingSession>>

    // Filtro per veicolo
    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId ORDER BY startTime DESC")
    fun getParkingsByVehicle(vehicleId: Int): Flow<List<ParkingSession>>

    // Filtro per tipo
    @Query("SELECT * FROM parking_sessions WHERE type = :type ORDER BY startTime DESC")
    fun getParkingsByType(type: String): Flow<List<ParkingSession>>

    // Filtro per intervallo di tempo
    @Query("SELECT * FROM parking_sessions WHERE startTime >= :from AND startTime <= :to ORDER BY startTime DESC")
    fun getParkingsByTimeRange(from: Long, to: Long): Flow<List<ParkingSession>>
}