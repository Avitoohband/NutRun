package com.avitoohband.nutrun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialStateTest {
    @Test
    fun trialExpiresAfterThirtyDaysWithoutDeletingAccess() {
        val state = TrialState(LocalDate.of(2026, 6, 1))

        assertTrue(state.isTrialActive(LocalDate.of(2026, 6, 30)))
        assertFalse(state.isTrialActive(LocalDate.of(2026, 7, 1)))
        assertEquals(0, state.daysRemaining(LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun forcedFreePlanEndsTheTrialImmediately() {
        val state = TrialState(LocalDate.of(2026, 7, 20), isForcedFreePlan = true)

        assertFalse(state.isTrialActive(LocalDate.of(2026, 7, 23)))
    }
}
