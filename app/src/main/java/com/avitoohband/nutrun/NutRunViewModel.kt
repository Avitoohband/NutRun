package com.avitoohband.nutrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avitoohband.nutrun.BuildConfig
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.billing.BillingManager
import com.avitoohband.nutrun.billing.BillingUiState
import android.app.Activity
import com.avitoohband.nutrun.auth.AuthenticationGateway
import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.FoodTemplateEntity
import com.avitoohband.nutrun.data.FoodSearchService
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.NutRunRepository
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WaterLogEntity
import com.avitoohband.nutrun.data.WeightEntryEntity
import com.avitoohband.nutrun.domain.DailyNutritionSummary
import com.avitoohband.nutrun.domain.FoodCatalogItem
import com.avitoohband.nutrun.domain.MealType
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.domain.calculateHealthEstimate
import com.avitoohband.nutrun.domain.crossedHydrationGoal
import com.avitoohband.nutrun.health.NutRunHealthConnectManager
import com.avitoohband.nutrun.walk.WalkGpsMonitor
import com.avitoohband.nutrun.walk.WalkGpsState
import com.avitoohband.nutrun.walk.WalkLocationMonitor
import com.avitoohband.nutrun.reminders.HydrationScheduler
import com.avitoohband.nutrun.reminders.ReminderRescheduleRecoveryScheduler
import com.avitoohband.nutrun.reminders.ReminderSystem
import com.avitoohband.nutrun.reminders.SupplementReminderScheduler
import com.avitoohband.nutrun.reminders.TrainingReminderScheduler
import com.avitoohband.nutrun.reminders.rescheduleReminderSystemsWithRecovery
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HydrationGoalCelebration(
    val goalMl: Int,
    val totalMl: Int
)

data class NutRunUiState(
    val session: SessionPreferences = SessionPreferences(),
    val sessionResolved: Boolean = false,
    val profile: UserProfile? = null,
    val food: List<FoodLogEntity> = emptyList(),
    val recentFoods: List<FoodLogEntity> = emptyList(),
    val foodTemplates: List<FoodTemplateEntity> = emptyList(),
    val nutritionTargets: NutritionTargets? = null,
    val water: List<WaterLogEntity> = emptyList(),
    val hydrationPlan: HydrationPlanEntity = HydrationPlanEntity(),
    val trainingReminderSettings: TrainingReminderSettingsEntity = TrainingReminderSettingsEntity(),
    val supplementReminderSettings: SupplementReminderSettingsEntity = SupplementReminderSettingsEntity(),
    val weights: List<WeightEntryEntity> = emptyList(),
    val walks: List<WalkSessionEntity> = emptyList(),
    val activeWalk: WalkSessionEntity? = null
) {
    val notificationSettingsReadyAccountId: String?
        get() {
            if (!sessionResolved) return null
            val accountId = session.authenticatedUserId ?: return null
            return accountId.takeIf {
                hydrationPlan.userId == accountId &&
                    trainingReminderSettings.userId == accountId &&
                    supplementReminderSettings.userId == accountId
            }
        }

    val nutrition = DailyNutritionSummary(
        calories = food.sumOf { it.calories },
        proteinGrams = food.sumOf { it.proteinGrams },
        carbohydrateGrams = food.sumOf { it.carbohydrateGrams },
        fatGrams = food.sumOf { it.fatGrams }
    )
    val waterMl = water.sumOf { it.amountMl }
    val healthEstimate = profile?.let {
        calculateHealthEstimate(
            it.birthDate,
            it.biologicalSex,
            it.heightCm,
            it.weightKg,
            it.activityLevel,
            it.goal,
            it.calorieTarget
        )
    }
}

data class HealthConnectUiState(
    val available: Boolean = false,
    val permissionGranted: Boolean = false,
    val busy: Boolean = false,
    val importedSteps: Long = 0,
    val lastSyncMessage: String? = null
)

private data class ReminderSettingsSnapshot(
    val userId: String?,
    val hydration: HydrationPlanEntity,
    val training: TrainingReminderSettingsEntity,
    val supplements: SupplementReminderSettingsEntity
)

