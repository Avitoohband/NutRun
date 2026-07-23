package com.avitoohband.nutrun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.DayOfWeek
import java.time.LocalDate

class PrototypeViewModel : ViewModel() {
    private var nextId = 10
    private fun id(prefix: String) = "$prefix-${nextId++}"

    var isAuthenticated by mutableStateOf(false)
        private set
    var notificationPermissionGranted by mutableStateOf(false)
        private set
    var usesMetricUnits by mutableStateOf(true)
        private set
    var trialState by mutableStateOf(TrialState(LocalDate.now().minusDays(7)))
        private set
    var selectedSessionId by mutableStateOf<String?>(null)
        private set
    var activeWorkoutSessionId by mutableStateOf<String?>(null)
        private set
    var isWorkoutPaused by mutableStateOf(false)
        private set
    var lastWorkoutSummary by mutableStateOf<WorkoutSummary?>(null)
        private set
    var suggestionDecision by mutableStateOf(SuggestionDecision.PENDING)
        private set
    var suggestedWeightKg by mutableStateOf(42.5)
        private set

    val supplements = mutableStateListOf(
        Supplement("supplement-1", "Vitamin D", "2,000 IU", SupplementSchedule(RecurrenceType.DAILY)),
        Supplement("supplement-2", "Omega-3", "1,000 mg", SupplementSchedule(RecurrenceType.DAILY)),
        Supplement("supplement-3", "Vitamin B12", "1,000 mcg", SupplementSchedule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3)),
        Supplement("supplement-4", "Vitamin C", "500 mg", SupplementSchedule(RecurrenceType.EVERY_N_DAYS, intervalDays = 2))
    )

    val exerciseLibrary = listOf(
        Exercise("lat-pulldown", "Lat pulldown", "Strength", "Lats", "Biceps, upper back", "Sit tall, pull the bar toward your upper chest, then return with control.", "Keep shoulders down and stop for sharp pain.", defaultWeightKg = 40.0),
        Exercise("push-up", "Push-up", "Bodyweight", "Chest, triceps", "Front shoulders, core", "Keep a straight line from head to heel and lower with control.", "Elevate your hands if you cannot maintain form."),
        Exercise("goblet-squat", "Goblet squat", "Strength", "Quads, glutes", "Core, calves", "Hold the weight close, sit between your hips, and stand through your feet.", "Use a comfortable depth and keep your knees tracking naturally.", defaultWeightKg = 18.0),
        Exercise("easy-run", "Easy run", "Endurance", "Heart, calves", "Glutes, hamstrings", "Run at a conversational effort with relaxed shoulders.", "Slow down or stop if you feel unwell.", defaultDurationMinutes = 30, defaultDistanceKm = 4.0),
        Exercise("freestyle-swim", "Freestyle swim", "Endurance", "Shoulders, back", "Core, hips", "Rotate through the torso and keep your stroke relaxed.", "Choose a supervised environment appropriate to your ability.", defaultDurationMinutes = 20)
    )

    val sessions = mutableStateListOf(
        TrainingSession("session-1", "Push + Biceps", DayOfWeek.MONDAY, listOf(ExerciseTarget("target-1", exerciseLibrary[1]), ExerciseTarget("target-2", exerciseLibrary[2]))),
        TrainingSession("session-2", "Pull + Triceps", DayOfWeek.WEDNESDAY, listOf(ExerciseTarget("target-3", exerciseLibrary[0]), ExerciseTarget("target-4", exerciseLibrary[1]))),
        TrainingSession("session-3", "HIIT", DayOfWeek.FRIDAY, listOf(ExerciseTarget("target-5", exerciseLibrary[3]))),
        TrainingSession("session-4", "Easy run", DayOfWeek.SATURDAY, listOf(ExerciseTarget("target-6", exerciseLibrary[3])))
    )
    val completedExerciseIds = mutableStateMapOf<String, Boolean>()
    val history = mutableStateListOf(
        "Pull + Triceps - completed",
        "Easy run - 4.2 km",
        "Push + Biceps - completed"
    )

    fun completeRegistration() {
        isAuthenticated = true
    }

    fun setNotificationPermission(granted: Boolean) {
        notificationPermissionGranted = granted
    }

    fun toggleUnits() {
        usesMetricUnits = !usesMetricUnits
    }

    fun toggleSupplement(id: String, checked: Boolean) {
        val index = supplements.indexOfFirst { it.id == id }
        if (index >= 0) supplements[index] = supplements[index].copy(completedToday = checked)
    }

    fun addSupplement(name: String, dose: String, schedule: SupplementSchedule) {
        supplements += Supplement(id("supplement"), name.trim(), dose.trim(), schedule)
    }

    fun addSession(name: String, weekday: DayOfWeek) {
        val session = TrainingSession(id("session"), name.trim(), weekday)
        sessions += session
        selectedSessionId = session.id
    }

    fun selectSession(id: String) {
        selectedSessionId = id
    }

    fun addExerciseToSelectedSession(
        exercise: Exercise,
        sets: Int = exercise.defaultSets,
        reps: Int = exercise.defaultReps,
        weightKg: Double? = exercise.defaultWeightKg,
        durationMinutes: Int? = exercise.defaultDurationMinutes,
        distanceKm: Double? = exercise.defaultDistanceKm
    ) {
        val sessionId = selectedSessionId ?: return
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        val target = ExerciseTarget(id("target"), exercise, sets, reps, weightKg, durationMinutes, distanceKm)
        sessions[index] = sessions[index].copy(exercises = sessions[index].exercises + target)
    }

    fun updateSelectedExercise(targetId: String, sets: Int, reps: Int, weightKg: Double?, durationMinutes: Int?, distanceKm: Double?) {
        val sessionId = selectedSessionId ?: return
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        sessions[index] = sessions[index].copy(exercises = sessions[index].exercises.map { target ->
            if (target.id == targetId) target.copy(sets = sets, reps = reps, weightKg = weightKg, durationMinutes = durationMinutes, distanceKm = distanceKm) else target
        })
    }

    fun startWorkout(sessionId: String) {
        activeWorkoutSessionId = sessionId
        isWorkoutPaused = false
        completedExerciseIds.clear()
    }

    fun toggleExerciseComplete(targetId: String, completed: Boolean) {
        completedExerciseIds[targetId] = completed
    }

    fun pauseOrResumeWorkout() {
        isWorkoutPaused = !isWorkoutPaused
    }

    fun finishWorkout() {
        val session = activeSession() ?: return
        val completed = session.exercises.count { completedExerciseIds[it.id] == true }
        lastWorkoutSummary = WorkoutSummary(session.name, completed, session.exercises.size)
        history.add(0, "${session.name} - completed $completed/${session.exercises.size} exercises")
        activeWorkoutSessionId = null
        completedExerciseIds.clear()
        isWorkoutPaused = false
    }

    fun activeSession(): TrainingSession? = sessions.firstOrNull { it.id == activeWorkoutSessionId }

    fun dismissWorkoutSummary() {
        lastWorkoutSummary = null
    }

    fun selectedSession(): TrainingSession? = sessions.firstOrNull { it.id == selectedSessionId }

    fun decideSuggestion(decision: SuggestionDecision, editedWeightKg: Double = suggestedWeightKg) {
        suggestionDecision = decision
        suggestedWeightKg = editedWeightKg
        if (decision == SuggestionDecision.ACCEPTED) {
            sessions.indices.forEach { index ->
                sessions[index] = sessions[index].copy(
                    exercises = sessions[index].exercises.map { target ->
                        if (target.exercise.id == "lat-pulldown") target.copy(weightKg = editedWeightKg) else target
                    }
                )
            }
            history.add(0, "Lat pulldown progression accepted: ${displayWeight(editedWeightKg, usesMetricUnits)}")
        }
    }

    fun simulateTrialExpiry() {
        trialState = trialState.copy(isForcedFreePlan = true)
    }
}
