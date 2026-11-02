package com.example.parkmatee.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.parkmatee.appContainer
import com.example.parkmatee.data.entity.ParkingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val container = context.appContainer
                    val repository = container.parkingRepository
                    val alarmScheduler = container.alarmScheduler
                    val workScheduler = container.workScheduler

                    val activeSessions: List<ParkingSession> = repository.getActiveParkings().first()
                    val now = System.currentTimeMillis()

                    activeSessions.forEach { session: ParkingSession ->
                        // Ripristina il WorkManager (idempotente)
                        workScheduler.schedulePeriodicReminder(session.id, session.startTime)

                        // Ripristina l'AlarmManager leggendo il warningMinutes dall'entità salvata
                        session.expiryTime?.let { expiry: Long ->
                            if (expiry > now) {
                                alarmScheduler.scheduleTicketAlarms(
                                    sessionId = session.id,
                                    expiryTimeMillis = expiry,
                                    warningMinutes = session.warningMinutes
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}