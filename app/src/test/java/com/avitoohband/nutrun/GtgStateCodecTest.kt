package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GtgStateCodecTest {
    @Test
    fun schemaFourRoundTripPreservesPlansLogsAndSnapshots() {
        val plan = GtgPlan(
            id = UUID.randomUUID().toString(),
            exerciseId = "pull-up",
            exerciseName = "Pull up",
            measure = GtgMeasure.REPS,
            targetPerSet = 5,
            dailySetGoal = 6,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            weightKg = 10.0,
            archived = true
        )
        val log = GtgLog(
            id = UUID.randomUUID().toString(),
            planId = plan.id,
            exerciseId = plan.exerciseId,
            exerciseName = plan.exerciseName,
            measure = GtgMeasure.REPS,
            reps = 5,
            durationSeconds = null,
            weightKg = 10.0,
            rir = 0,
            notes = "Clean reps",
            performedAtMillis = 1_789_560_000_000,
            zoneId = "Asia/Jerusalem",
            performedOn = LocalDate.of(2026, 9, 16),
            dailySetGoalSnapshot = 6
        )
        val state = GtgState(listOf(plan), listOf(log))

        val payload = encodeTrainingState(gtgState = state)
        val restored = requireNotNull(decodeTrainingState(payload, builtInExerciseCatalog()))

        assertEquals(4, JSONObject(payload).getInt("schemaVersion"))
        assertEquals(state, restored.gtgState)
    }

    @Test
    fun schemaThreeDecodesWithEmptyGtgState() {
        val payload = JSONObject().put("schemaVersion", 3).toString()
        assertEquals(GtgState(), requireNotNull(decodeTrainingState(payload, builtInExerciseCatalog())).gtgState)
    }

    @Test
    fun unsupportedFutureSchemaIsRejected() {
        val payload = JSONObject().put("schemaVersion", 5).toString()
        assertNull(decodeTrainingState(payload, builtInExerciseCatalog()))
    }
}
