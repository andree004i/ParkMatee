package com.example.parkmatee.data.entity

/** Tipo di sosta. Nel DB e' salvato come testo ([dbValue]) tramite TypeConverter. */
enum class ParkingType(val dbValue: String, val label: String) {
    FREE("free", "Gratuito"),
    HOURLY("hourly", "A ore"),
    FIXED("fixed", "Ticket fisso");

    companion object {
        fun fromDb(value: String): ParkingType =
            entries.firstOrNull { it.dbValue == value } ?: FREE
    }
}
