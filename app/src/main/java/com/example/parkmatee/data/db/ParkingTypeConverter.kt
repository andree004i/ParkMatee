package com.example.parkmatee.data.db

import androidx.room.TypeConverter
import com.example.parkmatee.data.entity.ParkingType

class ParkingTypeConverter {
    @TypeConverter
    fun toDb(type: ParkingType): String = type.dbValue

    @TypeConverter
    fun fromDb(value: String): ParkingType = ParkingType.fromDb(value)
}
