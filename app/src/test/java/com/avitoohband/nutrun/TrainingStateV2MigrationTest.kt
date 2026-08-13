package com.avitoohband.nutrun

import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingStateV2MigrationTest {
    @Test
    fun v2RoundTripPreservesTemplatesPlansAndCustomExercises() {
        val custom = Exercise(
            id = "custom-1",
            name = "My carry",
            category = "Custom",
            primaryMuscles = "Grip",
            secondaryMuscles = "",
            instructions = "Walk steadily.",
            safetyNote = ""
        )
        val template = WorkoutTemplate(
            "template-1",
            "Carry day",
            listOf(ExerciseTarget("target-1", custom, sets = 4, reps = 30))
        )

        val payload = encodeTrainingState(
            customExercises = listOf(custom),
            workoutTemplates = listOf(template),
            weeklyDayPlans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf(template.id)))
        )
        val restored = requireNotNull(decodeTrainingState(payload, builtInExerciseCatalog()))

        assertEquals(2, JSONObject(payload).getInt("schemaVersion"))
        assertEquals(custom, restored.customExercises.single())
        assertEquals(4, restored.workoutTemplates.single().exercises.single().sets)
    }

    @Test
    fun legacySessionsBecomeTemplatesAndOrderedDayPlans() {
        val restored = requireNotNull(decodeTrainingState(legacyPayload(), builtInExerciseCatalog()))

        assertEquals(
            listOf("legacy-push", "legacy-walk"),
            restored.weeklyDayPlans.first { it.weekday == DayOfWeek.MONDAY }.templateIds
        )
        assertEquals(
            setOf("legacy-push", "legacy-walk"),
            restored.workoutTemplates.map { it.id }.toSet()
        )
    }

    @Test
    fun migrationPreservesActiveWorkoutHistoryAndOverrides() {
        val restored = requireNotNull(
            decodeTrainingState(legacyPayloadWithActivity(), builtInExerciseCatalog())
        )

        assertEquals("legacy-push", restored.activeWorkoutSessionId)
        assertEquals("legacy-push", restored.workoutHistory.single().sessionId)
        assertEquals("legacy-push", restored.scheduleOverrides.single().sessionId)
    }

    @Test
    fun danglingPlanReferencesAreDiscardedWithoutDroppingTemplates() {
        val restored = requireNotNull(
            decodeTrainingState(v2PayloadWithDanglingId(), builtInExerciseCatalog())
        )

        assertEquals(listOf("valid"), restored.weeklyDayPlans.single().templateIds)
        assertEquals("valid", restored.workoutTemplates.single().id)
    }

    private fun legacyPayload(): String = JSONObject()
        .put("supplements", JSONArray())
        .put("sessions", JSONArray().apply {
            put(legacySession("legacy-push", "Push", "bench-press"))
            put(legacySession("legacy-walk", "Walk", "brisk-walk"))
        })
        .put("history", JSONArray())
        .put("completedExerciseIds", JSONObject())
        .put("suggestionDecision", SuggestionDecision.PENDING.name)
        .put("suggestedWeightKg", 42.5)
        .put("usesMetricUnits", false)
        .toString()

    private fun legacyPayloadWithActivity(): String = JSONObject(legacyPayload())
        .put("activeWorkoutSessionId", "legacy-push")
        .put("workoutHistory", JSONArray().put(
            JSONObject()
                .put("id", "record-1")
                .put("sessionId", "legacy-push")
                .put("sessionName", "Push")
                .put("performedOn", "2026-08-10")
                .put("startedAtMillis", 100L)
                .put("finishedAtMillis", 200L)
                .put("completedTargetIds", JSONArray().put("legacy-push-target"))
                .put("completedLogicalTargets", 1)
                .put("totalLogicalTargets", 1)
                .put("sets", JSONArray())
        ))
        .put("scheduleOverrides", JSONArray().put(
            JSONObject()
                .put("sessionId", "legacy-push")
                .put("originalDate", "2026-08-10")
                .put("scheduledDate", "2026-08-11")
                .put("skipped", false)
        ))
        .toString()

    private fun v2PayloadWithDanglingId(): String = JSONObject()
        .put("schemaVersion", 2)
        .put("supplements", JSONArray())
        .put("customExercises", JSONArray())
        .put("workoutTemplates", JSONArray().put(
            JSONObject()
                .put("id", "valid")
                .put("name", "Valid")
                .put("guidance", JSONArray())
                .put("origin", WorkoutTemplateOrigin.BUILT_IN.name)
                .put("exercises", JSONArray().put(
                    JSONObject()
                        .put("id", "valid-target")
                        .put("exerciseId", "bench-press")
                        .put("sets", 3)
                        .put("reps", 10)
                ))
        ))
        .put("weeklyDayPlans", JSONArray().put(
            JSONObject()
                .put("weekday", DayOfWeek.MONDAY.name)
                .put("templateIds", JSONArray().put("valid").put("missing").put("valid"))
                .put("isRestDay", false)
        ))
        .toString()

    private fun legacySession(id: String, name: String, exerciseId: String): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("weekday", DayOfWeek.MONDAY.name)
        .put("guidance", JSONArray())
        .put("exercises", JSONArray().put(
            JSONObject()
                .put("id", "$id-target")
                .put("exerciseId", exerciseId)
                .put("sets", 3)
                .put("reps", 10)
        ))
}
