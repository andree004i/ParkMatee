package com.example.parkmatee.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.costAtOrZero
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

    // --- SESSIONI ATTIVE (attiva = endTime IS NULL, unica definizione) ---
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NULL ORDER BY startTime DESC")
    abstract fun getActiveParkings(): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    abstract suspend fun getActiveSessionForVehicle(vehicleId: Int): ParkingSession?

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND endTime IS NULL")
    abstract suspend fun getActiveSessionsForVehicle(vehicleId: Int): List<ParkingSession>

    @Query("SELECT * FROM parking_sessions WHERE savedLocationId = :savedLocationId AND endTime IS NULL ORDER BY startTime DESC")
    abstract suspend fun getActiveSessionsForSavedLocationId(savedLocationId: Int): List<ParkingSession>

    /**
     * Avvio atomico: chiude TUTTE le soste attive del veicolo (endTime + costo finale) e inserisce
     * la nuova nella stessa transazione. Se qualcosa fallisce, non cambia nulla.
     * Room non supporta indici univoci parziali, quindi l'unicita della sosta attiva per veicolo
     * e garantita da questa transazione.
     */
    @Transaction
    open suspend fun startReplacingActive(newSession: ParkingSession, now: Long): StartResult {
        val closed = getActiveSessionsForVehicle(newSession.vehicleId).map { active ->
            active.copy(endTime = now, finalCost = active.costAtOrZero(now))
        }
        closed.forEach { updateParking(it) }
        val newId = insertParking(newSession).toInt()
        return StartResult(newSessionId = newId, closedSessions = closed)
    }

    /**
     * Chiusura sicura: rilegge la riga dentro la transazione e chiude solo se e ancora attiva.
     * Cosi un secondo "Termina" (o una copia vecchia della sosta in memoria) non sposta
     * l'orario di fine e non sovrascrive nota o foto modificate nel frattempo.
     * Restituisce la sosta chiusa, o null se non esiste o era gia terminata.
     */
    @Transaction
    open suspend fun endActive(sessionId: Int, now: Long): ParkingSession? {
        val current = getParkingById(sessionId) ?: return null
        if (current.endTime != null) return null
        val closed = current.copy(endTime = now, finalCost = current.costAtOrZero(now))
        updateParking(closed)
        return closed
    }

    // --- STORICO E STATISTICHE (HistoryScreen & StatsScreen) ---
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    abstract fun getParkingHistory(): Flow<List<ParkingSession>>

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
