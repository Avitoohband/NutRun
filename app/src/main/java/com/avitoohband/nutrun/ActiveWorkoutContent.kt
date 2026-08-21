package com.avitoohband.nutrun

import kotlin.math.roundToInt

data class WorkoutSetInput(
    val weight: String,
    val reps: String,
    val minutes: String,
    val rpe: String
)

data class ValidatedWorkoutSetInput(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val rpe: Double? = null
)

data class WorkoutSetInputValidation(
    val value: ValidatedWorkoutSetInput?,
    val weightError: String? = null,
    val repsError: String? = null,
    val minutesError: String? = null,
    val rpeError: String? = null
) {
    val isValid: Boolean get() = value != null
}

fun validateWorkoutSetInput(
    input: WorkoutSetInput,
    durationTarget: Boolean,
    metric: Boolean
): WorkoutSetInputValidation {
    val enteredWeight = input.weight.localizedDecimalOrNull()
    val enteredMinutes = input.minutes.localizedDecimalOrNull()
    val enteredRpe = input.rpe.localizedDecimalOrNull()
    val parsedReps = input.reps.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
    val weightKg = enteredWeight?.let { if (metric) it else it / KG_TO_POUNDS }

    val weightError = when {
        durationTarget || input.weight.isBlank() -> null
        enteredWeight == null -> "Enter a valid weight."
        enteredWeight < 0.0 -> "Weight cannot be negative."
        weightKg == null || !weightKg.isFinite() || weightKg > 2_000.0 ->
            "Weight must not exceed 2000 kg."
        else -> null
    }
    val repsError = when {
        durationTarget || input.reps.isBlank() -> null
        parsedReps == null -> "Enter a whole number of reps."
        parsedReps !in 0..1_000 -> "Reps must be between 0 and 1000."
        else -> null
    }
    val minutesError = when {
        !durationTarget || input.minutes.isBlank() -> null
        enteredMinutes == null -> "Enter valid minutes."
        enteredMinutes !in 0.0..1_440.0 -> "Minutes must be between 0 and 1440."
        else -> null
    }
    val rpeError = when {
        input.rpe.isBlank() -> null
        enteredRpe == null -> "Enter a valid RPE."
        enteredRpe !in 0.0..10.0 -> "RPE must be between 0 and 10."
        else -> null
    }
    val hasError = listOf(weightError, repsError, minutesError, rpeError).any { it != null }
    val value = if (hasError) {
        null
    } else {
        ValidatedWorkoutSetInput(
            reps = parsedReps.takeUnless { durationTarget },
            weightKg = weightKg.takeUnless { durationTarget },
            durationSeconds = enteredMinutes
                ?.takeIf { durationTarget }
                ?.times(60.0)
                ?.roundToInt(),
            rpe = enteredRpe
        )
    }
    return WorkoutSetInputValidation(
        value = value,
        weightError = weightError,
        repsError = repsError,
        minutesError = minutesError,
        rpeError = rpeError
    )
}

private fun String.localizedDecimalOrNull(): Double? =
    trim()
        .takeIf(String::isNotEmpty)
        ?.replace(',', '.')
        ?.toDoubleOrNull()
