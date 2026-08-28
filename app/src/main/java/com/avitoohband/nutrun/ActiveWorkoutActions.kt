package com.avitoohband.nutrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ActiveWorkoutActions(
    model: TrainingViewModel,
    focusedTargetId: String?,
    onExerciseAdded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.weight(1f).testTag("active-add-exercise")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add exercise")
        }
        focusedTargetId?.let { targetId ->
            val isSkipped = model.activeWorkout?.skippedTargetIds?.contains(targetId) == true
            if (!isSkipped) {
                OutlinedButton(
                    onClick = { model.skipActiveExercise(targetId) },
                    modifier = Modifier.weight(1f).testTag("active-skip-$targetId")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Text("Skip")
                }
            }
        }
    }
    if (showPicker) {
        ActiveExercisePicker(
            model = model,
            onDismiss = { showPicker = false },
            onSelect = { exerciseId ->
                when (model.addExerciseToActiveWorkout(exerciseId)) {
                    TrainingMutationResult.Success -> onExerciseAdded(exerciseId)
                    else -> Unit
                }
                showPicker = false
            }
        )
    }
}

@Composable
internal fun ActiveExercisePicker(
    model: TrainingViewModel,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val activeExerciseIds = model.activeWorkout?.exercises?.map { it.exercise.id }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("active-exercise-search"),
                    label = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (listOf("All") + model.exerciseLibrary.map(Exercise::category).distinct()).forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(value) }
                        )
                    }
                }
                LazyColumn {
                    items(
                        filterExercises(model.exerciseLibrary, query, category)
                            .filterNot { it.id in activeExerciseIds },
                        key = Exercise::id
                    ) { exercise ->
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text(exercise.category) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active-add-${exercise.id}")
                                .clickable { onSelect(exercise.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun SkippedExerciseCard(
    target: ExerciseTarget,
    metric: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("skipped-exercise-${target.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Skipped", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            Text(target.exercise.name, style = MaterialTheme.typography.titleMedium)
            Text(target.summary(metric), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.testTag("active-undo-skip-${target.id}")
            ) {
                Text("Undo skip")
            }
        }
    }
}

@Composable
internal fun QuickWorkoutEmptyState(
    model: TrainingViewModel,
    onExerciseAdded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("quick-workout-empty"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("No exercises yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add exercises as you train.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { showPicker = true },
            modifier = Modifier.testTag("quick-workout-add-first")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add first exercise")
        }
    }
    if (showPicker) {
        ActiveExercisePicker(
            model = model,
            onDismiss = { showPicker = false },
            onSelect = { exerciseId ->
                when (model.addExerciseToActiveWorkout(exerciseId)) {
                    TrainingMutationResult.Success -> onExerciseAdded(exerciseId)
                    else -> Unit
                }
                showPicker = false
            }
        )
    }
}
