package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate

data class WorkoutTemplate(
    val id: String,
    val name: String,
    val exercises: List<ExerciseTarget> = emptyList(),
    val guidance: List<String> = emptyList()
)

data class WeeklyDayPlan(
    val weekday: DayOfWeek,
    val templateIds: List<String> = emptyList(),
    val isRestDay: Boolean = false
) {
    init {
        require(!isRestDay || templateIds.isEmpty())
        require(templateIds == templateIds.distinct())
    }
}

data class DefaultTrainingProgram(
    val templates: List<WorkoutTemplate>,
    val dayPlans: List<WeeklyDayPlan>
)

fun replaceDayAssignments(
    plans: List<WeeklyDayPlan>,
    weekday: DayOfWeek,
    templateIds: List<String>
): List<WeeklyDayPlan> {
    val replacement = WeeklyDayPlan(weekday, templateIds.distinct())
    val index = plans.indexOfFirst { it.weekday == weekday }
    return if (index == -1) plans + replacement else plans.mapIndexed { planIndex, plan ->
        if (planIndex == index) replacement else plan
    }
}

fun markRestDay(
    plans: List<WeeklyDayPlan>,
    weekday: DayOfWeek
): List<WeeklyDayPlan> {
    val replacement = WeeklyDayPlan(weekday, isRestDay = true)
    val index = plans.indexOfFirst { it.weekday == weekday }
    return if (index == -1) plans + replacement else plans.mapIndexed { planIndex, plan ->
        if (planIndex == index) replacement else plan
    }
}

fun templatesForDate(
    templates: List<WorkoutTemplate>,
    plans: List<WeeklyDayPlan>,
    overrides: List<TrainingScheduleOverride>,
    date: LocalDate
): List<WorkoutTemplate> {
    val movedFromDate = overrides
        .filter { it.originalDate == date }
        .map(TrainingScheduleOverride::sessionId)
        .toSet()
    val recurringIds = plans
        .firstOrNull { it.weekday == date.dayOfWeek && !it.isRestDay }
        ?.templateIds
        .orEmpty()
        .filterNot(movedFromDate::contains)
    val movedIds = overrides
        .filter { !it.skipped && it.scheduledDate == date }
        .map(TrainingScheduleOverride::sessionId)

    return (recurringIds + movedIds)
        .distinct()
        .mapNotNull { templateId -> templates.firstOrNull { it.id == templateId } }
}

fun WorkoutTemplate.logicalTargetCount(): Int =
    exercises.filter { it.alternativeGroupId == null }.size +
        exercises.mapNotNull(ExerciseTarget::alternativeGroupId).distinct().size

fun WorkoutTemplate.completedLogicalTargetCount(completedIds: Map<String, Boolean>): Int =
    exercises.count { it.alternativeGroupId == null && completedIds[it.id] == true } +
        exercises
            .filter { it.alternativeGroupId != null }
            .groupBy(ExerciseTarget::alternativeGroupId)
            .count { (_, targets) -> targets.any { completedIds[it.id] == true } }

fun defaultTrainingProgram(exercises: List<Exercise>): DefaultTrainingProgram {
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
    fun cardioTarget(id: String, exerciseId: String, duration: Int, maximumDuration: Int) =
        ExerciseTarget(
            id = id,
            exercise = exercise(exerciseId),
            sets = 1,
            reps = 1,
            durationMinutes = duration,
            maximumDurationMinutes = maximumDuration,
            intensityGuidance = "Light-to-moderate intensity",
            alternativeGroupId = "session-sunday-cardio-choice"
        )

    val cardio = WorkoutTemplate(
        id = "session-sunday-cardio",
        name = "Walk or Swim",
        exercises = listOf(
            cardioTarget("session-sunday-cardio-walk", "brisk-walk", 45, 60),
            cardioTarget("session-sunday-cardio-swim", "freestyle-swim", 30, 45)
        )
    )
    val push = WorkoutTemplate(
        id = "session-monday-push-biceps",
        name = "Push + Biceps",
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
    )
    val pull = WorkoutTemplate(
        id = "session-wednesday-pull-triceps",
        name = "Pull + Triceps",
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
    )
    val legs = WorkoutTemplate(
        id = "session-friday-shoulders-legs",
        name = "Shoulders + Legs + HIIT",
        exercises = listOf(
            strengthTarget("friday-pistol-squat", "pistol-squat", 4, 6, 8),
            strengthTarget("friday-walking-lunge", "walking-lunge", 3, 8, 10),
            strengthTarget("friday-shoulder-press", "dumbbell-shoulder-press", 4, 8, 10),
            strengthTarget("friday-lateral-raise", "cable-lateral-raise", 3, 12, 15),
            strengthTarget("friday-face-pull", "face-pull", 3, 12, 15),
            strengthTarget("friday-calf-raise", "standing-calf-raise", 3, 15, 20)
        )
    )

    return DefaultTrainingProgram(
        templates = listOf(cardio, push, pull, legs),
        dayPlans = listOf(
            WeeklyDayPlan(DayOfWeek.SUNDAY, listOf(cardio.id)),
            WeeklyDayPlan(DayOfWeek.MONDAY, listOf(push.id)),
            WeeklyDayPlan(DayOfWeek.TUESDAY, listOf(cardio.id)),
            WeeklyDayPlan(DayOfWeek.WEDNESDAY, listOf(pull.id)),
            WeeklyDayPlan(DayOfWeek.THURSDAY, listOf(cardio.id)),
            WeeklyDayPlan(DayOfWeek.FRIDAY, listOf(legs.id)),
            WeeklyDayPlan(DayOfWeek.SATURDAY, isRestDay = true)
        )
    )
}
