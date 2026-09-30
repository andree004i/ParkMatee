package com.example.parkmatee.ui.savedlocations

import com.example.parkmatee.data.entity.SavedLocation

object SavedLocationGeofenceRequestId {
    private const val PREFIX = "saved_location::"
    private const val DELIMITER = "::"

    fun encode(location: SavedLocation): String {
        return "$PREFIX${location.id}$DELIMITER${location.name}"
    }

    fun decodeId(requestId: String): Int? {
        if (!requestId.startsWith(PREFIX)) return null
        return requestId.substringAfter(PREFIX)
            .substringBefore(DELIMITER)
            .toIntOrNull()
    }

    fun decodeName(requestId: String): String {
        if (!requestId.startsWith(PREFIX)) return "luogo salvato"
        val name = requestId.substringAfter(PREFIX)
            .substringAfter(DELIMITER, "luogo salvato")
        return if (name.isBlank()) "luogo salvato" else name
    }
}