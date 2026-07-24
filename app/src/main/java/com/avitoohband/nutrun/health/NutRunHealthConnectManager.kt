package com.avitoohband.nutrun.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MealType as HealthMealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.avitoohband.nutrun.WorkoutRecord
import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WaterLogEntity
import com.avitoohband.nutrun.domain.MealType
import com.avitoohband.nutrun.domain.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class HealthConnectSyncResult(
    val importedWeightKg: Double?,
    val importedSteps: Long,
    val exportedRecords: Int
)

@Singleton
class NutRunHealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient {
        check(isAvailable()) { "Health Connect is not available on this device." }
        return HealthConnectClient.getOrCreate(context)
    }

    suspend fun hasAllPermissions(): Boolean =
        isAvailable() &&
            client().permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun synchronize(
        profile: UserProfile,
        water: List<WaterLogEntity>,
        food: List<FoodLogEntity>,
        workouts: List<WorkoutRecord>,
        walks: List<WalkSessionEntity>,
        date: LocalDate = LocalDate.now()
    ): HealthConnectSyncResult {
        check(hasAllPermissions()) { "Health Connect permission is required." }
        val healthClient = client()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val steps = healthClient.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )[StepsRecord.COUNT_TOTAL] ?: 0L
        val latestWeight = healthClient.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                ascendingOrder = false,
                pageSize = 1
            )
        ).records.firstOrNull()?.weight?.inKilograms
        val records = buildExportRecords(
            profile = latestWeight?.let { profile.copy(weightKg = it) } ?: profile,
            water = water,
            food = food,
            workouts = workouts,
            walks = walks
        )
        if (records.isNotEmpty()) healthClient.insertRecords(records)

        return HealthConnectSyncResult(latestWeight, steps, records.size)
    }

    private fun buildExportRecords(
        profile: UserProfile,
        water: List<WaterLogEntity>,
        food: List<FoodLogEntity>,
        workouts: List<WorkoutRecord>,
        walks: List<WalkSessionEntity>
    ): List<Record> {
        val records = mutableListOf<Record>()
        val now = Instant.now()
        records += WeightRecord(
            time = now,
            zoneOffset = zoneOffset(now),
            weight = Mass.kilograms(profile.weightKg),
            metadata = metadata(
                clientRecordId = "nutrun-profile-weight",
                clientRecordVersion = now.toEpochMilli()
            )
        )
        water.forEach { entry ->
            val end = Instant.ofEpochMilli(entry.loggedAtMillis)
            records += HydrationRecord(
                startTime = end.minusSeconds(1),
                startZoneOffset = zoneOffset(end),
                endTime = end,
                endZoneOffset = zoneOffset(end),
                volume = Volume.milliliters(entry.amountMl.toDouble()),
                metadata = metadata(
                    clientRecordId = "nutrun-water-${entry.id}",
                    clientRecordVersion = entry.loggedAtMillis
                )
            )
        }
        food.forEach { entry ->
            val start = Instant.ofEpochMilli(entry.updatedAtMillis)
            records += NutritionRecord(
                startTime = start,
                startZoneOffset = zoneOffset(start),
                endTime = start.plusSeconds(1),
                endZoneOffset = zoneOffset(start),
                name = entry.name,
                mealType = mealType(entry.mealType),
                energy = Energy.kilocalories(entry.calories.toDouble()),
                protein = Mass.grams(entry.proteinGrams),
                totalCarbohydrate = Mass.grams(entry.carbohydrateGrams),
                totalFat = Mass.grams(entry.fatGrams),
                metadata = metadata(
                    clientRecordId = "nutrun-food-${entry.id}",
                    clientRecordVersion = entry.updatedAtMillis
                )
            )
        }
        workouts.take(100).forEach { workout ->
            val start = Instant.ofEpochMilli(workout.startedAtMillis)
            val end = Instant.ofEpochMilli(workout.finishedAtMillis)
                .coerceAtLeast(start.plusSeconds(1))
            records += ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zoneOffset(start),
                endTime = end,
                endZoneOffset = zoneOffset(end),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                title = workout.sessionName,
                metadata = metadata(
                    clientRecordId = "nutrun-workout-${workout.id}",
                    clientRecordVersion = workout.finishedAtMillis
                )
            )
        }
        walks.filter { it.endedAtMillis != null }.take(100).forEach { walk ->
            val start = Instant.ofEpochMilli(walk.startedAtMillis)
            val end = Instant.ofEpochMilli(walk.endedAtMillis!!)
                .coerceAtLeast(start.plusSeconds(1))
            records += ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zoneOffset(start),
                endTime = end,
                endZoneOffset = zoneOffset(end),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                title = "NutRun walk",
                metadata = metadata(
                    clientRecordId = "nutrun-walk-${walk.id}",
                    clientRecordVersion = walk.endedAtMillis
                )
            )
        }
        return records
    }

    private fun mealType(value: String): Int = when (runCatching { MealType.valueOf(value) }.getOrNull()) {
        MealType.BREAKFAST -> HealthMealType.MEAL_TYPE_BREAKFAST
        MealType.LUNCH -> HealthMealType.MEAL_TYPE_LUNCH
        MealType.DINNER -> HealthMealType.MEAL_TYPE_DINNER
        MealType.SNACK -> HealthMealType.MEAL_TYPE_SNACK
        null -> HealthMealType.MEAL_TYPE_UNKNOWN
    }

    private fun metadata(clientRecordId: String, clientRecordVersion: Long) = Metadata(
        clientRecordId = clientRecordId,
        clientRecordVersion = clientRecordVersion,
        recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
    )

    private fun zoneOffset(instant: Instant) =
        ZoneId.systemDefault().rules.getOffset(instant)
}
