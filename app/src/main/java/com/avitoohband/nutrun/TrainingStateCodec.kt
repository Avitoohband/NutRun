package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class PersistedTrainingState(
    val supplements: List<Supplement>,
    val sessions: List<TrainingSession>,
    val history: List<String>,
    val selectedSessionId: String?,
    val activeWorkoutSessionId: String?,
    val isWorkoutPaused: Boolean,
    val completedExerciseIds: Map<String, Boolean>,
    val suggestionDecision: SuggestionDecision,
    val suggestedWeightKg: Double
)

fun defaultSupplements() = listOf(
    Supplement("supplement-1", "Vitamin D", "2,000 IU", SupplementSchedule(RecurrenceType.DAILY)),
    Supplement("supplement-2", "Omega-3", "1,000 mg", SupplementSchedule(RecurrenceType.DAILY)),
    Supplement(
        "supplement-3",
        "Vitamin B12",
        "1,000 mcg",
        SupplementSchedule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3)
    ),
    Supplement(
        "supplement-4",
        "Vitamin C",
        "500 mg",
        SupplementSchedule(RecurrenceType.EVERY_N_DAYS, intervalDays = 2)
    )
)

fun defaultSessions(exercises: List<Exercise>) = listOf(
    TrainingSession(
        "session-1",
        "Push + Biceps",
        DayOfWeek.MONDAY,
        listOf(ExerciseTarget("target-1", exercises[1]), ExerciseTarget("target-2", exercises[2]))
    ),
    TrainingSession(
        "session-2",
        "Pull + Triceps",
        DayOfWeek.WEDNESDAY,
        listOf(ExerciseTarget("target-3", exercises[0]), ExerciseTarget("target-4", exercises[1]))
    ),
    TrainingSession(
        "session-3",
        "HIIT",
        DayOfWeek.FRIDAY,
        listOf(ExerciseTarget("target-5", exercises[3]))
    ),
    TrainingSession(
        "session-4",
        "Easy run",
        DayOfWeek.SATURDAY,
        listOf(ExerciseTarget("target-6", exercises[3]))
    )
)

fun defaultTrainingHistory() = listOf(
    "Pull + Triceps - completed",
    "Easy run - 4.2 km",
    "Push + Biceps - completed"
)

fun encodeTrainingState(
    supplements: List<Supplement>,
    sessions: List<TrainingSession>,
    history: List<String>,
    selectedSessionId: String?,
    activeWorkoutSessionId: String?,
    isWorkoutPaused: Boolean,
    completedExerciseIds: Map<String, Boolean>,
    suggestionDecision: SuggestionDecision,
    suggestedWeightKg: Double
): String = JSONObject()
    .put("supplements", JSONArray().apply {
        supplements.forEach { supplement ->
            put(
                JSONObject()
                    .put("id", supplement.id)
                    .put("name", supplement.name)
                    .put("dose", supplement.dose)
                    .put("completedToday", supplement.completedToday)
                    .put("scheduleType", supplement.schedule.type.name)
                    .put("startDate", supplement.schedule.startDate.toString())
                    .put("intervalDays", supplement.schedule.intervalDays)
                    .put(
                        "weekdays",
                        JSONArray(supplement.schedule.weekdays.map(DayOfWeek::name))
                    )
            )
        }
    })
    .put("sessions", JSONArray().apply {
        sessions.forEach { session ->
            put(
                JSONObject()
                    .put("id", session.id)
                    .put("name", session.name)
                    .put("weekday", session.weekday.name)
                    .put("exercises", JSONArray().apply {
                        session.exercises.forEach { target ->
                            put(
                                JSONObject()
                                    .put("id", target.id)
                                    .put("exerciseId", target.exercise.id)
                                    .put("sets", target.sets)
                                    .put("reps", target.reps)
                                    .putNullable("weightKg", target.weightKg)
                                    .putNullable("durationMinutes", target.durationMinutes)
                                    .putNullable("distanceKm", target.distanceKm)
                            )
                        }
                    })
            )
        }
    })
    .put("history", JSONArray(history))
    .putNullable("selectedSessionId", selectedSessionId)
    .putNullable("activeWorkoutSessionId", activeWorkoutSessionId)
    .put("isWorkoutPaused", isWorkoutPaused)
    .put("completedExerciseIds", JSONObject(completedExerciseIds))
    .put("suggestionDecision", suggestionDecision.name)
    .put("suggestedWeightKg", suggestedWeightKg)
    .toString()

fun decodeTrainingState(
    payload: String,
    exerciseLibrary: List<Exercise>
): PersistedTrainingState? = runCatching {
    val root = JSONObject(payload)
    val exerciseById = exerciseLibrary.associateBy(Exercise::id)
    val supplements = root.getJSONArray("supplements").objects().map { item ->
        Supplement(
            id = item.getString("id"),
            name = item.getString("name"),
            dose = item.getString("dose"),
            schedule = SupplementSchedule(
                type = RecurrenceType.valueOf(item.getString("scheduleType")),
                startDate = LocalDate.parse(item.getString("startDate")),
                intervalDays = item.optInt("intervalDays", 1),
                weekdays = item.optJSONArray("weekdays")
                    ?.strings()
                    ?.map(DayOfWeek::valueOf)
                    ?.toSet()
                    .orEmpty()
            ),
            completedToday = item.optBoolean("completedToday")
        )
    }
    val sessions = root.getJSONArray("sessions").objects().map { item ->
        TrainingSession(
            id = item.getString("id"),
            name = item.getString("name"),
            weekday = DayOfWeek.valueOf(item.getString("weekday")),
            exercises = item.getJSONArray("exercises").objects().mapNotNull { target ->
                val exercise = exerciseById[target.getString("exerciseId")] ?: return@mapNotNull null
                ExerciseTarget(
                    id = target.getString("id"),
                    exercise = exercise,
                    sets = target.optInt("sets", exercise.defaultSets),
                    reps = target.optInt("reps", exercise.defaultReps),
                    weightKg = target.nullableDouble("weightKg"),
                    durationMinutes = target.nullableInt("durationMinutes"),
                    distanceKm = target.nullableDouble("distanceKm")
                )
            }
        )
    }
    val completed = root.optJSONObject("completedExerciseIds")
        ?.let { objectValue ->
            objectValue.keys().asSequence().associateWith(objectValue::getBoolean)
        }
        .orEmpty()
    PersistedTrainingState(
        supplements = supplements,
        sessions = sessions,
        history = root.getJSONArray("history").strings(),
        selectedSessionId = root.nullableString("selectedSessionId"),
        activeWorkoutSessionId = root.nullableString("activeWorkoutSessionId"),
        isWorkoutPaused = root.optBoolean("isWorkoutPaused"),
        completedExerciseIds = completed,
        suggestionDecision = SuggestionDecision.valueOf(
            root.optString("suggestionDecision", SuggestionDecision.PENDING.name)
        ),
        suggestedWeightKg = root.optDouble("suggestedWeightKg", 42.5)
    )
}.getOrNull()

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.nullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)

private fun JSONObject.nullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).map(::getJSONObject)

private fun JSONArray.strings(): List<String> =
    (0 until length()).map(::getString)
