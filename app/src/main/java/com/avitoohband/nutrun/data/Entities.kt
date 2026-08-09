package com.avitoohband.nutrun.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.ZoneId

@Entity(tableName = "user_profile", indices = [Index("userId")])
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val email: String,
    val birthDate: String,
    val biologicalSex: String,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: String,
    val goal: String,
    val unitSystem: String,
    val calorieTarget: Int,
    val updatedAtMillis: Long
)

@Entity(tableName = "weight_entries", indices = [Index("userId"), Index("recordedAtMillis")])
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val weightKg: Double,
    val recordedAtMillis: Long,
    val pendingSync: Boolean = true
)

@Entity(tableName = "food_logs", indices = [Index("userId"), Index("localDate"), Index("updatedAtMillis")])
data class FoodLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val mealType: String,
    val catalogId: String?,
    val name: String,
    val brand: String?,
    val servingGrams: Double,
    val calories: Int,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val updatedAtMillis: Long,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "food_templates",
    indices = [Index("userId"), Index("kind"), Index("lastUsedAtMillis")]
)
data class FoodTemplateEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val kind: String,
    val payloadJson: String,
    val createdAtMillis: Long,
    val lastUsedAtMillis: Long,
    val useCount: Int = 0
)

@Entity(tableName = "water_logs", indices = [Index("userId"), Index("localDate")])
data class WaterLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val amountMl: Int,
    val loggedAtMillis: Long,
    val pendingSync: Boolean = true
)

@Entity(tableName = "hydration_plan", indices = [Index("userId")])
data class HydrationPlanEntity(
    @PrimaryKey val id: String = "",
    val userId: String = "",
    val goalMl: Int = 2_000,
    val servingMl: Int = 250,
    val wakingStartMinute: Int = 8 * 60,
    val wakingEndMinute: Int = 22 * 60,
    val intervalMinutes: Int = 60,
    val remindersEnabled: Boolean = true
)

@Entity(tableName = "training_reminder_settings", indices = [Index("userId", unique = true)])
data class TrainingReminderSettingsEntity(
    @PrimaryKey val id: String = "",
    val userId: String = "",
    val enabled: Boolean = true,
    val previousDayMinute: Int = 20 * 60,
    val sameDayMinute: Int = 8 * 60,
    val timezoneId: String = java.time.ZoneId.systemDefault().id
)

@Entity(
    tableName = "supplement_reminder_settings",
    indices = [Index("userId", unique = true)]
)
data class SupplementReminderSettingsEntity(
    @PrimaryKey val id: String = "",
    val userId: String = "",
    val enabled: Boolean = false,
    val timezoneId: String = ZoneId.systemDefault().id
)

@Entity(
    tableName = "reminder_delivery",
    indices = [
        Index("userId"),
        Index(value = ["userId", "reminderType", "trainingDate"], unique = true)
    ]
)
data class ReminderDeliveryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val reminderType: String,
    val trainingDate: String,
    val deliveredAtMillis: Long
)

@Entity(tableName = "walk_sessions", indices = [Index("userId"), Index("startedAtMillis"), Index("state")])
data class WalkSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val state: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val accumulatedDurationMillis: Long = 0,
    val resumedAtMillis: Long?,
    val distanceMeters: Double = 0.0,
    val stepBaseline: Long?,
    val stepOffset: Long = 0,
    val steps: Long?,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "walk_points",
    indices = [
        Index("userId"),
        Index("sessionId"),
        Index(value = ["sessionId", "recordedAtMillis"], unique = true)
    ]
)
data class WalkPointEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val recordedAtMillis: Long
)

@Entity(tableName = "training_state")
data class TrainingStateEntity(
    @PrimaryKey val userId: String,
    val payloadJson: String,
    val updatedAtMillis: Long,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "sync_operations",
    indices = [Index("userId"), Index(value = ["userId", "entityType", "entityId"], unique = true)]
)
data class SyncOperationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadJson: String?,
    val createdAtMillis: Long,
    val attempts: Int = 0
)
