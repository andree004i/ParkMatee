package com.example.parkmatee.data.repository

import com.example.parkmatee.data.dao.SavedLocationDao
import com.example.parkmatee.data.entity.SavedLocation
import kotlinx.coroutines.flow.Flow

class SavedLocationRepository(private val dao: SavedLocationDao) {

    fun getAllLocations(): Flow<List<SavedLocation>> = dao.getAllLocations()

    suspend fun isNameTaken(name: String, excludeId: Int = 0): Boolean =
        dao.countByName(name, excludeId) > 0

    suspend fun getById(id: Int): SavedLocation? = dao.getById(id)

    suspend fun insert(location: SavedLocation) = dao.insert(location)

    suspend fun update(location: SavedLocation) = dao.update(location)

    suspend fun delete(location: SavedLocation) = dao.delete(location)
}