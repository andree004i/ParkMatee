package com.example.parkmatee.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.parkmatee.MainActivity
import com.example.parkmatee.R
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.util.ParkingFormatters

object NotificationConstants {
    /** Canale dei promemoria di sosta (importanza normale). Mantiene l'ID storico. */
    const val PARKING_NOTIFICATION_CHANNEL_ID = "parking_reminders"

    /** Canale delle scadenze del ticket (importanza alta: sono le notifiche urgenti). */
    const val TICKET_EXPIRY_CHANNEL_ID = "ticket_expiry"

    const val START_NOTIFICATION_BASE_ID = 10_000
    const val ACTIVE_REMINDER_NOTIFICATION_BASE_ID = 20_000
    const val EXPIRING_NOTIFICATION_BASE_ID = 30_000
    const val EXPIRED_NOTIFICATION_BASE_ID = 40_000
    const val SESSION_ENDED_NOTIFICATION_BASE_ID = 60_000

    /** Extra dell'intent di apertura: id della sosta a cui si riferisce la notifica. */
    const val EXTRA_OPEN_SESSION_ID = "extra_open_session_id"
}

fun ensureParkingNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    manager.createNotificationChannel(
        NotificationChannel(
            NotificationConstants.PARKING_NOTIFICATION_CHANNEL_ID,
            "Promemoria di sosta",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avvio sosta e promemoria periodici con tempo trascorso e costo stimato"
        }
    )

    manager.createNotificationChannel(
        NotificationChannel(
            NotificationConstants.TICKET_EXPIRY_CHANNEL_ID,
            "Scadenza ticket",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avviso prima della scadenza e avviso alla scadenza del ticket"
        }
    )
}

fun canShowParkingNotifications(context: Context): Boolean {
    val runtimePermissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    return runtimePermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Mostra una notifica di sosta.
 * - [channelId]: canale (promemoria oppure scadenza ticket).
 * - [sessionId]: se presente, il tocco apre l'app sulla schermata Park e con [withEndAction]
 *   compare l'azione "Termina sosta".
 * Il codice richiesta dei PendingIntent e l'id della notifica, unico per tipo e sosta: notifiche
 * diverse non si sovrascrivono tra loro.
 */
@SuppressLint("MissingPermission")
fun showParkingNotification(
    context: Context,
    notificationId: Int,
    title: String,
    text: String,
    channelId: String = NotificationConstants.PARKING_NOTIFICATION_CHANNEL_ID,
    sessionId: Int? = null,
    withEndAction: Boolean = false
): Boolean {
    if (!canShowParkingNotifications(context)) return false

    ensureParkingNotificationChannel(context)

    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        sessionId?.let { putExtra(NotificationConstants.EXTRA_OPEN_SESSION_ID, it) }
    }
    val openPending = PendingIntent.getActivity(
        context,
        notificationId,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val isUrgent = channelId == NotificationConstants.TICKET_EXPIRY_CHANNEL_ID

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openPending)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

    if (withEndAction && sessionId != null) {
        val endIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_END_PARKING
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, sessionId)
        }
        val endPending = PendingIntent.getBroadcast(
            context,
            notificationId,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Termina sosta", endPending)
    }

    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    return true
}

/** Avviso prima della scadenza: orario e minuti rimasti sono letti dalla sosta nel DB. */
fun showTicketExpiringNotification(context: Context, session: ParkingSession): Boolean {
    val expiry = session.expiryTime
    val text = if (expiry != null) {
        val minutesLeft = ((expiry - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        "Il ticket scade alle ${ParkingFormatters.formatTime(expiry, "HH:mm")} (tra circa $minutesLeft min)."
    } else {
        "Il ticket per il tuo parcheggio sta per scadere."
    }
    return showParkingNotification(
        context = context,
        notificationId = NotificationConstants.EXPIRING_NOTIFICATION_BASE_ID + session.id,
        title = "Ticket in scadenza",
        text = text,
        channelId = NotificationConstants.TICKET_EXPIRY_CHANNEL_ID,
        sessionId = session.id,
        withEndAction = true
    )
}

/** Avviso all'istante di scadenza (obbligatorio). */
fun showTicketExpiredNotification(context: Context, session: ParkingSession): Boolean {
    val expiry = session.expiryTime
    val text = if (expiry != null) {
        "Il ticket e scaduto alle ${ParkingFormatters.formatTime(expiry, "HH:mm")}. Rinnovalo o termina la sosta."
    } else {
        "Il ticket del parcheggio e scaduto."
    }
    return showParkingNotification(
        context = context,
        notificationId = NotificationConstants.EXPIRED_NOTIFICATION_BASE_ID + session.id,
        title = "Ticket scaduto",
        text = text,
        channelId = NotificationConstants.TICKET_EXPIRY_CHANNEL_ID,
        sessionId = session.id,
        withEndAction = true
    )
}

/** Rimuove dalla barra le notifiche gia mostrate per una sosta (chiusa o sostituita). */
fun cancelSessionNotifications(context: Context, sessionId: Int) {
    val manager = NotificationManagerCompat.from(context)
    listOf(
        NotificationConstants.START_NOTIFICATION_BASE_ID,
        NotificationConstants.ACTIVE_REMINDER_NOTIFICATION_BASE_ID,
        NotificationConstants.EXPIRING_NOTIFICATION_BASE_ID,
        NotificationConstants.EXPIRED_NOTIFICATION_BASE_ID
    ).forEach { base -> manager.cancel(base + sessionId) }
}