internal interface NutRunViewModelReminderRuntime {
    val session: Flow<SessionPreferences>
    val profile: Flow<UserProfile?>
    val hydrationPlan: Flow<HydrationPlanEntity>
    val weights: Flow<List<WeightEntryEntity>>
    val walks: Flow<List<WalkSessionEntity>>
    val activeWalk: Flow<WalkSessionEntity?>
    val trainingReminderSettings: Flow<TrainingReminderSettingsEntity>
    val supplementReminderSettings: Flow<SupplementReminderSettingsEntity>
    val recentFoods: Flow<List<FoodLogEntity>>
    val foodTemplates: Flow<List<FoodTemplateEntity>>
    val nutritionTargets: Flow<NutritionTargets?>

    fun food(date: LocalDate): Flow<List<FoodLogEntity>>
    fun water(date: LocalDate): Flow<List<WaterLogEntity>>
    fun walkPoints(sessionId: String): Flow<List<WalkPointEntity>>

    fun scheduleHydration(plan: HydrationPlanEntity)
    fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity)
    suspend fun rescheduleSupplementReminders(userId: String)
    suspend fun scheduleReminderRecovery(userId: String, system: ReminderSystem)
    suspend fun currentSession(): SessionPreferences

    suspend fun saveHydrationPlan(userId: String, plan: HydrationPlanEntity)
    suspend fun saveTrainingReminderSettings(
        userId: String,
        settings: TrainingReminderSettingsEntity
    )
    suspend fun saveSupplementReminderSettings(
        userId: String,
        settings: SupplementReminderSettingsEntity
    )

    suspend fun pauseWalk()
    fun signOutAuthentication()
    suspend fun signOutPreferences()
    suspend fun signOutReminders(userId: String, clearSession: suspend () -> Unit)
}

private class ProductionNutRunViewModelReminderRuntime(
    private val repository: NutRunRepository,
    private val preferences: AppPreferences,
    private val hydrationScheduler: HydrationScheduler,
    private val trainingReminderScheduler: TrainingReminderScheduler,
    private val reminderRescheduleRecoveryScheduler: ReminderRescheduleRecoveryScheduler,
    private val supplementReminderSchedulingCoordinator: SupplementReminderSchedulingCoordinator,
    private val authenticationGateway: AuthenticationGateway
) : NutRunViewModelReminderRuntime {
    override val session = preferences.session
    override val profile = repository.profile
    override val hydrationPlan = repository.hydrationPlan
    override val weights = repository.weights
    override val walks = repository.walks
    override val activeWalk = repository.activeWalk
    override val trainingReminderSettings = repository.trainingReminderSettings
    override val supplementReminderSettings = repository.supplementReminderSettings
    override val recentFoods = repository.recentFoods
    override val foodTemplates = repository.foodTemplates
    override val nutritionTargets = repository.nutritionTargets

    override fun food(date: LocalDate): Flow<List<FoodLogEntity>> = repository.food(date)

    override fun water(date: LocalDate): Flow<List<WaterLogEntity>> = repository.water(date)

    override fun walkPoints(sessionId: String): Flow<List<WalkPointEntity>> =
        repository.walkPoints(sessionId)

    override fun scheduleHydration(plan: HydrationPlanEntity) {
        hydrationScheduler.schedule(plan)
    }

    override fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity) {
        trainingReminderScheduler.schedule(userId, settings)
    }

    override suspend fun rescheduleSupplementReminders(userId: String) {
        supplementReminderSchedulingCoordinator.reschedule(userId)
    }

    override suspend fun scheduleReminderRecovery(userId: String, system: ReminderSystem) {
        reminderRescheduleRecoveryScheduler.schedule(userId, setOf(system))
    }

    override suspend fun currentSession(): SessionPreferences = preferences.currentSession()

    override suspend fun saveHydrationPlan(userId: String, plan: HydrationPlanEntity) {
        repository.saveHydrationPlan(userId, plan)
    }

    override suspend fun saveTrainingReminderSettings(
        userId: String,
        settings: TrainingReminderSettingsEntity
    ) {
        repository.saveTrainingReminderSettings(userId, settings)
    }

    override suspend fun saveSupplementReminderSettings(
        userId: String,
        settings: SupplementReminderSettingsEntity
    ) {
        repository.saveSupplementReminderSettings(userId, settings)
    }

    override suspend fun pauseWalk() {
        repository.updateWalkState(com.avitoohband.nutrun.domain.WalkState.PAUSED)
    }

    override fun signOutAuthentication() {
        authenticationGateway.signOut()
    }

    override suspend fun signOutPreferences() {
        preferences.signOut()
    }

    override suspend fun signOutReminders(
        userId: String,
        clearSession: suspend () -> Unit
    ) {
        supplementReminderSchedulingCoordinator.signOut(userId, clearSession)
    }
}

