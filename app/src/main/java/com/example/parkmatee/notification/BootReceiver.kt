package com.example.parkmatee.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.parkmatee.appContainer
import com.example.parkmatee.ui.savedlocations.registerEnabledSavedLocationGeofences
import com.google.android.gms.tasks.Tasks
import com.example.parkmatee.data.entity.ParkingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // ACTION_EXACT_PERMISSION: l'utente ha concesso "Allarmi e promemoria" dopo l'avvio di una
        // sosta; gli allarmi gia programmati (meno precisi) vanno riprogrammati come esatti.
        val isPermissionChange = action == ACTION_EXACT_PERMISSION
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            isPermissionChange
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val container = context.appContainer
                    val repository = container.parkingRepository
                    val alarmScheduler = container.alarmScheduler
                    val workScheduler = container.workScheduler

                    // Play Services cancella i geofence al riavvio e dopo un aggiornamento dell'app.
                    if (!isPermissionChange) try {
                        val locations = container.savedLocationRepository.getAllLocations().first()
                        registerEnabledSavedLocationGeofences(context, locations)?.let { Tasks.await(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val activeSessions: List<ParkingSession> = repository.getActiveParkings().first()
                    val now = System.currentTimeMillis()

                    activeSessions.forEach { session: ParkingSession ->
                        // Ripristina il WorkManager (idempotente)
                        workScheduler.schedulePeriodicReminder(session.id)

                        // Ripristina l'AlarmManager leggendo il warningMinutes dall'entità salvata
                        session.expiryTime?.let { expiry: Long ->
                            if (expiry > now) {
                                alarmScheduler.scheduleTicketAlarms(
                                    sessionId = session.id,
                                    expiryTimeMillis = expiry,
                                    warningMinutes = session.warningMinutes
                                )
                            } else if (!isPermissionChange) {
                                // Il ticket e scaduto mentre il dispositivo era spento: l'allarme
                                // e andato perso, quindi avvisiamo ora.
                                showTicketExpiredNotification(context, session)
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

    companion object {
        private const val ACTION_EXACT_PERMISSION =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
