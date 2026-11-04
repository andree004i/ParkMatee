package com.example.parkmatee.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.parkmatee.ui.history.HistoryFiltersSection
import com.example.parkmatee.util.ParkingFormatters

@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    StatsContent(
        uiState = uiState,
        viewModel = viewModel
    )
}

@Composable
private fun StatsContent(
    uiState: StatsUiState,
    viewModel: StatsViewModel
) {
    var metric by rememberSaveable { mutableStateOf(StatsMetric.COST) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Statistiche", style = MaterialTheme.typography.titleLarge)

        HistoryFiltersSection(
            vehicles = uiState.vehicles,
            filters = uiState.filters,
            onTypeFilterSelected = viewModel::onTypeFilterSelected,
            onVehicleFilterSelected = viewModel::onVehicleFilterSelected,
            onRangeSelected = viewModel::onRangeSelected,
            onClearFilters = viewModel::onClearFilters
        )

        uiState.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        when {
            uiState.isLoading -> Unit

            uiState.sessions.isEmpty() -> EmptyStats(
                filtersActive = uiState.filters.isActive,
                onClearFilters = viewModel::onClearFilters
            )

            else -> {
                MetricChips(metric = metric, onMetricSelected = { metric = it })

                ChartsTab(uiState = uiState, metric = metric, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyStats(filtersActive: Boolean, onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filtersActive) {
            Text("Nessuna sosta terminata corrisponde ai filtri scelti.")
            OutlinedButton(onClick = onClearFilters) { Text("Azzera filtri") }
        } else {
            Text("Grafici non ancora disponibili.")
            Text(
                text = "Avvia e termina almeno una sosta per vedere le statistiche.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MetricChips(metric: StatsMetric, onMetricSelected: (StatsMetric) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Mostra:", modifier = Modifier.padding(top = 8.dp))
        StatsMetric.entries.forEach {
            FilterChip(
                selected = metric == it,
                onClick = { onMetricSelected(it) },
                label = { Text(it.label) }
            )
        }
    }
}

@Composable
private fun ChartsTab(uiState: StatsUiState, metric: StatsMetric, modifier: Modifier = Modifier) {
    var granularity by rememberSaveable { mutableStateOf(StatsGranularity.DAY) }

    val buckets = remember(uiState.sessions, granularity) {
        aggregateBuckets(uiState.sessions, granularity, System.currentTimeMillis())
    }
    var selectedBucket by remember(buckets) { mutableIntStateOf((buckets.size - 1).coerceAtLeast(0)) }
    val byVehicle = remember(uiState.sessions, uiState.vehicles) {
        aggregateByVehicle(uiState.sessions, uiState.vehicles)
    }
    var selectedVehicle by remember(byVehicle) { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Soste terminate", uiState.sessions.size.toString(), Modifier.weight(1f))
            StatCard("Costo totale", ParkingFormatters.formatCurrency(uiState.totalCost), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Durata media", formatMinutes(uiState.averageDurationMinutes), Modifier.weight(1f))
            StatCard("Sosta piu lunga", formatMinutes(uiState.longestDurationMinutes), Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${metric.label} per periodo", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Ultimi ${granularity.bucketCount} ${granularity.unitPlural}. Ogni sosta e attribuita al periodo in cui inizia.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsGranularity.entries.forEach {
                        FilterChip(
                            selected = granularity == it,
                            onClick = { granularity = it },
                            label = { Text(it.label) }
                        )
                    }
                }

                buckets.getOrNull(selectedBucket)?.let { bucket ->
                    Text(
                        text = "${bucket.label}: ${ParkingFormatters.formatCurrency(bucket.cost)} - ${bucket.count} soste",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                BucketColumnChart(
                    buckets = buckets,
                    metric = metric,
                    selectedIndex = selectedBucket,
                    onSelect = { selectedBucket = it }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${metric.label} per veicolo", style = MaterialTheme.typography.titleMedium)

                byVehicle.getOrNull(selectedVehicle)?.let { item ->
                    Text(
                        text = "${item.label}: ${ParkingFormatters.formatCurrency(item.cost)} - ${item.count} soste",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                BucketColumnChart(
                    buckets = byVehicle,
                    metric = metric,
                    selectedIndex = selectedVehicle,
                    onSelect = { selectedVehicle = it }
                )
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}
