package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrototypeViewModelTest {
    @Test
    fun duplicateExercisesReceiveIndependentTargetIds() {
        val model = PrototypeViewModel(null, null)
        model.selectSession("session-1")
        val exercise = model.exerciseLibrary.first()

        model.addExerciseToSelectedSession(exercise)
        model.addExerciseToSelectedSession(exercise)

        val added = model.selectedSession()!!.exercises.takeLast(2)
        assertNotEquals(added[0].id, added[1].id)
    }

    @Test
    fun finishingWorkoutRetainsSummaryUntilDismissed() {
        val model = PrototypeViewModel(null, null)
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
        val model = PrototypeViewModel(null, null)

        model.decideSuggestion(SuggestionDecision.ACCEPTED, 45.0)

        val target = model.sessions.flatMap { it.exercises }.first { it.exercise.id == "lat-pulldown" }
        assertEquals(45.0, target.weightKg!!, 0.001)
        assertFalse(model.history.isEmpty())
    }
}