private fun productionNutRunViewModelScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

private class FallbackWalkLocationMonitor : WalkGpsMonitor {
    private val _state = MutableStateFlow<WalkGpsState>(WalkGpsState.PermissionRequired)
    override val state: StateFlow<WalkGpsState> = _state.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class NutRunViewModel internal constructor(
    private val reminderRuntime: NutRunViewModelReminderRuntime,
    coroutineScope: CoroutineScope,
    walkLocationMonitor: WalkGpsMonitor? = null
) : ViewModel(coroutineScope) {
    private val walkMonitor: WalkGpsMonitor = walkLocationMonitor ?: FallbackWalkLocationMonitor()
    private lateinit var repository: NutRunRepository
    private lateinit var preferences: AppPreferences
    private lateinit var foodSearchService: FoodSearchService
    private lateinit var hydrationScheduler: HydrationScheduler
    private lateinit var trainingReminderScheduler: TrainingReminderScheduler
    private lateinit var supplementReminderScheduler: SupplementReminderScheduler
    private lateinit var reminderRescheduleRecoveryScheduler: ReminderRescheduleRecoveryScheduler
    private lateinit var billingManager: BillingManager
    private lateinit var authenticationGateway: AuthenticationGateway
    private lateinit var healthConnectManager: NutRunHealthConnectManager
    private val currentDate = MutableStateFlow(LocalDate.now())
    private val hydrationLogMutex = Mutex()
    private val _hydrationGoalCelebrations =
        MutableSharedFlow<HydrationGoalCelebration>(extraBufferCapacity = 1)
    val hydrationGoalCelebrations: SharedFlow<HydrationGoalCelebration> =
        _hydrationGoalCelebrations.asSharedFlow()
    private val food = currentDate.flatMapLatest(reminderRuntime::food)
    private val water = currentDate.flatMapLatest(reminderRuntime::water)
    val state: StateFlow<NutRunUiState> = combine(
        reminderRuntime.session,
        reminderRuntime.profile,
        food,
        water,
        reminderRuntime.hydrationPlan,
        reminderRuntime.weights,
        reminderRuntime.walks,
        reminderRuntime.activeWalk,
        reminderRuntime.trainingReminderSettings,
        reminderRuntime.supplementReminderSettings,
        reminderRuntime.recentFoods,
        reminderRuntime.foodTemplates,
        reminderRuntime.nutritionTargets
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        NutRunUiState(
            session = values[0] as SessionPreferences,
            sessionResolved = true,
            profile = values[1] as UserProfile?,
            food = values[2] as List<FoodLogEntity>,
            water = values[3] as List<WaterLogEntity>,
            hydrationPlan = values[4] as HydrationPlanEntity,
            weights = values[5] as List<WeightEntryEntity>,
            walks = values[6] as List<WalkSessionEntity>,
            activeWalk = values[7] as WalkSessionEntity?,
            trainingReminderSettings = values[8] as TrainingReminderSettingsEntity,
            supplementReminderSettings = values[9] as SupplementReminderSettingsEntity,
            recentFoods = values[10] as List<FoodLogEntity>,
            foodTemplates = values[11] as List<FoodTemplateEntity>,
            nutritionTargets = values[12] as NutritionTargets?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutRunUiState())

    val routePoints: StateFlow<List<WalkPointEntity>> = reminderRuntime.activeWalk
        .map(::activeRouteSessionId)
        .flatMapLatest { id -> id?.let(reminderRuntime::walkPoints) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedWalkId = MutableStateFlow<String?>(null)
    val selectedWalkId: StateFlow<String?> = _selectedWalkId
    val selectedWalkRoutePoints: StateFlow<List<WalkPointEntity>> = selectedWalkId
        .flatMapLatest { id -> id?.let(reminderRuntime::walkPoints) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val foodSearchState = MutableStateFlow<FoodSearchUiState>(FoodSearchUiState.Idle)
    val pendingNutritionDeletion = MutableStateFlow<PendingNutritionDeletion?>(null)
    val walkGpsState: StateFlow<WalkGpsState> = walkMonitor.state
    val message = MutableStateFlow<String?>(null)
    lateinit var billingState: StateFlow<BillingUiState>
        private set
    val healthConnectState = MutableStateFlow(
        HealthConnectUiState()
    )
    val healthConnectPermissions: Set<String>
        get() = if (::healthConnectManager.isInitialized) healthConnectManager.permissions else emptySet()
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var deletionJob: Job? = null

    init {
        viewModelScope.launch {
            state.map {
                ReminderSettingsSnapshot(
                    it.notificationSettingsReadyAccountId,
                    it.hydrationPlan,
                    it.trainingReminderSettings,
                    it.supplementReminderSettings
                )
            }.distinctUntilChanged().collect { snapshot ->
                val userId = snapshot.userId
                if (userId != null) {
                    rescheduleReminderSystemsWithRecovery(
                        userId = userId,
                        hydration = {
                            reminderRuntime.scheduleHydration(snapshot.hydration)
                        },
                        training = {
                            reminderRuntime.scheduleTraining(
                                userId,
                                snapshot.training.copy(
                                    timezoneId = java.time.ZoneId.systemDefault().id
                                )
                            )
                        },
                        supplements = {
                            reminderRuntime.rescheduleSupplementReminders(userId)
                        },
                        scheduleRecovery = reminderRuntime::scheduleReminderRecovery
                    )
                }
            }
        }
    }

    @Inject
    constructor(
        repository: NutRunRepository,
        preferences: AppPreferences,
        foodSearchService: FoodSearchService,
        hydrationScheduler: HydrationScheduler,
        trainingReminderScheduler: TrainingReminderScheduler,
        supplementReminderScheduler: SupplementReminderScheduler,
        reminderRescheduleRecoveryScheduler: ReminderRescheduleRecoveryScheduler,
        supplementReminderSchedulingCoordinator: SupplementReminderSchedulingCoordinator,
        billingManager: BillingManager,
        authenticationGateway: AuthenticationGateway,
        healthConnectManager: NutRunHealthConnectManager,
        walkLocationMonitor: WalkLocationMonitor
    ) : this(
        ProductionNutRunViewModelReminderRuntime(
            repository,
            preferences,
            hydrationScheduler,
            trainingReminderScheduler,
            reminderRescheduleRecoveryScheduler,
            supplementReminderSchedulingCoordinator,
            authenticationGateway
        ),
        productionNutRunViewModelScope(),
        walkLocationMonitor
    ) {
        this.repository = repository
        this.preferences = preferences
        this.foodSearchService = foodSearchService
        this.hydrationScheduler = hydrationScheduler
        this.trainingReminderScheduler = trainingReminderScheduler
        this.supplementReminderScheduler = supplementReminderScheduler
        this.reminderRescheduleRecoveryScheduler = reminderRescheduleRecoveryScheduler
        this.billingManager = billingManager
        this.authenticationGateway = authenticationGateway
        this.healthConnectManager = healthConnectManager
        initializeProduction()
    }

    private fun initializeProduction() {
        billingState = billingManager.state
        healthConnectState.value = HealthConnectUiState(available = healthConnectManager.isAvailable())
        viewModelScope.launch {
            refreshHealthConnectStatus()
        }
        viewModelScope.launch {
            preferences.session
                .map { it.authenticatedUserId }
                .distinctUntilChanged()
                .collect { accountId ->
                    clearFoodSearch()
                    if (accountId != null && !isDemoAccount(accountId)) {
                        billingManager.connect()
                        repository.synchronize()
                    }
                }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                currentDate.value = LocalDate.now()
            }
        }
    }

    fun authenticate(email: String, password: String, createAccount: Boolean) {
        if (!createAccount && canUseDemoAccount(BuildConfig.DEBUG, email, password)) {
            enterDemo()
            return
        }
        if (!email.contains('@') || password.length < 6) {
            message.value = "Enter a valid email and a password with at least 6 characters."
            return
        }
        viewModelScope.launch {
            authenticationGateway.authenticate(email.trim(), password, createAccount)
                .onSuccess {
                    preferences.signIn(
                        userId = it.userId,
                        email = it.email,
                        trialStartedAtMillis = it.trialStartedAtMillis,
                        subscriber = it.subscriber
                    )
                    repository.claimLegacyData(it.userId, it.email)
                    repository.synchronize()
                }
                .onFailure { message.value = it.message ?: "Authentication failed." }
        }
    }

    fun enterDemo() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            runCatching {
                preferences.signIn(
                    userId = DEMO_USER_ID,
                    email = DEMO_EMAIL,
                    trialStartedAtMillis = System.currentTimeMillis(),
                    subscriber = true
                )
                repository.saveProfileIfMissing(defaultDemoProfile())
            }.onFailure {
                message.value = it.message ?: "Could not open the demo account."
            }
        }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            runCatching { repository.saveProfile(profile) }
                .onFailure { message.value = it.message ?: "Could not save profile." }
        }
    }

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val available = healthConnectManager.isAvailable()
            val granted = available && runCatching {
                healthConnectManager.hasAllPermissions()
            }.getOrDefault(false)
            healthConnectState.value = healthConnectState.value.copy(
                available = available,
                permissionGranted = granted
            )
        }
    }

    fun synchronizeHealthConnect(workouts: List<WorkoutRecord>) {
        val snapshot = state.value
        val profile = snapshot.profile ?: return
        viewModelScope.launch {
            healthConnectState.value = healthConnectState.value.copy(busy = true)
            runCatching {
                healthConnectManager.synchronize(
                    profile = profile,
                    water = snapshot.water,
                    food = snapshot.food,
                    workouts = workouts,
                    walks = snapshot.walks
                )
            }.onSuccess { result ->
                val importedWeight = result.importedWeightKg
                if (
                    importedWeight != null &&
                    kotlin.math.abs(importedWeight - profile.weightKg) >= 0.05
                ) {
                    repository.saveProfile(profile.copy(weightKg = importedWeight))
                }
                healthConnectState.value = HealthConnectUiState(
                    available = true,
                    permissionGranted = true,
                    importedSteps = result.importedSteps,
                    lastSyncMessage = "Synced ${result.exportedRecords} records."
                )
            }.onFailure { error ->
                healthConnectState.value = healthConnectState.value.copy(
                    busy = false,
                    lastSyncMessage = error.message ?: "Health Connect sync failed."
                )
            }
        }
    }

    fun searchFood(query: String) {
        searchJob?.cancel()
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            clearFoodSearch()
            return
        }
        if (isDemoAccount(state.value.session.authenticatedUserId)) {
            foodSearchState.value = FoodSearchUiState.Error(
                normalized,
                "Online food search is unavailable in the local demo account."
            )
            return
        }
        val generation = ++searchGeneration
        foodSearchState.value = foodSearchStateForQuery(query)
        searchJob = viewModelScope.launch {
            delay(300)
            if (generation != searchGeneration) return@launch
            runCatching { foodSearchService.search(normalized) }
                .onSuccess { items ->
                    resolveFoodSearchResult(searchGeneration, generation, normalized, items)
                        ?.let { foodSearchState.value = it }
                }
                .onFailure { error ->
                    resolveFoodSearchError(
                        searchGeneration,
                        generation,
                        normalized,
                        error.message ?: "Food search is unavailable."
                    )?.let { foodSearchState.value = it }
                }
        }
    }

    fun clearFoodSearch() {
        searchJob?.cancel()
        searchJob = null
        searchGeneration++
        foodSearchState.value = FoodSearchUiState.Idle
    }

    fun saveNutritionTargets(targets: NutritionTargets) {
        val userId = state.value.session.authenticatedUserId ?: return
        viewModelScope.launch {
            runCatching { repository.saveNutritionTargets(userId, targets) }
                .onFailure { message.value = it.message ?: "Could not save nutrition targets." }
        }
    }

    fun saveFood(item: FoodCatalogItem, mealType: MealType, id: String? = null) {
        viewModelScope.launch {
            repository.saveFood(item, mealType, id = id ?: java.util.UUID.randomUUID().toString())
            clearFoodSearch()
        }
    }

    fun duplicateFood(id: String) {
        viewModelScope.launch { repository.duplicateFood(id) }
    }

    fun logRecentFood(entry: FoodLogEntity) {
        viewModelScope.launch { repository.logRecentFood(entry) }
    }

    fun saveFavoriteFood(entry: FoodLogEntity) {
        viewModelScope.launch { repository.saveFavoriteFood(entry) }
    }

    fun saveMealTemplate(name: String, mealType: MealType) {
        val entries = state.value.food.filter { it.mealType == mealType.name }
        if (entries.isEmpty()) {
            message.value = "Add food to this meal before saving it."
            return
        }
        viewModelScope.launch {
            runCatching { repository.saveMealTemplate(name, entries) }
                .onFailure { message.value = it.message ?: "Could not save the meal." }
        }
    }

    fun logFoodTemplate(template: FoodTemplateEntity) {
        viewModelScope.launch {
            runCatching { repository.logFoodTemplate(template) }
                .onFailure { message.value = it.message ?: "Could not add the saved food." }
        }
    }

    fun deleteFoodTemplate(template: FoodTemplateEntity) {
        requestTemplateDeletion(template)
    }

    fun deleteFood(entry: FoodLogEntity) {
        requestFoodDeletion(entry)
    }

    fun requestFoodDeletion(entry: FoodLogEntity) {
        viewModelScope.launch {
            commitPendingNutritionDeletion()
            val userId = state.value.session.authenticatedUserId ?: return@launch
            if (entry.userId != userId) return@launch
            pendingNutritionDeletion.value = PendingNutritionDeletion(
                id = entry.id,
                label = entry.name,
                kind = NutritionDeletionKind.FOOD,
                ownerUserId = userId,
                foodEntry = entry
            )
            scheduleNutritionDeletionCommit()
        }
    }

    fun requestTemplateDeletion(template: FoodTemplateEntity) {
        viewModelScope.launch {
            commitPendingNutritionDeletion()
            val userId = state.value.session.authenticatedUserId ?: return@launch
            if (template.userId != userId) return@launch
            pendingNutritionDeletion.value = PendingNutritionDeletion(
                id = template.id,
                label = template.name,
                kind = NutritionDeletionKind.TEMPLATE,
                ownerUserId = userId,
                template = template
            )
            scheduleNutritionDeletionCommit()
        }
    }

    fun undoNutritionDeletion() {
        val pending = pendingNutritionDeletion.value ?: return
        val userId = state.value.session.authenticatedUserId
        if (userId == null || pending.ownerUserId != userId) return
        deletionJob?.cancel()
        deletionJob = null
        pendingNutritionDeletion.value = null
    }

    suspend fun commitPendingNutritionDeletion() {
        deletionJob?.cancel()
        deletionJob = null
        val pending = pendingNutritionDeletion.value ?: return
        val userId = state.value.session.authenticatedUserId
        pendingNutritionDeletion.value = null
        if (userId == null || pending.ownerUserId != userId) return
        when (pending.kind) {
            NutritionDeletionKind.FOOD -> pending.foodEntry?.let { repository.deleteFood(it) }
            NutritionDeletionKind.TEMPLATE -> pending.template?.let { repository.deleteFoodTemplate(it) }
        }
    }

    private fun scheduleNutritionDeletionCommit() {
        deletionJob?.cancel()
        deletionJob = viewModelScope.launch {
            delay(NUTRITION_DELETION_WINDOW_MS)
            commitPendingNutritionDeletion()
        }
    }

    fun addWater(amountMl: Int) {
        viewModelScope.launch {
            runCatching { logWaterAndCelebrate(amountMl, updateQuickServing = false) }
                .onFailure { message.value = it.message ?: "Could not log water." }
        }
    }

    fun setQuickServingAndAddWater(amountMl: Int) {
        viewModelScope.launch {
            runCatching { logWaterAndCelebrate(amountMl, updateQuickServing = true) }
                .onFailure { message.value = it.message ?: "Could not log water." }
        }
    }

    private suspend fun logWaterAndCelebrate(
        amountMl: Int,
        updateQuickServing: Boolean
    ) {
        hydrationLogMutex.withLock {
            val date = LocalDate.now()
            val previousMl = repository.currentWaterTotal(date)
            val goalMl = repository.currentHydrationPlan()?.goalMl
                ?: state.value.hydrationPlan.goalMl
            val totalMl = if (updateQuickServing) {
                repository.setQuickServingAndAddWater(amountMl, date)
            } else {
                repository.addWater(amountMl, date)
            }
            if (crossedHydrationGoal(previousMl, totalMl, goalMl)) {
                _hydrationGoalCelebrations.emit(
                    HydrationGoalCelebration(goalMl = goalMl, totalMl = totalMl)
                )
            }
        }
    }

    fun saveHydrationPlan(plan: HydrationPlanEntity) {
        viewModelScope.launch {
            runCatching { repository.saveHydrationPlan(plan) }
                .onSuccess {
                    state.value.session.authenticatedUserId?.let { userId ->
                        hydrationScheduler.schedule(
                            plan.copy(id = "hydration:$userId", userId = userId)
                        )
                    }
                }
                .onFailure { message.value = it.message ?: "Could not save hydration settings." }
        }
    }

    fun saveTrainingReminderSettings(settings: TrainingReminderSettingsEntity) {
        viewModelScope.launch {
            runCatching { repository.saveTrainingReminderSettings(settings) }
                .onSuccess {
                    state.value.session.authenticatedUserId?.let { userId ->
                        trainingReminderScheduler.schedule(
                            userId,
                            settings.copy(
                                id = "training-reminders:$userId",
                                userId = userId,
                                timezoneId = java.time.ZoneId.systemDefault().id
                            )
                        )
                    }
                }
                .onFailure { message.value = it.message ?: "Could not save reminder settings." }
        }
    }

    fun saveSupplementReminderSettings(settings: SupplementReminderSettingsEntity) {
        viewModelScope.launch {
            try {
                val userId = reminderRuntime.currentSession().authenticatedUserId ?: return@launch
                reminderRuntime.saveSupplementReminderSettings(userId, settings)
                val currentUserId = reminderRuntime.currentSession().authenticatedUserId
                if (currentUserId != userId) return@launch
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message.value = error.message ?: "Could not save reminder settings."
            }
        }
    }

    suspend fun persistNotificationSettings(
        accountId: String,
        hydration: HydrationPlanEntity,
        training: TrainingReminderSettingsEntity,
        supplements: SupplementReminderSettingsEntity
    ): NotificationSettingsSaveResult {
        val completedStages = mutableSetOf(NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS)
        accountChanged(accountId, NotificationSettingsSaveStage.HYDRATION, completedStages)
            ?.let { return it }
        saveNotificationStage(accountId, NotificationSettingsSaveStage.HYDRATION, completedStages) {
            reminderRuntime.saveHydrationPlan(accountId, hydration)
        }?.let { return it }

        accountChanged(accountId, NotificationSettingsSaveStage.TRAINING, completedStages)
            ?.let { return it }
        saveNotificationStage(accountId, NotificationSettingsSaveStage.TRAINING, completedStages) {
            reminderRuntime.saveTrainingReminderSettings(accountId, training)
        }?.let { return it }

        accountChanged(accountId, NotificationSettingsSaveStage.SUPPLEMENT_MASTER, completedStages)
            ?.let { return it }
        saveNotificationStage(
            accountId,
            NotificationSettingsSaveStage.SUPPLEMENT_MASTER,
            completedStages
        ) {
            reminderRuntime.saveSupplementReminderSettings(accountId, supplements)
        }?.let { return it }

        return NotificationSettingsSaveResult.Success(accountId)
    }

    private suspend fun saveNotificationStage(
        accountId: String,
        stage: NotificationSettingsSaveStage,
        completedStages: MutableSet<NotificationSettingsSaveStage>,
        save: suspend () -> Unit
    ): NotificationSettingsSaveResult? = try {
        save()
        completedStages += stage
        accountChanged(accountId, stage, completedStages)
    } catch (error: Exception) {
        if (error is CancellationException && !currentCoroutineContext().isActive) throw error
        accountChanged(accountId, stage, completedStages) ?: NotificationSettingsSaveResult.Failed(
            expectedAccountId = accountId,
            stage = stage,
            message = error.message ?: "Persistence failed."
        )
    }

    private suspend fun accountChanged(
        expectedAccountId: String,
        stage: NotificationSettingsSaveStage,
        completedStages: Set<NotificationSettingsSaveStage>
    ): NotificationSettingsSaveResult.AccountChanged? {
        val actualAccountId = reminderRuntime.currentSession().authenticatedUserId
        return if (actualAccountId == expectedAccountId) {
            null
        } else {
            NotificationSettingsSaveResult.AccountChanged(
                expectedAccountId = expectedAccountId,
                actualAccountId = actualAccountId,
                stage = stage,
                completedStages = completedStages.toSet()
            )
        }
    }

    internal suspend fun currentAuthenticatedAccountId(): String? =
        reminderRuntime.currentSession().authenticatedUserId

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkMode(enabled) }
    }

    fun setSubscriberForDevelopment(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            state.value.session.authenticatedUserId?.let {
                preferences.setSubscriber(it, enabled)
            }
        }
    }

    fun purchase(activity: Activity, productId: String) {
        if (isDemoAccount(state.value.session.authenticatedUserId)) return
        billingManager.launch(activity, productId)
    }

    fun restorePurchases() {
        if (isDemoAccount(state.value.session.authenticatedUserId)) return
        billingManager.restore()
    }

    fun signOut(): Job {
        clearSelectedWalk()
        clearFoodSearch()
        return viewModelScope.launch {
            commitPendingNutritionDeletion()
            val userId = reminderRuntime.currentSession().authenticatedUserId
            reminderRuntime.pauseWalk()
            if (userId != null) {
                reminderRuntime.signOutReminders(userId) {
                    if (!isDemoAccount(userId)) reminderRuntime.signOutAuthentication()
                    reminderRuntime.signOutPreferences()
                }
            } else {
                reminderRuntime.signOutAuthentication()
                reminderRuntime.signOutPreferences()
            }
        }
    }

    fun selectCompletedWalk(id: String) {
        _selectedWalkId.value = id
    }

    fun clearSelectedWalk() {
        _selectedWalkId.value = null
    }

    fun startWalkGpsMonitoring() {
        walkMonitor.start()
    }

    fun stopWalkGpsMonitoring() {
        walkMonitor.stop()
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val userId = state.value.session.authenticatedUserId
                ?: return@launch
            if (isDemoAccount(userId)) {
                repository.clearAccountData(userId)
                preferences.clearAccount(userId)
                return@launch
            }
            authenticationGateway.deleteAccount()
                .onSuccess {
                    repository.clearAccountData(userId)
                    preferences.clearAccount(userId)
                }
                .onFailure { message.value = it.message ?: "Account deletion failed." }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

suspend fun rescheduleSupplementReminderWork(
    userId: String,
    settings: SupplementReminderSettingsEntity,
    supplementReminderScheduler: SupplementReminderScheduler,
    recoveryScheduler: ReminderRescheduleRecoveryScheduler?
) {
    try {
        if (settings.enabled) {
            supplementReminderScheduler.schedule(userId, settings)
        } else {
            supplementReminderScheduler.cancel(userId)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        try {
            recoveryScheduler?.schedule(userId, setOf(ReminderSystem.SUPPLEMENTS))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }
}

internal interface SupplementReminderSchedulingStore {
    suspend fun currentUserId(): String?
    suspend fun supplementReminderSettings(userId: String): SupplementReminderSettingsEntity?
}

private class ProductionSupplementReminderSchedulingStore(
    private val repository: NutRunRepository,
    private val preferences: AppPreferences
) : SupplementReminderSchedulingStore {
    override suspend fun currentUserId(): String? = preferences.currentSession().authenticatedUserId

    override suspend fun supplementReminderSettings(userId: String): SupplementReminderSettingsEntity? =
        if (currentUserId() == userId) repository.currentSupplementReminderSettings() else null
}

@Singleton
class SupplementReminderSchedulingCoordinator private constructor(
    private val store: SupplementReminderSchedulingStore,
    private val supplementReminderScheduler: SupplementReminderScheduler,
    private val recoveryScheduler: ReminderRescheduleRecoveryScheduler,
    @Suppress("UNUSED_PARAMETER") private val coordinatorConstructor: Boolean
) {
    @Inject
    constructor(
        repository: NutRunRepository,
        preferences: AppPreferences,
        supplementReminderScheduler: SupplementReminderScheduler,
        recoveryScheduler: ReminderRescheduleRecoveryScheduler
    ) : this(
        ProductionSupplementReminderSchedulingStore(repository, preferences),
        supplementReminderScheduler,
        recoveryScheduler,
        true
    )

    internal constructor(
        store: SupplementReminderSchedulingStore,
        supplementReminderScheduler: SupplementReminderScheduler,
        recoveryScheduler: ReminderRescheduleRecoveryScheduler
    ) : this(store, supplementReminderScheduler, recoveryScheduler, true)

    private val mutex = Mutex()

    suspend fun reschedule(userId: String) {
        mutex.withLock {
            if (store.currentUserId() != userId) return
            val settings = store.supplementReminderSettings(userId)
                ?: SupplementReminderSettingsEntity(userId = userId)
            if (store.currentUserId() != userId) return
            rescheduleSupplementReminderWork(
                userId,
                settings.copy(
                    id = "supplement-reminders:$userId",
                    userId = userId,
                    timezoneId = java.time.ZoneId.systemDefault().id
                ),
                supplementReminderScheduler,
                recoveryScheduler
            )
        }
    }

    suspend fun cancel(userId: String) {
        mutex.withLock {
            supplementReminderScheduler.cancel(userId)
            recoveryScheduler.cancel(userId)
        }
    }

    suspend fun signOut(userId: String, clearSession: suspend () -> Unit) {
        mutex.withLock {
            supplementReminderScheduler.cancel(userId)
            recoveryScheduler.cancel(userId)
            clearSession()
        }
    }
}
