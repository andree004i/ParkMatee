package com.example.parkmatee.data.repository

import com.example.parkmatee.data.dao.VehicleDao
import com.example.parkmatee.data.entity.Vehicle
import kotlinx.coroutines.flow.Flow

class VehicleRepository(private val dao: VehicleDao) {

    fun getAllVehicles(): Flow<List<Vehicle>> = dao.getAllVehicles()

    suspend fun isNameTaken(name: String, excludeId: Int = 0): Boolean =
        dao.countByName(name, excludeId) > 0

    suspend fun insertVehicle(vehicle: Vehicle) = dao.insert(vehicle)

    suspend fun deleteVehicle(vehicle: Vehicle) = dao.delete(vehicle)

    suspend fun updateVehicle(vehicle: Vehicle) = dao.update(vehicle)
}