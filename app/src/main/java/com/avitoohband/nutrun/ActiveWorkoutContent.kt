package com.avitoohband.nutrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    val active = model.activeWorkout ?: return
    val session = model.activeSession() ?: return
    val targets = session.exercises
    if (targets.isEmpty()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("active-workout"),
            bottomBar = {
                ActiveWorkoutBottomBar(
                    focusedIndex = 0,
                    lastIndex = 0,
                    onPrevious = {},
                    onNext = {},
                    onCancelRequest = onCancelRequest,
                    onFinishRequest = onFinishRequest
                )
            }
        ) { innerPadding ->
            QuickWorkoutEmptyState(
                model = model,
                onExerciseAdded = { /* recomposition updates list */ },
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        }
        return
    }
    var focusedIndex by rememberSaveable(session.id) { mutableIntStateOf(0) }
    LaunchedEffect(session.id, targets.size) {
        focusedIndex = focusedIndex.coerceIn(0, targets.lastIndex)
    }
    val startedAtMillis = active.startedAtMillis
    var elapsedClockMillis by remember(startedAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            delay(1_000)
            elapsedClockMillis = System.currentTimeMillis()
        }
    }
    var showFinishReview by rememberSaveable(session.id) { mutableStateOf(false) }
    val setDrafts = remember(session.id, model.usesMetricUnits) {
        mutableStateMapOf<String, WorkoutSetInput>()
    }
    val completedLogicalTargets = active.completedLogicalTargetCount()
    val resolvedLogicalTargets = active.resolvedLogicalTargetCount()
    val totalLogicalTargets = active.logicalTargetCount()
    fun requestFinish() {
        if (resolvedLogicalTargets < totalLogicalTargets) {
            showFinishReview = true
        } else {
            onFinishRequest()
        }
    }
    if (showFinishReview) {
        AlertDialog(
            onDismissRequest = { showFinishReview = false },
            modifier = Modifier.testTag("incomplete-workout-review"),
            title = { Text("Finish incomplete workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$completedLogicalTargets of $totalLogicalTargets targets complete.")
                    Text("Unfinished targets will be saved as incomplete in workout history.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishReview = false
                        onFinishRequest()
                    },
                    modifier = Modifier.testTag("finish-anyway")
                ) {
                    Text("Finish anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishReview = false },
                    modifier = Modifier.testTag("keep-training")
                ) {
                    Text("Keep training")
                }
            }
        )
    }
    val target = targets[focusedIndex.coerceIn(0, targets.lastIndex)]
    val isTargetSkipped = target.id in active.skippedTargetIds
    val timerEnd = model.restTimerEndAtMillis

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("active-workout"),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            session.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            formatWorkoutElapsed(
                                ((elapsedClockMillis - startedAtMillis) / 1_000).coerceAtLeast(0)
                            ),
                            modifier = Modifier.testTag("active-workout-elapsed"),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
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
                    if (timerEnd != null) {
                        ActiveWorkoutRestTimer(
                            endAtMillis = timerEnd,
                            nowMillis = elapsedClockMillis,
                            onAddTime = model::addRestTime,
                            onSkip = model::skipRestTimer
                        )
                    }
                    ActiveWorkoutActions(
                        model = model,
                        focusedTargetId = target.id,
                        onExerciseAdded = { exerciseId ->
                            val newIndex = targets.indexOfLast { it.exercise.id == exerciseId }
                            if (newIndex >= 0) focusedIndex = newIndex
                        }
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
                onFinishRequest = ::requestFinish
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = target.id) {
                if (isTargetSkipped) {
                    SkippedExerciseCard(
                        target = target,
                        metric = model.usesMetricUnits,
                        onUndo = { model.restoreSkippedActiveExercise(target.id) }
                    )
                } else {
                    ActiveExerciseCard(
                        model = model,
                        target = target,
                        setDrafts = setDrafts
                    )
                }
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
                    Icon(Icons.Default.Close, contentDescription = "Cancel workout")
                    Text("Cancel")
                }
                Button(
                    onClick = onFinishRequest,
                    modifier = Modifier.weight(1f).testTag("finish-workout")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Finish workout")
                    Text("Finish")
                }
            }
        }
    }
}

