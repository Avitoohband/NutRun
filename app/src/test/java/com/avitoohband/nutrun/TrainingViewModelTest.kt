package com.avitoohband.nutrun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingViewModelTest {
    @Test
    fun progressionSuggestionRecalculatesAfterFinishedWorkoutWithoutChangingTarget() {
        val (model, session, target) = weightedTargetFixture()

        assertNull(model.progressionSuggestion(target))

        finishSuccessfulWorkout(model, session, target)

        val suggestion = requireNotNull(model.progressionSuggestion(target))
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertEquals(60.0, suggestion.currentWeightKg, 0.001)
        assertEquals(62.5, suggestion.suggestedWeightKg, 0.001)
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)
        assertTrue(model.activeSetLogs.isEmpty())
        assertEquals(
            listOf(target.id),
            model.progressionSuggestions(session).map { (suggestedTarget, _) -> suggestedTarget.id }
        )
    }

    @Test
    fun progressionSuggestionRecalculatesAfterWorkoutEditAndDelete() {
        val (model, session, target) = weightedTargetFixture()
        finishSuccessfulWorkout(model, session, target)
        val completedWorkout = model.workoutHistory.single()
        val highRpeWorkout = completedWorkout.copy(
            sets = completedWorkout.sets.map { set ->
                if (set.targetId == target.id) set.copy(rpe = 9.0) else set
            }
        )

        model.updateWorkoutRecord(highRpeWorkout)

        val editedSuggestion = requireNotNull(model.progressionSuggestion(target))
        assertEquals(ProgressionAction.KEEP, editedSuggestion.action)
        assertEquals(60.0, editedSuggestion.suggestedWeightKg, 0.001)
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)

        model.deleteWorkoutRecord(highRpeWorkout.id)

        assertNull(model.progressionSuggestion(target))
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)
    }

    @Test
    fun progressionSuggestionRecalculatesForUnitChangesWithoutChangingActiveLogs() {
        val (model, session, target) = weightedTargetFixture()
        finishSuccessfulWorkout(model, session, target)
        model.startWorkout(session.id)
        val prefilledLogs = model.activeSetLogs[target.id].orEmpty()

        val metricSuggestion = requireNotNull(model.progressionSuggestion(target))
        model.updateUsesMetricUnits(false)
        val imperialSuggestion = requireNotNull(model.progressionSuggestion(target))

        assertEquals(62.5, metricSuggestion.suggestedWeightKg, 0.001)
        assertEquals(62.268, imperialSuggestion.suggestedWeightKg, 0.001)
        assertEquals(prefilledLogs, model.activeSetLogs[target.id])
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)
    }

    @Test
    fun duplicateExercisesAreRejectedAndRemovalIsSessionOnly() {
        val model = TrainingViewModel(null, null)
        model.selectSession("session-monday-push-biceps")
        val exercise = model.exerciseLibrary.first { candidate ->
            model.selectedSession()!!.exercises.none { it.exercise.id == candidate.id }
        }
        val originalCatalogSize = model.exerciseLibrary.size

        model.addExerciseToSelectedSession(exercise)
        model.addExerciseToSelectedSession(exercise)

        val matches = model.selectedSession()!!.exercises.filter { it.exercise.id == exercise.id }
        assertEquals(1, matches.size)
        model.removeExerciseFromSelectedSession(matches.single().id)
        assertTrue(model.selectedSession()!!.exercises.none { it.exercise.id == exercise.id })
        assertEquals(originalCatalogSize, model.exerciseLibrary.size)
    }

    @Test
    fun finishingWorkoutRetainsSummaryUntilDismissed() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first()
        model.startWorkout(session.id)
        model.toggleExerciseComplete(session.exercises.first().id, true)

        model.finishWorkout()

        assertNull(model.activeSession())
        assertNotNull(model.lastWorkoutSummary)
        assertEquals(1, model.lastWorkoutSummary!!.completedExercises)
        model.dismissWorkoutSummary()
        assertNull(model.lastWorkoutSummary)
    }

    @Test
    fun acceptingProgressionUpdatesFutureTarget() {
        val model = TrainingViewModel(null, null)

        model.decideSuggestion(SuggestionDecision.ACCEPTED, 45.0)

        val target = model.sessions.flatMap { it.exercises }.first { it.exercise.id == "lat-pulldown" }
        assertEquals(45.0, target.weightKg!!, 0.001)
        assertFalse(model.history.isEmpty())
    }

    @Test
    fun cardioAlternativesAreMutuallyExclusiveAndCountAsOneTarget() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-sunday-cardio" }
        val walk = session.exercises.first()
        val swim = session.exercises.last()

        model.startWorkout(session.id)
        model.toggleExerciseComplete(walk.id, true)
        model.toggleExerciseComplete(swim.id, true)

        assertFalse(model.completedExerciseIds[walk.id] == true)
        assertTrue(model.completedExerciseIds[swim.id] == true)
        model.finishWorkout()
        assertEquals(1, model.lastWorkoutSummary!!.completedExercises)
        assertEquals(1, model.lastWorkoutSummary!!.totalExercises)
        assertTrue(model.history.first().startsWith(formatToday()))
    }

    @Test
    fun completedSetsCreateStructuredHistoryAndBecomePreviousValues() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val target = session.exercises.first()

        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = target.id,
            setNumber = 1,
            reps = 8,
            weightKg = 60.0,
            durationSeconds = null,
            rpe = 8.0,
            completed = true
        )
        model.finishWorkout()

        val workout = model.workoutHistory.single()
        val loggedSet = workout.sets.first {
            it.targetId == target.id && it.setNumber == 1
        }
        assertEquals(session.id, workout.sessionId)
        assertEquals(480.0, workout.totalVolumeKg, 0.001)
        assertEquals(target.exercise.name, loggedSet.exerciseName)
        assertEquals(8, loggedSet.reps)
        assertEquals(60.0, loggedSet.weightKg!!, 0.001)
        assertEquals(8.0, loggedSet.rpe!!, 0.001)
        assertEquals(60.0, model.personalRecords().single().bestWeightKg, 0.001)
        assertEquals(1, model.previousSets(target.exercise.id).count { it.completed })
    }

    @Test
    fun movedAndSkippedSessionsChangeOnlyTheSelectedWeek() {
        val model = TrainingViewModel(null, null)
        val week = trainingWeek(LocalDate.of(2026, 7, 20))
        val monday = week.first()
        val tuesday = monday.plusDays(1)
        val mondaySession = model.sessions.first { it.weekday == java.time.DayOfWeek.MONDAY }

        model.rescheduleSession(mondaySession.id, monday, tuesday)

        assertFalse(model.sessionsForDate(monday).any { it.id == mondaySession.id })
        assertTrue(model.sessionsForDate(tuesday).any { it.id == mondaySession.id })
        model.skipSession(mondaySession.id, monday)
        assertFalse(model.sessionsForDate(monday).any { it.id == mondaySession.id })
        assertFalse(model.sessionsForDate(tuesday).any { it.id == mondaySession.id })
    }

    @Test
    fun configurableRestTimerUsesTheSavedDefault() {
        val model = TrainingViewModel(null, null)
        model.updateDefaultRestTimerSeconds(120)
        val beforeStart = System.currentTimeMillis()

        model.startRestTimer()

        val remaining = model.restTimerEndAtMillis!! - beforeStart
        assertEquals(120, model.defaultRestTimerSeconds)
        assertTrue(remaining in 119_000L..121_000L)
    }

    @Test
    fun weightUnitCanSwitchBetweenKilogramsAndPounds() {
        val model = TrainingViewModel(null, null)

        model.updateUsesMetricUnits(false)

        assertFalse(model.usesMetricUnits)
        assertEquals("132.3 lb", displayWeight(60.0, model.usesMetricUnits))

        model.updateUsesMetricUnits(true)

        assertTrue(model.usesMetricUnits)
        assertEquals("60 kg", displayWeight(60.0, model.usesMetricUnits))
    }

    @Test
    fun workoutHistoryCanBeEditedAndDeleted() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val target = session.exercises.first()
        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = target.id,
            setNumber = 1,
            reps = 8,
            weightKg = 60.0,
            durationSeconds = null,
            rpe = 8.0,
            completed = true
        )
        model.finishWorkout()
        val original = model.workoutHistory.single()
        val editedDate = LocalDate.of(2026, 7, 28)
        val edited = original.copy(
            sessionName = "Edited push workout",
            performedOn = editedDate,
            sets = original.sets.map { set ->
                if (set.targetId == target.id && set.setNumber == 1) {
                    set.copy(reps = 10, weightKg = 65.0)
                } else {
                    set
                }
            }
        )

        model.updateWorkoutRecord(edited)

        val saved = model.workoutHistory.single()
        val savedSet = saved.sets.first {
            it.targetId == target.id && it.setNumber == 1
        }
        assertEquals("Edited push workout", saved.sessionName)
        assertEquals(editedDate, saved.performedOn)
        assertEquals(10, savedSet.reps)
        assertEquals(65.0, savedSet.weightKg!!, 0.001)
        assertTrue(model.history.first().contains("Edited push workout"))

        model.deleteWorkoutRecord(saved.id)

        assertTrue(model.workoutHistory.isEmpty())
        assertTrue(model.history.none { it.contains("Edited push workout") })
    }

    @Test
    fun cancellingWorkoutDiscardsActiveProgressWithoutHistory() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first()
        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = session.exercises.first().id,
            setNumber = 1,
            reps = 8,
            weightKg = 40.0,
            durationSeconds = null,
            rpe = 7.0,
            completed = true
        )

        model.cancelWorkout()

        assertNull(model.activeSession())
        assertTrue(model.activeSetLogs.isEmpty())
        assertTrue(model.completedExerciseIds.isEmpty())
        assertTrue(model.workoutHistory.isEmpty())
        assertNull(model.restTimerEndAtMillis)
    }

    private fun weightedTargetFixture(): Triple<TrainingViewModel, TrainingSession, ExerciseTarget> {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val originalTarget = session.exercises.first()
        model.selectSession(session.id)
        model.updateSelectedExercise(
            targetId = originalTarget.id,
            sets = originalTarget.sets,
            reps = originalTarget.reps,
            weightKg = 60.0,
            durationMinutes = originalTarget.durationMinutes,
            distanceKm = originalTarget.distanceKm
        )
        val updatedSession = requireNotNull(model.selectedSession())
        val target = updatedSession.exercises.first { it.id == originalTarget.id }
        return Triple(model, updatedSession, target)
    }

    private fun finishSuccessfulWorkout(
        model: TrainingViewModel,
        session: TrainingSession,
        target: ExerciseTarget
    ) {
        model.startWorkout(session.id)
        repeat(target.sets) { index ->
            model.updateWorkoutSet(
                targetId = target.id,
                setNumber = index + 1,
                reps = requireNotNull(target.maximumReps),
                weightKg = 60.0,
                durationSeconds = null,
                rpe = 8.0,
                completed = true
            )
        }
        model.finishWorkout()
    }

    private fun sessionTarget(
        model: TrainingViewModel,
        session: TrainingSession,
        target: ExerciseTarget
    ): ExerciseTarget = model.sessions
        .first { it.id == session.id }
        .exercises
        .first { it.id == target.id }
}
