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
import com.example.parkmatee.appContainer
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

    val container = remember(context) { context.appContainer }
    val vehicleRepository = container.vehicleRepository
    val parkingRepository = container.parkingRepository
    val savedLocationRepository = container.savedLocationRepository
    val alarmScheduler = container.alarmScheduler

    val vehicleViewModel: VehicleViewModel = viewModel(
        factory = VehicleViewModel.Factory(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository
        )
    )

    val parkingViewModel: ParkingViewModel = viewModel(
        factory = ParkingViewModel.Factory(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository,
            savedLocationRepository = savedLocationRepository,
            alarmScheduler = alarmScheduler,
            sessionService = container.sessionService
        )
    )

    val savedLocationViewModel: SavedLocationViewModel = viewModel(
        factory = SavedLocationViewModel.Factory(
            repository = savedLocationRepository,
            parkingRepository = parkingRepository
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
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository
        )
    )

    val mapViewModel: MapViewModel = viewModel(
        factory = MapViewModel.Factory(
            vehicleRepository = vehicleRepository,
            parkingRepository = parkingRepository,
            savedLocationRepository = savedLocationRepository,
            sessionService = container.sessionService
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

        // Rotta PARKING con luogo salvato opzionale
        composable(
            route = "${Routes.PARKING}?savedLocationId={savedLocationId}",
            arguments = listOf(
                navArgument("savedLocationId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val savedLocationId = backStackEntry.arguments?.getInt("savedLocationId") ?: -1

            ParkingScreen(
                viewModel = parkingViewModel,
                savedLocationId = savedLocationId
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
                viewModel = mapViewModel,
                onOpenParking = {
                    navController.navigate(Routes.PARKING) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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