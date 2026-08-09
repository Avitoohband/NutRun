package com.avitoohband.nutrun

import kotlin.math.round

enum class ProgressionAction { INCREASE, KEEP, REDUCE }

data class ProgressionSuggestion(
    val action: ProgressionAction,
    val currentWeightKg: Double,
    val suggestedWeightKg: Double,
    val reason: String
)

fun progressionSuggestion(
    target: ExerciseTarget,
    history: List<WorkoutRecord>,
    usesMetricUnits: Boolean
): ProgressionSuggestion? {
    if (!target.isWeightedRepetitionTarget()) return null

    val minimumReps = target.reps
    val maximumReps = target.maximumReps ?: minimumReps
    val requiredSetCount = target.sets
    if (minimumReps <= 0 || maximumReps < minimumReps || requiredSetCount <= 0) return null

    val relevantWorkouts = history
        .filter { workout -> workout.sets.any { it.targetId == target.id } }
        .sortedWith(compareByDescending<WorkoutRecord> { it.finishedAtMillis }.thenByDescending { it.id })
    val latestWorkout = relevantWorkouts.firstOrNull() ?: return null
    val latestAttempt = latestWorkout.toAttempt(target.id, requiredSetCount) ?: return null
    val currentWeightKg = latestAttempt.weightKg

    if (latestAttempt.isMiss(minimumReps)) {
        val previousWorkout = relevantWorkouts.getOrNull(1) ?: return keepSuggestion(currentWeightKg)
        val previousAttempt = previousWorkout.toAttempt(target.id, requiredSetCount) ?: return null
        if (previousAttempt.isMiss(minimumReps)) {
            return ProgressionSuggestion(
                action = ProgressionAction.REDUCE,
                currentWeightKg = currentWeightKg,
                suggestedWeightKg = reducedWeight(currentWeightKg, usesMetricUnits),
                reason = "Reduce after two consecutive missed attempts."
            )
        }
        return keepSuggestion(currentWeightKg)
    }

    if (latestAttempt.sets.all { it.reps!! >= maximumReps } &&
        latestAttempt.sets.all { it.rpe!! <= 8.0 }
    ) {
        return ProgressionSuggestion(
            action = ProgressionAction.INCREASE,
            currentWeightKg = currentWeightKg,
            suggestedWeightKg = increasedWeight(currentWeightKg, usesMetricUnits),
            reason = "Increase because every required set reached the top of the range at RPE 8 or below."
        )
    }

    return keepSuggestion(
        currentWeightKg = currentWeightKg,
        highRpe = latestAttempt.sets.any { it.rpe!! >= 9.0 }
    )
}

private data class ProgressionAttempt(
    val weightKg: Double,
    val sets: List<WorkoutSetLog>
) {
    fun isMiss(minimumReps: Int): Boolean =
        sets.any { !it.completed || it.reps!! < minimumReps }
}

private fun ExerciseTarget.isWeightedRepetitionTarget(): Boolean =
    weightKg != null && durationMinutes == null && distanceKm == null

private fun WorkoutRecord.toAttempt(
    targetId: String,
    requiredSetCount: Int
): ProgressionAttempt? {
    val setsByNumber = sets
        .asSequence()
        .filter { it.targetId == targetId && it.setNumber in 1..requiredSetCount }
        .groupBy(WorkoutSetLog::setNumber)
    if ((1..requiredSetCount).any { setNumber -> setsByNumber[setNumber]?.size != 1 }) return null

    val requiredSets = (1..requiredSetCount).map { setsByNumber.getValue(it).single() }
    val weights = requiredSets.map { it.weightKg }
    if (weights.any { it == null || !it.isFinite() || it <= 0.0 }) return null
    if (weights.distinct().size != 1) return null
    if (requiredSets.any { it.reps == null || it.reps!! < 0 }) return null
    if (requiredSets.any { it.rpe == null || !it.rpe!!.isFinite() || it.rpe!! !in 1.0..10.0 }) return null

    return ProgressionAttempt(
        weightKg = weights.first()!!,
        sets = requiredSets
    )
}

private fun keepSuggestion(currentWeightKg: Double, highRpe: Boolean = false): ProgressionSuggestion =
    ProgressionSuggestion(
        action = ProgressionAction.KEEP,
        currentWeightKg = currentWeightKg,
        suggestedWeightKg = currentWeightKg,
        reason = if (highRpe) {
            "Keep the current load because at least one set reached RPE 9 or higher."
        } else {
            "Keep the current load and reassess after the next attempt."
        }
    )

private fun increasedWeight(currentWeightKg: Double, usesMetricUnits: Boolean): Double =
    if (usesMetricUnits) currentWeightKg + 2.5 else currentWeightKg + 5.0 / KG_TO_POUNDS

private fun reducedWeight(currentWeightKg: Double, usesMetricUnits: Boolean): Double =
    if (usesMetricUnits) {
        strictlyReducedWeight(currentWeightKg, 0.5)
    } else {
        strictlyReducedWeight(currentWeightKg * KG_TO_POUNDS, 1.0) / KG_TO_POUNDS
    }

private fun strictlyReducedWeight(currentWeight: Double, resolution: Double): Double {
    val exactReduction = currentWeight * 0.95
    val roundedReduction = round(exactReduction / resolution) * resolution
    if (roundedReduction > 0.0 && roundedReduction < currentWeight) return roundedReduction

    val steppedReduction = currentWeight - resolution
    return if (steppedReduction > 0.0) steppedReduction else exactReduction
}
