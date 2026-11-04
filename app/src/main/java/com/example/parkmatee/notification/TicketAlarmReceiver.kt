package com.example.parkmatee.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.parkmatee.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TicketAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val alarmType = intent.getStringExtra(EXTRA_ALARM_TYPE) ?: return

        if (sessionId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Il contenuto e calcolato ora, leggendo la sosta dal DB. Non notificare se la
                // sessione non esiste piu o e gia terminata.
                val session = context.appContainer.parkingRepository.getParkingById(sessionId)
                if (session == null || session.endTime != null) return@launch

                when (alarmType) {
                    ALARM_TYPE_EXPIRING -> showTicketExpiringNotification(context, session)
                    ALARM_TYPE_EXPIRED -> showTicketExpiredNotification(context, session)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val ALARM_TYPE_EXPIRING = "alarm_expiring"
        const val ALARM_TYPE_EXPIRED = "alarm_expired"
    }
}
