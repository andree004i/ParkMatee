package com.example.parkmatee.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.parkmatee.data.entity.SavedLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {

    @Query("SELECT * FROM saved_locations")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Insert
    suspend fun insert(location: SavedLocation)

    @Update
    suspend fun update(location: SavedLocation)

    @Delete
    suspend fun delete(location: SavedLocation)
}