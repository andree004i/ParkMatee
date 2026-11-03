package com.example.parkmatee.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.parkmatee.appContainer
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.costForElapsed
import com.example.parkmatee.util.ParkingFormatters
import kotlinx.coroutines.flow.first

class ParkingReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getInt(KEY_SESSION_ID, -1)
        if (sessionId == -1) return Result.failure()

        val container = applicationContext.appContainer

        // Se la sessione non esiste piu (es. veicolo eliminato) o e terminata,
        // il reminder si auto-cancella invece di notificare un parcheggio fantasma.
        val session = container.parkingRepository.getParkingById(sessionId)

        if (session == null || session.endTime != null) {
            WorkManager.getInstance(applicationContext)
                .cancelUniqueWork("parking_reminder_$sessionId")
            return Result.success()
        }

        // Tutto il contenuto e calcolato adesso, dai dati salvati nel DB.
        val elapsedMillis = (System.currentTimeMillis() - session.startTime).coerceAtLeast(0L)
        val hours = elapsedMillis / 3_600_000L
        val minutes = (elapsedMillis % 3_600_000L) / 60_000L
        val durationText = if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"

        val vehicleName = runCatching {
            container.vehicleRepository.getAllVehicles().first()
                .firstOrNull { it.id == session.vehicleId }?.name
        }.getOrNull()

        val costText = when (session.type) {
            ParkingType.FREE -> null
            ParkingType.HOURLY -> runCatching { session.costForElapsed(elapsedMillis) }.getOrNull()
                ?.let { "Costo stimato finora: ${ParkingFormatters.formatCurrency(it)}." }
            ParkingType.FIXED -> {
                val ticket = session.fixedCost?.let { "Ticket: ${ParkingFormatters.formatCurrency(it)}." }
                val expiry = session.expiryTime?.let { "Scade alle ${ParkingFormatters.formatTime(it, "HH:mm")}." }
                listOfNotNull(ticket, expiry).joinToString(" ").ifBlank { null }
            }
        }

        val text = buildString {
            append(if (vehicleName != null) "$vehicleName e parcheggiato da $durationText." else "Sosta attiva da $durationText.")
            costText?.let { append(' ').append(it) }
            append(" Ricordati di terminarla quando riparti.")
        }

        showParkingNotification(
            context = applicationContext,
            notificationId = NotificationConstants.ACTIVE_REMINDER_NOTIFICATION_BASE_ID + sessionId,
            title = "Parcheggio ancora attivo",
            text = text,
            sessionId = sessionId,
            withEndAction = true
        )

        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "key_session_id"
        const val KEY_START_TIME = "key_start_time"
    }
}
