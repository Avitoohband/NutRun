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
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingStateEntity
import com.avitoohband.nutrun.reminders.ReminderRescheduleRecoveryScheduler
import com.avitoohband.nutrun.reminders.SupplementReminderScheduler
import com.avitoohband.nutrun.reminders.TrainingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SupplementReminderConfig(
    val enabled: Boolean,
    val minute: Int
)

internal interface TrainingViewModelRuntime {
    val session: Flow<SessionPreferences>
    fun trainingState(userId: String): Flow<TrainingStateEntity?>
    suspend fun currentUserId(): String?
    suspend fun saveTrainingState(userId: String, payload: String)
    suspend fun currentTrainingReminderSettings(userId: String): TrainingReminderSettingsEntity?
    suspend fun currentSupplementReminderSettings(userId: String): SupplementReminderSettingsEntity?
    fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity)
    suspend fun scheduleSupplement(userId: String, settings: SupplementReminderSettingsEntity)
}

private class ProductionTrainingViewModelRuntime(
    private val repository: NutRunRepository,
    private val preferences: AppPreferences,
    private val trainingReminderScheduler: TrainingReminderScheduler?,
    private val reminderRescheduleRecoveryScheduler: ReminderRescheduleRecoveryScheduler?,
    private val supplementReminderSchedulingCoordinator: SupplementReminderSchedulingCoordinator?,
    private val supplementReminderScheduler: SupplementReminderScheduler?
) : TrainingViewModelRuntime {
    override val session: Flow<SessionPreferences> = preferences.session

    override fun trainingState(userId: String): Flow<TrainingStateEntity?> = repository.trainingState(userId)

    override suspend fun currentUserId(): String? = preferences.currentSession().authenticatedUserId

    override suspend fun saveTrainingState(userId: String, payload: String) {
        repository.saveTrainingState(userId, payload)
    }

    override suspend fun currentTrainingReminderSettings(
        userId: String
    ): TrainingReminderSettingsEntity? =
        if (currentUserId() == userId) repository.currentTrainingReminderSettings() else null

    override suspend fun currentSupplementReminderSettings(
        userId: String
    ): SupplementReminderSettingsEntity? =
        if (currentUserId() == userId) repository.currentSupplementReminderSettings() else null

    override fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity) {
        trainingReminderScheduler?.schedule(userId, settings)
    }

    override suspend fun scheduleSupplement(userId: String, settings: SupplementReminderSettingsEntity) {
        supplementReminderSchedulingCoordinator?.reschedule(userId)
            ?: supplementReminderScheduler?.let { scheduler ->
                rescheduleSupplementReminderWork(
                    userId,
                    settings,
                    scheduler,
                    reminderRescheduleRecoveryScheduler
                )
            }
    }
}

