package com.example.parkmatee.ui.stats

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.costAt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Grandezza mostrata dai grafici: euro oppure numero di soste. */
enum class StatsMetric(val label: String) {
    COST("Costo"),
    COUNT("N. soste")
}

/** Ampiezza dei periodi sull'asse del tempo. */
enum class StatsGranularity(val label: String, val bucketCount: Int, val unitPlural: String) {
    DAY("Giorno", 14, "giorni"),
    WEEK("Settimana", 12, "settimane"),
    MONTH("Mese", 12, "mesi")
}

/** Un periodo (giorno, settimana o mese) con i totali delle soste che INIZIANO in quel periodo. */
data class StatsBucket(
    val startMillis: Long,
    val label: String,
    val cost: Double,
    val count: Int
) {
    fun value(metric: StatsMetric): Double = when (metric) {
        StatsMetric.COST -> cost
        StatsMetric.COUNT -> count.toDouble()
    }
}

/** Punto della heat map: coordinate e peso. */
data class HeatPoint(val latitude: Double, val longitude: Double, val weight: Double)

/**
 * Costo di una sosta terminata: costo finale salvato, altrimenti ricalcolato.
 * Se i dati fossero incoerenti vale 0 invece di far fallire tutte le statistiche.
 */
fun ParkingSession.statsCost(): Double =
    finalCost ?: runCatching { costAt(endTime ?: startTime) }.getOrDefault(0.0)

/**
 * Aggrega le soste per giorno, settimana (da lunedi) o mese nel fuso orario indicato.
 * - Una sosta e attribuita al periodo in cui INIZIA (stessa regola dello storico), quindi una
 *   sosta a cavallo di mezzanotte o di fine mese non viene spezzata ne contata due volte.
 * - Restituisce sempre gli ultimi [StatsGranularity.bucketCount] periodi consecutivi, fino a
 *   quello di [nowMillis]: i periodi senza soste valgono 0, cosi l'asse del tempo e continuo.
 */
fun aggregateBuckets(
    sessions: List<ParkingSession>,
    granularity: StatsGranularity,
    nowMillis: Long,
    zone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): List<StatsBucket> {
    fun calendarAt(timestamp: Long): Calendar = Calendar.getInstance(zone, locale).apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun bucketStart(timestamp: Long): Long {
        val c = calendarAt(timestamp)
        when (granularity) {
            StatsGranularity.DAY -> Unit
            StatsGranularity.WEEK -> {
                // SUNDAY=1 ... SATURDAY=7  ->  giorni trascorsi dal lunedi
                val sinceMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
                c.add(Calendar.DAY_OF_MONTH, -sinceMonday)
            }
            StatsGranularity.MONTH -> c.set(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis
    }

    fun shift(timestamp: Long, steps: Int): Long {
        val c = calendarAt(timestamp)
        when (granularity) {
            StatsGranularity.DAY -> c.add(Calendar.DAY_OF_MONTH, steps)
            StatsGranularity.WEEK -> c.add(Calendar.DAY_OF_MONTH, 7 * steps)
            StatsGranularity.MONTH -> c.add(Calendar.MONTH, steps)
        }
        return c.timeInMillis
    }

    val newest = maxOf(nowMillis, sessions.maxOfOrNull { it.startTime } ?: nowMillis)
    val starts = ArrayList<Long>(granularity.bucketCount)
    var cursor = bucketStart(newest)
    repeat(granularity.bucketCount) {
        starts.add(0, cursor)
        cursor = shift(cursor, -1)
    }

    val indexByStart = starts.withIndex().associate { it.value to it.index }
    val costs = DoubleArray(starts.size)
    val counts = IntArray(starts.size)
    sessions.forEach { session ->
        val index = indexByStart[bucketStart(session.startTime)] ?: return@forEach
        costs[index] += session.statsCost()
        counts[index] += 1
    }

    val pattern = if (granularity == StatsGranularity.MONTH) "MMM yy" else "dd/MM"
    val formatter = SimpleDateFormat(pattern, locale).apply { timeZone = zone }

    return starts.mapIndexed { i, start ->
        StatsBucket(
            startMillis = start,
            label = formatter.format(start),
            // arrotondamento al centesimo per evitare residui di virgola mobile nella somma
            cost = Math.round(costs[i] * 100.0) / 100.0,
            count = counts[i]
        )
    }
}

/** Punti per la heat map. Con la metrica costo, se nessuna sosta costa nulla si usa il conteggio. */
fun buildHeatPoints(sessions: List<ParkingSession>, metric: StatsMetric): List<HeatPoint> {
    val useCost = metric == StatsMetric.COST && sessions.any { it.statsCost() > 0.0 }
    return sessions.map { session ->
        HeatPoint(
            latitude = session.latitude,
            longitude = session.longitude,
            weight = if (useCost) session.statsCost() else 1.0
        )
    }
}
