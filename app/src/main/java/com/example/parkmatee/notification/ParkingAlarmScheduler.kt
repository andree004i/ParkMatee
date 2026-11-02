package com.example.parkmatee.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class ParkingAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun scheduleTicketAlarms(sessionId: Int, expiryTimeMillis: Long, warningMinutes: Int) {
        val now = System.currentTimeMillis()
        val warningMillis = warningMinutes * 60 * 1_000L
        val warningTime = expiryTimeMillis - warningMillis

        if (warningTime > now) {
            val intent = Intent(context, TicketAlarmReceiver::class.java).apply {
                putExtra(TicketAlarmReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(TicketAlarmReceiver.EXTRA_ALARM_TYPE, TicketAlarmReceiver.ALARM_TYPE_EXPIRING)
                putExtra(TicketAlarmReceiver.EXTRA_WARNING_MINUTES, warningMinutes)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId * 10 + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSafeAlarm(warningTime, pendingIntent)
        }

        if (expiryTimeMillis > now) {
            val intent = Intent(context, TicketAlarmReceiver::class.java).apply {
                putExtra(TicketAlarmReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(TicketAlarmReceiver.EXTRA_ALARM_TYPE, TicketAlarmReceiver.ALARM_TYPE_EXPIRED)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId * 10 + 2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSafeAlarm(expiryTimeMillis, pendingIntent)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setSafeAlarm(timeMillis: Long, pendingIntent: PendingIntent) {
        if (canScheduleExact()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            }
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }

    fun cancelTicketAlarms(sessionId: Int) {
        val intent = Intent(context, TicketAlarmReceiver::class.java)

        val pendingWarning = PendingIntent.getBroadcast(
            context,
            sessionId * 10 + 1,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingWarning?.let { alarmManager.cancel(it) }

        val pendingExpired = PendingIntent.getBroadcast(
            context,
            sessionId * 10 + 2,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingExpired?.let { alarmManager.cancel(it) }
    }
}