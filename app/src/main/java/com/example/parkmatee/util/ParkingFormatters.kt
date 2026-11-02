package com.example.parkmatee.util

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.costForElapsed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ParkingFormatters {

    fun formatCurrency(value: Double): String {
        return String.format(Locale.getDefault(), "%.2f €", value)
    }

    fun formatParkingType(type: ParkingType): String = type.label

    fun calculateLiveCost(session: ParkingSession, elapsedMillis: Long): Double =
        session.costForElapsed(elapsedMillis)

    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatTime(timestamp: Long, pattern: String = "HH:mm"): String {
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}