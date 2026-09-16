package com.avitoohband.nutrun

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class GtgAnalyticsTest {
    @Test
    fun totalsUseStoredDateAndDeduplicateRepeatedIds() {
        val date = LocalDate.of(2026, 9, 16)
        val first = log("log-1", date, reps = 5)
        val second = log("log-2", date, reps = 4)
        val nextDay = log("log-3", date.plusDays(1), reps = 10)

        assertEquals(
            GtgDayTotals(2, 9, 0),
            gtgDayTotals(listOf(first, first, second, nextDay), first.planId, date)
        )
    }

    @Test
    fun holdTotalsNeverMixWithRepetitions() {
        val date = LocalDate.of(2026, 9, 16)
        val hold = log("hold-1", date, reps = null, durationSeconds = 30, measure = GtgMeasure.HOLD_SECONDS)

        assertEquals(GtgDayTotals(1, 0, 30), gtgDayTotals(listOf(hold), hold.planId, date))
    }

    private fun log(
        id: String,
        date: LocalDate,
        reps: Int?,
        durationSeconds: Int? = null,
        measure: GtgMeasure = GtgMeasure.REPS
    ) = GtgLog(
        id = id,
        planId = "plan-1",
        exerciseId = "pull-up",
        exerciseName = "Pull up",
        measure = measure,
        reps = reps,
        durationSeconds = durationSeconds,
        weightKg = null,
        rir = null,
        notes = "",
        performedAtMillis = 1_789_560_000_000,
        zoneId = "Asia/Jerusalem",
        performedOn = date,
        dailySetGoalSnapshot = 5
    )
}
