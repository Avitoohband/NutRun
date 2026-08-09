package com.avitoohband.nutrun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionSuggestionsTest {
    @Test
    fun increaseUsesMetricIncrementAfterAllSetsReachTheTopOfTheRangeAtLowRpe() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(),
            history = listOf(attempt(finishedAtMillis = 200L, reps = listOf(12, 12, 12), rpes = listOf(8.0, 7.5, 8.0))),
            usesMetricUnits = true
        )

        assertEquals(ProgressionAction.INCREASE, suggestion?.action)
        assertEquals(40.0, suggestion?.currentWeightKg ?: 0.0, 0.001)
        assertEquals(42.5, suggestion?.suggestedWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun increaseUsesAnExactFivePoundIncrementConvertedToCanonicalKilograms() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(weightKg = 45.359237),
            history = listOf(attempt(weightKg = 45.359237, reps = listOf(12, 12, 12), rpes = listOf(8.0, 8.0, 8.0))),
            usesMetricUnits = false
        )

        assertEquals(ProgressionAction.INCREASE, suggestion?.action)
        assertEquals(47.62719885, suggestion?.suggestedWeightKg ?: 0.0, 0.000001)
    }

    @Test
    fun highRpeAtTheMinimumRepetitionsKeepsTheLatestWorkingWeight() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(),
            history = listOf(attempt(reps = listOf(10, 11, 10), rpes = listOf(7.0, 9.0, 8.0))),
            usesMetricUnits = true
        )

        assertEquals(ProgressionAction.KEEP, suggestion?.action)
        assertEquals(40.0, suggestion?.currentWeightKg ?: 0.0, 0.001)
        assertEquals(40.0, suggestion?.suggestedWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun ordinaryCompletedAttemptKeepsTheLatestWorkingWeight() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(),
            history = listOf(attempt(reps = listOf(10, 11, 11), rpes = listOf(7.0, 8.0, 8.0))),
            usesMetricUnits = true
        )

        assertEquals(ProgressionAction.KEEP, suggestion?.action)
        assertEquals(40.0, suggestion?.suggestedWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun twoNewestMissedAttemptsReduceTheLatestWorkingWeightWithMetricRounding() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(weightKg = 41.0),
            history = listOf(
                attempt(finishedAtMillis = 100L, weightKg = 41.0, reps = listOf(12, 12, 12)),
                attempt(finishedAtMillis = 300L, weightKg = 41.0, reps = listOf(9, 9, 10)),
                attempt(finishedAtMillis = 200L, weightKg = 41.0, reps = listOf(9, 10, 10))
            ),
            usesMetricUnits = true
        )

        assertEquals(ProgressionAction.REDUCE, suggestion?.action)
        assertEquals(41.0, suggestion?.currentWeightKg ?: 0.0, 0.001)
        assertEquals(40.0, suggestion?.suggestedWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun twoNewestMissedAttemptsReduceTheLatestWorkingWeightWithImperialRounding() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(weightKg = 45.359237),
            history = listOf(
                attempt(finishedAtMillis = 100L, weightKg = 45.359237, reps = listOf(9, 10, 10)),
                attempt(finishedAtMillis = 200L, weightKg = 45.359237, reps = listOf(9, 9, 10))
            ),
            usesMetricUnits = false
        )

        assertEquals(ProgressionAction.REDUCE, suggestion?.action)
        assertEquals(43.091275151, suggestion?.suggestedWeightKg ?: 0.0, 0.000001)
    }

    @Test
    fun incompleteRequiredSetsInTheTwoNewestAttemptsReduceTheLatestWorkingWeight() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(),
            history = listOf(
                attempt(finishedAtMillis = 100L, completed = listOf(false, true, true)),
                attempt(finishedAtMillis = 200L, completed = listOf(true, false, true))
            ),
            usesMetricUnits = true
        )

        assertEquals(ProgressionAction.REDUCE, suggestion?.action)
    }

    @Test
    fun missingOrInconsistentWorkingSetDataProducesNoSuggestion() {
        val target = weightedTarget()
        val missingRpe = attempt(rpes = listOf(8.0, null, 8.0))
        val inconsistentWeight = attempt(weightsKg = listOf(40.0, 42.5, 40.0))

        assertNull(progressionSuggestion(target, listOf(missingRpe), true))
        assertNull(progressionSuggestion(target, listOf(inconsistentWeight), true))
    }

    @Test
    fun insufficientSetsAndBodyweightTargetsAreNotEligible() {
        val insufficientSets = attempt(reps = listOf(12, 12), rpes = listOf(8.0, 8.0))
        val bodyweightTarget = weightedTarget(weightKg = null)
        val cardioTarget = weightedTarget().copy(durationMinutes = 10)

        assertNull(progressionSuggestion(weightedTarget(), listOf(insufficientSets), true))
        assertNull(progressionSuggestion(bodyweightTarget, listOf(attempt()), true))
        assertNull(progressionSuggestion(cardioTarget, listOf(attempt()), true))
    }

    @Test
    fun newestFinishedTimestampDeterminesTheAttemptRatherThanCallerListOrder() {
        val suggestion = progressionSuggestion(
            target = weightedTarget(),
            history = listOf(
                attempt(finishedAtMillis = 300L, weightKg = 42.5, reps = listOf(10, 11, 11)),
                attempt(finishedAtMillis = 100L, weightKg = 40.0, reps = listOf(12, 12, 12))
            ),
            usesMetricUnits = true
        )

        assertEquals(ProgressionAction.KEEP, suggestion?.action)
        assertEquals(42.5, suggestion?.currentWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun suggestionDoesNotMutateTheTargetOrHistory() {
        val target = weightedTarget()
        val history = mutableListOf(attempt(finishedAtMillis = 200L))
        val originalTarget = target.copy()
        val originalHistory = history.toList()

        val suggestion = progressionSuggestion(target, history, true)

        assertEquals(ProgressionAction.INCREASE, suggestion?.action)
        assertEquals(originalTarget, target)
        assertEquals(originalHistory, history)
        assertTrue(suggestion?.reason?.isNotBlank() == true)
    }

    private fun weightedTarget(weightKg: Double? = 40.0): ExerciseTarget = ExerciseTarget(
        id = "target-1",
        exercise = Exercise(
            id = "barbell-row",
            name = "Barbell row",
            category = "Strength",
            primaryMuscles = "Back",
            secondaryMuscles = "Biceps",
            instructions = "Row the bar.",
            safetyNote = "Keep your back neutral."
        ),
        sets = 3,
        reps = 10,
        maximumReps = 12,
        weightKg = weightKg
    )

    private fun attempt(
        finishedAtMillis: Long = 100L,
        weightKg: Double = 40.0,
        weightsKg: List<Double?> = List(3) { weightKg },
        reps: List<Int?> = List(3) { 12 },
        rpes: List<Double?> = List(3) { 8.0 },
        completed: List<Boolean> = List(3) { true }
    ): WorkoutRecord = WorkoutRecord(
        id = "workout-$finishedAtMillis-${reps.joinToString()}",
        sessionId = "session-1",
        sessionName = "Pull",
        performedOn = LocalDate.of(2026, 8, 9),
        startedAtMillis = finishedAtMillis - 1L,
        finishedAtMillis = finishedAtMillis,
        completedTargetIds = setOf("target-1"),
        completedLogicalTargets = 1,
        totalLogicalTargets = 1,
        sets = reps.indices.map { index ->
            WorkoutSetLog(
                id = "set-$finishedAtMillis-$index",
                targetId = "target-1",
                exerciseId = "barbell-row",
                setNumber = index + 1,
                reps = reps[index],
                weightKg = weightsKg[index],
                rpe = rpes[index],
                completed = completed[index]
            )
        }
    )
}
