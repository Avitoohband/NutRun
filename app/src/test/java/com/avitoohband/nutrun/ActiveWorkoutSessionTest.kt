package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutSessionTest {
    private val exercise = Exercise("bench-press", "Bench press", "Strength", "Chest", "", "", "")

    @Test
    fun sanitizeDiscardsUnknownTargetState() {
        val target = ExerciseTarget("target-1", exercise)
        val session = ActiveWorkoutSession(
            id = "active-1",
            sourceTemplateId = "workout-1",
            name = "Push",
            exercises = listOf(target),
            skippedTargetIds = setOf("missing"),
            completedTargetIds = setOf("missing", "target-1"),
            setLogs = mapOf("missing" to emptyList()),
            startedAtMillis = 1_000L,
            restTimerEndAtMillis = 500L
        ).sanitize()

        assertTrue(session.skippedTargetIds.isEmpty())
        assertEquals(setOf("target-1"), session.completedTargetIds)
        assertTrue(session.setLogs.isEmpty())
        assertEquals(null, session.restTimerEndAtMillis)
    }

    @Test
    fun shouldDeliverRestTimerCompletionRequiresExactMatch() {
        val active = ActiveWorkoutSession.quickWorkout("Quick", id = "active-1").copy(
            restTimerEndAtMillis = 10_000L
        )
        assertTrue(
            shouldDeliverRestTimerCompletion(
                expectedUserId = "user-a",
                expectedActiveWorkoutId = "active-1",
                expectedEndAtMillis = 10_000L,
                currentUserId = "user-a",
                currentActiveWorkout = active,
                nowMillis = 10_000L
            )
        )
        assertFalse(
            shouldDeliverRestTimerCompletion(
                expectedUserId = "user-a",
                expectedActiveWorkoutId = "active-1",
                expectedEndAtMillis = 10_000L,
                currentUserId = "user-b",
                currentActiveWorkout = active,
                nowMillis = 10_000L
            )
        )
        assertFalse(
            shouldDeliverRestTimerCompletion(
                expectedUserId = "user-a",
                expectedActiveWorkoutId = "active-1",
                expectedEndAtMillis = 10_000L,
                currentUserId = "user-a",
                currentActiveWorkout = active.copy(restTimerEndAtMillis = 11_000L),
                nowMillis = 10_000L
            )
        )
        assertFalse(
            shouldDeliverRestTimerCompletion(
                expectedUserId = "user-a",
                expectedActiveWorkoutId = "active-1",
                expectedEndAtMillis = 10_000L,
                currentUserId = "user-a",
                currentActiveWorkout = active,
                nowMillis = 9_999L
            )
        )
    }
}
