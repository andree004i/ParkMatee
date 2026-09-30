package com.example.parkmatee.util

import com.example.parkmatee.data.entity.ParkingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ParkingFormatters {

    fun formatCurrency(value: Double): String {
        return String.format(Locale.getDefault(), "%.2f €", value)
    }

    fun formatParkingType(type: String): String {
        return when (type) {
            "free" -> "Gratuito"
            "hourly" -> "A ore"
            "fixed" -> "Ticket fisso"
            else -> type
        }
    }

    fun calculateLiveCost(session: ParkingSession, elapsedMillis: Long): Double {
        return when (session.type) {
            "hourly" -> {
                val hourlyRate = session.hourlyRate ?: 0.0
                hourlyRate * elapsedMillis.toDouble() / 3_600_000.0
            }
            "fixed" -> session.fixedCost ?: 0.0
            else -> 0.0
        }
    }

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