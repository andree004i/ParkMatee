package com.example.parkmatee.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.parkmatee.GeofenceIntentAction

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(
    pendingGeofenceAction: GeofenceIntentAction? = null,
    onActionConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    // Intercetta l'azione pendente ed esegue la navigazione ONE-SHOT senza restoreState
    LaunchedEffect(pendingGeofenceAction) {
        if (pendingGeofenceAction != null) {
            val targetRoute = when (pendingGeofenceAction) {
                is GeofenceIntentAction.OpenSession -> Routes.PARKING
                is GeofenceIntentAction.OpenLocation -> {
                    "${Routes.SAVED_LOCATIONS}?savedLocationId=${pendingGeofenceAction.savedLocationId}"
                }
            }

            // Navigazione one-shot: NIENTE saveState / restoreState per garantire la ri-creazione
            // con i nuovi argomenti passati nella query string.
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }

            // Segnala a MainActivity che l'azione è stata consumata
            onActionConsumed()
        }
    }

    Scaffold(
        bottomBar = { BottomNavBar(navController = navController) }
    ) { padding ->
        ParkMateNavGraph(
            navController = navController,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem(Routes.VEHICLES, "Veicoli", Icons.Default.DirectionsCar),
        BottomNavItem(Routes.PARKING, "Park", Icons.Default.LocalParking),
        BottomNavItem(Routes.SAVED_LOCATIONS, "Luoghi", Icons.Default.Place),
        BottomNavItem(Routes.MAP, "Mappa", Icons.Default.Map),
        BottomNavItem(Routes.HISTORY, "Storico", Icons.Default.History),
        BottomNavItem(Routes.STATS, "Stats", Icons.Default.BarChart)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // Estraiamo la rotta base ignorando gli eventuali parametri di query (?savedLocationId=...)
    val baseRoute = navBackStackEntry?.destination?.route?.substringBefore("?")

    NavigationBar {
        items.forEach { item ->
            val isSelected = baseRoute == item.route

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        // Conservazione dello stato corretta per il cambio tab standard
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}