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
import com.avitoohband.nutrun.data.UserProfileEntity
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingStateEntity
import com.avitoohband.nutrun.reminders.ReminderRescheduleRecoveryScheduler
import com.avitoohband.nutrun.reminders.ReminderSystem
import com.avitoohband.nutrun.reminders.SupplementReminderScheduler
import com.avitoohband.nutrun.reminders.TrainingReminderScheduler
import com.avitoohband.nutrun.reminders.rescheduleReminderSystemsWithRecovery
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun resolveMetricUnits(profile: UserProfileEntity?, legacyMetric: Boolean?): Boolean =
    profile?.unitSystem?.let { UnitSystem.valueOf(it) == UnitSystem.METRIC }
        ?: legacyMetric
        ?: true

data class SupplementReminderConfig(
    val enabled: Boolean,
    val minute: Int
)
sealed interface TrainingMutationResult {
    data object Success : TrainingMutationResult
    data object NotReady : TrainingMutationResult
    data object ActiveWorkoutConflict : TrainingMutationResult
    data class ValidationError(val message: String) : TrainingMutationResult
}

data class CustomExerciseDraft(
    val name: String,
    val category: String = "Custom",
    val primaryMuscles: String = "",
    val secondaryMuscles: String = "",
    val instructions: String = "",
    val safetyNote: String = "",
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val defaultWeightKg: Double? = null,
    val defaultDurationMinutes: Int? = null,
    val defaultDistanceKm: Double? = null
)

private data class TrainingMutationSnapshot(
    val workoutTemplates: List<WorkoutTemplate>,
    val weeklyDayPlans: List<WeeklyDayPlan>,
    val customExercises: List<Exercise>,
    val scheduleOverrides: List<TrainingScheduleOverride>,
    val selectedSessionId: String?,
    val activeWorkoutSessionId: String?,
    val activeWorkoutStartedAtMillis: Long?,
    val completedExerciseIds: Map<String, Boolean>,
    val activeSetLogs: Map<String, List<WorkoutSetLog>>,
    val restTimerEndAtMillis: Long?
)


private data class TrainingPersistenceOperation(
    val accountId: String,
    val payload: String,
    val repositoryCompleted: Boolean = false,
    val supplementRescheduleCompleted: Boolean = false
)

