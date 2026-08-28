package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.util.UUID

data class ActiveWorkoutSession(
    val id: String,
    val sourceTemplateId: String?,
    val name: String,
    val exercises: List<ExerciseTarget>,
    val guidance: List<String> = emptyList(),
    val skippedTargetIds: Set<String> = emptySet(),
    val completedTargetIds: Set<String> = emptySet(),
    val setLogs: Map<String, List<WorkoutSetLog>> = emptyMap(),
    val startedAtMillis: Long,
    val restTimerEndAtMillis: Long? = null
) {
    fun sanitize(): ActiveWorkoutSession {
        val exerciseIds = exercises.map(ExerciseTarget::id)
        val validExerciseIds = exerciseIds.toSet()
        val validSkipped = skippedTargetIds.intersect(validExerciseIds)
        val validCompleted = completedTargetIds.intersect(validExerciseIds) - validSkipped
        val validSetLogs = setLogs.filterKeys { it in validExerciseIds }
        val validTimer = restTimerEndAtMillis?.takeIf { it > startedAtMillis }
        return copy(
            skippedTargetIds = validSkipped,
            completedTargetIds = validCompleted,
            setLogs = validSetLogs,
            restTimerEndAtMillis = validTimer
        )
    }

    fun logicalTargetCount(): Int =
        exercises
            .filter { it.id !in skippedTargetIds }
            .filter { it.alternativeGroupId == null }.size +
            exercises
                .filter { it.id !in skippedTargetIds }
                .mapNotNull(ExerciseTarget::alternativeGroupId)
                .distinct().size

    fun resolvedLogicalTargetCount(): Int =
        exercises
            .filter { it.alternativeGroupId == null && it.id in skippedTargetIds }
            .size +
            exercises
                .filter { it.alternativeGroupId != null }
                .groupBy(ExerciseTarget::alternativeGroupId)
                .count { (_, targets) -> targets.all { it.id in skippedTargetIds } } +
            completedLogicalTargetCount()

    fun completedLogicalTargetCount(): Int =
        exercises.count {
            it.alternativeGroupId == null &&
                it.id !in skippedTargetIds &&
                it.id in completedTargetIds
        } +
            exercises
                .filter { it.alternativeGroupId != null && it.id !in skippedTargetIds }
                .groupBy(ExerciseTarget::alternativeGroupId)
                .count { (_, targets) -> targets.any { it.id in completedTargetIds } }

    fun skippedLogicalTargetCount(): Int =
        exercises.count {
            it.alternativeGroupId == null && it.id in skippedTargetIds
        } +
            exercises
                .filter { it.alternativeGroupId != null }
                .groupBy(ExerciseTarget::alternativeGroupId)
                .count { (_, targets) -> targets.all { it.id in skippedTargetIds } }

    fun toTrainingSession(weekday: DayOfWeek = DayOfWeek.MONDAY): TrainingSession =
        TrainingSession(
            id = sourceTemplateId ?: id,
            name = name,
            weekday = weekday,
            exercises = exercises,
            guidance = guidance
        )

    companion object {
        fun fromTemplate(
            template: WorkoutTemplate,
            startedAtMillis: Long = System.currentTimeMillis(),
            id: String = "active-${UUID.randomUUID()}"
        ): ActiveWorkoutSession {
            val setLogs = template.exercises.associate { target ->
                target.id to (1..target.sets.coerceAtLeast(1)).map { setNumber ->
                    WorkoutSetLog(
                        id = "${target.id}:$startedAtMillis:$setNumber",
                        targetId = target.id,
                        exerciseId = target.exercise.id,
                        exerciseName = target.exercise.name,
                        setNumber = setNumber,
                        reps = target.reps.takeIf { target.durationMinutes == null },
                        weightKg = target.weightKg,
                        durationSeconds = target.durationMinutes?.times(60)
                    )
                }
            }
            return ActiveWorkoutSession(
                id = id,
                sourceTemplateId = template.id,
                name = template.name,
                exercises = template.exercises,
                guidance = template.guidance,
                setLogs = setLogs,
                startedAtMillis = startedAtMillis
            ).sanitize()
        }

        fun quickWorkout(
            name: String,
            startedAtMillis: Long = System.currentTimeMillis(),
            id: String = "active-${UUID.randomUUID()}"
        ): ActiveWorkoutSession = ActiveWorkoutSession(
            id = id,
            sourceTemplateId = null,
            name = name,
            exercises = emptyList(),
            startedAtMillis = startedAtMillis
        ).sanitize()
    }
}

fun shouldDeliverRestTimerCompletion(
    expectedUserId: String,
    expectedActiveWorkoutId: String,
    expectedEndAtMillis: Long,
    currentUserId: String?,
    currentActiveWorkout: ActiveWorkoutSession?,
    nowMillis: Long
): Boolean {
    if (currentUserId == null || currentUserId != expectedUserId) return false
    val active = currentActiveWorkout ?: return false
    if (active.id != expectedActiveWorkoutId) return false
    val endAt = active.restTimerEndAtMillis ?: return false
    if (endAt != expectedEndAtMillis) return false
    return nowMillis >= expectedEndAtMillis
}
