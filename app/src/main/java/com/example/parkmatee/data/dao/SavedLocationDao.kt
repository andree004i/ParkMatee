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

    @Query("SELECT * FROM saved_locations ORDER BY name COLLATE NOCASE")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT COUNT(*) FROM saved_locations WHERE LOWER(name) = LOWER(:name) AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Int): Int

    @Insert
    suspend fun insert(location: SavedLocation)

    @Update
    suspend fun update(location: SavedLocation)

    @Delete
    suspend fun delete(location: SavedLocation)
}