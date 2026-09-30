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
    suspend fun insertParking(parking: ParkingSession): Long

    @Update
    suspend fun updateParking(parking: ParkingSession)

    @Delete
    suspend fun deleteParking(parking: ParkingSession)

    // --- SESSIONI ATTIVE ---
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NULL ORDER BY startTime DESC")
    fun getActiveParkings(): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND endTime IS NULL LIMIT 1")
    suspend fun getActiveSessionForVehicle(vehicleId: Int): ParkingSession?

    @Query("SELECT * FROM parking_sessions WHERE savedLocationName = :savedLocationName AND endTime IS NULL LIMIT 1")
    suspend fun getActiveSessionForSavedLocation(savedLocationName: String): ParkingSession?

    // --- STORICO E STATISTICHE (HistoryScreen & StatsScreen) ---
    // Storico completo dei parcheggi terminati
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun getParkingHistory(): Flow<List<ParkingSession>>

    // Tutti i parcheggi (attivi e storici) se necessari per le statistiche generali
    @Query("SELECT * FROM parking_sessions ORDER BY startTime DESC")
    fun getAllParkings(): Flow<List<ParkingSession>>

    // Ricerca per ID singolo
    @Query("SELECT * FROM parking_sessions WHERE id = :id LIMIT 1")
    suspend fun getParkingById(id: Int): ParkingSession?

    // --- FILTRI ---
    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId ORDER BY startTime DESC")
    fun getParkingsByVehicle(vehicleId: Int): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE type = :type ORDER BY startTime DESC")
    fun getParkingsByType(type: String): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE startTime >= :from AND startTime <= :to ORDER BY startTime DESC")
    fun getParkingsByTimeRange(from: Long, to: Long): Flow<List<ParkingSession>>
}