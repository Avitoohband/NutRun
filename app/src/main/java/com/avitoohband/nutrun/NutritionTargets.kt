package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.NutritionTargetEntity
import kotlin.math.roundToInt

data class NutritionTargets(
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val custom: Boolean = false
)

fun recommendedNutritionTargets(calorieTarget: Int): NutritionTargets {
    val calories = calorieTarget.coerceAtLeast(0)
    return NutritionTargets(
        proteinGrams = (calories * 0.25 / 4.0).roundToInt().toDouble(),
        carbohydrateGrams = (calories * 0.45 / 4.0).roundToInt().toDouble(),
        fatGrams = (calories * 0.30 / 9.0).roundToInt().toDouble(),
        custom = false
    )
}

fun NutritionTargetEntity.toDomain(): NutritionTargets = NutritionTargets(
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    custom = custom
)

fun NutritionTargets.toEntity(userId: String): NutritionTargetEntity = NutritionTargetEntity(
    id = "nutrition-targets:$userId",
    userId = userId,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    custom = custom
)
