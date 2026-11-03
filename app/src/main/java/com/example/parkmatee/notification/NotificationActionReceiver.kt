package com.example.parkmatee.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.parkmatee.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Gestisce l'azione "Termina sosta" delle notifiche, senza aprire l'app. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_END_PARKING) return
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        if (sessionId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                val session = container.parkingRepository.getParkingById(sessionId)
                if (session != null && session.endTime == null) {
                    // Stessa logica della schermata Park e della Mappa: chiude e annulla i job.
                    container.sessionService.endSession(session)
                }
                cancelSessionNotifications(context, sessionId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_END_PARKING = "com.example.parkmatee.action.END_PARKING"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
