package com.example.parkmatee

import android.app.Application
import android.content.Context
import com.example.parkmatee.data.db.DatabaseProvider
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.SavedLocationRepository
import com.example.parkmatee.data.repository.VehicleRepository
import com.example.parkmatee.notification.ParkingAlarmScheduler
import com.example.parkmatee.notification.ParkingWorkScheduler

/**
 * Contenitore di dipendenze manuale: unico punto in cui vengono costruiti database,
 * repository e scheduler. Usato da UI, Worker e BroadcastReceiver (che non passano
 * da una Activity) per condividere le stesse istanze.
 */
class AppContainer(application: Application) {
    private val database = DatabaseProvider.getDatabase(application)

    val vehicleRepository by lazy { VehicleRepository(database.vehicleDao()) }
    val parkingRepository by lazy { ParkingRepository(database.parkingDao()) }
    val savedLocationRepository by lazy { SavedLocationRepository(database.savedLocationDao()) }
    val alarmScheduler by lazy { ParkingAlarmScheduler(application) }
    val workScheduler by lazy { ParkingWorkScheduler(application) }
    val sessionService by lazy {
        ParkingSessionService(application, parkingRepository, vehicleRepository, alarmScheduler, workScheduler)
    }
}

class ParkMateApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ParkMateApp).container
