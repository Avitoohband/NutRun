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
    val customExercises: List<Exercise>,
    val workoutTemplates: List<WorkoutTemplate>,
    val weeklyDayPlans: List<WeeklyDayPlan>,
    val legacyUsesMetricUnits: Boolean?,
    val usesMetricUnits: Boolean
)
internal val TrainingViewModel.sessions: List<TrainingSession>
    get() = workoutTemplates.toCompatibilitySessions(weeklyDayPlans)


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

fun defaultTrainingHistory(): List<String> = emptyList()

fun sanitizeTrainingHistory(history: List<String>): List<String> = history

fun encodeTrainingState(
    supplements: List<Supplement> = emptyList(),
    sessions: List<TrainingSession> = emptyList(),
    history: List<String> = emptyList(),
    selectedSessionId: String? = null,
    activeWorkoutSessionId: String? = null,
    @Suppress("UNUSED_PARAMETER") isWorkoutPaused: Boolean = false,
    completedExerciseIds: Map<String, Boolean> = emptyMap(),
    suggestionDecision: SuggestionDecision = SuggestionDecision.PENDING,
    suggestedWeightKg: Double = 42.5,
    workoutHistory: List<WorkoutRecord> = emptyList(),
    scheduleOverrides: List<TrainingScheduleOverride> = emptyList(),
    activeSetLogs: Map<String, List<WorkoutSetLog>> = emptyMap(),
    activeWorkoutStartedAtMillis: Long? = null,
    defaultRestTimerSeconds: Int = 90,
    @Suppress("UNUSED_PARAMETER") usesMetricUnits: Boolean = true,
    customExercises: List<Exercise> = emptyList(),
    workoutTemplates: List<WorkoutTemplate> = emptyList(),
    weeklyDayPlans: List<WeeklyDayPlan> = emptyList()
): String {
    val templates = workoutTemplates.ifEmpty { sessions.map(TrainingSession::toTemplate) }
    val plans = weeklyDayPlans.ifEmpty { sessions.toWeeklyDayPlans() }
    return JSONObject()
        .put("schemaVersion", 2)
        .put("supplements", JSONArray().apply { supplements.forEach { put(it.toJson()) } })
        .put("customExercises", JSONArray().apply { customExercises.forEach { put(it.toJson()) } })
        .put("workoutTemplates", JSONArray().apply { templates.forEach { put(it.toJson()) } })
        .put("weeklyDayPlans", JSONArray().apply { plans.forEach { put(it.toJson()) } })
        .put("history", JSONArray(history))
        .putNullable("selectedSessionId", selectedSessionId)
        .putNullable("activeWorkoutSessionId", activeWorkoutSessionId)
        .put("isWorkoutPaused", false)
        .put("completedExerciseIds", JSONObject(completedExerciseIds))
        .put("suggestionDecision", suggestionDecision.name)
        .put("suggestedWeightKg", suggestedWeightKg)
        .put("workoutHistory", JSONArray().apply { workoutHistory.forEach { put(it.toJson()) } })
        .put("scheduleOverrides", JSONArray().apply { scheduleOverrides.forEach { put(it.toJson()) } })
        .put("activeSetLogs", JSONObject().apply {
            activeSetLogs.forEach { (targetId, sets) -> put(targetId, JSONArray(sets.map(WorkoutSetLog::toJson))) }
        })
        .putNullable("activeWorkoutStartedAtMillis", activeWorkoutStartedAtMillis)
        .put("defaultRestTimerSeconds", defaultRestTimerSeconds.coerceIn(15, 600))
        .toString()
}

