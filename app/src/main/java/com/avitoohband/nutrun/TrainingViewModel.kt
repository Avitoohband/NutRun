package com.avitoohband.nutrun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.NutRunRepository
import com.avitoohband.nutrun.reminders.TrainingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val repository: NutRunRepository?,
    private val preferences: AppPreferences?,
    private val trainingReminderScheduler: TrainingReminderScheduler? = null
) : ViewModel() {
    private fun id(prefix: String) = "$prefix-${UUID.randomUUID()}"
    private var currentUserId: String? = null
    private var persistJob: Job? = null
    private var restoredPayload: String? = null

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
    var lastWorkoutSummary by mutableStateOf<WorkoutSummary?>(null)
        private set
    var suggestionDecision by mutableStateOf(SuggestionDecision.PENDING)
        private set
    var suggestedWeightKg by mutableDoubleStateOf(42.5)
        private set
    var activeWorkoutStartedAtMillis by mutableStateOf<Long?>(null)
        private set
    var restTimerEndAtMillis by mutableStateOf<Long?>(null)
        private set
    var defaultRestTimerSeconds by mutableIntStateOf(90)
        private set

    val exerciseLibrary = builtInExerciseCatalog()
    val supplements = mutableStateListOf<Supplement>().apply { addAll(defaultSupplements()) }
    val sessions = mutableStateListOf<TrainingSession>().apply { addAll(defaultSessions(exerciseLibrary)) }
    val completedExerciseIds = mutableStateMapOf<String, Boolean>()
    val history = mutableStateListOf<String>()
    val workoutHistory = mutableStateListOf<WorkoutRecord>()
    val scheduleOverrides = mutableStateListOf<TrainingScheduleOverride>()
    val activeSetLogs = mutableStateMapOf<String, List<WorkoutSetLog>>()

    init {
        if (repository != null && preferences != null) {
            viewModelScope.launch {
                preferences.session.collectLatest { session ->
                    currentUserId = session.authenticatedUserId
                    restoredPayload = null
                    resetTrainingState()
                    session.authenticatedUserId?.let { userId ->
                        repository.trainingState(userId).collectLatest { state ->
                            state?.payloadJson
                                ?.takeIf { it != restoredPayload }
                                ?.let {
                                    restoredPayload = it
                                    restoreTrainingState(it)
                                }
                        }
                    }
                }
            }
        }
    }

    fun completeRegistration() {
        isAuthenticated = true
    }

    fun setNotificationPermission(granted: Boolean) {
        notificationPermissionGranted = granted
    }

    fun updateUsesMetricUnits(metric: Boolean) {
        if (usesMetricUnits == metric) return
        usesMetricUnits = metric
        persistTrainingState()
    }

    fun toggleSupplement(id: String, checked: Boolean) {
        val index = supplements.indexOfFirst { it.id == id }
        if (index >= 0) {
            supplements[index] = supplements[index].copy(
                completedOn = if (checked) LocalDate.now() else null
            )
        }
        persistTrainingState()
    }

    fun addSupplement(name: String, dose: String, schedule: SupplementSchedule) {
        supplements += Supplement(id("supplement"), name.trim(), dose.trim(), schedule)
        persistTrainingState()
    }

    fun removeSupplement(id: String) {
        if (supplements.removeAll { it.id == id }) {
            persistTrainingState()
        }
    }

    fun updateSupplement(
        id: String,
        name: String,
        dose: String,
        schedule: SupplementSchedule
    ) {
        val index = supplements.indexOfFirst { it.id == id }
        if (index < 0) return
        supplements[index] = supplements[index].copy(
            name = name.trim(),
            dose = dose.trim(),
            schedule = schedule
        )
        persistTrainingState()
    }

    fun addSession(name: String, weekday: DayOfWeek) {
        val session = TrainingSession(id("session"), name.trim(), weekday)
        sessions += session
        selectedSessionId = session.id
        persistTrainingState()
    }

    fun selectSession(id: String) {
        selectedSessionId = id
        persistTrainingState()
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
        if (sessions[index].exercises.any { it.exercise.id == exercise.id }) return
        val target = ExerciseTarget(
            id = id("target"),
            exercise = exercise,
            sets = sets,
            reps = reps,
            weightKg = weightKg,
            durationMinutes = durationMinutes,
            distanceKm = distanceKm
        )
        sessions[index] = sessions[index].copy(exercises = sessions[index].exercises + target)
        persistTrainingState()
    }

    fun removeExerciseFromSelectedSession(targetId: String) {
        val sessionId = selectedSessionId ?: return
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        sessions[index] = sessions[index].copy(
            exercises = sessions[index].exercises.filterNot { it.id == targetId }
        )
        persistTrainingState()
    }

    fun updateSelectedExercise(targetId: String, sets: Int, reps: Int, weightKg: Double?, durationMinutes: Int?, distanceKm: Double?) {
        val sessionId = selectedSessionId ?: return
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        sessions[index] = sessions[index].copy(exercises = sessions[index].exercises.map { target ->
            if (target.id == targetId) target.copy(sets = sets, reps = reps, weightKg = weightKg, durationMinutes = durationMinutes, distanceKm = distanceKm) else target
        })
        persistTrainingState()
    }

    fun startWorkout(sessionId: String) {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return
        if (session.exercises.isEmpty()) return
        val startedAt = System.currentTimeMillis()
        activeWorkoutSessionId = sessionId
        activeWorkoutStartedAtMillis = startedAt
        completedExerciseIds.clear()
        activeSetLogs.clear()
        session.exercises.forEach { target ->
            activeSetLogs[target.id] = (1..target.sets.coerceAtLeast(1)).map { setNumber ->
                WorkoutSetLog(
                    id = "${target.id}:$startedAt:$setNumber",
                    targetId = target.id,
                    exerciseId = target.exercise.id,
                    exerciseName = target.exercise.name,
                    setNumber = setNumber,
                    reps = target.reps.takeIf { target.durationMinutes == null },
                    weightKg = target.weightKg,
                    durationSeconds = target.durationMinutes?.times(60)
                )
            }
        }
        persistTrainingState()
    }

    fun updateWorkoutSet(
        targetId: String,
        setNumber: Int,
        reps: Int?,
        weightKg: Double?,
        durationSeconds: Int?,
        rpe: Double?,
        completed: Boolean
    ) {
        require(reps == null || reps in 0..1_000)
        require(weightKg == null || weightKg in 0.0..2_000.0)
        require(durationSeconds == null || durationSeconds in 0..86_400)
        require(rpe == null || rpe in 1.0..10.0)
        val session = activeSession() ?: return
        val target = session.exercises.firstOrNull { it.id == targetId } ?: return
        val wasCompleted = activeSetLogs[targetId]
            .orEmpty()
            .firstOrNull { it.setNumber == setNumber }
            ?.completed == true
        activeSetLogs[targetId] = activeSetLogs[targetId].orEmpty().map { set ->
            if (set.setNumber == setNumber) {
                set.copy(
                    reps = reps,
                    weightKg = weightKg,
                    durationSeconds = durationSeconds,
                    rpe = rpe,
                    completed = completed
                )
            } else {
                set
            }
        }
        if (completed && target.alternativeGroupId != null) {
            session.exercises
                .filter {
                    it.id != targetId &&
                        it.alternativeGroupId == target.alternativeGroupId
                }
                .forEach { alternative ->
                    activeSetLogs[alternative.id] = activeSetLogs[alternative.id].orEmpty()
                        .map { it.copy(completed = false) }
                    completedExerciseIds[alternative.id] = false
                }
        }
        completedExerciseIds[targetId] =
            activeSetLogs[targetId].orEmpty().isNotEmpty() &&
                activeSetLogs[targetId].orEmpty().all(WorkoutSetLog::completed)
        if (completed && !wasCompleted) startRestTimer()
        persistTrainingState()
    }

    fun previousSets(exerciseId: String): List<WorkoutSetLog> =
        workoutHistory
            .firstOrNull { workout -> workout.sets.any { it.exerciseId == exerciseId } }
            ?.sets
            ?.filter { it.exerciseId == exerciseId && it.completed }
            .orEmpty()

    fun progressionSuggestion(target: ExerciseTarget): ProgressionSuggestion? =
        progressionSuggestion(target, workoutHistory, usesMetricUnits)

    fun progressionSuggestions(
        session: TrainingSession
    ): List<Pair<ExerciseTarget, ProgressionSuggestion>> =
        session.exercises.mapNotNull { target ->
            progressionSuggestion(target)?.let { suggestion -> target to suggestion }
        }

    fun updateDefaultRestTimerSeconds(seconds: Int) {
        defaultRestTimerSeconds = seconds.coerceIn(15, 600)
        persistTrainingState()
    }

    fun startRestTimer(seconds: Int = defaultRestTimerSeconds) {
        restTimerEndAtMillis = System.currentTimeMillis() + seconds.coerceAtLeast(1) * 1_000L
    }

    fun addRestTime(seconds: Int = 30) {
        restTimerEndAtMillis = (restTimerEndAtMillis ?: System.currentTimeMillis()) +
            seconds.coerceAtLeast(1) * 1_000L
    }

    fun skipRestTimer() {
        restTimerEndAtMillis = null
    }

    fun toggleExerciseComplete(targetId: String, completed: Boolean) {
        val session = activeSession()
        val target = session?.exercises?.firstOrNull { it.id == targetId }
        if (completed && target?.alternativeGroupId != null) {
            session.exercises
                .filter {
                    it.id != targetId &&
                        it.alternativeGroupId == target.alternativeGroupId
                }
                .forEach { completedExerciseIds[it.id] = false }
        }
        completedExerciseIds[targetId] = completed
        persistTrainingState()
    }

    fun finishWorkout() {
        val session = activeSession() ?: return
        val completed = session.completedLogicalTargetCount(completedExerciseIds)
        val total = session.logicalTargetCount()
        val finishedAt = System.currentTimeMillis()
        workoutHistory.add(
            0,
            WorkoutRecord(
                id = id("workout"),
                sessionId = session.id,
                sessionName = session.name,
                performedOn = LocalDate.now(),
                startedAtMillis = activeWorkoutStartedAtMillis ?: finishedAt,
                finishedAtMillis = finishedAt,
                completedTargetIds = completedExerciseIds
                    .filterValues { it }
                    .keys,
                completedLogicalTargets = completed,
                totalLogicalTargets = total,
                sets = activeSetLogs.values.flatten()
            )
        )
        lastWorkoutSummary = WorkoutSummary(session.name, completed, total)
        history.add(0, "${formatToday()} | ${session.name} | completed $completed/$total targets")
        activeWorkoutSessionId = null
        activeWorkoutStartedAtMillis = null
        completedExerciseIds.clear()
        activeSetLogs.clear()
        restTimerEndAtMillis = null
        persistTrainingState()
    }

    fun cancelWorkout() {
        activeWorkoutSessionId = null
        activeWorkoutStartedAtMillis = null
        completedExerciseIds.clear()
        activeSetLogs.clear()
        restTimerEndAtMillis = null
        persistTrainingState()
    }

    fun updateWorkoutRecord(updated: WorkoutRecord) {
        val index = workoutHistory.indexOfFirst { it.id == updated.id }
        if (index < 0) return
        val previous = workoutHistory[index]
        val completedTargets = updated.sets
            .groupBy(WorkoutSetLog::targetId)
            .filterValues { sets -> sets.isNotEmpty() && sets.all(WorkoutSetLog::completed) }
            .keys
        val session = sessions.firstOrNull { it.id == updated.sessionId }
        val logicalCompleted = session?.completedLogicalTargetCount(
            completedTargets.associateWith { true }
        ) ?: completedTargets.size.coerceAtMost(updated.totalLogicalTargets)
        val sanitized = updated.copy(
            sessionName = updated.sessionName.trim(),
            completedTargetIds = completedTargets,
            completedLogicalTargets = logicalCompleted
        )
        workoutHistory[index] = sanitized
        replaceLegacyWorkoutSummary(previous, sanitized)
        persistTrainingState()
    }

    fun deleteWorkoutRecord(id: String) {
        val index = workoutHistory.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = workoutHistory.removeAt(index)
        removeLegacyWorkoutSummary(removed)
        persistTrainingState()
    }

    private fun replaceLegacyWorkoutSummary(
        previous: WorkoutRecord,
        updated: WorkoutRecord
    ) {
        val index = history.indexOfFirst {
            it.startsWith("${formatToday(previous.performedOn)} | ${previous.sessionName} |")
        }
        if (index >= 0) {
            history[index] =
                "${formatToday(updated.performedOn)} | ${updated.sessionName} | " +
                    "completed ${updated.completedLogicalTargets}/${updated.totalLogicalTargets} targets"
        }
    }

    private fun removeLegacyWorkoutSummary(workout: WorkoutRecord) {
        val index = history.indexOfFirst {
            it.startsWith("${formatToday(workout.performedOn)} | ${workout.sessionName} |")
        }
        if (index >= 0) history.removeAt(index)
    }

    fun activeSession(): TrainingSession? = sessions.firstOrNull { it.id == activeWorkoutSessionId }

    fun dismissWorkoutSummary() {
        lastWorkoutSummary = null
        persistTrainingState()
    }

    fun selectedSession(): TrainingSession? = sessions.firstOrNull { it.id == selectedSessionId }

    fun sessionsForDate(date: LocalDate): List<TrainingSession> =
        com.avitoohband.nutrun.sessionsForDate(sessions, scheduleOverrides, date)

    fun nextScheduledSession(
        fromDate: LocalDate = LocalDate.now()
    ): Pair<LocalDate, TrainingSession>? =
        (0L..14L).firstNotNullOfOrNull { offset ->
            val date = fromDate.plusDays(offset)
            sessionsForDate(date).firstOrNull()?.let { date to it }
        }

    fun rescheduleSession(sessionId: String, originalDate: LocalDate, scheduledDate: LocalDate) {
        require(!scheduledDate.isBefore(LocalDate.now().minusYears(1)))
        scheduleOverrides.removeAll {
            it.sessionId == sessionId && it.originalDate == originalDate
        }
        scheduleOverrides += TrainingScheduleOverride(
            sessionId = sessionId,
            originalDate = originalDate,
            scheduledDate = scheduledDate
        )
        persistTrainingState()
    }

    fun skipSession(sessionId: String, originalDate: LocalDate) {
        scheduleOverrides.removeAll {
            it.sessionId == sessionId && it.originalDate == originalDate
        }
        scheduleOverrides += TrainingScheduleOverride(
            sessionId = sessionId,
            originalDate = originalDate,
            scheduledDate = null,
            skipped = true
        )
        persistTrainingState()
    }

    fun personalRecords(): List<ExerciseRecord> = exerciseRecords(workoutHistory)

    fun weeklyVolume(weekStart: LocalDate = startOfWeek()): Double =
        weeklyTrainingVolume(workoutHistory, weekStart)

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
        persistTrainingState()
    }

    fun simulateTrialExpiry() {
        trialState = trialState.copy(isForcedFreePlan = true)
    }

    private fun persistTrainingState() {
        val userId = currentUserId ?: return
        val targetRepository = repository ?: return
        val payload = encodeTrainingState(
            supplements = supplements,
            sessions = sessions,
            history = history,
            selectedSessionId = selectedSessionId,
            activeWorkoutSessionId = activeWorkoutSessionId,
            isWorkoutPaused = false,
            completedExerciseIds = completedExerciseIds,
            suggestionDecision = suggestionDecision,
            suggestedWeightKg = suggestedWeightKg,
            workoutHistory = workoutHistory,
            scheduleOverrides = scheduleOverrides,
            activeSetLogs = activeSetLogs,
            activeWorkoutStartedAtMillis = activeWorkoutStartedAtMillis,
            defaultRestTimerSeconds = defaultRestTimerSeconds,
            usesMetricUnits = usesMetricUnits
        )
        restoredPayload = payload
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            targetRepository.saveTrainingState(userId, payload)
            targetRepository.currentTrainingReminderSettings()?.let { settings ->
                trainingReminderScheduler?.schedule(userId, settings)
            }
        }
    }

    private fun restoreTrainingState(payload: String) {
        decodeTrainingState(payload, exerciseLibrary)?.let { restored ->
            supplements.clear()
            supplements.addAll(restored.supplements)
            sessions.clear()
            sessions.addAll(restored.sessions)
            history.clear()
            history.addAll(restored.history)
            selectedSessionId = restored.selectedSessionId
            activeWorkoutSessionId = restored.activeWorkoutSessionId
            completedExerciseIds.clear()
            completedExerciseIds.putAll(restored.completedExerciseIds)
            suggestionDecision = restored.suggestionDecision
            suggestedWeightKg = restored.suggestedWeightKg
            workoutHistory.clear()
            workoutHistory.addAll(restored.workoutHistory)
            scheduleOverrides.clear()
            scheduleOverrides.addAll(restored.scheduleOverrides)
            activeSetLogs.clear()
            activeSetLogs.putAll(restored.activeSetLogs)
            activeWorkoutStartedAtMillis = restored.activeWorkoutStartedAtMillis
            defaultRestTimerSeconds = restored.defaultRestTimerSeconds
            usesMetricUnits = restored.usesMetricUnits
        }
    }

    private fun resetTrainingState() {
        supplements.clear()
        supplements.addAll(defaultSupplements())
        sessions.clear()
        sessions.addAll(defaultSessions(exerciseLibrary))
        history.clear()
        history.addAll(defaultTrainingHistory())
        workoutHistory.clear()
        scheduleOverrides.clear()
        activeSetLogs.clear()
        selectedSessionId = null
        activeWorkoutSessionId = null
        activeWorkoutStartedAtMillis = null
        restTimerEndAtMillis = null
        defaultRestTimerSeconds = 90
        usesMetricUnits = true
        completedExerciseIds.clear()
        lastWorkoutSummary = null
        suggestionDecision = SuggestionDecision.PENDING
        suggestedWeightKg = 42.5
    }
}
