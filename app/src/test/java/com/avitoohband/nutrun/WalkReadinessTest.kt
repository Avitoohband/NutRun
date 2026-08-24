package com.avitoohband.nutrun

import com.avitoohband.nutrun.walk.WALK_GPS_ACQUIRING_TIMEOUT_MS
import com.avitoohband.nutrun.walk.WALK_GPS_READY_ACCURACY_METERS
import com.avitoohband.nutrun.walk.WALK_GPS_STALE_MS
import com.avitoohband.nutrun.walk.WalkGpsState
import com.avitoohband.nutrun.walk.walkGpsStateAfterAcquiringTimeout
import com.avitoohband.nutrun.walk.walkGpsStateFromFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkReadinessTest {
    @Test
    fun readyAccuracyUsesTwentyFiveMeterThreshold() {
        assertEquals(
            WalkGpsState.Ready(WALK_GPS_READY_ACCURACY_METERS),
            walkGpsStateFromFix(WALK_GPS_READY_ACCURACY_METERS, fixAgeMillis = 0L)
        )
        assertTrue(
            walkGpsStateFromFix(WALK_GPS_READY_ACCURACY_METERS + 1f, fixAgeMillis = 0L)
                is WalkGpsState.Weak
        )
    }

    @Test
    fun staleFixReturnsAcquiring() {
        assertEquals(
            WalkGpsState.Acquiring,
            walkGpsStateFromFix(10f, fixAgeMillis = WALK_GPS_STALE_MS + 1L)
        )
    }

    @Test
    fun acquiringTimesOutToUnavailable() {
        assertEquals(
            WalkGpsState.Unavailable("GPS signal not found"),
            walkGpsStateAfterAcquiringTimeout(
                WalkGpsState.Acquiring,
                WALK_GPS_ACQUIRING_TIMEOUT_MS
            )
        )
    }

    @Test
    fun acquiringTimeoutDoesNotOverrideReadyState() {
        val ready = WalkGpsState.Ready(12f)
        assertEquals(
            ready,
            walkGpsStateAfterAcquiringTimeout(ready, WALK_GPS_ACQUIRING_TIMEOUT_MS + 1_000L)
        )
    }
}
