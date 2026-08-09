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
import com.avitoohband.nutrun.reminders.HydrationScheduler
import com.avitoohband.nutrun.reminders.TrainingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    val profile: UserProfile? = null,
    val food: List<FoodLogEntity> = emptyList(),
    val recentFoods: List<FoodLogEntity> = emptyList(),
    val foodTemplates: List<FoodTemplateEntity> = emptyList(),
    val water: List<WaterLogEntity> = emptyList(),
    val hydrationPlan: HydrationPlanEntity = HydrationPlanEntity(),
    val trainingReminderSettings: TrainingReminderSettingsEntity = TrainingReminderSettingsEntity(),
    val weights: List<WeightEntryEntity> = emptyList(),
    val walks: List<WalkSessionEntity> = emptyList(),
    val activeWalk: WalkSessionEntity? = null
) {
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

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class NutRunViewModel @Inject constructor(
    private val repository: NutRunRepository,
    private val preferences: AppPreferences,
    private val foodSearchService: FoodSearchService,
    private val hydrationScheduler: HydrationScheduler,
    private val trainingReminderScheduler: TrainingReminderScheduler,
    private val billingManager: BillingManager,
    private val authenticationGateway: AuthenticationGateway,
    private val healthConnectManager: NutRunHealthConnectManager
) : ViewModel() {
    private val currentDate = MutableStateFlow(LocalDate.now())
    private val hydrationLogMutex = Mutex()
    private val _hydrationGoalCelebrations =
        MutableSharedFlow<HydrationGoalCelebration>(extraBufferCapacity = 1)
    val hydrationGoalCelebrations: SharedFlow<HydrationGoalCelebration> =
        _hydrationGoalCelebrations.asSharedFlow()
    private val food = currentDate.flatMapLatest(repository::food)
    private val water = currentDate.flatMapLatest(repository::water)

    val state: StateFlow<NutRunUiState> = combine(
        preferences.session,
        repository.profile,
        food,
        water,
        repository.hydrationPlan,
        repository.weights,
        repository.walks,
        repository.activeWalk,
        repository.trainingReminderSettings,
        repository.recentFoods,
        repository.foodTemplates
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        NutRunUiState(
            session = values[0] as SessionPreferences,
            profile = values[1] as UserProfile?,
            food = values[2] as List<FoodLogEntity>,
            water = values[3] as List<WaterLogEntity>,
            hydrationPlan = values[4] as HydrationPlanEntity,
            weights = values[5] as List<WeightEntryEntity>,
            walks = values[6] as List<WalkSessionEntity>,
            activeWalk = values[7] as WalkSessionEntity?,
            trainingReminderSettings = values[8] as TrainingReminderSettingsEntity,
            recentFoods = values[9] as List<FoodLogEntity>,
            foodTemplates = values[10] as List<FoodTemplateEntity>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutRunUiState())

    val routePoints: StateFlow<List<WalkPointEntity>> = repository.activeWalk
        .map(::activeRouteSessionId)
        .flatMapLatest { id -> id?.let(repository::walkPoints) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedWalkId = MutableStateFlow<String?>(null)
    val selectedWalkId: StateFlow<String?> = _selectedWalkId
    val selectedWalkRoutePoints: StateFlow<List<WalkPointEntity>> = selectedWalkId
        .flatMapLatest { id -> id?.let(repository::walkPoints) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val foodSearchResults = MutableStateFlow<List<FoodCatalogItem>>(emptyList())
    val foodSearchBusy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val billingState: StateFlow<BillingUiState> = billingManager.state
    val healthConnectState = MutableStateFlow(
        HealthConnectUiState(available = healthConnectManager.isAvailable())
    )
    val healthConnectPermissions: Set<String> = healthConnectManager.permissions
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            refreshHealthConnectStatus()
        }
        viewModelScope.launch {
            preferences.session
                .map { it.authenticatedUserId }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { userId ->
                    if (!isDemoAccount(userId)) {
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
        viewModelScope.launch {
            state.map {
                Triple(
                    it.session.authenticatedUserId,
                    it.hydrationPlan,
                    it.trainingReminderSettings
                )
            }.distinctUntilChanged().collect { (userId, hydration, training) ->
                if (userId != null) {
                    hydrationScheduler.schedule(hydration.copy(userId = userId))
                    trainingReminderScheduler.schedule(
                        userId,
                        training.copy(userId = userId, timezoneId = java.time.ZoneId.systemDefault().id)
                    )
                }
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
        if (isDemoAccount(state.value.session.authenticatedUserId)) {
            foodSearchResults.value = emptyList()
            message.value = "Online food search is unavailable in the local demo account."
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            foodSearchBusy.value = true
            runCatching { foodSearchService.search(query) }
                .onSuccess { foodSearchResults.value = it }
                .onFailure { message.value = it.message ?: "Food search is unavailable." }
            foodSearchBusy.value = false
        }
    }

    fun saveFood(item: FoodCatalogItem, mealType: MealType, id: String? = null) {
        viewModelScope.launch { repository.saveFood(item, mealType, id = id ?: java.util.UUID.randomUUID().toString()) }
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
        viewModelScope.launch { repository.deleteFoodTemplate(template) }
    }

    fun deleteFood(entry: FoodLogEntity) {
        viewModelScope.launch { repository.deleteFood(entry) }
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

    fun signOut() {
        clearSelectedWalk()
        viewModelScope.launch {
            val userId = state.value.session.authenticatedUserId
            repository.updateWalkState(com.avitoohband.nutrun.domain.WalkState.PAUSED)
            if (!isDemoAccount(userId)) authenticationGateway.signOut()
            preferences.signOut()
        }
    }

    fun selectCompletedWalk(id: String) {
        _selectedWalkId.value = id
    }

    fun clearSelectedWalk() {
        _selectedWalkId.value = null
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
