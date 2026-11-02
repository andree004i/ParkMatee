package com.example.parkmatee.data.db

import androidx.room.TypeConverter
import com.example.parkmatee.data.entity.VehicleType

class VehicleTypeConverter {
    @TypeConverter
    fun toDb(type: VehicleType): String = type.dbValue

    @TypeConverter
    fun fromDb(value: String): VehicleType = VehicleType.fromDb(value)
}
