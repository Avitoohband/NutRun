package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
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
    val completedOn: LocalDate? = null
) {
    fun isCompletedOn(date: LocalDate): Boolean = completedOn == date
}

fun dueSupplementsForDate(supplements: List<Supplement>, date: LocalDate): List<Supplement> =
    supplements.filter { it.schedule.isDueOn(date) }.sortedBy { it.isCompletedOn(date) }

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
    val maximumReps: Int? = null,
    val weightKg: Double? = exercise.defaultWeightKg,
    val durationMinutes: Int? = exercise.defaultDurationMinutes,
    val maximumDurationMinutes: Int? = null,
    val intensityGuidance: String? = null,
    val alternativeGroupId: String? = null,
    val distanceKm: Double? = exercise.defaultDistanceKm
) {
    private fun repLabel(): String =
        maximumReps?.takeIf { it != reps }?.let { "$reps-$it" } ?: reps.toString()

    private fun durationLabel(): String? = durationMinutes?.let { minimum ->
        maximumDurationMinutes?.takeIf { it != minimum }?.let { "$minimum-$it min" } ?: "$minimum min"
    }

    fun summary(metric: Boolean): String = when {
        durationMinutes != null && distanceKm != null -> "${durationLabel()} | ${displayDistance(distanceKm, metric)}"
        durationMinutes != null -> durationLabel().orEmpty()
        distanceKm != null -> displayDistance(distanceKm, metric)
        weightKg != null -> "$sets x ${repLabel()} at ${displayWeight(weightKg, metric)}"
        else -> "$sets x ${repLabel()} reps"
    }
}

data class TrainingSession(
    val id: String,
    val name: String,
    val weekday: DayOfWeek,
    val exercises: List<ExerciseTarget> = emptyList(),
    val guidance: List<String> = emptyList()
)

data class WorkoutSetLog(
    val id: String,
    val targetId: String,
    val exerciseId: String,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val rpe: Double? = null,
    val completed: Boolean = false
) {
    val volumeKg: Double
        get() = if (completed) (weightKg ?: 0.0) * (reps ?: 0) else 0.0

    val estimatedOneRepMaxKg: Double?
        get() = weightKg?.takeIf { completed && (reps ?: 0) > 0 }
            ?.let { weight -> weight * (1.0 + (reps ?: 0) / 30.0) }
}

data class WorkoutRecord(
    val id: String,
    val sessionId: String,
    val sessionName: String,
    val performedOn: LocalDate,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val completedTargetIds: Set<String>,
    val completedLogicalTargets: Int,
    val totalLogicalTargets: Int,
    val sets: List<WorkoutSetLog>
) {
    val totalVolumeKg: Double get() = sets.sumOf(WorkoutSetLog::volumeKg)
}

data class ExerciseRecord(
    val exerciseId: String,
    val bestWeightKg: Double,
    val estimatedOneRepMaxKg: Double,
    val bestSetVolumeKg: Double
)

data class TrainingScheduleOverride(
    val sessionId: String,
    val originalDate: LocalDate,
    val scheduledDate: LocalDate?,
    val skipped: Boolean = false
)

fun TrainingSession.isToday(date: LocalDate = LocalDate.now()): Boolean =
    weekday == date.dayOfWeek

fun TrainingSession.logicalTargetCount(): Int =
    exercises.filter { it.alternativeGroupId == null }.size +
        exercises.mapNotNull(ExerciseTarget::alternativeGroupId).distinct().size

fun TrainingSession.completedLogicalTargetCount(completedIds: Map<String, Boolean>): Int =
    exercises.count { it.alternativeGroupId == null && completedIds[it.id] == true } +
        exercises
            .filter { it.alternativeGroupId != null }
            .groupBy(ExerciseTarget::alternativeGroupId)
            .count { (_, targets) -> targets.any { completedIds[it.id] == true } }

fun nextScheduledSession(
    sessions: List<TrainingSession>,
    date: LocalDate = LocalDate.now()
): TrainingSession? = sessions.minByOrNull { session ->
    (session.weekday.value - date.dayOfWeek.value + 7) % 7
}

fun sessionsForDate(
    sessions: List<TrainingSession>,
    overrides: List<TrainingScheduleOverride>,
    date: LocalDate
): List<TrainingSession> {
    val movedFromDate = overrides
        .filter { it.originalDate == date }
        .map(TrainingScheduleOverride::sessionId)
        .toSet()
    val recurring = sessions.filter {
        it.weekday == date.dayOfWeek && it.id !in movedFromDate
    }
    val movedHere = overrides
        .filter { !it.skipped && it.scheduledDate == date }
        .mapNotNull { override -> sessions.firstOrNull { it.id == override.sessionId } }
    return (recurring + movedHere).distinctBy(TrainingSession::id)
}

fun startOfWeek(date: LocalDate = LocalDate.now()): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun trainingWeek(date: LocalDate = LocalDate.now()): List<LocalDate> =
    (0L..6L).map(startOfWeek(date)::plusDays)

fun exerciseRecords(history: List<WorkoutRecord>): List<ExerciseRecord> =
    history
        .flatMap(WorkoutRecord::sets)
        .filter(WorkoutSetLog::completed)
        .groupBy(WorkoutSetLog::exerciseId)
        .mapNotNull { (exerciseId, sets) ->
            val weights = sets.mapNotNull(WorkoutSetLog::weightKg)
            if (weights.isEmpty()) return@mapNotNull null
            ExerciseRecord(
                exerciseId = exerciseId,
                bestWeightKg = weights.max(),
                estimatedOneRepMaxKg = sets.mapNotNull(WorkoutSetLog::estimatedOneRepMaxKg)
                    .maxOrNull() ?: 0.0,
                bestSetVolumeKg = sets.maxOf(WorkoutSetLog::volumeKg)
            )
        }
        .sortedByDescending(ExerciseRecord::estimatedOneRepMaxKg)

fun weeklyTrainingVolume(
    history: List<WorkoutRecord>,
    weekStart: LocalDate = startOfWeek()
): Double = history
    .filter { it.performedOn in weekStart..weekStart.plusDays(6) }
    .sumOf(WorkoutRecord::totalVolumeKg)

fun formatToday(date: LocalDate = LocalDate.now()): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))

fun recentTrainingHistory(history: List<String>): List<String> =
    history.filter {
        it.contains(" | completed ") ||
            it.contains(" - completed")
    }

fun isValidWaterAmount(amountMl: Int?): Boolean = amountMl != null && amountMl in 50..2_000

fun filterExercises(exercises: List<Exercise>, query: String, category: String): List<Exercise> =
    exercises.filter { exercise ->
        (category == "All" || exercise.category == category) &&
            (
                query.isBlank() ||
                    listOf(
                        exercise.name,
                        exercise.category,
                        exercise.primaryMuscles,
                        exercise.secondaryMuscles
                    ).any { it.contains(query.trim(), ignoreCase = true) }
                )
    }

data class WorkoutSummary(
    val sessionName: String,
    val completedExercises: Int,
    val totalExercises: Int,
    val completedOn: LocalDate = LocalDate.now()
)

enum class SuggestionDecision { PENDING, ACCEPTED, POSTPONED, REJECTED }
