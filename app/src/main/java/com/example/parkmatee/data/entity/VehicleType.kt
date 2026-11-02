package com.example.parkmatee.data.entity

/**
 * Tipo di veicolo. Il valore salvato nel DB e' [dbValue] (testo stabile): per aggiungere
 * un nuovo tipo basta una nuova voce qui, senza toccare schema o UI.
 */
enum class VehicleType(val dbValue: String, val label: String) {
    CAR("car", "Auto"),
    MOTORCYCLE("motorcycle", "Moto"),
    BICYCLE("bicycle", "Bicicletta"),
    OTHER("other", "Altro");

    companion object {
        fun fromDb(value: String): VehicleType =
            entries.firstOrNull { it.dbValue == value } ?: OTHER
    }
}
