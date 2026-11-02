package com.example.parkmatee

import com.example.parkmatee.data.entity.ParkingSession
import com.example.parkmatee.data.entity.ParkingType
import com.example.parkmatee.data.entity.costAt
import com.example.parkmatee.data.entity.costForElapsed
import com.example.parkmatee.data.entity.validationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ParkingCostTest {

    private val hour = 3_600_000L

    private fun session(
        type: ParkingType,
        hourlyRate: Double? = null,
        fixedCost: Double? = null,
        expiryTime: Long? = null,
        startTime: Long = 0L,
        endTime: Long? = null
    ) = ParkingSession(
        vehicleId = 1,
        type = type,
        startTime = startTime,
        endTime = endTime,
        latitude = 0.0,
        longitude = 0.0,
        hourlyRate = hourlyRate,
        fixedCost = fixedCost,
        expiryTime = expiryTime
    )

    @Test
    fun hourly_oneHour_costsOneRate() {
        assertEquals(2.0, session(ParkingType.HOURLY, hourlyRate = 2.0).costForElapsed(hour), 0.0)
    }

    @Test
    fun hourly_halfHour_isProportional() {
        assertEquals(1.0, session(ParkingType.HOURLY, hourlyRate = 2.0).costForElapsed(hour / 2), 0.0)
    }

    @Test
    fun hourly_isRoundedToCents() {
        // 1,50 EUR/h per 1 minuto = 0,025 -> 0,03 (HALF_UP)
        assertEquals(0.03, session(ParkingType.HOURLY, hourlyRate = 1.5).costForElapsed(60_000L), 0.0)
    }

    @Test
    fun hourly_negativeElapsed_isZero() {
        assertEquals(0.0, session(ParkingType.HOURLY, hourlyRate = 2.0).costForElapsed(-5_000L), 0.0)
    }

    @Test
    fun fixed_costIsConstant() {
        val s = session(ParkingType.FIXED, fixedCost = 3.5, expiryTime = hour)
        assertEquals(3.5, s.costForElapsed(0L), 0.0)
        assertEquals(3.5, s.costForElapsed(10 * hour), 0.0)
    }

    @Test
    fun free_isAlwaysZero() {
        assertEquals(0.0, session(ParkingType.FREE).costForElapsed(5 * hour), 0.0)
    }

    @Test
    fun hourly_withoutRate_failsInsteadOfReturningZero() {
        assertThrows(IllegalStateException::class.java) {
            session(ParkingType.HOURLY).costForElapsed(hour)
        }
    }

    @Test
    fun costAt_usesEndTimeWhenClosed() {
        val closed = session(ParkingType.HOURLY, hourlyRate = 2.0, endTime = hour)
        assertEquals(2.0, closed.costAt(nowMillis = 5 * hour), 0.0)
    }

    @Test
    fun validation_rejectsIncoherentSessions() {
        assertNotNull(session(ParkingType.HOURLY).validationError())
        assertNotNull(session(ParkingType.HOURLY, hourlyRate = 0.0).validationError())
        assertNotNull(session(ParkingType.FIXED, fixedCost = 2.0).validationError())
        assertNotNull(session(ParkingType.FIXED, expiryTime = hour).validationError())
        assertNotNull(session(ParkingType.FIXED, fixedCost = 2.0, expiryTime = 0L).validationError())
    }

    @Test
    fun validation_acceptsCoherentSessions() {
        assertNull(session(ParkingType.FREE).validationError())
        assertNull(session(ParkingType.HOURLY, hourlyRate = 1.0).validationError())
        assertNull(session(ParkingType.FIXED, fixedCost = 2.0, expiryTime = hour).validationError())
    }
}