internal interface TrainingViewModelRuntime {
    val session: Flow<SessionPreferences>
    fun trainingState(userId: String): Flow<TrainingStateEntity?>
    fun profile(userId: String): Flow<UserProfileEntity?> = flowOf(null)
    suspend fun currentUserId(): String?
    suspend fun saveTrainingState(userId: String, payload: String)
    suspend fun currentTrainingReminderSettings(userId: String): TrainingReminderSettingsEntity?
    suspend fun currentSupplementReminderSettings(userId: String): SupplementReminderSettingsEntity?
    fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity)
    suspend fun scheduleSupplement(userId: String, settings: SupplementReminderSettingsEntity)
    suspend fun scheduleRecovery(userId: String, system: ReminderSystem)
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
    override fun profile(userId: String): Flow<UserProfileEntity?> = repository.profileEntity(userId)


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

    override suspend fun scheduleRecovery(userId: String, system: ReminderSystem) {
        reminderRescheduleRecoveryScheduler?.schedule(userId, setOf(system))
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
    private val persistenceMutex = Mutex()
    private var persistenceGeneration = 0L
    private var legacyUsesMetricUnits: Boolean? = null
    private var supplementReschedulePending = false
    private var restoredPayload: String? = null
    var profileUnitReadyAccountId by mutableStateOf<String?>(null)
        private set
    private var restoredUserId: String? = null
    private var persistenceOperation: TrainingPersistenceOperation? = null

    var supplementReminderReadyAccountId by mutableStateOf<String?>(null)
        private set
    var supplementReminderUpdatesReady by mutableStateOf(runtime == null)
        private set
    val trainingMutationsReady: Boolean
        get() = runtime == null || (
            supplementReminderUpdatesReady && profileUnitReadyAccountId == currentUserId &&
                currentUserId != null &&
                restoredUserId == currentUserId
            )

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

    var mutationError by mutableStateOf<String?>(null)
        private set

    fun dismissMutationError() {
        mutationError = null
    }

    private val builtInExercises = builtInExerciseCatalog()
    private val defaultProgram = defaultTrainingProgram(builtInExercises)
    val supplements = mutableStateListOf<Supplement>().apply { addAll(defaultSupplements()) }
    val customExercises = mutableStateListOf<Exercise>()
    val exerciseLibrary: List<Exercise>
        get() = builtInExercises + customExercises
    val workoutTemplates = mutableStateListOf<WorkoutTemplate>().apply {
        addAll(defaultProgram.templates)
    }
    val weeklyDayPlans = mutableStateListOf<WeeklyDayPlan>().apply {
        addAll(defaultProgram.dayPlans)
    }
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
                    persistenceGeneration += 1
                    supplementReschedulePending = false
                    currentUserId = session.authenticatedUserId
                    restoredPayload = null
                    mutationError = null
                    restoredUserId = null
                    persistenceOperation = null
                    supplementReminderReadyAccountId = null
                    supplementReminderUpdatesReady = false
                    profileUnitReadyAccountId = null
                    resetTrainingState()
                    session.authenticatedUserId?.let { userId ->
                        runtime.trainingState(userId)
                            .combine(runtime.profile(userId)) { state, profile -> state to profile }
                            .collectLatest { (state, profile) ->
                            state?.payloadJson?.let {
                                if (it != restoredPayload) {
                                    persistJob?.cancel()
                                    persistenceGeneration += 1
                                    persistenceOperation = null
                                    supplementReschedulePending = false
                                    mutationError = null
                                    restoredPayload = it
                                    restoreTrainingState(it)
                                }
                            }
                            if (currentUserId == userId) {
                            usesMetricUnits = resolveMetricUnits(profile, legacyUsesMetricUnits)
                                restoredUserId = userId
                                supplementReminderReadyAccountId = userId
                                supplementReminderUpdatesReady = true
                            }
                                profileUnitReadyAccountId = userId
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

    fun toggleSupplement(id: String, checked: Boolean) {
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
        supplements.indices.forEach { index ->
            supplements[index] = supplements[index].copy(reminderEnabled = enabled)
        }
        persistTrainingState(rescheduleSupplementReminders = true)
    }

    internal fun updateSupplementReminders(configurations: Map<String, SupplementReminderConfig>) {
        if (!trainingMutationsReady) return
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
        persistenceGeneration += 1
        val durableGeneration = persistenceGeneration
        persistJob?.cancel()
        var repositoryCompleted = false
        return try {
            persistenceMutex.withLock {
                val lockedAccountId = targetRuntime.currentUserId()
                if (lockedAccountId != accountId || currentUserId != accountId) {
                    return@withLock NotificationSettingsSaveResult.AccountChanged(
                        accountId,
                        lockedAccountId,
                        NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS
                    )
                }
                if (
                    restoredUserId != accountId ||
                    supplementReminderReadyAccountId != accountId
                ) {
                    return@withLock NotificationSettingsSaveResult.NotReady(accountId)
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
                var operation = persistenceOperationFor(accountId, payload)
                if (!operation.repositoryCompleted) {
                    val previousRestoredPayload = restoredPayload
                    restoredPayload = payload
                    try {
                        targetRuntime.saveTrainingState(accountId, payload)
                    } catch (error: Exception) {
                        if (restoredPayload == payload) {
                            restoredPayload = previousRestoredPayload
                        }
                        throw error
                    }
                    operation = operation.copy(repositoryCompleted = true)
                    retainPersistenceOperation(operation)
                }
                repositoryCompleted = operation.repositoryCompleted

                val persistedAccountId = targetRuntime.currentUserId()
                if (persistedAccountId != accountId || currentUserId != accountId) {
                    return@withLock NotificationSettingsSaveResult.AccountChanged(
                        accountId,
                        persistedAccountId,
                        NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                        setOf(NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS)
                    )
                }

                applySupplementReminderConfigurations(configurations)
                if (!operation.supplementRescheduleCompleted) {
                    rescheduleSupplementReminders(accountId, targetRuntime)
                    val scheduledAccountId = targetRuntime.currentUserId()
                    if (scheduledAccountId != accountId || currentUserId != accountId) {
                        return@withLock NotificationSettingsSaveResult.AccountChanged(
                            accountId,
                            scheduledAccountId,
                            NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                            setOf(NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS)
                        )
                    }
                    operation = operation.copy(supplementRescheduleCompleted = true)
                    retainPersistenceOperation(operation)
                }
                if (persistenceGeneration == durableGeneration) {
                    supplementReschedulePending = false
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
                    NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                    if (repositoryCompleted) {
                        setOf(NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS)
                    } else {
                        emptySet()
                    }
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

    private fun applySupplementReminderConfigurations(
        configurations: Map<String, SupplementReminderConfig>
    ) {
        supplements.indices.forEach { index ->
            configurations[supplements[index].id]?.let { config ->
                supplements[index] = supplements[index].copy(
                    reminderEnabled = config.enabled,
                    reminderMinute = config.minute
                )
            }
        }
    }

    private fun persistenceOperationFor(
        accountId: String,
        payload: String
    ): TrainingPersistenceOperation {
        val current = persistenceOperation
        if (current?.accountId == accountId && current.payload == payload) return current
        return TrainingPersistenceOperation(accountId, payload).also {
            persistenceOperation = it
        }
    }

    private fun retainPersistenceOperation(operation: TrainingPersistenceOperation) {
        val current = persistenceOperation
        if (
            currentUserId == operation.accountId &&
            current?.accountId == operation.accountId &&
            current.payload == operation.payload
        ) {
            persistenceOperation = operation
        }
    }

    fun createWorkout(name: String): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return TrainingMutationResult.ValidationError("Workout name cannot be blank.")
        }
        val snapshot = trainingMutationSnapshot()
        val template = WorkoutTemplate.userCreated(trimmedName)
        workoutTemplates += template
        selectedSessionId = template.id
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun renameWorkout(templateId: String, name: String): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return TrainingMutationResult.ValidationError("Workout name cannot be blank.")
        }
        val index = workoutTemplates.indexOfFirst { it.id == templateId }
        if (index < 0) return TrainingMutationResult.ValidationError("Workout not found.")
        val snapshot = trainingMutationSnapshot()
        workoutTemplates[index] = workoutTemplates[index].copy(name = trimmedName)
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun deleteWorkout(
        templateId: String,
        today: LocalDate = LocalDate.now()
    ): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        if (activeWorkoutSessionId == templateId) return TrainingMutationResult.ActiveWorkoutConflict
        if (workoutTemplates.none { it.id == templateId }) {
            return TrainingMutationResult.ValidationError("Workout not found.")
        }
        val snapshot = trainingMutationSnapshot()
        workoutTemplates.removeAll { it.id == templateId }
        weeklyDayPlans.indices.forEach { index ->
            val plan = weeklyDayPlans[index]
            weeklyDayPlans[index] = plan.copy(templateIds = plan.templateIds.filterNot { it == templateId })
        }
        scheduleOverrides.removeAll {
            it.sessionId == templateId && !it.originalDate.isBefore(today)
        }
        if (selectedSessionId == templateId) selectedSessionId = null
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun replaceAssignments(
        day: DayOfWeek,
        templateIds: List<String>
    ): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        val distinctIds = templateIds.distinct()
        val knownIds = workoutTemplates.map(WorkoutTemplate::id).toSet()
        if (distinctIds.any { it !in knownIds }) {
            return TrainingMutationResult.ValidationError("One or more workouts were not found.")
        }
        val snapshot = trainingMutationSnapshot()
        val plans = replaceDayAssignments(weeklyDayPlans, day, distinctIds)
        weeklyDayPlans.clear()
        weeklyDayPlans.addAll(plans)
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun setRestDay(day: DayOfWeek): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        val snapshot = trainingMutationSnapshot()
        val plans = markRestDay(weeklyDayPlans, day)
        weeklyDayPlans.clear()
        weeklyDayPlans.addAll(plans)
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun createCustomExerciseAndAdd(
        templateId: String,
        draft: CustomExerciseDraft
    ): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        val trimmedName = draft.name.trim()
        if (trimmedName.isEmpty()) {
            return TrainingMutationResult.ValidationError("Exercise name cannot be blank.")
        }
        if (draft.defaultSets !in 1..20) {
            return TrainingMutationResult.ValidationError("Sets must be between 1 and 20.")
        }
        if (draft.defaultReps < 1) {
            return TrainingMutationResult.ValidationError("Repetitions must be at least 1.")
        }
        if (draft.defaultWeightKg?.let { !it.isFinite() || it < 0.0 } == true) {
            return TrainingMutationResult.ValidationError("Weight must be finite and non-negative.")
        }
        if (draft.defaultDurationMinutes?.let { it <= 0 } == true) {
            return TrainingMutationResult.ValidationError("Duration must be greater than zero.")
        }
        if (draft.defaultDistanceKm?.let { !it.isFinite() || it <= 0.0 } == true) {
            return TrainingMutationResult.ValidationError("Distance must be finite and greater than zero.")
        }
        if (exerciseLibrary.any { it.name.trim().equals(trimmedName, ignoreCase = true) }) {
            return TrainingMutationResult.ValidationError("An exercise with this name already exists.")
        }
        val templateIndex = workoutTemplates.indexOfFirst { it.id == templateId }
        if (templateIndex < 0) return TrainingMutationResult.ValidationError("Workout not found.")

        val snapshot = trainingMutationSnapshot()
        val exercise = Exercise(
            id = id("exercise"),
            name = trimmedName,
            category = draft.category.trim().ifEmpty { "Custom" },
            primaryMuscles = draft.primaryMuscles.trim(),
            secondaryMuscles = draft.secondaryMuscles.trim(),
            instructions = draft.instructions.trim(),
            safetyNote = draft.safetyNote.trim(),
            defaultSets = draft.defaultSets,
            defaultReps = draft.defaultReps,
            defaultWeightKg = draft.defaultWeightKg,
            defaultDurationMinutes = draft.defaultDurationMinutes,
            defaultDistanceKm = draft.defaultDistanceKm
        )
        customExercises += exercise
        val target = ExerciseTarget(id = id("target"), exercise = exercise)
        workoutTemplates[templateIndex] = workoutTemplates[templateIndex].copy(
            exercises = workoutTemplates[templateIndex].exercises + target
        )
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun updateTargetSets(
        templateId: String,
        targetId: String,
        sets: Int
    ): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        if (sets !in 1..20) {
            return TrainingMutationResult.ValidationError("Sets must be between 1 and 20.")
        }
        val templateIndex = workoutTemplates.indexOfFirst { it.id == templateId }
        if (templateIndex < 0) return TrainingMutationResult.ValidationError("Workout not found.")
        if (workoutTemplates[templateIndex].exercises.none { it.id == targetId }) {
            return TrainingMutationResult.ValidationError("Exercise target not found.")
        }
        val snapshot = trainingMutationSnapshot()
        workoutTemplates[templateIndex] = workoutTemplates[templateIndex].copy(
            exercises = workoutTemplates[templateIndex].exercises.map { target ->
                if (target.id == targetId) target.copy(sets = sets) else target
            }
        )
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun addSession(name: String, weekday: DayOfWeek) {
        if (!trainingMutationsReady) return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val snapshot = trainingMutationSnapshot()
        val template = WorkoutTemplate.userCreated(trimmedName)
        workoutTemplates += template
        val existingIds = weeklyDayPlans.firstOrNull { it.weekday == weekday }?.templateIds.orEmpty()
        val plans = replaceDayAssignments(weeklyDayPlans, weekday, existingIds + template.id)
        weeklyDayPlans.clear()
        weeklyDayPlans.addAll(plans)
        selectedSessionId = template.id
        persistTrainingState(rollbackSnapshot = snapshot)
    }

    fun selectSession(id: String) {
        if (!trainingMutationsReady || workoutTemplates.none { it.id == id }) return
        val snapshot = trainingMutationSnapshot()
        selectedSessionId = id
        persistTrainingState(rollbackSnapshot = snapshot)
    }

    fun addExerciseToSelectedSession(
        exercise: Exercise,
        sets: Int = exercise.defaultSets,
        reps: Int = exercise.defaultReps,
        weightKg: Double? = exercise.defaultWeightKg,
        durationMinutes: Int? = exercise.defaultDurationMinutes,
        distanceKm: Double? = exercise.defaultDistanceKm
    ): TrainingMutationResult {
        if (!trainingMutationsReady) return TrainingMutationResult.NotReady
        if (sets !in 1..20) {
            return TrainingMutationResult.ValidationError("Sets must be between 1 and 20.")
        }
        val templateId = selectedSessionId
            ?: return TrainingMutationResult.ValidationError("No workout is selected.")
        val canonicalExercise = exerciseLibrary.firstOrNull { it.id == exercise.id }
            ?: return TrainingMutationResult.ValidationError("Exercise is not in the current library.")
        val index = workoutTemplates.indexOfFirst { it.id == templateId }
        if (index < 0) return TrainingMutationResult.ValidationError("Workout not found.")
        if (workoutTemplates[index].exercises.any { it.exercise.id == canonicalExercise.id }) {
            return TrainingMutationResult.ValidationError("Exercise is already in this workout.")
        }
        val snapshot = trainingMutationSnapshot()
        val target = ExerciseTarget(
            id = id("target"),
            exercise = canonicalExercise,
            sets = sets,
            reps = reps,
            weightKg = weightKg,
            durationMinutes = durationMinutes,
            distanceKm = distanceKm
        )
        workoutTemplates[index] = workoutTemplates[index].copy(
            exercises = workoutTemplates[index].exercises + target
        )
        persistTrainingState(rollbackSnapshot = snapshot)
        return TrainingMutationResult.Success
    }

    fun removeExerciseFromSelectedSession(targetId: String) {
        if (!trainingMutationsReady) return
        val templateId = selectedSessionId ?: return
        val index = workoutTemplates.indexOfFirst { it.id == templateId }
        if (index < 0 || workoutTemplates[index].exercises.none { it.id == targetId }) return
        val snapshot = trainingMutationSnapshot()
        workoutTemplates[index] = workoutTemplates[index].copy(
            exercises = workoutTemplates[index].exercises.filterNot { it.id == targetId }
        )
        persistTrainingState(rollbackSnapshot = snapshot)
    }

    fun updateSelectedExercise(
        targetId: String,
        sets: Int,
        reps: Int,
        weightKg: Double?,
        durationMinutes: Int?,
        distanceKm: Double?
    ) {
        if (!trainingMutationsReady || sets !in 1..20) return
        val templateId = selectedSessionId ?: return
        val index = workoutTemplates.indexOfFirst { it.id == templateId }
        if (index < 0 || workoutTemplates[index].exercises.none { it.id == targetId }) return
        val snapshot = trainingMutationSnapshot()
        workoutTemplates[index] = workoutTemplates[index].copy(
            exercises = workoutTemplates[index].exercises.map { target ->
                if (target.id == targetId) {
                    target.copy(
                        sets = sets,
                        reps = reps,
                        weightKg = weightKg,
                        durationMinutes = durationMinutes,
                        distanceKm = distanceKm
                    )
                } else {
                    target
                }
            }
        )
        persistTrainingState(rollbackSnapshot = snapshot)
    }

    fun startWorkout(sessionId: String) {
        if (!trainingMutationsReady) return
        val session = workoutTemplates.firstOrNull { it.id == sessionId } ?: return
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
        if (!trainingMutationsReady) return
        require(reps == null || reps in 0..1_000)
        require(weightKg == null || weightKg in 0.0..2_000.0)
        require(durationSeconds == null || durationSeconds in 0..86_400)
        require(rpe == null || rpe in 0.0..10.0)
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
        if (!trainingMutationsReady) return
        defaultRestTimerSeconds = seconds.coerceIn(15, 600)
        persistTrainingState()
    }

    fun startRestTimer(seconds: Int = defaultRestTimerSeconds) {
        if (!trainingMutationsReady) return
        restTimerEndAtMillis = System.currentTimeMillis() + seconds.coerceAtLeast(1) * 1_000L
    }

    fun addRestTime(seconds: Int = 30) {
        if (!trainingMutationsReady) return
        restTimerEndAtMillis = (restTimerEndAtMillis ?: System.currentTimeMillis()) +
            seconds.coerceAtLeast(1) * 1_000L
    }

    fun skipRestTimer() {
        if (!trainingMutationsReady) return
        restTimerEndAtMillis = null
    }

    fun toggleExerciseComplete(targetId: String, completed: Boolean) {
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
        activeWorkoutSessionId = null
        activeWorkoutStartedAtMillis = null
        completedExerciseIds.clear()
        activeSetLogs.clear()
        restTimerEndAtMillis = null
        persistTrainingState()
    }

    fun updateWorkoutRecord(updated: WorkoutRecord) {
        if (!trainingMutationsReady) return
        val index = workoutHistory.indexOfFirst { it.id == updated.id }
        if (index < 0) return
        val previous = workoutHistory[index]
        val completedTargets = updated.sets
            .groupBy(WorkoutSetLog::targetId)
            .filterValues { sets -> sets.isNotEmpty() && sets.all(WorkoutSetLog::completed) }
            .keys
        val session = workoutTemplates.firstOrNull { it.id == updated.sessionId }
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
        if (!trainingMutationsReady) return
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

    fun activeSession(): TrainingSession? = activeWorkoutSessionId?.let(::compatibilitySession)
    private fun compatibilitySession(templateId: String): TrainingSession? {
        val template = workoutTemplates.firstOrNull { it.id == templateId } ?: return null
        val weekday = weeklyDayPlans.firstOrNull {
            !it.isRestDay && templateId in it.templateIds
        }?.weekday ?: DayOfWeek.MONDAY
        return TrainingSession(
            id = template.id,
            name = template.name,
            weekday = weekday,
            exercises = template.exercises,
            guidance = template.guidance
        )
    }


    fun dismissWorkoutSummary() {
        if (!trainingMutationsReady) return
        lastWorkoutSummary = null
        persistTrainingState()
    }

    fun selectedSession(): TrainingSession? = selectedSessionId?.let(::compatibilitySession)

    fun sessionsForDate(date: LocalDate): List<TrainingSession> =
        templatesForDate(workoutTemplates, weeklyDayPlans, scheduleOverrides, date).map { template ->
            TrainingSession(
                id = template.id,
                name = template.name,
                weekday = date.dayOfWeek,
                exercises = template.exercises,
                guidance = template.guidance
            )
        }

    fun nextScheduledSession(
        fromDate: LocalDate = LocalDate.now()
    ): Pair<LocalDate, TrainingSession>? =
        (0L..14L).firstNotNullOfOrNull { offset ->
            val date = fromDate.plusDays(offset)
            sessionsForDate(date).firstOrNull()?.let { date to it }
        }

    fun rescheduleSession(sessionId: String, originalDate: LocalDate, scheduledDate: LocalDate) {
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
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
        if (!trainingMutationsReady) return
        suggestionDecision = decision
        suggestedWeightKg = editedWeightKg
        if (decision == SuggestionDecision.ACCEPTED) {
            workoutTemplates.indices.forEach { index ->
                workoutTemplates[index] = workoutTemplates[index].copy(
                    exercises = workoutTemplates[index].exercises.map { target ->
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

    private fun trainingMutationSnapshot() = TrainingMutationSnapshot(
        workoutTemplates = workoutTemplates.toList(),
        weeklyDayPlans = weeklyDayPlans.toList(),
        customExercises = customExercises.toList(),
        scheduleOverrides = scheduleOverrides.toList(),
        selectedSessionId = selectedSessionId,
        activeWorkoutSessionId = activeWorkoutSessionId,
        activeWorkoutStartedAtMillis = activeWorkoutStartedAtMillis,
        completedExerciseIds = completedExerciseIds.toMap(),
        activeSetLogs = activeSetLogs.mapValues { (_, sets) -> sets.toList() },
        restTimerEndAtMillis = restTimerEndAtMillis
    )

    private fun restoreMutationSnapshot(snapshot: TrainingMutationSnapshot) {
        workoutTemplates.clear()
        workoutTemplates.addAll(snapshot.workoutTemplates)
        weeklyDayPlans.clear()
        weeklyDayPlans.addAll(snapshot.weeklyDayPlans)
        customExercises.clear()
        customExercises.addAll(snapshot.customExercises)
        scheduleOverrides.clear()
        scheduleOverrides.addAll(snapshot.scheduleOverrides)
        selectedSessionId = snapshot.selectedSessionId
        activeWorkoutSessionId = snapshot.activeWorkoutSessionId
        activeWorkoutStartedAtMillis = snapshot.activeWorkoutStartedAtMillis
        completedExerciseIds.clear()
        completedExerciseIds.putAll(snapshot.completedExerciseIds)
        activeSetLogs.clear()
        activeSetLogs.putAll(snapshot.activeSetLogs)
        restTimerEndAtMillis = snapshot.restTimerEndAtMillis
    }

    private fun persistTrainingState(
        rescheduleSupplementReminders: Boolean = false,
        rollbackSnapshot: TrainingMutationSnapshot? = null
    ) {
        val userId = currentUserId ?: return
        val targetRuntime = runtime ?: return
        if (restoredUserId != userId) return
        supplementReschedulePending = supplementReschedulePending || rescheduleSupplementReminders
        persistenceGeneration += 1
        val generation = persistenceGeneration
        if (rollbackSnapshot != null) mutationError = null
        persistJob?.cancel()
        persistJob = modelScope.launch {
            persistenceMutex.withLock {
            if (
                generation != persistenceGeneration ||
                currentUserId != userId ||
                restoredUserId != userId
            ) {
                return@withLock
            }

            var payload: String? = null
            val previousRestoredPayload = restoredPayload
            var operation: TrainingPersistenceOperation
            try {
                payload = currentTrainingPayload(supplements)
                operation = persistenceOperationFor(userId, payload)
                if (!operation.repositoryCompleted) {
                    restoredPayload = payload
                    targetRuntime.saveTrainingState(userId, payload)
                    operation = operation.copy(repositoryCompleted = true)
                    retainPersistenceOperation(operation)
                }
            } catch (error: Exception) {
                if (payload != null && restoredPayload == payload) {
                    restoredPayload = previousRestoredPayload
                }
                val persistedAccount = runCatching { targetRuntime.currentUserId() }.getOrNull()
                if (
                    rollbackSnapshot != null &&
                    generation == persistenceGeneration &&
                    currentUserId == userId &&
                    restoredUserId == userId &&
                    persistedAccount == userId
                ) {
                    restoreMutationSnapshot(rollbackSnapshot)
                    mutationError = error.message ?: "Training state persistence failed."
                }
                return@withLock
            }

            if (generation != persistenceGeneration) return@withLock
            if (
                targetRuntime.currentUserId() != userId ||
                currentUserId != userId
            ) {
                return@withLock
            }

            val scheduleSupplements =
                supplementReschedulePending && !operation.supplementRescheduleCompleted
            val systems = if (scheduleSupplements) {
                setOf(ReminderSystem.TRAINING, ReminderSystem.SUPPLEMENTS)
            } else {
                setOf(ReminderSystem.TRAINING)
            }
            val scheduling = rescheduleReminderSystemsWithRecovery(
                userId = userId,
                hydration = {},
                training = {
                    val settings = targetRuntime.currentTrainingReminderSettings(userId)
                        ?: TrainingReminderSettingsEntity(userId = userId)
                    targetRuntime.scheduleTraining(userId, settings)
                },
                supplements = {
                    rescheduleSupplementReminders(userId, targetRuntime)
                },
                scheduleRecovery = targetRuntime::scheduleRecovery,
                systems = systems
            )
            if (
                generation != persistenceGeneration ||
                targetRuntime.currentUserId() != userId ||
                currentUserId != userId
            ) {
                return@withLock
            }
            if (
                scheduleSupplements &&
                ReminderSystem.SUPPLEMENTS !in scheduling.failedSystems
            ) {
                operation = operation.copy(supplementRescheduleCompleted = true)
                retainPersistenceOperation(operation)
            }
            if (supplementReschedulePending && operation.supplementRescheduleCompleted) {
                supplementReschedulePending = false
            }
            }
        }
    }

    private fun currentTrainingPayload(supplements: List<Supplement>): String =
        encodeTrainingState(
            supplements = supplements,
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
            customExercises = customExercises,
            workoutTemplates = workoutTemplates,
            weeklyDayPlans = weeklyDayPlans
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
        decodeTrainingState(payload, builtInExercises)?.let { restored ->
            supplements.clear()
            supplements.addAll(restored.supplements)
            customExercises.clear()
            customExercises.addAll(restored.customExercises)
            workoutTemplates.clear()
            workoutTemplates.addAll(restored.workoutTemplates)
            weeklyDayPlans.clear()
            weeklyDayPlans.addAll(restored.weeklyDayPlans)
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
            legacyUsesMetricUnits = restored.legacyUsesMetricUnits
        }
    }

    private fun resetTrainingState() {
        supplements.clear()
        supplements.addAll(defaultSupplements())
        customExercises.clear()
        workoutTemplates.clear()
        workoutTemplates.addAll(defaultProgram.templates)
        weeklyDayPlans.clear()
        weeklyDayPlans.addAll(defaultProgram.dayPlans)
        history.clear()
        history.addAll(defaultTrainingHistory())
        workoutHistory.clear()
        scheduleOverrides.clear()
        activeSetLogs.clear()
        selectedSessionId = null
        activeWorkoutSessionId = null
        activeWorkoutStartedAtMillis = null
        restTimerEndAtMillis = null
        legacyUsesMetricUnits = null
        defaultRestTimerSeconds = 90
        usesMetricUnits = true
        completedExerciseIds.clear()
        lastWorkoutSummary = null
        suggestionDecision = SuggestionDecision.PENDING
        suggestedWeightKg = 42.5
    }
}
