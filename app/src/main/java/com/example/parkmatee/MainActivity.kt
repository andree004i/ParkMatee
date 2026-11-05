package com.example.parkmatee

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.parkmatee.notification.NotificationConstants
import com.example.parkmatee.ui.MainScreen
import com.example.parkmatee.ui.theme.ParkMateeTheme

// Cosa aprire quando l'app viene lanciata toccando una notifica
sealed class NotificationTarget {
    /** Tocco su una notifica del geofence: apre il luogo salvato. */
    data class OpenLocation(val savedLocationId: Int) : NotificationTarget()

    /** Tocco su una notifica di sosta (promemoria o scadenza): apre la schermata Park. */
    data class OpenSession(val sessionId: Int) : NotificationTarget()
}

class MainActivity : ComponentActivity() {

    // Stato reattivo dell'Activity per catturare l'azione prima o durante il montaggio Compose
    private val pendingNotificationTarget = mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solo al primo avvio: dopo una rotazione o un ripristino l'intent e gia stato gestito.
        if (savedInstanceState == null) handleNotificationIntent(intent)

        setContent {
            ParkMateeTheme {
                MainScreen(
                    pendingNotificationTarget = pendingNotificationTarget.value,
                    onActionConsumed = {
                        // Consumato: resetta lo stato per evitare ri-trigger
                        pendingNotificationTarget.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Gestione dell'Intent se l'Activity era gia attiva in background/foreground
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val openSessionId = intent.getIntExtra(NotificationConstants.EXTRA_OPEN_SESSION_ID, -1)
        if (openSessionId != -1) {
            pendingNotificationTarget.value = NotificationTarget.OpenSession(openSessionId)
            intent.removeExtra(NotificationConstants.EXTRA_OPEN_SESSION_ID)
            return
        }

        val savedLocationId = intent.getIntExtra("EXTRA_SAVED_LOCATION_ID", -1)

        if (savedLocationId == -1) return

        pendingNotificationTarget.value = NotificationTarget.OpenLocation(savedLocationId)

        // Rimozione dell'extra per evitare la ri-esecuzione alla rotazione dello schermo
        intent.removeExtra("EXTRA_SAVED_LOCATION_ID")
    }
}