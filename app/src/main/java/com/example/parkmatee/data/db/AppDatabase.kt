package com.example.parkmatee.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.parkmatee.data.dao.ParkingDao
import com.example.parkmatee.data.dao.SavedLocationDao
import com.example.parkmatee.data.dao.VehicleDao
import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.SavedLocation
import com.example.parkmatee.data.entity.Vehicle

// L'istanza viene creata esclusivamente da DatabaseProvider (unica fonte di verita).
// Versione 4: lo schema delle entity era cambiato senza aumentare la versione, quindi i
// database gia creati con la v3 non erano piu validi (Room: "cannot verify the data integrity").
// DatabaseProvider usa fallbackToDestructiveMigration, quindi le tabelle vengono ricreate.
@Database(
    entities = [Vehicle::class, ParkingSession::class, SavedLocation::class],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun parkingDao(): ParkingDao
    abstract fun savedLocationDao(): SavedLocationDao
}
