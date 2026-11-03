package com.example.parkmatee.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.math.BigDecimal
import java.math.RoundingMode

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
    indices = [
        Index("vehicleId"),
        Index("startTime"),
        Index(value = ["vehicleId", "endTime"])
    ]
)
data class ParkingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val vehicleId: Int,

    val type: ParkingType,

    val startTime: Long,
    val endTime: Long? = null,

    val latitude: Double,
    val longitude: Double,

    // Nome della saved location usata (null = GPS)
    val savedLocationName: String? = null,

    // Id del luogo salvato usato (null = GPS/mappa/manuale). Niente FK: lo storico resta
    // valido anche se il luogo viene eliminato; le coordinate sono copiate nella sosta.
    val savedLocationId: Int? = null,

    // Hourly park
    val hourlyRate: Double? = null,

    // Fixed-ticket park
    val fixedCost: Double? = null,
    val expiryTime: Long? = null,

    val warningMinutes: Int = 5,

    // Costo finale calcolato alla chiusura
    val finalCost: Double? = null,

    // Extra
    val photoPath: String? = null,
    val note: String? = null
)

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Costo per un dato tempo trascorso. Unica formula dell'app: proporzionale al tempo
 * effettivo (nessuna "ora iniziata"), arrotondata al centesimo (HALF_UP).
 * Lancia IllegalStateException se i dati sono incoerenti con il tipo.
 */
fun ParkingSession.costForElapsed(elapsedMillis: Long): Double {
    val elapsed = elapsedMillis.coerceAtLeast(0L)
    val raw = when (type) {
        ParkingType.FREE -> 0.0
        ParkingType.HOURLY -> checkNotNull(hourlyRate) { "Tariffa oraria mancante" } * elapsed / MILLIS_PER_HOUR
        ParkingType.FIXED -> checkNotNull(fixedCost) { "Costo fisso mancante" }
    }
    return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP).toDouble()
}

/** Costo all'istante [nowMillis] (o alla chiusura, se la sosta e terminata). */
fun ParkingSession.costAt(nowMillis: Long): Double =
    costForElapsed((endTime ?: nowMillis) - startTime)

/** Controllo di coerenza di dominio: null se valida, altrimenti il motivo. */
fun ParkingSession.validationError(): String? = when (type) {
    ParkingType.FREE -> null
    ParkingType.HOURLY ->
        if ((hourlyRate ?: 0.0) <= 0.0) "Tariffa oraria mancante o non valida" else null
    ParkingType.FIXED -> when {
        fixedCost == null || fixedCost < 0.0 -> "Costo fisso mancante o non valido"
        expiryTime == null || expiryTime <= startTime -> "Scadenza del ticket mancante o non valida"
        else -> null
    }
}
