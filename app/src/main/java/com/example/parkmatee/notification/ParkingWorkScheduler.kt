package com.example.parkmatee.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ParkingWorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodicReminder(sessionId: Int) {
        val inputData = workDataOf(ParkingReminderWorker.KEY_SESSION_ID to sessionId)

        val workRequest = PeriodicWorkRequestBuilder<ParkingReminderWorker>(
            15, TimeUnit.MINUTES
        )
            .setInputData(inputData)
            // Senza ritardo iniziale il primo promemoria partirebbe subito ("attivo da 0 min").
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "parking_reminder_$sessionId",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelPeriodicReminder(sessionId: Int) {
        workManager.cancelUniqueWork("parking_reminder_$sessionId")
    }
}