fun decodeTrainingState(
    payload: String,
    exerciseLibrary: List<Exercise>
): PersistedTrainingState? = runCatching {
    val root = JSONObject(payload)
    val isVersion2 = root.optInt("schemaVersion", 1) >= 2
    val builtInExerciseIds = exerciseLibrary
        .filterNot { it.id.isTypedUuid("exercise-") }
        .map(Exercise::id).toSet()
    val customExercises = root.optJSONArray("customExercises")?.objects()?.map(JSONObject::toExercise)
        ?.filter { it.id.isTypedUuid("exercise-") && it.id !in builtInExerciseIds }
        ?.distinctBy(Exercise::id)
        .orEmpty()
    val exerciseById = (exerciseLibrary + customExercises).associateBy(Exercise::id)
    val legacyUsesMetricUnits = root.takeIf { !isVersion2 }?.nullableBoolean("usesMetricUnits")
    val supplements = root.optJSONArray("supplements")?.objects()?.map(JSONObject::toSupplement).orEmpty()
    val decodedTemplates = if (isVersion2) {
        root.optJSONArray("workoutTemplates")?.objects()?.mapNotNull { item ->
            runCatching { item.toWorkoutTemplate(exerciseById) }.getOrNull()
        }.orEmpty()
    } else {
        root.optJSONArray("sessions")?.objects()?.map { it.toTrainingSession(exerciseById).toTemplate() }.orEmpty()
    }
    val workoutTemplates = decodedTemplates.distinctBy(WorkoutTemplate::id)
    val weeklyDayPlans = if (isVersion2) {
        root.optJSONArray("weeklyDayPlans")?.objects()
            ?.mapNotNull { it.toWeeklyDayPlan(workoutTemplates.map(WorkoutTemplate::id).toSet()) }
            ?.distinctBy(WeeklyDayPlan::weekday)
            .orEmpty()
    } else {
        root.optJSONArray("sessions")?.objects()
            ?.mapNotNull { item ->
                item.nullableString("weekday")?.let { DayOfWeek.valueOf(it) to item.getString("id") }
            }
            ?.groupBy({ it.first }, { it.second })
            ?.map { (weekday, templateIds) -> WeeklyDayPlan(weekday, templateIds.distinct()) }
            .orEmpty()
    }
    val sessions = workoutTemplates.toCompatibilitySessions(weeklyDayPlans)
    val completed = root.optJSONObject("completedExerciseIds")?.let { objectValue ->
        objectValue.keys().asSequence().associateWith(objectValue::getBoolean)
    }.orEmpty()
    val workoutHistory = root.optJSONArray("workoutHistory")?.objects()?.map(JSONObject::toWorkoutRecord).orEmpty()
    val scheduleOverrides = root.optJSONArray("scheduleOverrides")?.objects()?.map(JSONObject::toScheduleOverride).orEmpty()
    val activeSetLogs = root.optJSONObject("activeSetLogs")?.let { logs ->
        logs.keys().asSequence().associateWith { targetId ->
            logs.getJSONArray(targetId).objects().map(JSONObject::toWorkoutSet)
        }
    }.orEmpty()
    PersistedTrainingState(
        supplements = supplements,
        sessions = sessions,
        history = root.optJSONArray("history")?.strings().orEmpty(),
        selectedSessionId = root.nullableString("selectedSessionId"),
        activeWorkoutSessionId = root.nullableString("activeWorkoutSessionId")
            ?.takeIf { activeId -> workoutTemplates.any { it.id == activeId && it.exercises.isNotEmpty() } },
        isWorkoutPaused = false,
        completedExerciseIds = completed,
        suggestionDecision = SuggestionDecision.valueOf(root.optString("suggestionDecision", SuggestionDecision.PENDING.name)),
        suggestedWeightKg = root.optDouble("suggestedWeightKg", 42.5),
        workoutHistory = workoutHistory,
        scheduleOverrides = scheduleOverrides,
        activeSetLogs = activeSetLogs,
        activeWorkoutStartedAtMillis = root.nullableLong("activeWorkoutStartedAtMillis"),
        defaultRestTimerSeconds = root.optInt("defaultRestTimerSeconds", 90).coerceIn(15, 600),
        customExercises = customExercises,
        workoutTemplates = workoutTemplates,
        weeklyDayPlans = weeklyDayPlans,
        legacyUsesMetricUnits = legacyUsesMetricUnits,
        usesMetricUnits = legacyUsesMetricUnits ?: true
    )
}.getOrNull()

private fun Supplement.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("dose", dose)
    .putNullable("completedOn", completedOn?.toString())
    .put("reminderEnabled", reminderEnabled)
    .put("reminderMinute", reminderMinute)
    .put("scheduleType", schedule.type.name)
    .put("startDate", schedule.startDate.toString())
    .put("intervalDays", schedule.intervalDays)
    .put("weekdays", JSONArray(schedule.weekdays.map(DayOfWeek::name)))

