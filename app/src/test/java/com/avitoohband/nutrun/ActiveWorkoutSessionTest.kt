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
    fun skippedTargetsDoNotResolveRemainingIncompleteTargets() {
        val targets = (1..3).map { index ->
            ExerciseTarget("target-$index", exercise.copy(id = "exercise-$index"))
        }
        val session = ActiveWorkoutSession(
            id = "active-1",
            sourceTemplateId = "workout-1",
            name = "Push",
            exercises = targets,
            skippedTargetIds = setOf("target-1", "target-2"),
            startedAtMillis = 1_000L
        )

        assertEquals(1, session.logicalTargetCount())
        assertEquals(0, session.completedLogicalTargetCount())
        assertEquals(0, session.resolvedLogicalTargetCount())
    }

    @Test
    fun completedAndSkippedTargetsOnlyResolveCompletedRemainingWork() {
        val targets = (1..4).map { index ->
            ExerciseTarget("target-$index", exercise.copy(id = "exercise-$index"))
        }
        val session = ActiveWorkoutSession(
            id = "active-1",
            sourceTemplateId = "workout-1",
            name = "Push",
            exercises = targets,
            skippedTargetIds = setOf("target-1", "target-2"),
            completedTargetIds = setOf("target-3"),
            startedAtMillis = 1_000L
        )

        assertEquals(2, session.logicalTargetCount())
        assertEquals(1, session.completedLogicalTargetCount())
        assertEquals(1, session.resolvedLogicalTargetCount())
    }

    @Test
    fun alternativeGroupCountsOnceWhenOneOptionRemainsOrCompletes() {
        val alternatives = listOf(
            ExerciseTarget("walk", exercise.copy(id = "walk"), alternativeGroupId = "cardio"),
            ExerciseTarget("swim", exercise.copy(id = "swim"), alternativeGroupId = "cardio")
        )
        val oneSkipped = ActiveWorkoutSession(
            id = "active-1",
            sourceTemplateId = null,
            name = "Cardio",
            exercises = alternatives,
            skippedTargetIds = setOf("walk"),
            startedAtMillis = 1_000L
        )
        val completedAlternative = oneSkipped.copy(completedTargetIds = setOf("swim"))

        assertEquals(1, oneSkipped.logicalTargetCount())
        assertEquals(0, oneSkipped.completedLogicalTargetCount())
        assertEquals(1, completedAlternative.logicalTargetCount())
        assertEquals(1, completedAlternative.completedLogicalTargetCount())
    }

    @Test
    fun fullySkippedAlternativeGroupIsExcludedFromRemainingWork() {
        val alternatives = listOf(
            ExerciseTarget("walk", exercise.copy(id = "walk"), alternativeGroupId = "cardio"),
            ExerciseTarget("swim", exercise.copy(id = "swim"), alternativeGroupId = "cardio")
        )
        val session = ActiveWorkoutSession(
            id = "active-1",
            sourceTemplateId = null,
            name = "Cardio",
            exercises = alternatives,
            skippedTargetIds = setOf("walk", "swim"),
            startedAtMillis = 1_000L
        )

        assertEquals(0, session.logicalTargetCount())
        assertEquals(0, session.completedLogicalTargetCount())
        assertEquals(1, session.skippedLogicalTargetCount())
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
