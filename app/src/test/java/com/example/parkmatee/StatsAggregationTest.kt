package com.example.parkmatee

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.ui.stats.StatsGranularity
import com.example.parkmatee.ui.stats.StatsMetric
import com.example.parkmatee.ui.stats.aggregateBuckets
import com.example.parkmatee.ui.stats.buildHeatPoints
import org.junit.Assert.assertEquals
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Test

class StatsAggregationTest {

    private val zone = TimeZone.getTimeZone("Europe/Rome")
    private val locale = Locale.ITALY

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun session(start: Long, cost: Double, durationMillis: Long = 3_600_000L) = ParkingSession(
        vehicleId = 1,
        type = ParkingType.FIXED,
        startTime = start,
        endTime = start + durationMillis,
        latitude = 44.5,
        longitude = 11.3,
        fixedCost = cost,
        finalCost = cost
    )

    private val now = millis(2026, 11, 3, 12, 0) // martedi 3 novembre 2026

    @Test
    fun daysWithoutSessionsAreZeroAndSeriesIsContinuous() {
        val buckets = aggregateBuckets(listOf(session(millis(2026, 11, 1, 10, 0), 2.0)), StatsGranularity.DAY, now, zone, locale)
        assertEquals(14, buckets.size)
        assertEquals(1, buckets.sumOf { it.count })
        assertEquals(0, buckets.last().count) // oggi senza soste
        assertEquals(1, buckets[buckets.size - 3].count) // 1 novembre
    }

    @Test
    fun sessionCrossingMidnightCountsOnlyInStartDay() {
        val start = millis(2026, 11, 2, 23, 30)
        val buckets = aggregateBuckets(listOf(session(start, 3.0, 2 * 3_600_000L)), StatsGranularity.DAY, now, zone, locale)
        assertEquals(1, buckets[buckets.size - 2].count) // 2 novembre
        assertEquals(0, buckets.last().count)            // 3 novembre
        assertEquals(3.0, buckets.sumOf { it.cost }, 0.001)
    }

    @Test
    fun weekStartsOnMonday() {
        val sunday = session(millis(2026, 11, 1, 23, 0), 1.0)
        val monday = session(millis(2026, 11, 2, 8, 0), 2.0)
        val buckets = aggregateBuckets(listOf(sunday, monday), StatsGranularity.WEEK, now, zone, locale)
        assertEquals(12, buckets.size)
        assertEquals(1, buckets.last().count)         // settimana da lunedi 2 novembre
        assertEquals(2.0, buckets.last().cost, 0.001)
        assertEquals(1, buckets[buckets.size - 2].count) // domenica nella settimana precedente
    }

    @Test
    fun monthBoundaryIsRespected() {
        val october = session(millis(2026, 10, 31, 23, 30), 1.5)
        val november = session(millis(2026, 11, 1, 0, 30), 2.5)
        val buckets = aggregateBuckets(listOf(october, november), StatsGranularity.MONTH, now, zone, locale)
        assertEquals(2.5, buckets.last().cost, 0.001)
        assertEquals(1.5, buckets[buckets.size - 2].cost, 0.001)
    }

    @Test
    fun sessionsOutsideWindowAreIgnored() {
        val old = session(millis(2025, 1, 10, 10, 0), 9.0)
        val buckets = aggregateBuckets(listOf(old), StatsGranularity.MONTH, now, zone, locale)
        assertEquals(0, buckets.sumOf { it.count })
    }

    @Test
    fun heatPointsFallBackToCountWhenAllSessionsAreFree() {
        val free = ParkingSession(
            vehicleId = 1, type = ParkingType.FREE, startTime = now - 3_600_000L, endTime = now,
            latitude = 44.5, longitude = 11.3, finalCost = 0.0
        )
        val points = buildHeatPoints(listOf(free, free), StatsMetric.COST)
        assertEquals(listOf(1.0, 1.0), points.map { it.weight })
    }
}
