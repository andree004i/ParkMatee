package com.example.parkmatee.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TicketAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val alarmType = intent.getStringExtra(EXTRA_ALARM_TYPE) ?: return

        ensureParkingNotificationChannel(context)

        when (alarmType) {
            ALARM_TYPE_EXPIRING -> {
                showParkingNotification(
                    context = context,
                    notificationId = NotificationConstants.EXPIRING_NOTIFICATION_BASE_ID + sessionId,
                    title = "Ticket in scadenza",
                    text = "Il ticket per il tuo parcheggio sta per scadere."
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
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val ALARM_TYPE_EXPIRING = "alarm_expiring"
        const val ALARM_TYPE_EXPIRED = "alarm_expired"
    }
}