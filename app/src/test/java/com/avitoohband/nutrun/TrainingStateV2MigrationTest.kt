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
            id = "exercise-00000000-0000-0000-0000-000000000001",
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

        assertEquals(3, JSONObject(payload).getInt("schemaVersion"))
        assertEquals(custom, restored.customExercises.single())
        assertEquals(4, restored.workoutTemplates.single().exercises.single().sets)
    }

    @Test
    fun v3RoundTripPreservesActiveWorkoutSnapshot() {
        val exercise = builtInExerciseCatalog().first { it.id == "bench-press" }
        val template = WorkoutTemplate(
            "template-active",
            "Active day",
            listOf(
                ExerciseTarget(
                    "target-1",
                    exercise,
                    sets = 3
                )
            )
        )
        val active = ActiveWorkoutSession.fromTemplate(template, startedAtMillis = 42L)
        val payload = encodeTrainingState(
            workoutTemplates = listOf(template),
            activeWorkout = active
        )

        val restored = requireNotNull(decodeTrainingState(payload, builtInExerciseCatalog()))

        assertEquals(3, JSONObject(payload).getInt("schemaVersion"))
        assertEquals(template.exercises, restored.activeWorkout?.exercises)
        assertEquals(42L, restored.activeWorkout?.startedAtMillis)
        assertEquals(active.setLogs, restored.activeWorkout?.setLogs)
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

    @Test
    fun v2OmitsUnitKeysAndOnlyLegacyPayloadsRestoreTheirValue() {
        val v2Payload = encodeTrainingState(usesMetricUnits = false)
        val v2Root = JSONObject(v2Payload)

        assertEquals(false, v2Root.has("usesMetricUnits"))
        assertEquals(false, v2Root.has("legacyUsesMetricUnits"))
        assertEquals(null, requireNotNull(decodeTrainingState(v2Payload, builtInExerciseCatalog())).legacyUsesMetricUnits)

        val legacyWithoutUnits = JSONObject(legacyPayload()).apply { remove("usesMetricUnits") }.toString()
        assertEquals(null, requireNotNull(decodeTrainingState(legacyWithoutUnits, builtInExerciseCatalog())).legacyUsesMetricUnits)
        assertEquals(false, requireNotNull(decodeTrainingState(legacyPayload(), builtInExerciseCatalog())).legacyUsesMetricUnits)
    }

    @Test
    fun historyRoundTripsLosslesslyAcrossSchemaV2() {
        val history = listOf("Pull + Triceps - completed", "Genuine workout - completed 4/5 exercises")

        val restored = requireNotNull(decodeTrainingState(encodeTrainingState(history = history), builtInExerciseCatalog()))

        assertEquals(history, restored.history)
    }

    @Test
    fun v2RejectsMalformedCustomIdsAndBuiltInCollisions() {
        val validCustom = Exercise(
            "exercise-00000000-0000-0000-0000-000000000002",
            "Valid custom",
            "Custom",
            "Grip",
            "",
            "Carry it.",
            ""
        )
        val payload = JSONObject(encodeTrainingState(customExercises = listOf(validCustom)))
        payload.getJSONArray("customExercises").put(
            Exercise("exercise-invalid", "Bad", "Custom", "", "", "", "").toJsonForTest()
        ).put(
            Exercise("bench-press", "Replaced", "Custom", "", "", "", "").toJsonForTest()
        )
        payload.put("workoutTemplates", JSONArray().put(
            JSONObject()
                .put("id", "built-in-target")
                .put("name", "Built in target")
                .put("guidance", JSONArray())
                .put("origin", WorkoutTemplateOrigin.BUILT_IN.name)
                .put("exercises", JSONArray().put(
                    JSONObject().put("id", "target").put("exerciseId", "bench-press").put("sets", 3).put("reps", 10)
                ))
        ))

        val restored = requireNotNull(decodeTrainingState(payload.toString(), builtInExerciseCatalog()))

        assertEquals(listOf(validCustom), restored.customExercises)
        assertEquals(
            builtInExerciseCatalog().first { it.id == "bench-press" }.name,
            restored.workoutTemplates.single().exercises.single().exercise.name
        )
    }

    @Test
    fun v2RoundTripPreservesRepeatedAssignmentsAndRestDays() {
        val template = WorkoutTemplate("repeatable", "Repeatable")
        val plans = listOf(
            WeeklyDayPlan(DayOfWeek.MONDAY, listOf(template.id)),
            WeeklyDayPlan(DayOfWeek.WEDNESDAY, listOf(template.id)),
            WeeklyDayPlan(DayOfWeek.SATURDAY, isRestDay = true)
        )

        val restored = requireNotNull(decodeTrainingState(
            encodeTrainingState(workoutTemplates = listOf(template), weeklyDayPlans = plans),
            builtInExerciseCatalog()
        ))

        assertEquals(plans, restored.weeklyDayPlans)
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

    private fun Exercise.toJsonForTest() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("category", category)
        .put("primaryMuscles", primaryMuscles)
        .put("secondaryMuscles", secondaryMuscles)
        .put("instructions", instructions)
        .put("safetyNote", safetyNote)
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
