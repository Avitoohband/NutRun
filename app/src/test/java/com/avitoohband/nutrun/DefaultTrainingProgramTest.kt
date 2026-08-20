package com.avitoohband.nutrun

import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.UnitSystem
import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTrainingProgramTest {
    private val catalog = builtInExerciseCatalog()
    private val program = defaultTrainingProgram(catalog)
    private val templates = program.templates
    private val dayPlans = program.dayPlans
    private val sessions = defaultSessions(catalog)

    @Test
    fun translatedWeeklyProgramHasExpectedDaysTitlesAndEmptyHistory() {
        val cardio = templates.single { it.name == "Walk or Swim" }

        assertEquals(
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
            ),
            dayPlans.map(WeeklyDayPlan::weekday)
        )
        assertEquals(
            listOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            dayPlans.filter { cardio.id in it.templateIds }.map(WeeklyDayPlan::weekday)
        )
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            dayPlans.filter { it.templateIds.isNotEmpty() && cardio.id !in it.templateIds }
                .map(WeeklyDayPlan::weekday)
        )
        assertTrue(dayPlans.single { it.weekday == DayOfWeek.SATURDAY }.isRestDay)
        assertEquals("Push + Biceps", templates.single { it.id == "session-monday-push-biceps" }.name)
        assertEquals("Pull + Triceps", templates.single { it.id == "session-wednesday-pull-triceps" }.name)
        assertEquals("Shoulders + Legs + HIIT", templates.single { it.id == "session-friday-shoulders-legs" }.name)
        assertTrue(defaultTrainingHistory().isEmpty())
    }

    @Test
    fun cardioDaysEncodeOneOfDurationRangesAndIntensity() {
        val cardioSessions = sessions.filter { it.name == "Walk or Swim" }

        cardioSessions.forEach { session ->
            assertEquals(1, session.logicalTargetCount())
            assertEquals(listOf(45, 30), session.exercises.map(ExerciseTarget::durationMinutes))
            assertEquals(listOf(60, 45), session.exercises.map(ExerciseTarget::maximumDurationMinutes))
            assertEquals(1, session.exercises.map(ExerciseTarget::alternativeGroupId).distinct().size)
            assertTrue(session.exercises.all { it.intensityGuidance == "Light-to-moderate intensity" })
        }
    }

    @Test
    fun strengthDaysContainEveryRequestedRepRangeAndGuidance() {
        val ranges = sessions.flatMap { it.exercises }.associate {
            it.exercise.id to Triple(it.sets, it.reps, it.maximumReps)
        }

        assertEquals(Triple(4, 6, 8), ranges.getValue("bench-press"))
        assertEquals(Triple(3, 8, 10), ranges.getValue("incline-dumbbell-press"))
        assertEquals(Triple(3, 8, 12), ranges.getValue("bench-dip"))
        assertEquals(Triple(4, 6, 10), ranges.getValue("lat-pulldown"))
        assertEquals(Triple(4, 6, 8), ranges.getValue("pistol-squat"))
        assertEquals(Triple(3, 15, 20), ranges.getValue("standing-calf-raise"))

        val monday = sessions.first { it.weekday == DayOfWeek.MONDAY }
        val wednesday = sessions.first { it.weekday == DayOfWeek.WEDNESDAY }
        assertTrue(monday.guidance.any { "RPE 8-9" in it })
        assertTrue(monday.guidance.any { "90-120 seconds" in it && "60-75 seconds" in it })
        assertTrue(wednesday.guidance.any { "2 minutes" in it })

        val friday = sessions.first { it.weekday == DayOfWeek.FRIDAY }
        assertFalse(friday.exercises.any { "hiit" in it.exercise.id.lowercase() })
    }

    @Test
    fun localDateFormattingAndUpcomingSelectionAreDeterministic() {
        val thursday = LocalDate.of(2026, 7, 23)
        val saturday = LocalDate.of(2026, 7, 25)

        assertEquals("Thursday, July 23", formatToday(thursday))
        assertEquals("Walk or Swim", nextScheduledSession(sessions, thursday)!!.name)
        assertEquals(DayOfWeek.SUNDAY, nextScheduledSession(sessions, saturday)!!.weekday)
    }

    @Test
    fun oldPayloadDecodesWithoutRangesOrGuidanceAndNeverRestoresPause() {
        val payload = JSONObject(
            encodeTrainingState(
                supplements = defaultSupplements(),
                sessions = sessions,
                history = listOf(
                    "Pull + Triceps - completed",
                    "Genuine workout - completed 4/5 exercises",
                    "Easy run - 4.2 km",
                    "Push + Biceps - completed"
                ),
                selectedSessionId = sessions.first().id,
                activeWorkoutSessionId = sessions.first().id,
                isWorkoutPaused = true,
                completedExerciseIds = emptyMap(),
                suggestionDecision = SuggestionDecision.PENDING,
                suggestedWeightKg = 42.5
            )
        )
        val legacySessions = JSONArray().apply {
            sessions.forEach { session ->
                put(
                    JSONObject()
                        .put("id", session.id)
                        .put("name", session.name)
                        .put("weekday", session.weekday.name)
                        .put("guidance", JSONArray(session.guidance))
                        .put("exercises", JSONArray().apply {
                            session.exercises.forEach { target ->
                                put(
                                    JSONObject()
                                        .put("id", target.id)
                                        .put("exerciseId", target.exercise.id)
                                        .put("sets", target.sets)
                                        .put("reps", target.reps)
                                        .put("maximumReps", target.maximumReps)
                                        .put("weightKg", target.weightKg)
                                        .put("durationMinutes", target.durationMinutes)
                                        .put("maximumDurationMinutes", target.maximumDurationMinutes)
                                        .put("intensityGuidance", target.intensityGuidance)
                                        .put("alternativeGroupId", target.alternativeGroupId)
                                        .put("distanceKm", target.distanceKm)
                                )
                            }
                        })
                )
            }
        }
        payload.remove("schemaVersion")
        payload.remove("customExercises")
        payload.remove("workoutTemplates")
        payload.remove("weeklyDayPlans")
        payload.put("sessions", legacySessions)
        payload.put("isWorkoutPaused", true)
        legacySessions.let { encodedSessions ->
            repeat(encodedSessions.length()) { sessionIndex ->
                val encodedSession = encodedSessions.getJSONObject(sessionIndex)
                encodedSession.remove("guidance")
                val targets = encodedSession.getJSONArray("exercises")
                repeat(targets.length()) { targetIndex ->
                    targets.getJSONObject(targetIndex).apply {
                        remove("maximumReps")
                        remove("maximumDurationMinutes")
                        remove("intensityGuidance")
                        remove("alternativeGroupId")
                    }
                }
            }
        }

        val restored = decodeTrainingState(payload.toString(), catalog)!!

        assertFalse(restored.isWorkoutPaused)
        assertTrue(restored.sessions.all { it.guidance.isEmpty() })
        assertEquals(
            listOf(
                "Pull + Triceps - completed",
                "Genuine workout - completed 4/5 exercises",
                "Easy run - 4.2 km",
                "Push + Biceps - completed"
            ),
            restored.history
        )
        assertNull(restored.sessions.first().exercises.first().maximumDurationMinutes)
    }

    @Test
    fun persistedEmptyWorkoutIsNotRestoredAsActive() {
        val emptySession = TrainingSession(
            id = "empty-session",
            name = "Regular",
            weekday = DayOfWeek.SUNDAY
        )
        val payload = encodeTrainingState(
            supplements = defaultSupplements(),
            sessions = listOf(emptySession),
            history = emptyList(),
            selectedSessionId = emptySession.id,
            activeWorkoutSessionId = emptySession.id,
            isWorkoutPaused = false,
            completedExerciseIds = emptyMap(),
            suggestionDecision = SuggestionDecision.PENDING,
            suggestedWeightKg = 42.5
        )

        val restored = requireNotNull(decodeTrainingState(payload, catalog))

        assertNull(restored.activeWorkoutSessionId)
    }

    @Test
    fun demoCredentialsAreDebugOnlyAndProfileUsesRecommendedDefaults() {
        assertTrue(canUseDemoAccount(true, " demo ", "123456"))
        assertFalse(canUseDemoAccount(false, "demo", "123456"))
        assertFalse(canUseDemoAccount(true, "demo", "wrong"))

        val profile = defaultDemoProfile(LocalDate.of(2026, 7, 23))
        assertEquals(DEMO_EMAIL, profile.email)
        assertEquals(LocalDate.of(1990, 1, 1), profile.birthDate)
        assertEquals(BiologicalSex.MALE, profile.biologicalSex)
        assertEquals(175.0, profile.heightCm, 0.001)
        assertEquals(75.0, profile.weightKg, 0.001)
        assertEquals(ActivityLevel.MODERATE, profile.activityLevel)
        assertEquals(HealthGoal.MAINTAIN, profile.goal)
        assertEquals(UnitSystem.METRIC, profile.unitSystem)
        assertEquals(2_587, profile.calorieTarget)
    }
}
