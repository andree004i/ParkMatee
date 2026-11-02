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
        val warningMinutes = intent.getIntExtra(EXTRA_WARNING_MINUTES, -1)

        if (sessionId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Non notificare se la sessione non esiste piu o e gia terminata
                val session = context.appContainer.parkingRepository
                    .getParkingById(sessionId)
                if (session == null || session.endTime != null) return@launch

                ensureParkingNotificationChannel(context)

                when (alarmType) {
                    ALARM_TYPE_EXPIRING -> {
                        val text = if (warningMinutes > 0) {
                            "Il ticket per il tuo parcheggio scade tra $warningMinutes minuti."
                        } else {
                            "Il ticket per il tuo parcheggio sta per scadere."
                        }
                        showParkingNotification(
                            context = context,
                            notificationId = NotificationConstants.EXPIRING_NOTIFICATION_BASE_ID + sessionId,
                            title = "Ticket in scadenza",
                            text = text
                        )
                    }
                    ALARM_TYPE_EXPIRED -> {
                        showParkingNotification(
                            context = context,
                            notificationId = NotificationConstants.EXPIRED_NOTIFICATION_BASE_ID + sessionId,
                            title = "Ticket scaduto",
                            text = "Il ticket del parcheggio è scaduto!"
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val EXTRA_WARNING_MINUTES = "extra_warning_minutes"
        const val ALARM_TYPE_EXPIRING = "alarm_expiring"
        const val ALARM_TYPE_EXPIRED = "alarm_expired"
    }
}
