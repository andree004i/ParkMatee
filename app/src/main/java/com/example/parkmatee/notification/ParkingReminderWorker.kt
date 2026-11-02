package com.example.parkmatee.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.parkmatee.data.db.DatabaseProvider

class ParkingReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getInt(KEY_SESSION_ID, -1)
        val startTimeMillis = inputData.getLong(KEY_START_TIME, System.currentTimeMillis())

        if (sessionId == -1) return Result.failure()

        // Se la sessione non esiste piu (es. veicolo eliminato) o e terminata,
        // il reminder si auto-cancella invece di notificare un parcheggio fantasma.
        val session = DatabaseProvider.getDatabase(applicationContext)
            .parkingDao()
            .getParkingById(sessionId)

        if (session == null || session.endTime != null) {
            WorkManager.getInstance(applicationContext)
                .cancelUniqueWork("parking_reminder_$sessionId")
            return Result.success()
        }

        val elapsedMillis = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0L)
        val hours = elapsedMillis / 3_600_000L
        val minutes = (elapsedMillis % 3_600_000L) / 60_000L

        val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        showParkingNotification(
            context = applicationContext,
            notificationId = NotificationConstants.ACTIVE_REMINDER_NOTIFICATION_BASE_ID + sessionId,
            title = "Parcheggio ancora attivo",
            text = "Il tuo parcheggio è attivo da $durationText. Ricordati di terminarlo se sei ripartito!"
        )

        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "key_session_id"
        const val KEY_START_TIME = "key_start_time"
    }
}
