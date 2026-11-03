package com.example.parkmatee.ui.stats

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parkmatee.util.ParkingFormatters
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private fun formatMetric(value: Double, metric: StatsMetric): String = when (metric) {
    StatsMetric.COST -> ParkingFormatters.formatCurrency(value)
    StatsMetric.COUNT -> value.toInt().toString()
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
                fun indexAt(x: Float): Int {
                    val left = leftPad.toPx()
                    val slot = (size.width - left) / buckets.size.coerceAtLeast(1)
                    return ((x - left) / slot).toInt().coerceIn(0, (buckets.size - 1).coerceAtLeast(0))
                }
                detectTapGestures { offset -> if (buckets.isNotEmpty()) onSelect(indexAt(offset.x)) }
            }
            .pointerInput(buckets.size) {
                fun indexAt(x: Float): Int {
                    val left = leftPad.toPx()
                    val slot = (size.width - left) / buckets.size.coerceAtLeast(1)
                    return ((x - left) / slot).toInt().coerceIn(0, (buckets.size - 1).coerceAtLeast(0))
                }
                detectHorizontalDragGestures { change, _ ->
                    if (buckets.isNotEmpty()) onSelect(indexAt(change.position.x))
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

data class DonutSlice(val label: String, val value: Double, val color: Color)

/**
 * Grafico a ciambella: ripartizione per tipo di sosta. Tocco sulla fetta o sulla legenda
 * per selezionarla (la fetta selezionata si ingrossa).
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    metric: StatsMetric,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.value }
    val selected = slices.getOrNull(selectedIndex)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .semantics { contentDescription = "Grafico a ciambella: ripartizione per tipo di sosta. Tocca una fetta per selezionarla." }
                    .pointerInput(slices) {
                        detectTapGestures { offset ->
                            if (total <= 0.0) return@detectTapGestures
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dist = hypot(offset.x - cx, offset.y - cy)
                            if (dist < min(cx, cy) * 0.4f || dist > min(cx, cy)) return@detectTapGestures
                            val angle = ((atan2(offset.y - cy, offset.x - cx) * 180.0 / PI) + 90.0 + 360.0) % 360.0
                            var start = 0.0
                            slices.forEachIndexed { i, slice ->
                                val sweep = 360.0 * slice.value / total
                                if (angle >= start && angle < start + sweep) onSelect(i)
                                start += sweep
                            }
                        }
                    }
            ) {
                if (total <= 0.0) return@Canvas
                val stroke = 30.dp.toPx()
                val diameter = min(size.width, size.height) - stroke * 1.3f
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                var start = -90f
                slices.forEachIndexed { i, slice ->
                    val sweep = (360.0 * slice.value / total).toFloat()
                    drawArc(
                        color = if (i == selectedIndex) slice.color else slice.color.copy(alpha = 0.6f),
                        startAngle = start,
                        sweepAngle = if (slices.size > 1) (sweep - 1f).coerceAtLeast(0f) else sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = if (i == selectedIndex) stroke * 1.25f else stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep
                }
            }
            Text(
                text = selected?.let { formatMetric(it.value, metric) } ?: "-",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Legenda cliccabile (alternativa accessibile al tocco sulla fetta)
        slices.forEachIndexed { i, slice ->
            val percent = if (total > 0.0) slice.value / total * 100.0 else 0.0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(i) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(12.dp).background(slice.color, CircleShape))
                Text(
                    text = slice.label,
                    modifier = Modifier.weight(1f),
                    style = if (i == selectedIndex) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                )
                Text(text = "${formatMetric(slice.value, metric)} (${"%.0f".format(percent)}%)")
            }
        }
    }
}
