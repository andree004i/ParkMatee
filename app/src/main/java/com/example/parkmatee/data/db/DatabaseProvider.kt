package com.example.parkmatee.data.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE?.let { return@synchronized it }
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "parkmate_db"
            )
                .addMigrations(MIGRATION_7_8)
                // Ripiego solo per versioni senza migrazione (sviluppo); vedi report.
                .fallbackToDestructiveMigration(true)
                .build()

            INSTANCE = instance
            instance
        }
    }
}