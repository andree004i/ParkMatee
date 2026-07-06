package com.example.parkmatee.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.parkmatee.data.entity.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Insert
    suspend fun insert(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)

    @Update
    suspend fun update(vehicle: Vehicle)
}