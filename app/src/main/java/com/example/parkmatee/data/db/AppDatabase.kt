package com.example.parkmatee.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.dao.SavedLocationDao
import com.example.parkmatee.data.dao.VehicleDao
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.entity.Vehicle

// Database locale dell'app (Room). L'istanza e creata una sola volta da DatabaseProvider.
// Versione 8: indici su startTime e (vehicleId, endTime), con MIGRATION_7_8.
@Database(
    entities = [Vehicle::class, ParkingSession::class, SavedLocation::class],
    version = 8,
    exportSchema = true
)
@TypeConverters(VehicleTypeConverter::class, ParkingTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun parkingDao(): ParkingDao
    abstract fun savedLocationDao(): SavedLocationDao
}
