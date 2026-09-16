package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class GtgModelsTest {
    @Test
    fun planRejectsTargetsOutsideApprovedBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(targetPerSet = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(dailySetGoal = 101)
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(weightKg = Double.NaN)
        }
    }

    @Test
    fun logRequiresExactlyOneMeasurementMatchingItsMode() {
        assertThrows(IllegalArgumentException::class.java) {
            log(reps = 5, durationSeconds = 20)
        }
        assertThrows(IllegalArgumentException::class.java) {
            log(measure = GtgMeasure.HOLD_SECONDS, reps = 5, durationSeconds = null)
        }
    }

    @Test
    fun logRejectsInvalidEffortNotesAndWeight() {
        assertThrows(IllegalArgumentException::class.java) { log(rir = 11) }
        assertThrows(IllegalArgumentException::class.java) { log(notes = "x".repeat(1_001)) }
        assertThrows(IllegalArgumentException::class.java) { log(weightKg = Double.POSITIVE_INFINITY) }
    }

    private fun plan(
        targetPerSet: Int = 5,
        dailySetGoal: Int = 5,
        weightKg: Double? = null
    ) = GtgPlan(
        id = UUID.randomUUID().toString(),
        exerciseId = "pull-up",
        exerciseName = "Pull up",
        measure = GtgMeasure.REPS,
        targetPerSet = targetPerSet,
        dailySetGoal = dailySetGoal,
        weekdays = setOf(DayOfWeek.MONDAY),
        weightKg = weightKg
    )

    private fun log(
        measure: GtgMeasure = GtgMeasure.REPS,
        reps: Int? = 5,
        durationSeconds: Int? = null,
        weightKg: Double? = null,
        rir: Int? = 3,
        notes: String = ""
    ) = GtgLog(
        id = UUID.randomUUID().toString(),
        planId = UUID.randomUUID().toString(),
        exerciseId = "pull-up",
        exerciseName = "Pull up",
        measure = measure,
        reps = reps,
        durationSeconds = durationSeconds,
        weightKg = weightKg,
        rir = rir,
        notes = notes,
        performedAtMillis = 1_789_560_000_000,
        zoneId = "Asia/Jerusalem",
        performedOn = LocalDate.of(2026, 9, 16),
        dailySetGoalSnapshot = 5
    )
}
