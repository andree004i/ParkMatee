package com.example.parkmatee.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    StatsContent(
        uiState = uiState
    )
}

@Composable
private fun StatsContent(
    uiState: StatsUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Statistiche")

        uiState.errorMessage?.let { message ->
            Text(text = message)
        }

        StatCard(
            title = "Parcheggi terminati",
            value = uiState.totalCompletedParkings.toString()
        )

        StatCard(
            title = "Costo totale",
            value = "${formatCost(uiState.totalCost)} euro"
        )

        StatCard(
            title = "Parcheggi gratuiti",
            value = uiState.freeCount.toString()
        )

        StatCard(
            title = "Parcheggi a ore",
            value = uiState.hourlyCount.toString()
        )

        StatCard(
            title = "Ticket fissi",
            value = uiState.fixedCount.toString()
        )

        StatCard(
            title = "Durata media",
            value = formatDuration(uiState.averageDurationMinutes)
        )

        StatCard(
            title = "Durata più lunga",
            value = formatDuration(uiState.longestDurationMinutes)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title)
            Text(text = value)
        }
    }
}

private fun formatCost(
    cost: Double
): String {
    return String.format(
        Locale.getDefault(),
        "%.2f",
        cost
    )
}

private fun formatDuration(
    minutes: Long
): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (hours > 0) {
        "${hours}h ${remainingMinutes}min"
    } else {
        "${remainingMinutes}min"
    }
}