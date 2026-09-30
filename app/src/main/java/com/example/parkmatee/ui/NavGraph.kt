package com.example.parkmatee.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.parkmatee.data.db.DatabaseProvider
import com.example.parkmatee.data.repository.ParkingRepository
import com.example.parkmatee.data.repository.SavedLocationRepository
import com.example.parkmatee.data.repository.VehicleRepository
import com.example.parkmatee.ui.history.HistoryScreen
import com.example.parkmatee.ui.history.HistoryViewModel
import com.example.parkmatee.ui.map.MapScreen
import com.example.parkmatee.ui.map.MapViewModel
import com.example.parkmatee.ui.parking.ParkingScreen
import com.example.parkmatee.ui.parking.ParkingViewModel
import com.example.parkmatee.ui.savedlocations.SavedLocationScreen
import com.example.parkmatee.ui.savedlocations.SavedLocationViewModel
import com.example.parkmatee.ui.stats.StatsScreen
import com.example.parkmatee.ui.stats.StatsViewModel
import com.example.parkmatee.ui.vehicles.VehicleScreen
import com.example.parkmatee.ui.vehicles.VehicleViewModel

object Routes {
    const val VEHICLES = "vehicles"
    const val MAP = "map"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val PARKING = "parking"
    const val SAVED_LOCATIONS = "saved_locations"
}

@Composable
fun ParkMateNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    val database = remember(context) {
        DatabaseProvider.getDatabase(context)
    }

    val vehicleRepository = remember(database) {
        VehicleRepository(
            database.vehicleDao()
        )
    }

    val parkingRepository = remember(database) {
        ParkingRepository(
            database.parkingDao()
        )
    }

    val savedLocationRepository = remember(database) {
        SavedLocationRepository(
            database.savedLocationDao()
        )
    }

    val vehicleViewModel: VehicleViewModel = viewModel(
        factory = VehicleViewModel.Factory(
            vehicleRepository
        )
    )

    val parkingViewModel: ParkingViewModel = viewModel(
        factory = ParkingViewModel.Factory(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository,
            savedLocationRepository = savedLocationRepository
        )
    )

    val savedLocationViewModel: SavedLocationViewModel = viewModel(
        factory = SavedLocationViewModel.Factory(
            savedLocationRepository
        )
    )

    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository
        )
    )

    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(
            parkingRepository = parkingRepository
        )
    )

    val mapViewModel: MapViewModel = viewModel(
        factory = MapViewModel.Factory(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository,
            savedLocationRepository = savedLocationRepository
        )
    )

    NavHost(
        navController = navController,
        startDestination = Routes.VEHICLES,
        modifier = modifier
    ) {
        composable(Routes.VEHICLES) {
            VehicleScreen(
                viewModel = vehicleViewModel
            )
        }

        // Configurazione rotta PARKING con parametri opzionali per la geofence
        composable(
            route = "${Routes.PARKING}?savedLocationId={savedLocationId}&autoStart={autoStart}&autoEnd={autoEnd}",
            arguments = listOf(
                navArgument("savedLocationId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("autoStart") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("autoEnd") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val savedLocationId = backStackEntry.arguments?.getInt("savedLocationId") ?: -1
            val autoStart = backStackEntry.arguments?.getBoolean("autoStart") ?: false
            val autoEnd = backStackEntry.arguments?.getBoolean("autoEnd") ?: false

            ParkingScreen(
                viewModel = parkingViewModel,
                savedLocationId = savedLocationId,
                autoStart = autoStart,
                autoEnd = autoEnd
            )
        }

        // Configurazione rotta SAVED_LOCATIONS con parametro opzionale
        composable(
            route = "${Routes.SAVED_LOCATIONS}?savedLocationId={savedLocationId}",
            arguments = listOf(
                navArgument("savedLocationId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val savedLocationId = backStackEntry.arguments?.getInt("savedLocationId") ?: -1

            SavedLocationScreen(
                viewModel = savedLocationViewModel,
                savedLocationId = savedLocationId
            )
        }

        composable(Routes.MAP) {
            MapScreen(
                viewModel = mapViewModel
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = historyViewModel
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                viewModel = statsViewModel
            )
        }
    }
}