package com.example.parkmatee.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ParkingWorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodicReminder(sessionId: Int, startTimeMillis: Long) {
        val inputData = workDataOf(
            ParkingReminderWorker.KEY_SESSION_ID to sessionId,
            ParkingReminderWorker.KEY_START_TIME to startTimeMillis
        )

        val workRequest = PeriodicWorkRequestBuilder<ParkingReminderWorker>(
            15, TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "parking_reminder_$sessionId",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelPeriodicReminder(sessionId: Int) {
        workManager.cancelUniqueWork("parking_reminder_$sessionId")
    }
}