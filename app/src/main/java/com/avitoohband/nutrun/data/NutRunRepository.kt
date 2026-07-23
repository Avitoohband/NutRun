package com.avitoohband.nutrun.data

import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.FoodCatalogItem
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.MealType
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.domain.WalkState
import com.avitoohband.nutrun.sync.SyncScheduler
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.json.JSONArray

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class NutRunRepository @Inject constructor(
    private val dao: NutRunDao,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler
) {
    private val userId = preferences.session
        .map { it.authenticatedUserId }
        .distinctUntilChanged()

    val profile = userId.flatMapLatest { id ->
        id?.let(dao::observeProfile) ?: flowOf(null)
    }.map { it?.toDomain() }

    val weights = userId.flatMapLatest { id ->
        id?.let(dao::observeWeights) ?: flowOf(emptyList())
    }

    val hydrationPlan = userId.flatMapLatest { id ->
        id?.let(dao::observeHydrationPlan) ?: flowOf(null)
    }.map { it ?: HydrationPlanEntity() }

    val walks = userId.flatMapLatest { id ->
        id?.let(dao::observeWalks) ?: flowOf(emptyList())
    }

    val activeWalk = userId.flatMapLatest { id ->
        id?.let(dao::observeActiveWalk) ?: flowOf(null)
    }

    fun food(date: LocalDate): Flow<List<FoodLogEntity>> = userId.flatMapLatest { id ->
        id?.let { dao.observeFood(it, date.toString()) } ?: flowOf(emptyList())
    }

    fun water(date: LocalDate): Flow<List<WaterLogEntity>> = userId.flatMapLatest { id ->
        id?.let { dao.observeWater(it, date.toString()) } ?: flowOf(emptyList())
    }

    fun walkPoints(sessionId: String): Flow<List<WalkPointEntity>> = userId.flatMapLatest { id ->
        id?.let { dao.observeWalkPoints(it, sessionId) } ?: flowOf(emptyList())
    }

    fun trainingState(userId: String): Flow<TrainingStateEntity?> = dao.observeTrainingState(userId)

    suspend fun claimLegacyData(userId: String, email: String) {
        val legacyEmail = dao.legacyProfileEmail()
        if (
            dao.profile(userId) == null &&
            legacyEmail != null &&
            legacyEmail.equals(email.trim(), ignoreCase = true)
        ) {
            dao.claimLegacyData(userId)
            dao.profile(userId)?.let {
                queue(userId, "profile", it.id, "UPSERT", it.toJson())
            }
            dao.weights(userId).forEach {
                queue(userId, "weightEntries", it.id, "UPSERT", it.toJson())
            }
            dao.foods(userId).forEach {
                queue(userId, "foodLogs", it.id, "UPSERT", it.toJson())
            }
            dao.water(userId).forEach {
                queue(userId, "waterLogs", it.id, "UPSERT", it.toJson())
            }
            dao.hydrationPlan(userId)?.let {
                queue(userId, "hydrationPlan", it.id, "UPSERT", it.toJson())
            }
            dao.walks(userId).forEach { walk ->
                val route = JSONArray().apply {
                    dao.walkPoints(userId, walk.id).forEach { point ->
                        put(
                            JSONObject()
                                .put("latitude", point.latitude)
                                .put("longitude", point.longitude)
                                .put("accuracyMeters", point.accuracyMeters)
                                .put("recordedAtMillis", point.recordedAtMillis)
                        )
                    }
                }
                queue(
                    userId,
                    "walks",
                    walk.id,
                    "UPSERT",
                    JSONObject()
                        .put("startedAtMillis", walk.startedAtMillis)
                        .put("endedAtMillis", walk.endedAtMillis)
                        .put("durationMillis", walk.accumulatedDurationMillis)
                        .put("distanceMeters", walk.distanceMeters)
                        .put("steps", walk.steps)
                        .put("route", route)
                )
            }
        }
    }

    fun synchronize() = syncScheduler.schedule()

    suspend fun saveProfile(profile: UserProfile) {
        val accountId = requireUserId()
        val now = System.currentTimeMillis()
        val previous = dao.profile(accountId)
        val entity = UserProfileEntity(
            id = "profile:$accountId",
            userId = accountId,
            email = profile.email,
            birthDate = profile.birthDate.toString(),
            biologicalSex = profile.biologicalSex.name,
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            activityLevel = profile.activityLevel.name,
            goal = profile.goal.name,
            unitSystem = profile.unitSystem.name,
            calorieTarget = profile.calorieTarget,
            updatedAtMillis = now
        )
        dao.saveProfile(entity)
        queue(accountId, "profile", entity.id, "UPSERT", entity.toJson())
        if (previous == null || previous.weightKg != profile.weightKg) {
            val weight = WeightEntryEntity(
                id = UUID.randomUUID().toString(),
                userId = accountId,
                weightKg = profile.weightKg,
                recordedAtMillis = now
            )
            dao.saveWeight(weight)
            queue(accountId, "weightEntries", weight.id, "UPSERT", weight.toJson())
        }
        if (dao.hydrationPlan(accountId) == null) {
            saveHydrationPlan(defaultHydrationPlan(accountId))
        }
    }

    suspend fun saveFood(
        item: FoodCatalogItem,
        mealType: MealType,
        date: LocalDate = LocalDate.now(),
        id: String = UUID.randomUUID().toString()
    ) {
        val accountId = requireUserId()
        val entity = FoodLogEntity(
            id = id,
            userId = accountId,
            localDate = date.toString(),
            mealType = mealType.name,
            catalogId = item.id,
            name = item.name.trim(),
            brand = item.brand?.trim()?.takeIf(String::isNotEmpty),
            servingGrams = item.servingGrams,
            calories = item.calories,
            proteinGrams = item.proteinGrams,
            carbohydrateGrams = item.carbohydrateGrams,
            fatGrams = item.fatGrams,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.saveFood(entity)
        queue(accountId, "foodLogs", entity.id, "UPSERT", entity.toJson())
    }

    suspend fun duplicateFood(id: String) {
        val accountId = requireUserId()
        val source = dao.foodById(accountId, id) ?: return
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            updatedAtMillis = System.currentTimeMillis(),
            pendingSync = true
        )
        dao.saveFood(duplicate)
        queue(accountId, "foodLogs", duplicate.id, "UPSERT", duplicate.toJson())
    }

    suspend fun deleteFood(entry: FoodLogEntity) {
        val accountId = requireUserId()
        require(entry.userId == accountId)
        dao.deleteFood(entry)
        queue(accountId, "foodLogs", entry.id, "DELETE", null)
    }

    suspend fun addWater(amountMl: Int, date: LocalDate = LocalDate.now()) {
        require(amountMl in 1..5_000)
        val accountId = requireUserId()
        val entry = WaterLogEntity(
            id = UUID.randomUUID().toString(),
            userId = accountId,
            localDate = date.toString(),
            amountMl = amountMl,
            loggedAtMillis = System.currentTimeMillis()
        )
        dao.saveWater(entry)
        queue(accountId, "waterLogs", entry.id, "UPSERT", entry.toJson())
    }

    suspend fun saveHydrationPlan(plan: HydrationPlanEntity) {
        require(plan.goalMl in 250..10_000)
        require(plan.servingMl in 50..2_000)
        require(plan.intervalMinutes >= 15)
        require(plan.wakingEndMinute > plan.wakingStartMinute)
        val accountId = requireUserId()
        val scoped = plan.copy(id = "hydration:$accountId", userId = accountId)
        dao.saveHydrationPlan(scoped)
        queue(accountId, "hydrationPlan", scoped.id, "UPSERT", scoped.toJson())
    }

    suspend fun waterTotal(date: LocalDate = LocalDate.now()): Int =
        dao.waterTotal(requireUserId(), date.toString())

    suspend fun updateWalkState(state: WalkState) {
        val accountId = requireUserId()
        val walk = dao.activeWalk(accountId) ?: return
        val now = System.currentTimeMillis()
        val updated = when (state) {
            WalkState.ACTIVE -> walk.copy(state = state.name, resumedAtMillis = now)
            WalkState.PAUSED -> walk.copy(
                state = state.name,
                accumulatedDurationMillis = walk.accumulatedDurationMillis +
                    (walk.resumedAtMillis?.let { now - it } ?: 0),
                resumedAtMillis = null
            )
            WalkState.FINISHED -> walk.copy(
                state = state.name,
                endedAtMillis = now,
                accumulatedDurationMillis = walk.accumulatedDurationMillis +
                    (walk.resumedAtMillis?.let { now - it } ?: 0),
                resumedAtMillis = null
            )
        }
        dao.updateWalk(updated)
    }

    suspend fun saveTrainingState(userId: String, payloadJson: String) {
        require(userId == requireUserId())
        val entity = TrainingStateEntity(
            userId = userId,
            payloadJson = payloadJson,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.saveTrainingState(entity)
        queue(
            userId,
            "trainingState",
            userId,
            "UPSERT",
            JSONObject(payloadJson).put("updatedAtMillis", entity.updatedAtMillis)
        )
    }

    suspend fun clearAccountData(userId: String) = dao.clearAccountData(userId)

    private suspend fun requireUserId(): String =
        preferences.currentSession().authenticatedUserId
            ?: throw IllegalStateException("Sign in before changing account data.")

    private suspend fun queue(
        userId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payload: JSONObject?
    ) {
        dao.enqueueSync(
            SyncOperationEntity(
                id = "$userId:$entityType:$entityId",
                userId = userId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadJson = payload?.toString(),
                createdAtMillis = System.currentTimeMillis()
            )
        )
        syncScheduler.schedule()
    }
}

fun defaultHydrationPlan(userId: String = "") = HydrationPlanEntity(
    id = if (userId.isBlank()) "" else "hydration:$userId",
    userId = userId
)

private fun UserProfileEntity.toDomain() = UserProfile(
    email = email,
    birthDate = LocalDate.parse(birthDate),
    biologicalSex = BiologicalSex.valueOf(biologicalSex),
    heightCm = heightCm,
    weightKg = weightKg,
    activityLevel = ActivityLevel.valueOf(activityLevel),
    goal = HealthGoal.valueOf(goal),
    unitSystem = UnitSystem.valueOf(unitSystem),
    calorieTarget = calorieTarget
)

private fun UserProfileEntity.toJson() = JSONObject()
    .put("email", email)
    .put("birthDate", birthDate)
    .put("biologicalSex", biologicalSex)
    .put("heightCm", heightCm)
    .put("weightKg", weightKg)
    .put("activityLevel", activityLevel)
    .put("goal", goal)
    .put("unitSystem", unitSystem)
    .put("calorieTarget", calorieTarget)
    .put("updatedAtMillis", updatedAtMillis)

private fun WeightEntryEntity.toJson() = JSONObject()
    .put("weightKg", weightKg)
    .put("recordedAtMillis", recordedAtMillis)

private fun FoodLogEntity.toJson() = JSONObject()
    .put("date", localDate)
    .put("mealType", mealType)
    .put("catalogId", catalogId)
    .put("name", name)
    .put("brand", brand)
    .put("servingGrams", servingGrams)
    .put("calories", calories)
    .put("proteinGrams", proteinGrams)
    .put("carbohydrateGrams", carbohydrateGrams)
    .put("fatGrams", fatGrams)
    .put("updatedAtMillis", updatedAtMillis)

private fun WaterLogEntity.toJson() = JSONObject()
    .put("date", localDate)
    .put("amountMl", amountMl)
    .put("loggedAtMillis", loggedAtMillis)

private fun HydrationPlanEntity.toJson() = JSONObject()
    .put("goalMl", goalMl)
    .put("servingMl", servingMl)
    .put("wakingStartMinute", wakingStartMinute)
    .put("wakingEndMinute", wakingEndMinute)
    .put("intervalMinutes", intervalMinutes)
    .put("remindersEnabled", remindersEnabled)