private fun JSONObject.toSupplement() = Supplement(
    id = getString("id"),
    name = getString("name"),
    dose = getString("dose"),
    schedule = SupplementSchedule(
        type = RecurrenceType.valueOf(getString("scheduleType")),
        startDate = LocalDate.parse(getString("startDate")),
        intervalDays = optInt("intervalDays", 1),
        weekdays = optJSONArray("weekdays")?.strings()?.map(DayOfWeek::valueOf)?.toSet().orEmpty()
    ),
    completedOn = nullableString("completedOn")?.let(LocalDate::parse),
    reminderEnabled = optBoolean("reminderEnabled", false),
    reminderMinute = optInt("reminderMinute", 8 * 60).takeIf { it in 0 until 24 * 60 } ?: 8 * 60
)

private fun Exercise.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("category", category)
    .put("primaryMuscles", primaryMuscles)
    .put("secondaryMuscles", secondaryMuscles)
    .put("instructions", instructions)
    .put("safetyNote", safetyNote)
    .put("defaultSets", defaultSets)
    .put("defaultReps", defaultReps)
    .putNullable("defaultWeightKg", defaultWeightKg)
    .putNullable("defaultDurationMinutes", defaultDurationMinutes)
    .putNullable("defaultDistanceKm", defaultDistanceKm)

private fun JSONObject.toExercise() = Exercise(
    id = getString("id"),
    name = getString("name"),
    category = getString("category"),
    primaryMuscles = getString("primaryMuscles"),
    secondaryMuscles = getString("secondaryMuscles"),
    instructions = getString("instructions"),
    safetyNote = getString("safetyNote"),
    defaultSets = optInt("defaultSets", 3),
    defaultReps = optInt("defaultReps", 10),
    defaultWeightKg = nullableDouble("defaultWeightKg"),
    defaultDurationMinutes = nullableInt("defaultDurationMinutes"),
    defaultDistanceKm = nullableDouble("defaultDistanceKm")
)

private fun TrainingSession.toTemplate() = WorkoutTemplate(id, name, exercises, guidance)

private fun List<TrainingSession>.toWeeklyDayPlans(): List<WeeklyDayPlan> =
    groupBy(TrainingSession::weekday).map { (weekday, sameDay) -> WeeklyDayPlan(weekday, sameDay.map(TrainingSession::id).distinct()) }

private fun WorkoutTemplate.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("guidance", JSONArray(guidance))
    .put("origin", origin.name)
    .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })

private fun ExerciseTarget.toJson() = JSONObject()
    .put("id", id)
    .put("exerciseId", exercise.id)
    .put("sets", sets)
    .put("reps", reps)
    .putNullable("maximumReps", maximumReps)
    .putNullable("weightKg", weightKg)
    .putNullable("durationMinutes", durationMinutes)
    .putNullable("maximumDurationMinutes", maximumDurationMinutes)
    .putNullable("intensityGuidance", intensityGuidance)
    .putNullable("alternativeGroupId", alternativeGroupId)
    .putNullable("distanceKm", distanceKm)

private fun JSONObject.toWorkoutTemplate(exerciseById: Map<String, Exercise>) = WorkoutTemplate(
    id = getString("id"),
    name = getString("name"),
    exercises = optJSONArray("exercises")?.objects()?.mapNotNull { it.toExerciseTarget(exerciseById) }.orEmpty(),
    guidance = optJSONArray("guidance")?.strings().orEmpty(),
    origin = optString("origin", WorkoutTemplateOrigin.BUILT_IN.name)
        .let(WorkoutTemplateOrigin::valueOf)
)

private fun JSONObject.toTrainingSession(exerciseById: Map<String, Exercise>) = TrainingSession(
    id = getString("id"),
    name = getString("name"),
    weekday = DayOfWeek.valueOf(getString("weekday")),
    exercises = optJSONArray("exercises")?.objects()?.mapNotNull { it.toExerciseTarget(exerciseById) }.orEmpty(),
    guidance = optJSONArray("guidance")?.strings().orEmpty()
)

