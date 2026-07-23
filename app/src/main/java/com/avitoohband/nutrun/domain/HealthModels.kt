package com.avitoohband.nutrun.domain

import java.time.LocalDate
import java.time.Period
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class BiologicalSex { FEMALE, MALE }
enum class ActivityLevel(val multiplier: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    VERY_ACTIVE(1.725)
}

enum class HealthGoal(val calorieAdjustment: Int) {
    LOSE(-300),
    MAINTAIN(0),
    GAIN(300)
}

enum class UnitSystem { METRIC, IMPERIAL }
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }
enum class WalkState { ACTIVE, PAUSED, FINISHED }
enum class EntitlementKind { TRIAL, FREE_AD_SUPPORTED, SUBSCRIBER }

data class UserProfile(
    val email: String,
    val birthDate: LocalDate,
    val biologicalSex: BiologicalSex,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val goal: HealthGoal,
    val unitSystem: UnitSystem,
    val calorieTarget: Int
)

data class HealthEstimate(
    val bmi: Double,
    val bmrKcal: Int,
    val tdeeKcal: Int,
    val calorieTarget: Int
)

data class DailyNutritionSummary(
    val calories: Int = 0,
    val proteinGrams: Double = 0.0,
    val carbohydrateGrams: Double = 0.0,
    val fatGrams: Double = 0.0
)

data class FoodCatalogItem(
    val id: String,
    val name: String,
    val brand: String?,
    val servingGrams: Double,
    val calories: Int,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double
)

data class WalkSummary(
    val distanceKm: Double,
    val durationSeconds: Long,
    val steps: Long?
)

data class RouteSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long
)

fun calculateHealthEstimate(
    birthDate: LocalDate,
    biologicalSex: BiologicalSex,
    heightCm: Double,
    weightKg: Double,
    activityLevel: ActivityLevel,
    goal: HealthGoal,
    calorieTargetOverride: Int? = null,
    today: LocalDate = LocalDate.now()
): HealthEstimate {
    require(!birthDate.isAfter(today)) { "Birth date cannot be in the future" }
    require(heightCm in 80.0..260.0) { "Height is outside the supported range" }
    require(weightKg in 20.0..500.0) { "Weight is outside the supported range" }

    val age = Period.between(birthDate, today).years.coerceAtLeast(13)
    val heightMeters = heightCm / 100.0
    val bmi = weightKg / heightMeters.pow(2)
    val sexAdjustment = if (biologicalSex == BiologicalSex.MALE) 5 else -161
    val bmr = (10 * weightKg + 6.25 * heightCm - 5 * age + sexAdjustment).roundToInt()
    val tdee = (bmr * activityLevel.multiplier).roundToInt()
    val target = calorieTargetOverride ?: (tdee + goal.calorieAdjustment)
    return HealthEstimate(bmi, bmr, tdee, target.coerceAtLeast(1_200))
}

fun nutritionSummary(items: Iterable<FoodCatalogItem>): DailyNutritionSummary =
    items.fold(DailyNutritionSummary()) { total, item ->
        total.copy(
            calories = total.calories + item.calories,
            proteinGrams = total.proteinGrams + item.proteinGrams,
            carbohydrateGrams = total.carbohydrateGrams + item.carbohydrateGrams,
            fatGrams = total.fatGrams + item.fatGrams
        )
    }

fun isHydrationReminderEligible(
    consumedMl: Int,
    goalMl: Int,
    currentMinuteOfDay: Int,
    wakingStartMinute: Int,
    wakingEndMinute: Int
): Boolean = consumedMl < goalMl && currentMinuteOfDay in wakingStartMinute..wakingEndMinute

fun acceptedRouteDistanceMeters(previous: RouteSample?, current: RouteSample): Float? {
    if (current.accuracyMeters <= 0f || current.accuracyMeters > 50f) return null
    if (previous == null) return 0f
    val elapsedSeconds = (current.timestampMillis - previous.timestampMillis) / 1_000.0
    if (elapsedSeconds <= 0.0) return null

    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(current.latitude - previous.latitude)
    val longitudeDelta = Math.toRadians(current.longitude - previous.longitude)
    val a = sin(latitudeDelta / 2).pow(2) +
        cos(Math.toRadians(previous.latitude)) *
        cos(Math.toRadians(current.latitude)) *
        sin(longitudeDelta / 2).pow(2)
    val distance = (2 * earthRadiusMeters * asin(sqrt(a))).toFloat()
    val speedMetersPerSecond = distance / elapsedSeconds
    return distance.takeIf { it <= 250f && speedMetersPerSecond <= 12f }
}

fun sessionSteps(sensorBaseline: Long?, currentSensorValue: Long?): Long? =
    if (sensorBaseline == null || currentSensorValue == null) null
    else (currentSensorValue - sensorBaseline).coerceAtLeast(0)

fun accumulatedSessionSteps(
    completedSegments: Long,
    sensorBaseline: Long?,
    currentSensorValue: Long?
): Long? = sessionSteps(sensorBaseline, currentSensorValue)?.let(completedSegments::plus)
