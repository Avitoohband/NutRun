package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class GtgMeasure { REPS, HOLD_SECONDS }

data class GtgPlan(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val measure: GtgMeasure,
    val targetPerSet: Int,
    val dailySetGoal: Int,
    val weekdays: Set<DayOfWeek>,
    val weightKg: Double? = null,
    val archived: Boolean = false
) {
    init {
        require(id.isNotBlank())
        require(exerciseId.isNotBlank())
        require(exerciseName.isNotBlank())
        require(targetPerSet in measure.targetRange)
        require(dailySetGoal in 1..100)
        require(weekdays.isNotEmpty())
        require(weightKg == null || weightKg.isFinite() && weightKg in 0.0..1_000.0)
    }
}

data class GtgLog(
    val id: String,
    val planId: String,
    val exerciseId: String,
    val exerciseName: String,
    val measure: GtgMeasure,
    val reps: Int?,
    val durationSeconds: Int?,
    val weightKg: Double?,
    val rir: Int?,
    val notes: String,
    val performedAtMillis: Long,
    val zoneId: String,
    val performedOn: LocalDate,
    val dailySetGoalSnapshot: Int
) {
    init {
        require(id.isNotBlank())
        require(planId.isNotBlank())
        require(exerciseId.isNotBlank())
        require(exerciseName.isNotBlank())
        require(
            when (measure) {
                GtgMeasure.REPS -> reps in GtgMeasure.REPS.targetRange && durationSeconds == null
                GtgMeasure.HOLD_SECONDS -> durationSeconds in GtgMeasure.HOLD_SECONDS.targetRange && reps == null
            }
        )
        require(weightKg == null || weightKg.isFinite() && weightKg in 0.0..1_000.0)
        require(rir == null || rir in 0..10)
        require(notes.length <= 1_000)
        require(performedAtMillis >= 0L)
        require(runCatching { ZoneId.of(zoneId) }.isSuccess)
        require(dailySetGoalSnapshot in 1..100)
    }
}

data class GtgState(
    val plans: List<GtgPlan> = emptyList(),
    val logs: List<GtgLog> = emptyList()
)

val GtgMeasure.targetRange: IntRange
    get() = when (this) {
        GtgMeasure.REPS -> 1..1_000
        GtgMeasure.HOLD_SECONDS -> 1..3_600
    }

fun GtgPlan.referencesKnownExercise(exerciseIds: Set<String>): Boolean = exerciseId in exerciseIds
