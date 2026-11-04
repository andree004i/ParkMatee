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
sealed class GeofenceIntentAction {
    data class OpenLocation(val savedLocationId: Int) : GeofenceIntentAction()

    /** Tocco su una notifica di sosta (promemoria o scadenza): apre la schermata Park. */
    data class OpenSession(val sessionId: Int) : GeofenceIntentAction()
}

class MainActivity : ComponentActivity() {

    // Stato reattivo dell'Activity per catturare l'azione prima o durante il montaggio Compose
    private val pendingGeofenceAction = mutableStateOf<GeofenceIntentAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Gestione dell'Intent in caso di Cold Start (app chiusa)
        handleGeofenceIntent(intent)

        setContent {
            ParkMateeTheme {
                MainScreen(
                    pendingGeofenceAction = pendingGeofenceAction.value,
                    onActionConsumed = {
                        // Consumato: resetta lo stato per evitare ri-trigger
                        pendingGeofenceAction.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Gestione dell'Intent se l'Activity era gia attiva in background/foreground
        handleGeofenceIntent(intent)
    }

    private fun handleGeofenceIntent(intent: Intent?) {
        if (intent == null) return

        val openSessionId = intent.getIntExtra(NotificationConstants.EXTRA_OPEN_SESSION_ID, -1)
        if (openSessionId != -1) {
            pendingGeofenceAction.value = GeofenceIntentAction.OpenSession(openSessionId)
            intent.removeExtra(NotificationConstants.EXTRA_OPEN_SESSION_ID)
            return
        }

        val savedLocationId = intent.getIntExtra("EXTRA_SAVED_LOCATION_ID", -1)

        if (savedLocationId == -1) return

        pendingGeofenceAction.value = GeofenceIntentAction.OpenLocation(savedLocationId)

        // Rimozione dell'extra per evitare la ri-esecuzione alla rotazione dello schermo
        intent.removeExtra("EXTRA_SAVED_LOCATION_ID")
    }
}