private fun JSONObject.toExerciseTarget(exerciseById: Map<String, Exercise>): ExerciseTarget? {
    val exercise = exerciseById[getString("exerciseId")] ?: return null
    return ExerciseTarget(
        id = getString("id"),
        exercise = exercise,
        sets = optInt("sets", exercise.defaultSets).coerceIn(1, 20),
        reps = optInt("reps", exercise.defaultReps),
        maximumReps = nullableInt("maximumReps"),
        weightKg = nullableDouble("weightKg"),
        durationMinutes = nullableInt("durationMinutes"),
        maximumDurationMinutes = nullableInt("maximumDurationMinutes"),
        intensityGuidance = nullableString("intensityGuidance"),
        alternativeGroupId = nullableString("alternativeGroupId"),
        distanceKm = nullableDouble("distanceKm")
    )
}

private fun WeeklyDayPlan.toJson() = JSONObject()
    .put("weekday", weekday.name)
    .put("templateIds", JSONArray(templateIds))
    .put("isRestDay", isRestDay)

private fun JSONObject.toWeeklyDayPlan(templateIds: Set<String>): WeeklyDayPlan? = runCatching {
    val isRestDay = optBoolean("isRestDay", false)
    val validIds = optJSONArray("templateIds")?.strings()?.filter(templateIds::contains)?.distinct().orEmpty()
    WeeklyDayPlan(DayOfWeek.valueOf(getString("weekday")), if (isRestDay) emptyList() else validIds, isRestDay)
}.getOrNull()

internal fun List<WorkoutTemplate>.toCompatibilitySessions(plans: List<WeeklyDayPlan>): List<TrainingSession> {
    val weekdayByTemplateId = plans.filterNot(WeeklyDayPlan::isRestDay)
        .flatMap { plan -> plan.templateIds.map { it to plan.weekday } }
        .toMap()
    return mapNotNull { template ->
        weekdayByTemplateId[template.id]?.let { weekday ->
            TrainingSession(template.id, template.name, weekday, template.exercises, template.guidance)
        }
    }
}

private fun WorkoutRecord.toJson() = JSONObject()
    .put("id", id)
    .put("sessionId", sessionId)
    .put("sessionName", sessionName)
    .put("performedOn", performedOn.toString())
    .put("startedAtMillis", startedAtMillis)
    .put("finishedAtMillis", finishedAtMillis)
    .put("completedTargetIds", JSONArray(completedTargetIds.toList()))
    .put("completedLogicalTargets", completedLogicalTargets)
    .put("totalLogicalTargets", totalLogicalTargets)
    .put("sets", JSONArray(sets.map(WorkoutSetLog::toJson)))

private fun JSONObject.toWorkoutRecord() = WorkoutRecord(
    id = getString("id"),
    sessionId = getString("sessionId"),
    sessionName = getString("sessionName"),
    performedOn = LocalDate.parse(getString("performedOn")),
    startedAtMillis = getLong("startedAtMillis"),
    finishedAtMillis = getLong("finishedAtMillis"),
    completedTargetIds = optJSONArray("completedTargetIds")?.strings()?.toSet().orEmpty(),
    completedLogicalTargets = optInt("completedLogicalTargets"),
    totalLogicalTargets = optInt("totalLogicalTargets"),
    sets = optJSONArray("sets")?.objects()?.map(JSONObject::toWorkoutSet).orEmpty()
)

private fun TrainingScheduleOverride.toJson() = JSONObject()
    .put("sessionId", sessionId)
    .put("originalDate", originalDate.toString())
    .putNullable("scheduledDate", scheduledDate?.toString())
    .put("skipped", skipped)

private fun JSONObject.toScheduleOverride() = TrainingScheduleOverride(
    sessionId = getString("sessionId"),
    originalDate = LocalDate.parse(getString("originalDate")),
    scheduledDate = nullableString("scheduledDate")?.let(LocalDate::parse),
    skipped = optBoolean("skipped", false)
)
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

private fun JSONObject.nullableBoolean(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else getBoolean(key)

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
