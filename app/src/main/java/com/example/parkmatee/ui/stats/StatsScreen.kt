package com.example.parkmatee.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    StatsContent(
        uiState = uiState,
        onPeriodSelected = viewModel::onPeriodSelected
    )
}

@Composable
private fun StatsContent(
    uiState: StatsUiState,
    onPeriodSelected: (StatsPeriodFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Statistiche")

        PeriodFilterSection(
            selectedPeriod = uiState.selectedPeriod,
            onPeriodSelected = onPeriodSelected
        )

        uiState.errorMessage?.let { message ->
            Text(text = message)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Parcheggi terminati",
                value = uiState.totalCompletedParkings.toString(),
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Costo totale",
                value = "${formatCost(uiState.totalCost)} euro",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Durata media",
                value = formatDuration(uiState.averageDurationMinutes),
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Durata più lunga",
                value = formatDuration(uiState.longestDurationMinutes),
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.totalCompletedParkings == 0) {
            EmptyStatsCard()
        } else {
            InteractiveChartCard(
                title = "Distribuzione per tipo",
                subtitle = "Tocca una barra per vedere il dettaglio",
                entries = uiState.typeCountEntries
            )

            InteractiveChartCard(
                title = "Costo per tipo",
                subtitle = "Somma dei costi dei parcheggi terminati",
                entries = uiState.costByTypeEntries
            )

            InteractiveChartCard(
                title = "Durata media per tipo",
                subtitle = "Media calcolata sui parcheggi chiusi",
                entries = uiState.durationByTypeEntries
            )

            InteractiveChartCard(
                title = "Parcheggi negli ultimi giorni",
                subtitle = "Numero di parcheggi chiusi per giorno",
                entries = uiState.dailyParkingEntries
            )
        }
    }
}

@Composable
private fun PeriodFilterSection(
    selectedPeriod: StatsPeriodFilter,
    onPeriodSelected: (StatsPeriodFilter) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Periodo")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsPeriodFilter.values().forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = {
                        onPeriodSelected(period)
                    },
                    label = {
                        Text(text = period.label)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
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

@Composable
private fun EmptyStatsCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Grafici non ancora disponibili")
            Text(text = "Avvia e termina almeno un parcheggio per vedere statistiche e visualizzazioni.")
        }
    }
}

@Composable
private fun InteractiveChartCard(
    title: String,
    subtitle: String,
    entries: List<StatsChartEntry>
) {
    var selectedIndex by remember(entries) {
        mutableIntStateOf(0)
    }

    val safeEntries = entries.filter { entry ->
        entry.value >= 0.0
    }

    val selectedEntry = safeEntries.getOrNull(selectedIndex)
        ?: safeEntries.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title)
            Text(text = subtitle)

            selectedEntry?.let { entry ->
                Text(text = "Selezionato: ${entry.label} = ${entry.displayValue}")
            }

            HorizontalBarChart(
                entries = safeEntries,
                selectedIndex = selectedIndex,
                onEntrySelected = { index ->
                    selectedIndex = index
                }
            )
        }
    }
}

@Composable
private fun HorizontalBarChart(
    entries: List<StatsChartEntry>,
    selectedIndex: Int,
    onEntrySelected: (Int) -> Unit
) {
    val maxValue = entries.maxOfOrNull { entry ->
        entry.value
    } ?: 0.0

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            val progress = if (maxValue <= 0.0) {
                0f
            } else {
                (entry.value / maxValue).toFloat().coerceIn(0f, 1f)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEntrySelected(index)
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selectedIndex == index) {
                            "• ${entry.label}"
                        } else {
                            entry.label
                        }
                    )
                    Text(text = entry.displayValue)
                }

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
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
