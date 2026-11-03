package com.example.parkmatee.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.ui.history.HistoryFiltersSection
import com.example.parkmatee.util.ParkingFormatters
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState

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
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Andamento") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mappa di calore") })
                }

                MetricChips(metric = metric, onMetricSelected = { metric = it })

                if (tab == 0) {
                    ChartsTab(uiState = uiState, metric = metric, modifier = Modifier.weight(1f))
                } else {
                    HeatmapTab(uiState = uiState, metric = metric, modifier = Modifier.weight(1f))
                }
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
                text = "Avvia e termina almeno una sosta per vedere statistiche e mappa di calore.",
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
    var selectedSlice by remember(uiState.sessions, metric) { mutableIntStateOf(0) }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val slices = remember(uiState.costByType, uiState.countByType, metric, primary, secondary, tertiary) {
        ParkingType.entries.mapNotNull { type ->
            val color = when (type) {
                ParkingType.FREE -> secondary
                ParkingType.HOURLY -> primary
                ParkingType.FIXED -> tertiary
            }
            val value = when (metric) {
                StatsMetric.COST -> uiState.costByType[type] ?: 0.0
                StatsMetric.COUNT -> (uiState.countByType[type] ?: 0).toDouble()
            }
            // Le soste libere costano 0: nel grafico dei costi non hanno fetta
            if (value > 0.0) DonutSlice(type.label, value, color) else null
        }
    }

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
                Text("Ripartizione per tipo", style = MaterialTheme.typography.titleMedium)
                if (slices.isEmpty()) {
                    Text("Nessun costo nel periodo scelto: tutte le soste sono gratuite.")
                } else {
                    DonutChart(
                        slices = slices,
                        metric = metric,
                        selectedIndex = selectedSlice.coerceIn(0, slices.lastIndex),
                        onSelect = { selectedSlice = it }
                    )
                }
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

/**
 * Heat map geografica: dove sono state fatte le soste filtrate. Mappa interattiva (zoom,
 * spostamento). Dati solo locali; le tile della mappa di base sono l'unico traffico di rete.
 */
@Composable
private fun HeatmapTab(uiState: StatsUiState, metric: StatsMetric, modifier: Modifier = Modifier) {
    val points = remember(uiState.sessions, metric) { buildHeatPoints(uiState.sessions, metric) }
    val provider = remember(points) { HeatmapTileProvider(points) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(44.4949, 11.3426), 11f)
    }
    var isMapLoaded by remember { mutableStateOf(false) }

    // Inquadra tutti i punti ogni volta che cambiano i filtri (solo dopo il caricamento della mappa)
    LaunchedEffect(isMapLoaded, points) {
        if (!isMapLoaded || points.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.builder().apply {
            points.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        if (bounds.northeast == bounds.southwest) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(bounds.center, 15f))
        } else {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${uiState.sessions.size} soste. Pizzica o usa i tasti +/- per zoomare, trascina per spostarti.",
            style = MaterialTheme.typography.bodySmall
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = true),
                onMapLoaded = { isMapLoaded = true }
            ) {
                key(provider) {
                    TileOverlay(tileProvider = provider, fadeIn = true)
                }
            }
        }

        // Legenda: da poche (blu) a molte (rosso)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Poche", style = MaterialTheme.typography.labelSmall)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .padding(top = 3.dp)
                    .background(Brush.horizontalGradient(HeatmapTileProvider.STOPS.map { Color(it) }))
            )
            Text(
                text = if (metric == StatsMetric.COST) "Molto costose" else "Molte",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
