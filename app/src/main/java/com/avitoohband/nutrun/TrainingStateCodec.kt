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
    val suggestedWeightKg: Double,
    val workoutHistory: List<WorkoutRecord>,
    val scheduleOverrides: List<TrainingScheduleOverride>,
    val activeSetLogs: Map<String, List<WorkoutSetLog>>,
    val activeWorkoutStartedAtMillis: Long?,
    val defaultRestTimerSeconds: Int,
    val usesMetricUnits: Boolean
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

fun defaultSessions(exercises: List<Exercise>): List<TrainingSession> = legacyDefaultSessions(exercises)

private fun legacyDefaultSessions(exercises: List<Exercise>): List<TrainingSession> {
    val byId = exercises.associateBy(Exercise::id)
    fun exercise(id: String) = requireNotNull(byId[id]) { "Missing built-in exercise $id" }
    fun strengthTarget(id: String, exerciseId: String, sets: Int, minimumReps: Int, maximumReps: Int) =
        ExerciseTarget(
            id = id,
            exercise = exercise(exerciseId),
            sets = sets,
            reps = minimumReps,
            maximumReps = maximumReps,
            weightKg = null
        )
    fun cardioSession(id: String, weekday: DayOfWeek) = TrainingSession(
        id = id,
        name = "Walk or Swim",
        weekday = weekday,
        exercises = listOf(
            ExerciseTarget(
                id = "$id-walk",
                exercise = exercise("brisk-walk"),
                sets = 1,
                reps = 1,
                durationMinutes = 45,
                maximumDurationMinutes = 60,
                intensityGuidance = "Light-to-moderate intensity",
                alternativeGroupId = "$id-cardio-choice"
            ),
            ExerciseTarget(
                id = "$id-swim",
                exercise = exercise("freestyle-swim"),
                sets = 1,
                reps = 1,
                durationMinutes = 30,
                maximumDurationMinutes = 45,
                intensityGuidance = "Light-to-moderate intensity",
                alternativeGroupId = "$id-cardio-choice"
            )
        )
    )
    return listOf(
        cardioSession("session-sunday-cardio", DayOfWeek.SUNDAY),
        TrainingSession(
            id = "session-monday-push-biceps",
            name = "Push + Biceps",
            weekday = DayOfWeek.MONDAY,
            exercises = listOf(
                strengthTarget("monday-bench-press", "bench-press", 4, 6, 8),
                strengthTarget("monday-incline-press", "incline-dumbbell-press", 3, 8, 10),
                strengthTarget("monday-dips", "bench-dip", 3, 8, 12),
                strengthTarget("monday-machine-press", "machine-chest-press", 3, 10, 12),
                strengthTarget("monday-barbell-curl", "barbell-curl", 3, 8, 10),
                strengthTarget("monday-dumbbell-curl", "dumbbell-curl", 3, 10, 12)
            ),
            guidance = listOf(
                "Weight: stop with 1-2 repetitions in reserve (RPE 8-9).",
                "Rest between sets: 90-120 seconds for compound exercises; 60-75 seconds for biceps.",
                "Rest between exercises: 2 minutes."
            )
        ),
        cardioSession("session-tuesday-cardio", DayOfWeek.TUESDAY),
        TrainingSession(
            id = "session-wednesday-pull-triceps",
            name = "Pull + Triceps",
            weekday = DayOfWeek.WEDNESDAY,
            exercises = listOf(
                strengthTarget("wednesday-lat-pulldown", "lat-pulldown", 4, 6, 10),
                strengthTarget("wednesday-barbell-row", "barbell-row", 4, 8, 10),
                strengthTarget("wednesday-cable-row", "seated-cable-row", 3, 10, 12),
                strengthTarget("wednesday-face-pull", "face-pull", 3, 12, 15),
                strengthTarget("wednesday-triceps-pushdown", "triceps-pushdown", 3, 10, 12),
                strengthTarget("wednesday-overhead-extension", "overhead-triceps-extension", 3, 10, 12)
            ),
            guidance = listOf(
                "Weight: stop with 1-2 repetitions in reserve.",
                "Rest between sets: 90-120 seconds for back; 60-75 seconds for triceps.",
                "Rest between exercises: 2 minutes."
            )
        ),
        cardioSession("session-thursday-cardio", DayOfWeek.THURSDAY),
        TrainingSession(
            id = "session-friday-shoulders-legs",
            name = "Shoulders + Legs + HIIT",
            weekday = DayOfWeek.FRIDAY,
            exercises = listOf(
                strengthTarget("friday-pistol-squat", "pistol-squat", 4, 6, 8),
                strengthTarget("friday-walking-lunge", "walking-lunge", 3, 8, 10),
                strengthTarget("friday-shoulder-press", "dumbbell-shoulder-press", 4, 8, 10),
                strengthTarget("friday-lateral-raise", "cable-lateral-raise", 3, 12, 15),
                strengthTarget("friday-face-pull", "face-pull", 3, 12, 15),
                strengthTarget("friday-calf-raise", "standing-calf-raise", 3, 15, 20)
            )
        )
    )
}

