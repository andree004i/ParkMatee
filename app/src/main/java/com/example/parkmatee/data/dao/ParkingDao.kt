package com.example.parkmatee.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.costAt
import kotlinx.coroutines.flow.Flow

/** Esito dell'avvio di una sosta: id della nuova e soste chiuse automaticamente. */
data class StartResult(
    val newSessionId: Int,
    val closedSessions: List<ParkingSession>
)

@Dao
abstract class ParkingDao {

    @Insert
    abstract suspend fun insertParking(parking: ParkingSession): Long

    @Update
    abstract suspend fun updateParking(parking: ParkingSession)

    @Delete
    abstract suspend fun deleteParking(parking: ParkingSession)

    // --- SESSIONI ATTIVE (attiva = endTime IS NULL, unica definizione) ---
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NULL ORDER BY startTime DESC")
    abstract fun getActiveParkings(): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    abstract suspend fun getActiveSessionForVehicle(vehicleId: Int): ParkingSession?

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND endTime IS NULL")
    abstract suspend fun getActiveSessionsForVehicle(vehicleId: Int): List<ParkingSession>

    @Query("SELECT * FROM parking_sessions WHERE savedLocationName = :savedLocationName AND endTime IS NULL LIMIT 1")
    abstract suspend fun getActiveSessionForSavedLocation(savedLocationName: String): ParkingSession?

    @Query("SELECT * FROM parking_sessions WHERE savedLocationId = :savedLocationId AND endTime IS NULL ORDER BY startTime DESC")
    abstract suspend fun getActiveSessionsForSavedLocationId(savedLocationId: Int): List<ParkingSession>

    @Query("SELECT * FROM parking_sessions WHERE savedLocationId = :savedLocationId AND endTime IS NULL LIMIT 1")
    abstract suspend fun getActiveSessionForSavedLocationId(savedLocationId: Int): ParkingSession?

    /**
     * Avvio atomico: chiude TUTTE le soste attive del veicolo (endTime + costo finale) e inserisce
     * la nuova nella stessa transazione. Se qualcosa fallisce, non cambia nulla.
     * Room non supporta indici univoci parziali, quindi l'unicita della sosta attiva per veicolo
     * e garantita da questa transazione.
     */
    @Transaction
    open suspend fun startReplacingActive(newSession: ParkingSession, now: Long): StartResult {
        val closed = getActiveSessionsForVehicle(newSession.vehicleId).map { active ->
            active.copy(endTime = now, finalCost = active.costAt(now))
        }
        closed.forEach { updateParking(it) }
        val newId = insertParking(newSession).toInt()
        return StartResult(newSessionId = newId, closedSessions = closed)
    }

    // --- STORICO E STATISTICHE (HistoryScreen & StatsScreen) ---
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    abstract fun getParkingHistory(): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions ORDER BY startTime DESC")
    abstract fun getAllParkings(): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE id = :id LIMIT 1")
    abstract suspend fun getParkingById(id: Int): ParkingSession?

    /**
     * Storico filtrato direttamente in Room. Ogni filtro nullo e ignorato, quindi i criteri sono
     * combinabili. Solo soste terminate (le attive stanno nella schermata Park e nella Mappa).
     */
    @Query(
        """
        SELECT * FROM parking_sessions
        WHERE endTime IS NOT NULL
          AND (:vehicleId IS NULL OR vehicleId = :vehicleId)
          AND (:type IS NULL OR type = :type)
          AND (:fromTime IS NULL OR startTime >= :fromTime)
          AND (:toTime IS NULL OR startTime <= :toTime)
        ORDER BY startTime DESC
        """
    )
    abstract fun getFilteredHistory(
        vehicleId: Int?,
        type: String?,
        fromTime: Long?,
        toTime: Long?
    ): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId ORDER BY startTime DESC")
    abstract fun getParkingsByVehicle(vehicleId: Int): Flow<List<ParkingSession>>
}
