package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WaterLogEntity
import com.avitoohband.nutrun.data.WeightEntryEntity
import com.avitoohband.nutrun.domain.WalkState
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ProgressRange {
    DAYS_7,
    DAYS_30,
    DAYS_90,
    ALL
}

enum class ExerciseProgressMetric {
    MAX_WEIGHT,
    MAX_REPS,
    VOLUME,
    ESTIMATED_ONE_REP_MAX
}

data class DatedValue(
    val date: LocalDate,
    val value: Double
)

data class ProgressSeries(
    val label: String,
    val unit: String,
    val points: List<DatedValue>
)

const val ADHERENCE_DISPLAY_MAX_PERCENT = 200.0

fun progressRangeStart(range: ProgressRange, today: LocalDate): LocalDate? = when (range) {
    ProgressRange.DAYS_7 -> today.minusDays(6)
    ProgressRange.DAYS_30 -> today.minusDays(29)
    ProgressRange.DAYS_90 -> today.minusDays(89)
    ProgressRange.ALL -> null
}

fun isDateInProgressRange(
    date: LocalDate,
    range: ProgressRange,
    today: LocalDate
): Boolean {
    if (date > today) return false
    val start = progressRangeStart(range, today) ?: return true
    return date >= start
}

fun clampAdherencePercent(actual: Double, goal: Double): Double {
    if (goal <= 0.0) return 0.0
    return (actual / goal * 100.0).coerceIn(0.0, ADHERENCE_DISPLAY_MAX_PERCENT)
}

fun weightSeries(
    entries: List<WeightEntryEntity>,
    range: ProgressRange,
    zoneId: ZoneId,
    today: LocalDate = LocalDate.now(zoneId)
): ProgressSeries {
    val grouped = entries
        .mapNotNull { entry ->
            val date = java.time.Instant.ofEpochMilli(entry.recordedAtMillis)
                .atZone(zoneId)
                .toLocalDate()
            if (!isDateInProgressRange(date, range, today)) return@mapNotNull null
            date to entry
        }
        .groupBy { it.first }
        .mapValues { (_, pairs) ->
            pairs.maxBy { it.second.recordedAtMillis }.second.weightKg
        }
    val points = grouped.entries
        .sortedBy { it.key }
        .map { (date, weightKg) -> DatedValue(date, weightKg) }
    return ProgressSeries(label = "Weight", unit = "kg", points = points)
}

fun workoutFrequencySeries(
    workouts: List<WorkoutRecord>,
    range: ProgressRange,
    today: LocalDate = LocalDate.now()
): ProgressSeries {
    val counts = workouts
        .filter { isDateInProgressRange(it.performedOn, range, today) }
        .groupingBy { it.performedOn }
        .eachCount()
    val points = counts.entries
        .sortedBy { it.key }
        .map { (date, count) -> DatedValue(date, count.toDouble()) }
    return ProgressSeries(label = "Workouts", unit = "sessions", points = points)
}

fun trainingVolumeSeries(
    workouts: List<WorkoutRecord>,
    range: ProgressRange,
    today: LocalDate = LocalDate.now()
): ProgressSeries {
    val volumes = workouts
        .filter { isDateInProgressRange(it.performedOn, range, today) }
        .groupBy { it.performedOn }
        .mapValues { (_, dayWorkouts) -> dayWorkouts.sumOf(WorkoutRecord::totalVolumeKg) }
    val points = volumes.entries
        .sortedBy { it.key }
        .map { (date, volume) -> DatedValue(date, volume) }
    return ProgressSeries(label = "Training volume", unit = "kg", points = points)
}

fun walkingDistanceSeries(
    walks: List<WalkSessionEntity>,
    range: ProgressRange,
    zoneId: ZoneId,
    today: LocalDate = LocalDate.now(zoneId)
): ProgressSeries {
    val distances = walks
        .filter { it.state == WalkState.FINISHED.name }
        .mapNotNull { walk ->
            val date = java.time.Instant.ofEpochMilli(walk.startedAtMillis)
                .atZone(zoneId)
                .toLocalDate()
            if (!isDateInProgressRange(date, range, today)) return@mapNotNull null
            date to walk.distanceMeters
        }
        .groupBy { it.first }
        .mapValues { (_, pairs) -> pairs.sumOf { it.second } }
    val points = distances.entries
        .sortedBy { it.key }
        .map { (date, meters) -> DatedValue(date, meters) }
    return ProgressSeries(label = "Walking distance", unit = "m", points = points)
}