@Composable
private fun ActiveExerciseCard(
    model: TrainingViewModel,
    target: ExerciseTarget,
    setDrafts: MutableMap<String, WorkoutSetInput>
) {
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
                    "Choose one cardio option.",
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
            ActiveWorkoutLayoutToggle(
                mode = model.activeWorkoutLayoutMode,
                onModeChange = model::updateActiveWorkoutLayoutMode
            )
            val sets = model.activeSetLogs[target.id].orEmpty()
            when (model.activeWorkoutLayoutMode) {
                ActiveWorkoutLayoutMode.LIST -> {
                    sets.forEach { set ->
                        val input = setDrafts[set.id] ?: set.toWorkoutSetInput(model.usesMetricUnits)
                        WorkoutSetEditor(
                            set = set,
                            input = input,
                            onInputChange = { setDrafts[set.id] = it },
                            metric = model.usesMetricUnits,
                            hasPrevious = previousSets.any { it.setNumber == set.setNumber },
                            onCopyPrevious = {
                                if (
                                    model.copyPreviousSet(target.id, set.setNumber) ==
                                    TrainingMutationResult.Success
                                ) {
                                    model.activeSetLogs[target.id]
                                        .orEmpty()
                                        .firstOrNull { it.setNumber == set.setNumber }
                                        ?.let { copied ->
                                            setDrafts[set.id] =
                                                copied.toWorkoutSetInput(model.usesMetricUnits)
                                        }
                                }
                            },
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
                ActiveWorkoutLayoutMode.GRID -> {
                    WorkoutSetGrid(
                        sets = sets,
                        setDrafts = setDrafts,
                        metric = model.usesMetricUnits,
                        previousSetNumbers = previousSets.map { it.setNumber },
                        onCopyPrevious = { set ->
                            if (
                                model.copyPreviousSet(target.id, set.setNumber) ==
                                TrainingMutationResult.Success
                            ) {
                                model.activeSetLogs[target.id]
                                    .orEmpty()
                                    .firstOrNull { it.setNumber == set.setNumber }
                                    ?.let { copied ->
                                        setDrafts[set.id] =
                                            copied.toWorkoutSetInput(model.usesMetricUnits)
                                    }
                            }
                        },
                        onChange = { set, reps, weightKg, durationSeconds, rpe, completed ->
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutLayoutToggle(
    mode: ActiveWorkoutLayoutMode,
    onModeChange: (ActiveWorkoutLayoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active-workout-layout-toggle")
    ) {
        ActiveWorkoutLayoutMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index,
                    ActiveWorkoutLayoutMode.entries.size
                ),
                modifier = Modifier.testTag(
                    when (option) {
                        ActiveWorkoutLayoutMode.LIST -> "active-workout-layout-list"
                        ActiveWorkoutLayoutMode.GRID -> "active-workout-layout-grid"
                    }
                )
            ) {
                Text(
                    when (option) {
                        ActiveWorkoutLayoutMode.LIST -> "List"
                        ActiveWorkoutLayoutMode.GRID -> "Grid"
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkoutSetGrid(
    sets: List<WorkoutSetLog>,
    setDrafts: MutableMap<String, WorkoutSetInput>,
    metric: Boolean,
    previousSetNumbers: List<Int>,
    onCopyPrevious: (WorkoutSetLog) -> Unit,
    onChange: (
        WorkoutSetLog,
        Int?,
        Double?,
        Int?,
        Double?,
        Boolean
    ) -> Unit
) {
    val durationTarget = sets.any { it.durationSeconds != null }
    val scrollState = rememberScrollState()
    Row(Modifier.horizontalScroll(scrollState)) {
        Column(
            modifier = Modifier.testTag("active-workout-set-grid"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GridHeaderCell("Set", Modifier.width(44.dp))
                if (!durationTarget) {
                    GridHeaderCell(
                        if (metric) "Wt (kg)" else "Wt (lb)",
                        Modifier.width(72.dp)
                    )
                }
                GridHeaderCell(
                    if (durationTarget) "Min" else "Reps",
                    Modifier.width(64.dp)
                )
                GridHeaderCell("RPE", Modifier.width(56.dp))
                GridHeaderCell("Done", Modifier.width(52.dp))
            }
            sets.forEach { set ->
                val input = setDrafts[set.id] ?: set.toWorkoutSetInput(metric)
                WorkoutSetGridRow(
                    set = set,
                    input = input,
                    durationTarget = set.durationSeconds != null,
                    metric = metric,
                    hasPrevious = previousSetNumbers.contains(set.setNumber),
                    onInputChange = { setDrafts[set.id] = it },
                    onCopyPrevious = { onCopyPrevious(set) },
                    onChange = { reps, weightKg, durationSeconds, rpe, completed ->
                        onChange(set, reps, weightKg, durationSeconds, rpe, completed)
                    }
                )
            }
        }
    }
}

@Composable
private fun GridHeaderCell(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun WorkoutSetGridRow(
    set: WorkoutSetLog,
    input: WorkoutSetInput,
    durationTarget: Boolean,
    metric: Boolean,
    hasPrevious: Boolean,
    onInputChange: (WorkoutSetInput) -> Unit,
    onCopyPrevious: () -> Unit,
    onChange: (Int?, Double?, Int?, Double?, Boolean) -> Unit
) {
    val validation = validateWorkoutSetInput(input, durationTarget, metric)

    fun submit(next: WorkoutSetInput, completed: Boolean = set.completed) {
        submitWorkoutSetInput(
            input = next,
            durationTarget = durationTarget,
            metric = metric,
            completed = completed,
            onInputChange = onInputChange,
            onChange = onChange
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                set.setNumber.toString(),
                modifier = Modifier.testTag("workout-grid-set-${set.id}"),
                fontWeight = FontWeight.SemiBold
            )
            if (hasPrevious) {
                IconButton(
                    onClick = onCopyPrevious,
                    modifier = Modifier.testTag("copy-previous-${set.id}")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy previous set ${set.setNumber}"
                    )
                }
            }
        }
        if (!durationTarget) {
            WorkoutCompactDecimalField(
                value = input.weight,
                onValueChange = { submit(input.copy(weight = it)) },
                error = validation.weightError,
                tag = "workout-weight-${set.id}",
                modifier = Modifier.width(72.dp)
            )
        }
        if (durationTarget) {
            WorkoutCompactDecimalField(
                value = input.minutes,
                onValueChange = { submit(input.copy(minutes = it)) },
                error = validation.minutesError,
                tag = "workout-minutes-${set.id}",
                modifier = Modifier.width(64.dp)
            )
        } else {
            WorkoutCompactNumberField(
                value = input.reps,
                onValueChange = { submit(input.copy(reps = it)) },
                error = validation.repsError,
                tag = "workout-reps-${set.id}",
                modifier = Modifier.width(64.dp)
            )
        }
        WorkoutCompactDecimalField(
            value = input.rpe,
            onValueChange = { submit(input.copy(rpe = it)) },
            error = validation.rpeError,
            tag = "workout-effort-${set.id}",
            modifier = Modifier.width(56.dp)
        )
        Row(
            modifier = Modifier
                .width(52.dp)
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (set.completed) {
                        "Set ${set.setNumber} completed"
                    } else {
                        "Mark set ${set.setNumber} complete"
                    }
                    stateDescription = if (set.completed) "Completed" else "Not completed"
                }
                .clickable { submit(input, completed = !set.completed) }
                .testTag("workout-set-completed-${set.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = set.completed, onCheckedChange = null)
        }
    }
}

@Composable
private fun WorkoutCompactDecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(tag),
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error, maxLines = 1) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun WorkoutCompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(tag),
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error, maxLines = 1) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

private fun submitWorkoutSetInput(
    input: WorkoutSetInput,
    durationTarget: Boolean,
    metric: Boolean,
    completed: Boolean,
    onInputChange: (WorkoutSetInput) -> Unit,
    onChange: (Int?, Double?, Int?, Double?, Boolean) -> Unit
) {
    onInputChange(input)
    validateWorkoutSetInput(input, durationTarget, metric).value?.let { value ->
        onChange(value.reps, value.weightKg, value.durationSeconds, value.rpe, completed)
    }
}

@Composable
private fun WorkoutSetEditor(
    set: WorkoutSetLog,
    input: WorkoutSetInput,
    onInputChange: (WorkoutSetInput) -> Unit,
    metric: Boolean,
    hasPrevious: Boolean,
    onCopyPrevious: () -> Unit,
    onChange: (Int?, Double?, Int?, Double?, Boolean) -> Unit
) {
    val durationTarget = set.durationSeconds != null
    val validation = validateWorkoutSetInput(input, durationTarget, metric)

    fun submit(next: WorkoutSetInput, completed: Boolean = set.completed) {
        submitWorkoutSetInput(
            input = next,
            durationTarget = durationTarget,
            metric = metric,
            completed = completed,
            onInputChange = onInputChange,
            onChange = onChange
        )
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fieldsModifier = Modifier.fillMaxWidth()
                if (maxWidth >= 360.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WorkoutDecimalField(
                            value = input.weight,
                            onValueChange = { submit(input.copy(weight = it)) },
                            label = if (metric) "Weight (kg)" else "Weight (lb)",
                            error = validation.weightError,
                            tag = "workout-weight-${set.id}",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = input.reps,
                            onValueChange = { submit(input.copy(reps = it)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("workout-reps-${set.id}"),
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
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkoutDecimalField(
                            value = input.weight,
                            onValueChange = { submit(input.copy(weight = it)) },
                            label = if (metric) "Weight (kg)" else "Weight (lb)",
                            error = validation.weightError,
                            tag = "workout-weight-${set.id}",
                            modifier = fieldsModifier
                        )
                        OutlinedTextField(
                            value = input.reps,
                            onValueChange = { submit(input.copy(reps = it)) },
                            modifier = fieldsModifier.testTag("workout-reps-${set.id}"),
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
                }
            }
        }
        WorkoutDecimalField(
            value = input.rpe,
            onValueChange = { submit(input.copy(rpe = it)) },
            label = "RPE",
            error = validation.rpeError,
            tag = "workout-effort-${set.id}"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (set.completed) {
                        "Set ${set.setNumber} completed"
                    } else {
                        "Mark set ${set.setNumber} complete"
                    }
                    stateDescription = if (set.completed) "Completed" else "Not completed"
                }
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
    tag: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(tag),
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

private fun formatWorkoutElapsed(totalSeconds: Long): String =
    "%d:%02d:%02d".format(
        totalSeconds / 3_600,
        (totalSeconds % 3_600) / 60,
        totalSeconds % 60
    )

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
