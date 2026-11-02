package com.example.parkmatee.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

private const val LOCATION_TIMEOUT_MILLIS = 15_000L

fun isDeviceLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(manager)
}

/**
 * Posizione fresca (non la "last location", che puo essere vecchia o nulla) con timeout.
 * Il permesso FINE deve essere gia stato concesso. Esattamente uno tra
 * [onLocationLoaded] e [onLocationError] viene chiamato.
 */
@SuppressLint("MissingPermission")
fun fetchCurrentLocation(
    context: Context,
    onLocationLoaded: (Double, Double) -> Unit,
    onLocationError: (String) -> Unit
) {
    if (!isDeviceLocationEnabled(context)) {
        onLocationError("Localizzazione disattivata. Attivala nelle impostazioni del telefono.")
        return
    }

    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()
    val handler = Handler(Looper.getMainLooper())
    var finished = false

    fun finish(action: () -> Unit) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        action()
    }

    handler.postDelayed({
        cancellationTokenSource.cancel()
        finish { onLocationError("Tempo scaduto: impossibile ottenere la posizione. Riprova all'aperto o scegli il punto sulla mappa.") }
    }, LOCATION_TIMEOUT_MILLIS)

    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
        .addOnSuccessListener { location ->
            finish {
                if (location != null) {
                    onLocationLoaded(location.latitude, location.longitude)
                } else {
                    onLocationError("Posizione non disponibile. Riprova o scegli il punto sulla mappa.")
                }
            }
        }
        .addOnFailureListener {
            finish { onLocationError("Errore durante il recupero della posizione.") }
        }
}