fun calorieAdherenceSeries(
    foodLogs: List<FoodLogEntity>,
    calorieTarget: Int,
    range: ProgressRange,
    today: LocalDate = LocalDate.now()
): ProgressSeries {
    val caloriesByDate = foodLogs
        .mapNotNull { log ->
            val date = parseLocalDateOrNull(log.localDate) ?: return@mapNotNull null
            if (!isDateInProgressRange(date, range, today)) return@mapNotNull null
            date to log.calories
        }
        .groupBy { it.first }
        .mapValues { (_, pairs) -> pairs.sumOf { it.second }.toDouble() }
    val points = caloriesByDate.entries
        .sortedBy { it.key }
        .map { (date, calories) ->
            DatedValue(date, clampAdherencePercent(calories, calorieTarget.toDouble()))
        }
    return ProgressSeries(label = "Calorie adherence", unit = "%", points = points)
}

fun hydrationAdherenceSeries(
    waterLogs: List<WaterLogEntity>,
    goalMl: Int,
    range: ProgressRange,
    today: LocalDate = LocalDate.now()
): ProgressSeries {
    val waterByDate = waterLogs
        .mapNotNull { log ->
            val date = parseLocalDateOrNull(log.localDate) ?: return@mapNotNull null
            if (!isDateInProgressRange(date, range, today)) return@mapNotNull null
            date to log.amountMl
        }
        .groupBy { it.first }
        .mapValues { (_, pairs) -> pairs.sumOf { it.second }.toDouble() }
    val points = waterByDate.entries
        .sortedBy { it.key }
        .map { (date, amountMl) ->
            DatedValue(date, clampAdherencePercent(amountMl, goalMl.toDouble()))
        }
    return ProgressSeries(label = "Hydration adherence", unit = "%", points = points)
}

fun exercisesWithProgressHistory(workouts: List<WorkoutRecord>): List<String> =
    workouts
        .flatMap(WorkoutRecord::sets)
        .filter(WorkoutSetLog::completed)
        .map(WorkoutSetLog::exerciseId)
        .distinct()
        .sorted()

fun exerciseProgressSeries(
    workouts: List<WorkoutRecord>,
    exerciseId: String,
    metric: ExerciseProgressMetric,
    range: ProgressRange,
    today: LocalDate = LocalDate.now()
): ProgressSeries {
    val label = when (metric) {
        ExerciseProgressMetric.MAX_WEIGHT -> "Best weight"
        ExerciseProgressMetric.MAX_REPS -> "Best reps"
        ExerciseProgressMetric.VOLUME -> "Volume"
        ExerciseProgressMetric.ESTIMATED_ONE_REP_MAX -> "Estimated 1RM"
    }
    val unit = when (metric) {
        ExerciseProgressMetric.MAX_WEIGHT -> "kg"
        ExerciseProgressMetric.MAX_REPS -> "reps"
        ExerciseProgressMetric.VOLUME -> "kg"
        ExerciseProgressMetric.ESTIMATED_ONE_REP_MAX -> "kg"
    }
    val valuesByDate = workouts
        .filter { isDateInProgressRange(it.performedOn, range, today) }
        .mapNotNull { workout ->
            val sets = workout.sets.filter { it.exerciseId == exerciseId && it.completed }
            if (sets.isEmpty()) return@mapNotNull null
            val value = when (metric) {
                ExerciseProgressMetric.MAX_WEIGHT ->
                    sets.mapNotNull(WorkoutSetLog::weightKg).maxOrNull() ?: return@mapNotNull null
                ExerciseProgressMetric.MAX_REPS ->
                    sets.mapNotNull(WorkoutSetLog::reps).maxOrNull()?.toDouble()
                        ?: return@mapNotNull null
                ExerciseProgressMetric.VOLUME ->
                    sets.sumOf(WorkoutSetLog::volumeKg)
                ExerciseProgressMetric.ESTIMATED_ONE_REP_MAX ->
                    sets.mapNotNull(WorkoutSetLog::estimatedOneRepMaxKg).maxOrNull()
                        ?: return@mapNotNull null
            }
            workout.performedOn to value
        }
        .groupBy { it.first }
        .mapValues { (_, pairs) ->
            when (metric) {
                ExerciseProgressMetric.VOLUME -> pairs.sumOf { it.second }
                else -> pairs.maxOf { it.second }
            }
        }
    val points = valuesByDate.entries
        .sortedBy { it.key }
        .map { (date, value) -> DatedValue(date, value) }
    return ProgressSeries(label = label, unit = unit, points = points)
}

fun progressSeriesAccessibilitySummary(
    series: ProgressSeries,
    valueFormatter: (Double) -> String = { it.toString() }
): String {
    if (series.points.isEmpty()) {
        return "${series.label}: no data in selected range"
    }
    val latest = series.points.last()
    val formatted = valueFormatter(latest.value)
    return "${series.label}: $formatted ${series.unit} on ${formatProgressDate(latest.date)}, " +
        "${series.points.size} recorded days"
}

fun formatProgressDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH))

private fun parseLocalDateOrNull(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
