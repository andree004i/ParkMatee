package com.example.parkmatee.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 7 -> 8: indici su startTime e (vehicleId, endTime). Nessun dato viene perso. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parking_sessions_startTime` ON `parking_sessions` (`startTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parking_sessions_vehicleId_endTime` ON `parking_sessions` (`vehicleId`, `endTime`)")
    }
}
