package com.avitoohband.nutrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun ActiveWorkoutContent(
    model: TrainingViewModel,
    onEditRestTimer: () -> Unit,
    onCancelRequest: () -> Unit,
    onFinishRequest: () -> Unit
) {
    val session = model.activeSession() ?: return
    val targets = session.exercises
    if (targets.isEmpty()) return
    var focusedIndex by rememberSaveable(session.id) { mutableIntStateOf(0) }
    LaunchedEffect(session.id, targets.size) {
        focusedIndex = focusedIndex.coerceIn(0, targets.lastIndex)
    }
    val target = targets[focusedIndex.coerceIn(0, targets.lastIndex)]

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("active-workout"),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(session.name, style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Exercise ${focusedIndex + 1} of ${targets.size}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(
                            onClick = onEditRestTimer,
                            modifier = Modifier.testTag("rest-timer-settings")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Text("Rest ${model.defaultRestTimerSeconds}s")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (focusedIndex + 1f) / targets.size },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        bottomBar = {
            ActiveWorkoutBottomBar(
                focusedIndex = focusedIndex,
                lastIndex = targets.lastIndex,
                onPrevious = { focusedIndex = (focusedIndex - 1).coerceAtLeast(0) },
                onNext = { focusedIndex = (focusedIndex + 1).coerceAtMost(targets.lastIndex) },
                onCancelRequest = onCancelRequest,
                onFinishRequest = onFinishRequest
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val timerEnd = model.restTimerEndAtMillis
            if (timerEnd != null) {
                item(key = "rest-timer") {
                    ActiveRestTimer(
                        endAtMillis = timerEnd,
                        onAddTime = model::addRestTime,
                        onSkip = model::skipRestTimer
                    )
                }
            }
            item(key = target.id) {
                ActiveExerciseCard(model = model, target = target)
            }
            session.guidance.forEachIndexed { index, guidance ->
                item(key = "guidance-$index") {
                    Text(
                        guidance,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutBottomBar(
    focusedIndex: Int,
    lastIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCancelRequest: () -> Unit,
    onFinishRequest: () -> Unit
) {
    Surface(shadowElevation = 6.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = focusedIndex > 0,
                    modifier = Modifier.weight(1f).testTag("active-workout-previous")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous exercise")
                    Text("Previous")
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = focusedIndex < lastIndex,
                    modifier = Modifier.weight(1f).testTag("active-workout-next")
                ) {
                    Text("Next")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next exercise")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancelRequest,
                    modifier = Modifier.weight(1f).testTag("cancel-workout")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Text("Cancel")
                }
                Button(
                    onClick = onFinishRequest,
                    modifier = Modifier.weight(1f).testTag("finish-workout")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text("Finish")
                }
            }
        }
    }
}

@Composable
private fun ActiveExerciseCard(model: TrainingViewModel, target: ExerciseTarget) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("active-exercise-${target.id}"),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (target.alternativeGroupId != null) {
                Text(
                    "Choose one",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(target.exercise.name, style = MaterialTheme.typography.titleMedium)
            Text(target.summary(model.usesMetricUnits))
            model.progressionSuggestion(target)?.let { suggestion ->
                val action = when (suggestion.action) {
                    ProgressionAction.INCREASE -> "Increase to"
                    ProgressionAction.KEEP -> "Keep"
                    ProgressionAction.REDUCE -> "Reduce to"
                }
                Text(
                    text = "$action ${displayWeight(suggestion.suggestedWeightKg, model.usesMetricUnits)}",
                    modifier = Modifier.testTag("active-progression-${target.id}"),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            target.intensityGuidance?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val previousSets = model.previousSets(target.exercise.id)
            if (previousSets.isNotEmpty()) {
                Text(
                    "Previous: " + previousSets.joinToString(" | ") { previous ->
                        buildString {
                            append("Set ${previous.setNumber}")
                            previous.weightKg?.let { append(" ${displayWeight(it, model.usesMetricUnits)}") }
                            previous.reps?.let { append(" x $it") }
                            previous.rpe?.let { append(" RPE ${formatWorkoutInput(it)}") }
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            model.activeSetLogs[target.id].orEmpty().forEach { set ->
                WorkoutSetEditor(
                    set = set,
                    metric = model.usesMetricUnits,
                    hasPrevious = previousSets.any { it.setNumber == set.setNumber },
                    onCopyPrevious = { model.copyPreviousSet(target.id, set.setNumber) },
                    onChange = { reps, weightKg, durationSeconds, rpe, completed ->
                        model.updateWorkoutSet(
                            targetId = target.id,
                            setNumber = set.setNumber,
                            reps = reps,
                            weightKg = weightKg,
                            durationSeconds = durationSeconds,
                            rpe = rpe,
                            completed = completed
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkoutSetEditor(
    set: WorkoutSetLog,
    metric: Boolean,
    hasPrevious: Boolean,
    onCopyPrevious: () -> Unit,
    onChange: (Int?, Double?, Int?, Double?, Boolean) -> Unit
) {
    val durationTarget = set.durationSeconds != null
    var input by remember(set.id, metric) { mutableStateOf(set.toWorkoutSetInput(metric)) }
    var validation by remember(set.id, metric) {
        mutableStateOf(validateWorkoutSetInput(input, durationTarget, metric))
    }
    LaunchedEffect(set.reps, set.weightKg, set.durationSeconds, set.rpe, metric) {
        val savedInput = set.toWorkoutSetInput(metric)
        if (validateWorkoutSetInput(input, durationTarget, metric).isValid) {
            input = savedInput
            validation = validateWorkoutSetInput(savedInput, durationTarget, metric)
        }
    }

    fun submit(next: WorkoutSetInput, completed: Boolean = set.completed) {
        input = next
        validation = validateWorkoutSetInput(next, durationTarget, metric)
        validation.value?.let { value ->
            onChange(value.reps, value.weightKg, value.durationSeconds, value.rpe, completed)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Set ${set.setNumber}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            if (hasPrevious) {
                TextButton(onClick = onCopyPrevious, modifier = Modifier.testTag("copy-previous-${set.id}")) {
                    Text("Copy previous")
                }
            }
        }
        if (durationTarget) {
            WorkoutDecimalField(
                value = input.minutes,
                onValueChange = { submit(input.copy(minutes = it)) },
                label = "Minutes",
                error = validation.minutesError,
                tag = "workout-minutes-${set.id}"
            )
        } else {
            WorkoutDecimalField(
                value = input.weight,
                onValueChange = { submit(input.copy(weight = it)) },
                label = if (metric) "Weight (kg)" else "Weight (lb)",
                error = validation.weightError,
                tag = "workout-weight-${set.id}"
            )
            OutlinedTextField(
                value = input.reps,
                onValueChange = { submit(input.copy(reps = it)) },
                modifier = Modifier.fillMaxWidth().testTag("workout-reps-${set.id}"),
                label = { Text("Repetitions") },
                isError = validation.repsError != null,
                supportingText = if (validation.repsError != null) {
                    { Text(requireNotNull(validation.repsError)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        WorkoutDecimalField(
            value = input.rpe,
            onValueChange = { submit(input.copy(rpe = it)) },
            label = "RPE (0-10)",
            error = validation.rpeError,
            tag = "workout-effort-${set.id}"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { submit(input, completed = !set.completed) }
                .testTag("workout-set-completed-${set.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = set.completed, onCheckedChange = null)
            Text(if (set.completed) "Set completed" else "Mark set complete")
        }
    }
}

@Composable
private fun WorkoutDecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    tag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun ActiveRestTimer(
    endAtMillis: Long,
    onAddTime: () -> Unit,
    onSkip: () -> Unit
) {
    var currentTime by remember(endAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAtMillis) {
        while (currentTime < endAtMillis) {
            delay(1_000)
            currentTime = System.currentTimeMillis()
        }
    }
    val remainingSeconds = ((endAtMillis - currentTime + 999) / 1_000).coerceAtLeast(0).toInt()
    if (remainingSeconds == 0) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Rest", fontWeight = FontWeight.Bold)
            Text("%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddTime) { Text("Add 30 seconds") }
                TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

private fun WorkoutSetLog.toWorkoutSetInput(metric: Boolean) = WorkoutSetInput(
    weight = weightKg?.let { formatWorkoutInput(if (metric) it else it * KG_TO_POUNDS) }.orEmpty(),
    reps = reps?.toString().orEmpty(),
    minutes = durationSeconds?.div(60.0)?.let(::formatWorkoutInput).orEmpty(),
    rpe = rpe?.let(::formatWorkoutInput).orEmpty()
)

private fun formatWorkoutInput(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

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
