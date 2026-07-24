package com.avitoohband.nutrun

import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.domain.calculateHealthEstimate
import java.time.LocalDate

const val DEMO_USER_ID = "debug-demo"
const val DEMO_EMAIL = "demo@nutrun.local"
const val DEMO_USERNAME = "demo"
const val DEMO_PASSWORD = "123456"

fun isDemoAccount(userId: String?): Boolean = userId == DEMO_USER_ID

fun isDemoCredentials(username: String, password: String): Boolean =
    username.trim().equals(DEMO_USERNAME, ignoreCase = true) && password == DEMO_PASSWORD

fun canUseDemoAccount(isDebug: Boolean, username: String, password: String): Boolean =
    isDebug && isDemoCredentials(username, password)

fun defaultDemoProfile(today: LocalDate = LocalDate.now()): UserProfile {
    val birthDate = LocalDate.of(1990, 1, 1)
    val estimate = calculateHealthEstimate(
        birthDate = birthDate,
        biologicalSex = BiologicalSex.MALE,
        heightCm = 175.0,
        weightKg = 75.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = HealthGoal.MAINTAIN,
        today = today
    )
    return UserProfile(
        email = DEMO_EMAIL,
        birthDate = birthDate,
        biologicalSex = BiologicalSex.MALE,
        heightCm = 175.0,
        weightKg = 75.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = HealthGoal.MAINTAIN,
        unitSystem = UnitSystem.METRIC,
        calorieTarget = estimate.calorieTarget
    )
}
