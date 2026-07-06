package com.example.parkmatee.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "parking_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class ParkingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val vehicleId: Int,

    // "free", "hourly", "fixed"
    val type: String,

    val startTime: Long,
    val endTime: Long? = null,

    val latitude: Double,
    val longitude: Double,

    // Nome della saved location usata (null = GPS)
    val savedLocationName: String? = null,

    // Hourly park
    val hourlyRate: Double? = null,

    // Fixed-ticket park
    val fixedCost: Double? = null,
    val expiryTime: Long? = null,

    // Costo finale calcolato alla chiusura
    val finalCost: Double? = null,

    // Extra
    val photoPath: String? = null,
    val note: String? = null
)