private val legacySampleHistory = setOf(
    "Pull + Triceps - completed",
    "Easy run - 4.2 km",
    "Push + Biceps - completed"
)

fun defaultTrainingHistory(): List<String> = emptyList()

fun sanitizeTrainingHistory(history: List<String>): List<String> =
    history.filterNot(legacySampleHistory::contains)

fun encodeTrainingState(
    supplements: List<Supplement>,
    sessions: List<TrainingSession>,
    history: List<String>,
    selectedSessionId: String?,
    activeWorkoutSessionId: String?,
    @Suppress("UNUSED_PARAMETER") isWorkoutPaused: Boolean,
    completedExerciseIds: Map<String, Boolean>,
    suggestionDecision: SuggestionDecision,
    suggestedWeightKg: Double,
    workoutHistory: List<WorkoutRecord> = emptyList(),
    scheduleOverrides: List<TrainingScheduleOverride> = emptyList(),
    activeSetLogs: Map<String, List<WorkoutSetLog>> = emptyMap(),
    activeWorkoutStartedAtMillis: Long? = null,
    defaultRestTimerSeconds: Int = 90,
    usesMetricUnits: Boolean = true
): String = JSONObject()
    .put("supplements", JSONArray().apply {
        supplements.forEach { supplement ->
            put(
                JSONObject()
                    .put("id", supplement.id)
                    .put("name", supplement.name)
                    .put("dose", supplement.dose)
                    .putNullable("completedOn", supplement.completedOn?.toString())
                    .put("reminderEnabled", supplement.reminderEnabled)
                    .put("reminderMinute", supplement.reminderMinute)
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
                    .put("guidance", JSONArray(session.guidance))
                    .put("exercises", JSONArray().apply {
                        session.exercises.forEach { target ->
                            put(
                                JSONObject()
                                    .put("id", target.id)
                                    .put("exerciseId", target.exercise.id)
                                    .put("sets", target.sets)
                                    .put("reps", target.reps)
                                    .putNullable("maximumReps", target.maximumReps)
                                    .putNullable("weightKg", target.weightKg)
                                    .putNullable("durationMinutes", target.durationMinutes)
                                    .putNullable("maximumDurationMinutes", target.maximumDurationMinutes)
                                    .putNullable("intensityGuidance", target.intensityGuidance)
                                    .putNullable("alternativeGroupId", target.alternativeGroupId)
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
    .put("isWorkoutPaused", false)
    .put("completedExerciseIds", JSONObject(completedExerciseIds))
    .put("suggestionDecision", suggestionDecision.name)
    .put("suggestedWeightKg", suggestedWeightKg)
    .put("workoutHistory", JSONArray().apply {
        workoutHistory.forEach { workout ->
            put(
                JSONObject()
                    .put("id", workout.id)
                    .put("sessionId", workout.sessionId)
                    .put("sessionName", workout.sessionName)
                    .put("performedOn", workout.performedOn.toString())
                    .put("startedAtMillis", workout.startedAtMillis)
                    .put("finishedAtMillis", workout.finishedAtMillis)
                    .put("completedTargetIds", JSONArray(workout.completedTargetIds.toList()))
                    .put("completedLogicalTargets", workout.completedLogicalTargets)
                    .put("totalLogicalTargets", workout.totalLogicalTargets)
                    .put("sets", JSONArray(workout.sets.map(WorkoutSetLog::toJson)))
            )
        }
    })
    .put("scheduleOverrides", JSONArray().apply {
        scheduleOverrides.forEach { override ->
            put(
                JSONObject()
                    .put("sessionId", override.sessionId)
                    .put("originalDate", override.originalDate.toString())
                    .putNullable("scheduledDate", override.scheduledDate?.toString())
                    .put("skipped", override.skipped)
            )
        }
    })
    .put("activeSetLogs", JSONObject().apply {
        activeSetLogs.forEach { (targetId, sets) ->
            put(targetId, JSONArray(sets.map(WorkoutSetLog::toJson)))
        }
    })
    .putNullable("activeWorkoutStartedAtMillis", activeWorkoutStartedAtMillis)
    .put("defaultRestTimerSeconds", defaultRestTimerSeconds.coerceIn(15, 600))
    .put("usesMetricUnits", usesMetricUnits)
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
            completedOn = item.nullableString("completedOn")?.let(LocalDate::parse),
            reminderEnabled = item.optBoolean("reminderEnabled", false),
            reminderMinute = item.optInt("reminderMinute", 8 * 60).takeIf { it in 0 until 24 * 60 }
                ?: 8 * 60
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
                    maximumReps = target.nullableInt("maximumReps"),
                    weightKg = target.nullableDouble("weightKg"),
                    durationMinutes = target.nullableInt("durationMinutes"),
                    maximumDurationMinutes = target.nullableInt("maximumDurationMinutes"),
                    intensityGuidance = target.nullableString("intensityGuidance"),
                    alternativeGroupId = target.nullableString("alternativeGroupId"),
                    distanceKm = target.nullableDouble("distanceKm")
                )
            },
            guidance = item.optJSONArray("guidance")?.strings().orEmpty()
        )
    }
    val completed = root.optJSONObject("completedExerciseIds")
        ?.let { objectValue ->
            objectValue.keys().asSequence().associateWith(objectValue::getBoolean)
        }
        .orEmpty()
    val workoutHistory = root.optJSONArray("workoutHistory")?.objects()?.map { workout ->
        WorkoutRecord(
            id = workout.getString("id"),
            sessionId = workout.getString("sessionId"),
            sessionName = workout.getString("sessionName"),
            performedOn = LocalDate.parse(workout.getString("performedOn")),
            startedAtMillis = workout.getLong("startedAtMillis"),
            finishedAtMillis = workout.getLong("finishedAtMillis"),
            completedTargetIds = workout.optJSONArray("completedTargetIds")
                ?.strings()
                ?.toSet()
                .orEmpty(),
            completedLogicalTargets = workout.optInt("completedLogicalTargets"),
            totalLogicalTargets = workout.optInt("totalLogicalTargets"),
            sets = workout.optJSONArray("sets")?.objects()?.map(JSONObject::toWorkoutSet).orEmpty()
        )
    }.orEmpty()
    val scheduleOverrides = root.optJSONArray("scheduleOverrides")?.objects()?.map { override ->
        TrainingScheduleOverride(
            sessionId = override.getString("sessionId"),
            originalDate = LocalDate.parse(override.getString("originalDate")),
            scheduledDate = override.nullableString("scheduledDate")?.let(LocalDate::parse),
            skipped = override.optBoolean("skipped", false)
        )
    }.orEmpty()
    val activeSetLogs = root.optJSONObject("activeSetLogs")
        ?.let { logs ->
            logs.keys().asSequence().associateWith { targetId ->
                logs.getJSONArray(targetId).objects().map(JSONObject::toWorkoutSet)
            }
        }
        .orEmpty()
    PersistedTrainingState(
        supplements = supplements,
        sessions = sessions,
        history = sanitizeTrainingHistory(root.optJSONArray("history")?.strings().orEmpty()),
        selectedSessionId = root.nullableString("selectedSessionId"),
        activeWorkoutSessionId = root.nullableString("activeWorkoutSessionId")
            ?.takeIf { activeId ->
                sessions.any { session ->
                    session.id == activeId && session.exercises.isNotEmpty()
                }
            },
        isWorkoutPaused = false,
        completedExerciseIds = completed,
        suggestionDecision = SuggestionDecision.valueOf(
            root.optString("suggestionDecision", SuggestionDecision.PENDING.name)
        ),
        suggestedWeightKg = root.optDouble("suggestedWeightKg", 42.5),
        workoutHistory = workoutHistory,
        scheduleOverrides = scheduleOverrides,
        activeSetLogs = activeSetLogs,
        activeWorkoutStartedAtMillis = root.nullableLong("activeWorkoutStartedAtMillis"),
        defaultRestTimerSeconds = root.optInt("defaultRestTimerSeconds", 90)
            .coerceIn(15, 600),
        usesMetricUnits = root.optBoolean("usesMetricUnits", true)
    )
}.getOrNull()

private fun WorkoutSetLog.toJson() = JSONObject()
    .put("id", id)
    .put("targetId", targetId)
    .put("exerciseId", exerciseId)
    .putNullable("exerciseName", exerciseName)
    .put("setNumber", setNumber)
    .putNullable("reps", reps)
    .putNullable("weightKg", weightKg)
    .putNullable("durationSeconds", durationSeconds)
    .putNullable("rpe", rpe)
    .put("completed", completed)

private fun JSONObject.toWorkoutSet() = WorkoutSetLog(
    id = getString("id"),
    targetId = getString("targetId"),
    exerciseId = getString("exerciseId"),
    exerciseName = nullableString("exerciseName"),
    setNumber = getInt("setNumber"),
    reps = nullableInt("reps"),
    weightKg = nullableDouble("weightKg"),
    durationSeconds = nullableInt("durationSeconds"),
    rpe = nullableDouble("rpe"),
    completed = optBoolean("completed", false)
)

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.nullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)

private fun JSONObject.nullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONObject.nullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).map(::getJSONObject)

private fun JSONArray.strings(): List<String> =
    (0 until length()).map(::getString)
