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
import com.avitoohband.nutrun.data.FoodSearchService
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.NutRunRepository
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WaterLogEntity
import com.avitoohband.nutrun.data.WeightEntryEntity
import com.avitoohband.nutrun.data.defaultHydrationPlan
import com.avitoohband.nutrun.domain.DailyNutritionSummary
import com.avitoohband.nutrun.domain.FoodCatalogItem
import com.avitoohband.nutrun.domain.MealType
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.domain.calculateHealthEstimate
import com.avitoohband.nutrun.reminders.HydrationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class NutRunUiState(
    val session: SessionPreferences = SessionPreferences(),
    val profile: UserProfile? = null,
    val food: List<FoodLogEntity> = emptyList(),
    val water: List<WaterLogEntity> = emptyList(),
    val hydrationPlan: HydrationPlanEntity = HydrationPlanEntity(),
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

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class NutRunViewModel @Inject constructor(
    private val repository: NutRunRepository,
    private val preferences: AppPreferences,
    private val foodSearchService: FoodSearchService,
    private val hydrationScheduler: HydrationScheduler,
    private val billingManager: BillingManager,
    private val authenticationGateway: AuthenticationGateway
) : ViewModel() {
    private val currentDate = MutableStateFlow(LocalDate.now())
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
        repository.activeWalk
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
            activeWalk = values[7] as WalkSessionEntity?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutRunUiState())

    val routePoints: StateFlow<List<WalkPointEntity>> = combine(
        repository.activeWalk,
        repository.walks
    ) { active, walks -> active?.id ?: walks.firstOrNull()?.id }
        .flatMapLatest { id -> id?.let(repository::walkPoints) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val foodSearchResults = MutableStateFlow<List<FoodCatalogItem>>(emptyList())
    val foodSearchBusy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val billingState: StateFlow<BillingUiState> = billingManager.state
    private var searchJob: Job? = null

    init {
        billingManager.connect()
        viewModelScope.launch {
            preferences.session
                .map { it.authenticatedUserId }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { repository.synchronize() }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                currentDate.value = LocalDate.now()
            }
        }
    }

    fun authenticate(email: String, password: String, createAccount: Boolean) {
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

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            runCatching { repository.saveProfile(profile) }
                .onSuccess {
                    state.value.session.authenticatedUserId?.let { userId ->
                        hydrationScheduler.schedule(defaultHydrationPlan(userId))
                    }
                }
                .onFailure { message.value = it.message ?: "Could not save profile." }
        }
    }

    fun searchFood(query: String) {
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

    fun deleteFood(entry: FoodLogEntity) {
        viewModelScope.launch { repository.deleteFood(entry) }
    }

    fun addWater(amountMl: Int) {
        viewModelScope.launch { repository.addWater(amountMl) }
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
        billingManager.launch(activity, productId)
    }

    fun restorePurchases() {
        billingManager.restore()
    }

    fun signOut() {
        viewModelScope.launch {
            repository.updateWalkState(com.avitoohband.nutrun.domain.WalkState.PAUSED)
            authenticationGateway.signOut()
            preferences.signOut()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val userId = state.value.session.authenticatedUserId
                ?: return@launch
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
