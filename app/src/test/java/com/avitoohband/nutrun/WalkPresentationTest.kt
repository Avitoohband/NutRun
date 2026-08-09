package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.WalkSessionEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkPresentationTest {
    private val startedAt = ZonedDateTime.of(2026, 8, 9, 10, 0, 0, 0, ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    private val walk = WalkSessionEntity(
        id = "walk-1",
        userId = "user-1",
        state = "FINISHED",
        startedAtMillis = startedAt,
        endedAtMillis = startedAt + 45 * 60_000L,
        accumulatedDurationMillis = 45 * 60_000L,
        resumedAtMillis = null,
        distanceMeters = 3_000.0,
        stepBaseline = null,
        steps = 4_500L
    )

    @Test
    fun activeRouteSessionIdUsesTheActiveWalkId() {
        assertNull(activeRouteSessionId(null))
        assertEquals("walk-1", activeRouteSessionId(walk))
    }

    @Test
    fun walkDateUsesTheLocalLongDate() {
        assertEquals("Sunday, August 9, 2026", formatWalkDate(startedAt))
    }

    @Test
    fun walkTimeRangeUsesLocalStartAndEndTimes() {
        assertEquals("10:00 AM - 10:45 AM", formatWalkTimeRange(walk))
    }

    @Test
    fun walkDurationUsesElapsedTimeFormatting() {
        assertEquals("1:05:06", formatWalkDuration(3_906_000L))
        assertEquals("5:06", formatWalkDuration(306_000L))
    }

    @Test
    fun averageWalkPaceUsesMinutesPerKilometer() {
        assertEquals(15.0, averageWalkPaceMinutesPerKm(walk)!!, 0.001)
        assertNull(averageWalkPaceMinutesPerKm(walk.copy(distanceMeters = 0.0)))
    }
}
