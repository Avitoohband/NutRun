package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.EntitlementKind
import com.avitoohband.nutrun.domain.FoodCatalogItem
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.RouteSample
import com.avitoohband.nutrun.domain.acceptedRouteDistanceMeters
import com.avitoohband.nutrun.domain.calculateHealthEstimate
import com.avitoohband.nutrun.domain.crossedHydrationGoal
import com.avitoohband.nutrun.domain.isHydrationReminderEligible
import com.avitoohband.nutrun.domain.nutritionSummary
import com.avitoohband.nutrun.domain.sessionSteps
import com.avitoohband.nutrun.domain.accumulatedSessionSteps
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionDomainTest {
    @Test
    fun healthEstimateUsesMifflinStJeorAndGoalAdjustment() {
        val estimate = calculateHealthEstimate(
            birthDate = LocalDate.of(1996, 1, 1),
            biologicalSex = BiologicalSex.MALE,
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = ActivityLevel.MODERATE,
            goal = HealthGoal.LOSE,
            today = LocalDate.of(2026, 1, 1)
        )

        assertEquals(24.7, estimate.bmi, 0.1)
        assertEquals(1_780, estimate.bmrKcal)
        assertEquals(2_759, estimate.tdeeKcal)
        assertEquals(2_459, estimate.calorieTarget)
    }

    @Test
    fun nutritionTotalsRemainExactOffline() {
        val total = nutritionSummary(
            listOf(
                FoodCatalogItem("1", "One", null, 100.0, 200, 10.0, 20.0, 5.0),
                FoodCatalogItem("2", "Two", null, 50.0, 120, 4.0, 12.0, 6.0)
            )
        )

        assertEquals(320, total.calories)
        assertEquals(14.0, total.proteinGrams, 0.001)
        assertEquals(32.0, total.carbohydrateGrams, 0.001)
        assertEquals(11.0, total.fatGrams, 0.001)
    }

    @Test
    fun hydrationStopsAtGoalAndOutsideWakingWindow() {
        assertTrue(isHydrationReminderEligible(1_000, 2_000, 12 * 60, 8 * 60, 22 * 60))
        assertFalse(isHydrationReminderEligible(2_000, 2_000, 12 * 60, 8 * 60, 22 * 60))
        assertTrue(isHydrationReminderEligible(2_000, 2_500, 12 * 60, 8 * 60, 22 * 60))
        assertFalse(isHydrationReminderEligible(1_000, 2_000, 23 * 60, 8 * 60, 22 * 60))
    }

    @Test
    fun hydrationCelebratesOnlyWhenCrossingTheCurrentGoal() {
        assertTrue(crossedHydrationGoal(1_750, 2_000, 2_000))
        assertFalse(crossedHydrationGoal(2_000, 2_250, 2_000))
        assertFalse(crossedHydrationGoal(2_000, 2_250, 2_500))
        assertTrue(crossedHydrationGoal(2_250, 2_500, 2_500))
    }

    @Test
    fun routeFilterRejectsInaccurateAndImpossiblePoints() {
        val start = RouteSample(32.0853, 34.7818, 5f, 1_000)
        val nearby = RouteSample(32.0854, 34.7819, 5f, 11_000)
        val inaccurate = nearby.copy(accuracyMeters = 80f)
        val jump = RouteSample(32.1853, 34.8818, 5f, 12_000)

        assertTrue((acceptedRouteDistanceMeters(start, nearby) ?: 0f) in 5f..30f)
        assertNull(acceptedRouteDistanceMeters(start, inaccurate))
        assertNull(acceptedRouteDistanceMeters(start, jump))
    }

    @Test
    fun stepCounterUsesSessionBaselineAndReportsMissingSensor() {
        assertEquals(245L, sessionSteps(10_000, 10_245))
        assertEquals(0L, sessionSteps(10_000, 9_999))
        assertNull(sessionSteps(null, 10_245))
    }

    @Test
    fun resumedStepSegmentDoesNotIncludeStepsTakenWhilePaused() {
        val beforePause = accumulatedSessionSteps(0, 1_000, 1_120)
        val afterResume = accumulatedSessionSteps(beforePause!!, 1_300, 1_350)

        assertEquals(170L, afterResume)
    }

    @Test
    fun trainingStateRoundTripsThroughRoomPayload() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first()
        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = session.exercises.first().id,
            setNumber = 1,
            reps = null,
            weightKg = null,
            durationSeconds = 2_700,
            rpe = 6.0,
            completed = true
        )
        model.finishWorkout()
        val override = TrainingScheduleOverride(
            sessionId = session.id,
            originalDate = LocalDate.of(2026, 7, 26),
            scheduledDate = LocalDate.of(2026, 7, 27)
        )
        val payload = encodeTrainingState(
            supplements = model.supplements,
            sessions = model.sessions,
            history = model.history,
            selectedSessionId = "session-1",
            activeWorkoutSessionId = null,
            isWorkoutPaused = false,
            completedExerciseIds = mapOf("target-1" to true),
            suggestionDecision = SuggestionDecision.ACCEPTED,
            suggestedWeightKg = 45.0,
            workoutHistory = model.workoutHistory,
            scheduleOverrides = listOf(override),
            defaultRestTimerSeconds = 135,
            usesMetricUnits = false
        )

        val restored = decodeTrainingState(payload, model.exerciseLibrary)!!

        assertEquals(model.supplements.size, restored.supplements.size)
        assertEquals(model.sessions.size, restored.sessions.size)
        assertEquals("session-1", restored.selectedSessionId)
        assertTrue(restored.completedExerciseIds.getValue("target-1"))
        assertEquals(45.0, restored.suggestedWeightKg, 0.001)
        assertEquals(model.workoutHistory.toList(), restored.workoutHistory)
        assertEquals(listOf(override), restored.scheduleOverrides)
        assertEquals(135, restored.defaultRestTimerSeconds)
        assertFalse(restored.usesMetricUnits)
    }

    @Test
    fun exerciseCatalogHasEightyOneStableUniqueEntriesAndSearchesAllMetadata() {
        val catalog = builtInExerciseCatalog()

        assertEquals(81, catalog.size)
        assertEquals(81, catalog.map { it.id }.distinct().size)
        assertTrue(setOf("lat-pulldown", "push-up", "goblet-squat", "easy-run", "freestyle-swim", "pistol-squat").all {
            id -> catalog.any { it.id == id }
        })
        assertTrue(filterExercises(catalog, "hamstrings", "All").isNotEmpty())
        assertTrue(filterExercises(catalog, "CARDIO", "Cardio").isNotEmpty())
    }

    @Test
    fun supplementCompletionRollsOverAndCompletedItemsSortLastStably() {
        val today = LocalDate.of(2026, 7, 23)
        val daily = SupplementSchedule(RecurrenceType.DAILY, startDate = today.minusDays(1))
        val first = Supplement("1", "First", "1", daily)
        val completedA = Supplement("2", "Completed A", "1", daily, today)
        val second = Supplement("3", "Second", "1", daily)
        val completedB = Supplement("4", "Completed B", "1", daily, today)

        assertEquals(
            listOf("1", "3", "2", "4"),
            dueSupplementsForDate(listOf(first, completedA, second, completedB), today).map { it.id }
        )
        assertFalse(completedA.isCompletedOn(today.plusDays(1)))
    }

    @Test
    fun todayDetectionAndCustomWaterValidationUseBoundaries() {
        val monday = LocalDate.of(2026, 7, 20)
        assertTrue(TrainingSession("id", "Monday", DayOfWeek.MONDAY).isToday(monday))
        assertFalse(TrainingSession("id", "Tuesday", DayOfWeek.TUESDAY).isToday(monday))
        assertTrue(isValidWaterAmount(50))
        assertTrue(isValidWaterAmount(2_000))
        assertFalse(isValidWaterAmount(49))
        assertFalse(isValidWaterAmount(2_001))
    }

    @Test
    fun entitlementTransitionsWithoutBlockingCoreFeatures() {
        val now = Instant.parse("2026-07-23T00:00:00Z")
        val active = SessionPreferences(
            authenticatedEmail = "test@example.com",
            trialStartedAtMillis = now.minus(Duration.ofDays(10)).toEpochMilli()
        )
        val expired = active.copy(trialStartedAtMillis = now.minus(Duration.ofDays(31)).toEpochMilli())

        assertEquals(EntitlementKind.TRIAL, active.entitlement(now.toEpochMilli()))
        assertEquals(EntitlementKind.FREE_AD_SUPPORTED, expired.entitlement(now.toEpochMilli()))
        assertEquals(EntitlementKind.SUBSCRIBER, expired.copy(subscriber = true).entitlement(now.toEpochMilli()))
    }
}