@HiltViewModel
class TrainingViewModel private constructor(
    private val runtime: TrainingViewModelRuntime?,
    private val coroutineScope: CoroutineScope?,
    @Suppress("UNUSED_PARAMETER") private val runtimeConstructor: Boolean
) : ViewModel() {
    @Inject
    constructor(
        repository: NutRunRepository?,
        preferences: AppPreferences?,
        trainingReminderScheduler: TrainingReminderScheduler? = null,
        reminderRescheduleRecoveryScheduler: ReminderRescheduleRecoveryScheduler? = null,
        supplementReminderSchedulingCoordinator: SupplementReminderSchedulingCoordinator? = null,
        supplementReminderScheduler: SupplementReminderScheduler? = null
    ) : this(
        runtime = if (repository != null && preferences != null) {
            ProductionTrainingViewModelRuntime(
                repository,
                preferences,
                trainingReminderScheduler,
                reminderRescheduleRecoveryScheduler,
                supplementReminderSchedulingCoordinator,
                supplementReminderScheduler
            )
        } else {
            null
        },
        coroutineScope = null,
        runtimeConstructor = true
    )

    internal constructor(
        runtime: TrainingViewModelRuntime,
        coroutineScope: CoroutineScope
    ) : this(runtime, coroutineScope, true)

    private val modelScope: CoroutineScope get() = coroutineScope ?: viewModelScope
    private fun id(prefix: String) = "$prefix-${UUID.randomUUID()}"
    private var currentUserId: String? = null
    private var persistJob: Job? = null
    private var supplementReschedulePending = false
    private var restoredPayload: String? = null
    private var restoredUserId: String? = null

    var supplementReminderReadyAccountId by mutableStateOf<String?>(null)
        private set
    var supplementReminderUpdatesReady by mutableStateOf(runtime == null)
        private set
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
        runtime?.let { runtime ->
            modelScope.launch {
                runtime.session.collectLatest { session ->
                    persistJob?.cancel()
                    supplementReschedulePending = false
                    currentUserId = session.authenticatedUserId
                    restoredPayload = null
                    restoredUserId = null
                    supplementReminderReadyAccountId = null
                    supplementReminderUpdatesReady = false
                    resetTrainingState()
                    session.authenticatedUserId?.let { userId ->
                        runtime.trainingState(userId).collectLatest { state ->
                            state?.payloadJson
                                ?.takeIf { it != restoredPayload }
                                ?.let {
                                    restoredPayload = it
                                    restoreTrainingState(it)
                                }
                            if (currentUserId == userId) {
                                restoredUserId = userId
                                supplementReminderReadyAccountId = userId
                                supplementReminderUpdatesReady = true
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
            persistTrainingState(rescheduleSupplementReminders = true)
        }
    }

    fun addSupplement(
        name: String,
        dose: String,
        schedule: SupplementSchedule,
        reminderEnabled: Boolean = true,
        reminderMinute: Int = 480
    ) {
        requireValidReminderMinute(reminderMinute)
        supplements += Supplement(
            id = id("supplement"),
            name = name.trim(),
            dose = dose.trim(),
            schedule = schedule,
            reminderEnabled = reminderEnabled,
            reminderMinute = reminderMinute
        )
        persistTrainingState(rescheduleSupplementReminders = true)
    }

    fun removeSupplement(id: String) {
        if (supplements.removeAll { it.id == id }) {
            persistTrainingState(rescheduleSupplementReminders = true)
        }
    }

    fun updateSupplement(
        id: String,
        name: String,
        dose: String,
        schedule: SupplementSchedule
    ) {
        val existing = supplements.firstOrNull { it.id == id } ?: return
        updateSupplement(
            id = id,
            name = name,
            dose = dose,
            schedule = schedule,
            reminderEnabled = existing.reminderEnabled,
            reminderMinute = existing.reminderMinute
        )
    }

    fun updateSupplement(
        id: String,
        name: String,
        dose: String,
        schedule: SupplementSchedule,
        reminderEnabled: Boolean,
        reminderMinute: Int
    ) {
        requireValidReminderMinute(reminderMinute)
        val index = supplements.indexOfFirst { it.id == id }
        if (index < 0) return
        supplements[index] = supplements[index].copy(
            name = name.trim(),
            dose = dose.trim(),
            schedule = schedule,
            reminderEnabled = reminderEnabled,
            reminderMinute = reminderMinute
        )
        persistTrainingState(rescheduleSupplementReminders = true)
    }

    fun updateSupplementReminder(id: String, enabled: Boolean, minute: Int) {
        requireValidReminderMinute(minute)
        val index = supplements.indexOfFirst { it.id == id }
        if (index < 0) return
        supplements[index] = supplements[index].copy(
            reminderEnabled = enabled,
            reminderMinute = minute
        )
        persistTrainingState(rescheduleSupplementReminders = true)
    }

    fun setAllSupplementReminders(enabled: Boolean) {
        supplements.indices.forEach { index ->
            supplements[index] = supplements[index].copy(reminderEnabled = enabled)
        }
        persistTrainingState(rescheduleSupplementReminders = true)
    }

    internal fun updateSupplementReminders(configurations: Map<String, SupplementReminderConfig>) {
        configurations.values.forEach { requireValidReminderMinute(it.minute) }
        supplements.indices.forEach { index ->
            configurations[supplements[index].id]?.let { config ->
                supplements[index] = supplements[index].copy(
                    reminderEnabled = config.enabled,
                    reminderMinute = config.minute
                )
            }
        }
        persistTrainingState(rescheduleSupplementReminders = true)
    }

    suspend fun persistSupplementReminders(
        accountId: String,
        configurations: Map<String, SupplementReminderConfig>
    ): NotificationSettingsSaveResult {
        configurations.values.forEach { requireValidReminderMinute(it.minute) }
        val targetRuntime = runtime ?: return NotificationSettingsSaveResult.Failed(
            expectedAccountId = accountId,
            stage = NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
            message = "Durable persistence is unavailable."
        )
        if (currentUserId != accountId) {
            return NotificationSettingsSaveResult.AccountChanged(
                accountId,
                currentUserId,
                NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS
            )
        }
        if (restoredUserId != accountId || supplementReminderReadyAccountId != accountId) {
            return NotificationSettingsSaveResult.NotReady(accountId)
        }
        val updatedSupplements = supplements.map { supplement ->
            configurations[supplement.id]?.let { config ->
                supplement.copy(
                    reminderEnabled = config.enabled,
                    reminderMinute = config.minute
                )
            } ?: supplement
        }
        val payload = currentTrainingPayload(updatedSupplements)
        return try {
            targetRuntime.saveTrainingState(accountId, payload)
            val actualAccountId = targetRuntime.currentUserId()
            if (actualAccountId != accountId || currentUserId != accountId) {
                NotificationSettingsSaveResult.AccountChanged(
                    accountId,
                    actualAccountId,
                    NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS
                )
            } else {
                supplements.clear()
                supplements.addAll(updatedSupplements)
                restoredPayload = payload
                modelScope.launch {
                    runCatching { rescheduleSupplementReminders(accountId, targetRuntime) }
                }
                NotificationSettingsSaveResult.Success(accountId)
            }
        } catch (error: Exception) {
            if (error is CancellationException && !currentCoroutineContext().isActive) throw error
            val actualAccountId = runCatching { targetRuntime.currentUserId() }.getOrNull()
            if (actualAccountId != accountId || currentUserId != accountId) {
                NotificationSettingsSaveResult.AccountChanged(
                    accountId,
                    actualAccountId,
                    NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS
                )
            } else {
                NotificationSettingsSaveResult.Failed(
                    expectedAccountId = accountId,
                    stage = NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                    message = error.message ?: "Training state persistence failed."
                )
            }
        }
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

    private fun persistTrainingState(rescheduleSupplementReminders: Boolean = false) {
        val userId = currentUserId ?: return
        val targetRuntime = runtime ?: return
        if (restoredUserId != userId) return
        val payload = currentTrainingPayload(supplements)
        restoredPayload = payload
        supplementReschedulePending = supplementReschedulePending || rescheduleSupplementReminders
        persistJob?.cancel()
        persistJob = modelScope.launch {
            targetRuntime.saveTrainingState(userId, payload)
            if (targetRuntime.currentUserId() != userId) return@launch
            targetRuntime.currentTrainingReminderSettings(userId)?.let { settings ->
                targetRuntime.scheduleTraining(userId, settings)
            }
            if (supplementReschedulePending) {
                rescheduleSupplementReminders(userId, targetRuntime)
                supplementReschedulePending = false
            }
        }
    }

    private fun currentTrainingPayload(supplements: List<Supplement>): String =
        encodeTrainingState(
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

    private suspend fun rescheduleSupplementReminders(
        userId: String,
        targetRuntime: TrainingViewModelRuntime
    ) {
        val settings = targetRuntime.currentSupplementReminderSettings(userId)
            ?: SupplementReminderSettingsEntity(userId = userId)
        if (targetRuntime.currentUserId() == userId) targetRuntime.scheduleSupplement(userId, settings)
    }

    private fun requireValidReminderMinute(minute: Int) {
        require(minute in 0 until 24 * 60)
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
