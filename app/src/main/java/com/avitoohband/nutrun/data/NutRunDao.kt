package com.avitoohband.nutrun.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutRunDao {
    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    suspend fun profile(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWeight(entry: WeightEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWeights(entries: List<WeightEntryEntity>)

    @Query("SELECT * FROM weight_entries WHERE userId = :userId ORDER BY recordedAtMillis DESC")
    fun observeWeights(userId: String): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE userId = :userId ORDER BY recordedAtMillis DESC")
    suspend fun weights(userId: String): List<WeightEntryEntity>

    @Query(
        "SELECT * FROM food_logs WHERE userId = :userId AND localDate = :date " +
            "ORDER BY updatedAtMillis DESC"
    )
    fun observeFood(userId: String, date: String): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs WHERE userId = :userId")
    suspend fun foods(userId: String): List<FoodLogEntity>

    @Query(
        "SELECT * FROM food_logs WHERE userId = :userId " +
            "ORDER BY updatedAtMillis DESC LIMIT 30"
    )
    fun observeRecentFoods(userId: String): Flow<List<FoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFood(entry: FoodLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFoods(entries: List<FoodLogEntity>)

    @Delete
    suspend fun deleteFood(entry: FoodLogEntity)

    @Query("SELECT * FROM food_logs WHERE userId = :userId AND id = :id")
    suspend fun foodById(userId: String, id: String): FoodLogEntity?

    @Query(
        "SELECT * FROM food_templates WHERE userId = :userId " +
            "ORDER BY kind, useCount DESC, lastUsedAtMillis DESC"
    )
    fun observeFoodTemplates(userId: String): Flow<List<FoodTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFoodTemplate(template: FoodTemplateEntity)

    @Delete
    suspend fun deleteFoodTemplate(template: FoodTemplateEntity)

    @Query("SELECT * FROM nutrition_targets WHERE userId = :userId LIMIT 1")
    fun observeNutritionTarget(userId: String): Flow<NutritionTargetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNutritionTarget(target: NutritionTargetEntity)

    @Query(
        "SELECT * FROM water_logs WHERE userId = :userId AND localDate = :date " +
            "ORDER BY loggedAtMillis DESC"
    )
    fun observeWater(userId: String, date: String): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs WHERE userId = :userId")
    suspend fun water(userId: String): List<WaterLogEntity>

    @Query(
        "SELECT COALESCE(SUM(amountMl), 0) FROM water_logs " +
            "WHERE userId = :userId AND localDate = :date"
    )
    suspend fun waterTotal(userId: String, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWater(entry: WaterLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWater(entries: List<WaterLogEntity>)

    @Query("SELECT * FROM hydration_plan WHERE userId = :userId LIMIT 1")
    fun observeHydrationPlan(userId: String): Flow<HydrationPlanEntity?>

    @Query("SELECT * FROM hydration_plan WHERE userId = :userId LIMIT 1")
    suspend fun hydrationPlan(userId: String): HydrationPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHydrationPlan(plan: HydrationPlanEntity)

    @Transaction
    suspend fun saveQuickServingAndWater(plan: HydrationPlanEntity, entry: WaterLogEntity) {
        saveHydrationPlan(plan)
        saveWater(entry)
    }

    @Query("SELECT * FROM training_reminder_settings WHERE userId = :userId LIMIT 1")
    fun observeTrainingReminderSettings(userId: String): Flow<TrainingReminderSettingsEntity?>

    @Query("SELECT * FROM training_reminder_settings WHERE userId = :userId LIMIT 1")
    suspend fun trainingReminderSettings(userId: String): TrainingReminderSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrainingReminderSettings(settings: TrainingReminderSettingsEntity)

    @Query("SELECT * FROM supplement_reminder_settings WHERE userId = :userId LIMIT 1")
    fun observeSupplementReminderSettings(userId: String): Flow<SupplementReminderSettingsEntity?>

    @Query("SELECT * FROM supplement_reminder_settings WHERE userId = :userId LIMIT 1")
    suspend fun supplementReminderSettings(userId: String): SupplementReminderSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSupplementReminderSettings(settings: SupplementReminderSettingsEntity)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM reminder_delivery WHERE userId = :userId " +
            "AND reminderType = :type AND trainingDate = :trainingDate AND state = 'DELIVERED')"
    )
    suspend fun reminderDelivered(userId: String, type: String, trainingDate: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordReminderDelivery(delivery: ReminderDeliveryEntity): Long

    @Query("SELECT * FROM reminder_delivery WHERE id = :id LIMIT 1")
    suspend fun reminderDelivery(id: String): ReminderDeliveryEntity?

    @Query(
        "DELETE FROM reminder_delivery WHERE id = :id AND state = 'PENDING' " +
            "AND claimedAtMillis <= :expiredBeforeMillis"
    )
    suspend fun deleteExpiredReminderDeliveryClaim(id: String, expiredBeforeMillis: Long): Int

    @Query(
        "UPDATE reminder_delivery SET state = 'DELIVERED', deliveredAtMillis = :deliveredAtMillis " +
            "WHERE id = :id AND state = 'POSTED'"
    )
    suspend fun finalizeReminderDelivery(id: String, deliveredAtMillis: Long): Int

    @Query("UPDATE reminder_delivery SET state = 'POSTED' WHERE id = :id AND state = 'PENDING'")
    suspend fun markReminderDeliveryPosted(id: String): Int

    @Query("DELETE FROM reminder_delivery WHERE id = :id AND state = 'PENDING'")
    suspend fun releaseReminderDeliveryClaim(id: String): Int

    @Transaction
    suspend fun acquireSupplementDeliveryClaim(
        claim: ReminderDeliveryEntity,
        expiredBeforeMillis: Long
    ): String {
        when (reminderDelivery(claim.id)?.state) {
            ReminderDeliveryEntity.STATE_DELIVERED -> return "Delivered"
            ReminderDeliveryEntity.STATE_PENDING -> {
                deleteExpiredReminderDeliveryClaim(claim.id, expiredBeforeMillis)
            }
            "POSTED" -> return "Posted"
        }
        return if (recordReminderDelivery(claim) != -1L) "Acquired" else "Pending"
    }

    @Query("SELECT * FROM walk_sessions WHERE userId = :userId ORDER BY startedAtMillis DESC")
    fun observeWalks(userId: String): Flow<List<WalkSessionEntity>>

    @Query("SELECT * FROM walk_sessions WHERE userId = :userId")
    suspend fun walks(userId: String): List<WalkSessionEntity>

    @Query(
        "SELECT * FROM walk_sessions WHERE userId = :userId AND state != 'FINISHED' " +
            "ORDER BY startedAtMillis DESC LIMIT 1"
    )
    fun observeActiveWalk(userId: String): Flow<WalkSessionEntity?>

    @Query(
        "SELECT * FROM walk_sessions WHERE userId = :userId AND state != 'FINISHED' " +
            "ORDER BY startedAtMillis DESC LIMIT 1"
    )
    suspend fun activeWalk(userId: String): WalkSessionEntity?

    @Query("SELECT * FROM walk_sessions WHERE state != 'FINISHED' ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun activeWalkForServiceRestore(): WalkSessionEntity?

    @Query("SELECT * FROM walk_sessions WHERE userId = :userId AND id = :id")
    suspend fun walk(userId: String, id: String): WalkSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWalk(walk: WalkSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWalks(walks: List<WalkSessionEntity>)

    @Update
    suspend fun updateWalk(walk: WalkSessionEntity)

    @Query(
        "SELECT * FROM walk_points WHERE userId = :userId AND sessionId = :sessionId " +
            "ORDER BY recordedAtMillis"
    )
    fun observeWalkPoints(userId: String, sessionId: String): Flow<List<WalkPointEntity>>

    @Query(
        "SELECT * FROM walk_points WHERE userId = :userId AND sessionId = :sessionId " +
            "ORDER BY recordedAtMillis"
    )
    suspend fun walkPoints(userId: String, sessionId: String): List<WalkPointEntity>

    @Query(
        "SELECT * FROM walk_points WHERE userId = :userId AND sessionId = :sessionId " +
            "ORDER BY recordedAtMillis DESC LIMIT 1"
    )
    suspend fun lastWalkPoint(userId: String, sessionId: String): WalkPointEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveWalkPoint(point: WalkPointEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveWalkPoints(points: List<WalkPointEntity>)

    @Query("SELECT * FROM training_state WHERE userId = :userId")
    fun observeTrainingState(userId: String): Flow<TrainingStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrainingState(state: TrainingStateEntity)

    @Transaction
    suspend fun applyRemoteSnapshot(
        userId: String,
        profile: UserProfileEntity?,
        weights: List<WeightEntryEntity>,
        foods: List<FoodLogEntity>,
        water: List<WaterLogEntity>,
        hydrationPlan: HydrationPlanEntity?,
        walks: List<WalkSessionEntity>,
        walkPoints: List<WalkPointEntity>,
        trainingState: TrainingStateEntity?
    ) {
        clearProfile(userId)
        profile?.let { saveProfile(it) }
        clearWeights(userId)
        saveWeights(weights)
        clearFood(userId)
        clearFoodTemplates(userId)
        saveFoods(foods)
        clearWater(userId)
        saveWater(water)
        clearHydration(userId)
        hydrationPlan?.let { saveHydrationPlan(it) }
        saveWalks(walks)
        saveWalkPoints(walkPoints)
        clearTraining(userId)
        trainingState?.let { saveTrainingState(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSync(operation: SyncOperationEntity)

    @Query(
        "SELECT * FROM sync_operations WHERE userId = :userId " +
            "ORDER BY createdAtMillis LIMIT :limit"
    )
    suspend fun pendingSync(userId: String, limit: Int = 50): List<SyncOperationEntity>

    @Query("DELETE FROM sync_operations WHERE id = :id")
    suspend fun completeSync(id: String)

    @Query(
        "UPDATE weight_entries SET pendingSync = 0 " +
            "WHERE userId = :userId AND id = :entityId"
    )
    suspend fun markWeightSynced(userId: String, entityId: String)

    @Query(
        "UPDATE food_logs SET pendingSync = 0 " +
            "WHERE userId = :userId AND id = :entityId"
    )
    suspend fun markFoodSynced(userId: String, entityId: String)

    @Query(
        "UPDATE water_logs SET pendingSync = 0 " +
            "WHERE userId = :userId AND id = :entityId"
    )
    suspend fun markWaterSynced(userId: String, entityId: String)

    @Query(
        "UPDATE walk_sessions SET pendingSync = 0 " +
            "WHERE userId = :userId AND id = :entityId"
    )
    suspend fun markWalkSynced(userId: String, entityId: String)

    @Query("UPDATE training_state SET pendingSync = 0 WHERE userId = :userId")
    suspend fun markTrainingSynced(userId: String)

    @Transaction
    suspend fun completeSync(operation: SyncOperationEntity) {
        if (operation.operation != "DELETE") {
            when (operation.entityType) {
                "weightEntries" -> markWeightSynced(operation.userId, operation.entityId)
                "foodLogs" -> markFoodSynced(operation.userId, operation.entityId)
                "waterLogs" -> markWaterSynced(operation.userId, operation.entityId)
                "walks" -> markWalkSynced(operation.userId, operation.entityId)
                "trainingState" -> markTrainingSynced(operation.userId)
            }
        }
        completeSync(operation.id)
    }

    @Query("UPDATE sync_operations SET attempts = attempts + 1 WHERE id = :id")
    suspend fun recordSyncAttempt(id: String)

    @Query("UPDATE user_profile SET userId = :userId, id = :profileId WHERE userId = :legacy")
    suspend fun claimLegacyProfile(userId: String, profileId: String, legacy: String)

    @Query("SELECT email FROM user_profile WHERE userId = :legacy LIMIT 1")
    suspend fun legacyProfileEmail(legacy: String = NutRunDatabase.LEGACY_USER_ID): String?

    @Query("UPDATE weight_entries SET userId = :userId WHERE userId = :legacy")
    suspend fun claimLegacyWeights(userId: String, legacy: String)

    @Query("UPDATE food_logs SET userId = :userId WHERE userId = :legacy")
    suspend fun claimLegacyFood(userId: String, legacy: String)

    @Query("UPDATE water_logs SET userId = :userId WHERE userId = :legacy")
    suspend fun claimLegacyWater(userId: String, legacy: String)

    @Query("UPDATE hydration_plan SET userId = :userId, id = :planId WHERE userId = :legacy")
    suspend fun claimLegacyHydration(userId: String, planId: String, legacy: String)

    @Query("UPDATE walk_sessions SET userId = :userId WHERE userId = :legacy")
    suspend fun claimLegacyWalks(userId: String, legacy: String)

    @Query("UPDATE walk_points SET userId = :userId WHERE userId = :legacy")
    suspend fun claimLegacyWalkPoints(userId: String, legacy: String)

    @Transaction
    suspend fun claimLegacyData(userId: String) {
        val legacy = NutRunDatabase.LEGACY_USER_ID
        claimLegacyProfile(userId, "profile:$userId", legacy)
        claimLegacyWeights(userId, legacy)
        claimLegacyFood(userId, legacy)
        claimLegacyWater(userId, legacy)
        claimLegacyHydration(userId, "hydration:$userId", legacy)
        claimLegacyWalks(userId, legacy)
        claimLegacyWalkPoints(userId, legacy)
    }

    @Transaction
    suspend fun clearAccountData(userId: String) {
        clearWalkPoints(userId)
        clearWalks(userId)
        clearWater(userId)
        clearFood(userId)
        clearWeights(userId)
        clearProfile(userId)
        clearHydration(userId)
        clearTraining(userId)
        clearTrainingReminderSettings(userId)
        clearSupplementReminderSettings(userId)
        clearReminderDeliveries(userId)
        clearSync(userId)
    }

    @Query("DELETE FROM walk_points WHERE userId = :userId")
    suspend fun clearWalkPoints(userId: String)

    @Query("DELETE FROM walk_sessions WHERE userId = :userId")
    suspend fun clearWalks(userId: String)

    @Query("DELETE FROM water_logs WHERE userId = :userId")
    suspend fun clearWater(userId: String)

    @Query("DELETE FROM food_logs WHERE userId = :userId")
    suspend fun clearFood(userId: String)

    @Query("DELETE FROM food_templates WHERE userId = :userId")
    suspend fun clearFoodTemplates(userId: String)

    @Query("DELETE FROM weight_entries WHERE userId = :userId")
    suspend fun clearWeights(userId: String)

    @Query("DELETE FROM user_profile WHERE userId = :userId")
    suspend fun clearProfile(userId: String)

    @Query("DELETE FROM hydration_plan WHERE userId = :userId")
    suspend fun clearHydration(userId: String)

    @Query("DELETE FROM training_state WHERE userId = :userId")
    suspend fun clearTraining(userId: String)

    @Query("DELETE FROM training_reminder_settings WHERE userId = :userId")
    suspend fun clearTrainingReminderSettings(userId: String)

    @Query("DELETE FROM supplement_reminder_settings WHERE userId = :userId")
    suspend fun clearSupplementReminderSettings(userId: String)

    @Query("DELETE FROM reminder_delivery WHERE userId = :userId")
    suspend fun clearReminderDeliveries(userId: String)

    @Query("DELETE FROM sync_operations WHERE userId = :userId")
    suspend fun clearSync(userId: String)
}
