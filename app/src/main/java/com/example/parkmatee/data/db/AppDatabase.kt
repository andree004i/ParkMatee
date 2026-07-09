package com.example.parkmatee.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.dao.SavedLocationDao
import com.example.parkmatee.data.dao.VehicleDao
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.entity.Vehicle

@Database(
    entities = [Vehicle::class, ParkingSession::class, SavedLocation::class],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun parkingDao(): ParkingDao
    abstract fun savedLocationDao(): SavedLocationDao
}
