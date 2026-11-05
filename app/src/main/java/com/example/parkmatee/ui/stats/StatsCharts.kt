package com.example.parkmatee.ui.stats

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.example.parkmatee.util.ParkingFormatters

private fun formatMetric(value: Double, metric: StatsMetric): String = when (metric) {
    StatsMetric.COST -> ParkingFormatters.formatCurrency(value)
    StatsMetric.COUNT -> value.toInt().toString()
}

/** Indice della colonna sotto la coordinata [x] (area di disegno: da [leftPx] a [widthPx]). */
private fun indexAt(x: Float, leftPx: Float, widthPx: Float, count: Int): Int {
    val slot = (widthPx - leftPx) / count.coerceAtLeast(1)
    return ((x - leftPx) / slot).toInt().coerceIn(0, (count - 1).coerceAtLeast(0))
}

/**
 * Grafico a colonne nel tempo, disegnato con il Canvas di Compose.
 * Interattivo: un tap o il trascinamento orizzontale selezionano il periodo.
 */
@Composable
fun BucketColumnChart(
    buckets: List<StatsBucket>,
    metric: StatsMetric,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    val yPaint = remember(textColor, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.RIGHT
        }
    }
    val xPaint = remember(textColor, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
        }
    }

    val leftPad = 56.dp
    val bottomPad = 24.dp
    val topPad = 8.dp
    val maxValue = (buckets.maxOfOrNull { it.value(metric) } ?: 0.0).takeIf { it > 0.0 } ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .semantics { contentDescription = "Grafico a colonne: ${metric.label} per periodo. Tocca o trascina per selezionare." }
            .pointerInput(buckets.size) {
                detectTapGestures { offset ->
                    if (buckets.isNotEmpty()) {
                        onSelect(indexAt(offset.x, leftPad.toPx(), size.width.toFloat(), buckets.size))
                    }
                }
            }
            .pointerInput(buckets.size) {
                detectHorizontalDragGestures { change, _ ->
                    if (buckets.isNotEmpty()) {
                        onSelect(indexAt(change.position.x, leftPad.toPx(), size.width.toFloat(), buckets.size))
                    }
                }
            }
    ) {
        if (buckets.isEmpty()) return@Canvas

        val left = leftPad.toPx()
        val top = topPad.toPx()
        val plotWidth = size.width - left
        val plotHeight = size.height - top - bottomPad.toPx()
        val slot = plotWidth / buckets.size
        val barWidth = slot * 0.62f

        // Griglia e etichette dell'asse Y (0, meta, massimo)
        listOf(0.0, 0.5, 1.0).forEach { fraction ->
            val y = top + plotHeight * (1f - fraction.toFloat())
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    formatMetric(maxValue * fraction, metric),
                    left - 6.dp.toPx(),
                    y + yPaint.textSize / 3f,
                    yPaint
                )
            }
        }

        // Colonne
        buckets.forEachIndexed { i, bucket ->
            val value = bucket.value(metric)
            val height = if (value <= 0.0) 0f else (plotHeight * (value / maxValue).toFloat()).coerceAtLeast(2.dp.toPx())
            val x = left + i * slot + (slot - barWidth) / 2f
            drawRect(
                color = if (i == selectedIndex) barColor else barColor.copy(alpha = 0.45f),
                topLeft = Offset(x, top + plotHeight - height),
                size = Size(barWidth, height)
            )
        }

        // Etichette dell'asse X: al massimo ~6 per non sovrapporsi
        val step = kotlin.math.ceil(buckets.size / 6.0).toInt().coerceAtLeast(1)
        buckets.forEachIndexed { i, bucket ->
            if (i % step == 0 || i == selectedIndex) {
                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        bucket.label,
                        left + i * slot + slot / 2f,
                        size.height - 6.dp.toPx(),
                        xPaint
                    )
                }
            }
        }
    }
}

/**
 * Barre orizzontali per categoria (qui: veicolo). Ogni riga ha nome, barra proporzionale al
 * valore e importo; il tocco su una riga la seleziona. Nessun Canvas: solo Box e Row.
 */
@Composable
fun CategoryBars(
    items: List<StatsBucket>,
    metric: StatsMetric,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxValue = (items.maxOfOrNull { it.value(metric) } ?: 0.0).takeIf { it > 0.0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            val fraction = (item.value(metric) / maxValue).toFloat().coerceIn(0f, 1f)
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.label,
                    modifier = Modifier.width(88.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(20.dp)
                            .background(if (selected) barColor else barColor.copy(alpha = 0.45f))
                    )
                }
                Text(
                    text = formatMetric(item.value(metric), metric),
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}
