package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

const val KG_TO_POUNDS = 2.2046226218
const val KM_TO_MILES = 0.6213711922

private fun formatMeasurement(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun displayWeight(weightKg: Double, metric: Boolean): String =
    if (metric) "${formatMeasurement(weightKg)} kg" else "${formatMeasurement(weightKg * KG_TO_POUNDS)} lb"

fun displayDistance(distanceKm: Double, metric: Boolean): String =
    if (metric) "${formatMeasurement(distanceKm)} km" else "${formatMeasurement(distanceKm * KM_TO_MILES)} mi"

enum class RecurrenceType { DAILY, EVERY_N_DAYS, WEEKDAYS }

data class SupplementSchedule(
    val type: RecurrenceType,
    val startDate: LocalDate = LocalDate.now(),
    val intervalDays: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet()
) {
    fun isDueOn(date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        return when (type) {
            RecurrenceType.DAILY -> true
            RecurrenceType.EVERY_N_DAYS -> ChronoUnit.DAYS.between(startDate, date) % intervalDays.coerceAtLeast(1) == 0L
            RecurrenceType.WEEKDAYS -> date.dayOfWeek in weekdays
        }
    }

    fun label(): String = when (type) {
        RecurrenceType.DAILY -> "Daily"
        RecurrenceType.EVERY_N_DAYS -> "Every ${intervalDays.coerceAtLeast(1)} days"
        RecurrenceType.WEEKDAYS -> weekdays.sortedBy { it.value }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
    }
}

data class TrialState(
    val startedOn: LocalDate,
    val durationDays: Long = 30,
    val isForcedFreePlan: Boolean = false
) {
    fun isTrialActive(today: LocalDate = LocalDate.now()): Boolean =
        !isForcedFreePlan && today.isBefore(startedOn.plusDays(durationDays))

    fun daysRemaining(today: LocalDate = LocalDate.now()): Int =
        max(0, ChronoUnit.DAYS.between(today, startedOn.plusDays(durationDays)).toInt())
}

data class Supplement(
    val id: String,
    val name: String,
    val dose: String,
    val schedule: SupplementSchedule,
    val completedToday: Boolean = false
)

data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val instructions: String,
    val safetyNote: String,
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val defaultWeightKg: Double? = null,
    val defaultDurationMinutes: Int? = null,
    val defaultDistanceKm: Double? = null
)

data class ExerciseTarget(
    val id: String,
    val exercise: Exercise,
    val sets: Int = exercise.defaultSets,
    val reps: Int = exercise.defaultReps,
    val weightKg: Double? = exercise.defaultWeightKg,
    val durationMinutes: Int? = exercise.defaultDurationMinutes,
    val distanceKm: Double? = exercise.defaultDistanceKm
) {
    fun summary(metric: Boolean): String = when {
        durationMinutes != null && distanceKm != null -> "$durationMinutes min | ${displayDistance(distanceKm, metric)}"
        durationMinutes != null -> "$durationMinutes min"
        distanceKm != null -> displayDistance(distanceKm, metric)
        weightKg != null -> "$sets x $reps at ${displayWeight(weightKg, metric)}"
        else -> "$sets x $reps reps"
    }
}

data class TrainingSession(
    val id: String,
    val name: String,
    val weekday: DayOfWeek,
    val exercises: List<ExerciseTarget> = emptyList()
)

data class WorkoutSummary(
    val sessionName: String,
    val completedExercises: Int,
    val totalExercises: Int,
    val completedOn: LocalDate = LocalDate.now()
)

enum class SuggestionDecision { PENDING, ACCEPTED, POSTPONED, REJECTED }
