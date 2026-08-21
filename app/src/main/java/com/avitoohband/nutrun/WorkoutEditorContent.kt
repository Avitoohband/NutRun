package com.avitoohband.nutrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutEditorContent(
    model: TrainingViewModel,
    templateId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack
) {
    val template = model.workoutTemplates.firstOrNull { it.id == templateId }
    if (template == null) {
        LaunchedEffect(templateId) { onBack() }
        return
    }

    var query by rememberSaveable(templateId) { mutableStateOf("") }
    var category by rememberSaveable(templateId) { mutableStateOf("All") }
    var name by rememberSaveable(templateId) { mutableStateOf(template.name) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var createCustom by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ExerciseTarget?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val targets = remember(templateId) {
        mutableStateListOf<ExerciseTarget>().apply { addAll(template.exercises) }
    }
    val pendingCustomExercises = remember(templateId) { mutableStateListOf<Exercise>() }
    val isDirty = name != template.name || targets.toList() != template.exercises || pendingCustomExercises.isNotEmpty()
    val requestBack = {
        if (isDirty) confirmDiscard = true else onBack()
    }

    BackHandler(onBack = requestBack)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved workout changes will be lost.") },
            confirmButton = {
                Button(onClick = onBack) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            }
        )
    }
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${target.exercise.name}?") },
            text = { Text("This removes it only from this workout.") },
            confirmButton = {
                Button(
                    onClick = {
                        targets.removeAll { it.id == target.id }
                        removeTarget = null
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            }
        )
    }
    if (createCustom) {
        CustomExerciseDialog(
            existingNames = model.exerciseLibrary.map(Exercise::name) + pendingCustomExercises.map(Exercise::name),
            onCreate = { exercise ->
                pendingCustomExercises += exercise
                targets += ExerciseTarget(
                    id = "target-${UUID.randomUUID()}",
                    exercise = exercise
                )
                createCustom = false
            },
            onDismiss = { createCustom = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("workout-editor-screen"),
        topBar = {
            TopAppBar(
                title = { Text("Edit workout") },
                navigationIcon = {
                    IconButton(
                        onClick = requestBack,
                        modifier = Modifier.testTag("workout-editor-back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = requestBack, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        when (
                            val result = model.saveWorkoutDraft(
                                templateId = templateId,
                                name = name,
                                exercises = targets.toList(),
                                newCustomExercises = pendingCustomExercises.toList()
                            )
                        ) {
                            TrainingMutationResult.Success -> onSaved()
                            is TrainingMutationResult.ValidationError -> nameError = result.message
                            else -> nameError = "Unable to save workout."
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("workout-editor-save")
                ) {
                    Text("Save")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("workout-editor"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("workout-name"),
                    label = { Text("Workout name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { message -> ({ Text(message) }) }
                )
            }
            item { Text("Current exercises", fontWeight = FontWeight.Bold) }
            if (targets.isEmpty()) {
                item { Text("No exercises yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(targets, key = ExerciseTarget::id) { target ->
                TargetEditorRow(
                    target = target,
                    metric = model.usesMetricUnits,
                    onSets = { sets ->
                        val index = targets.indexOfFirst { it.id == target.id }
                        if (index >= 0) targets[index] = targets[index].copy(sets = sets)
                    },
                    onRemove = { removeTarget = target }
                )
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add exercise", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { createCustom = true },
                        modifier = Modifier.testTag("create-custom-exercise")
                    ) {
                        Icon(Icons.Default.Add, null)
                        Text("Custom")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("exercise-search"),
                    label = { Text("Search exercises") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (listOf("All") + model.exerciseLibrary.map(Exercise::category).distinct()).forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(value) },
                            modifier = Modifier.testTag("exercise-category-$value")
                        )
                    }
                }
            }
            items(filterExercises(model.exerciseLibrary, query, category), key = Exercise::id) { exercise ->
                val included = targets.any { it.exercise.id == exercise.id }
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = { Text("${exercise.category} | ${exercise.primaryMuscles}") },
                    trailingContent = { if (included) Text("Added") else Icon(Icons.Default.Add, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog-exercise-${exercise.id}")
                        .clickable(enabled = !included) {
                            targets += ExerciseTarget(
                                id = "target-${UUID.randomUUID()}",
                                exercise = exercise
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun TargetEditorRow(
    target: ExerciseTarget,
    metric: Boolean,
    onSets: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var text by remember(target.id, target.sets) { mutableStateOf(target.sets.toString()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(target.exercise.name)
            Text(target.summary(metric), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { onSets((target.sets - 1).coerceAtLeast(1)) }) {
            Icon(Icons.Default.Remove, "Fewer sets")
        }
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value
                value.toIntOrNull()?.takeIf { it in 1..20 }?.let(onSets)
            },
            modifier = Modifier.width(72.dp).testTag("target-sets-${target.id}"),
            singleLine = true
        )
        IconButton(onClick = { onSets((target.sets + 1).coerceAtMost(20)) }) {
            Icon(Icons.Default.Add, "More sets")
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag("remove-target-${target.id}")
        ) {
            Icon(Icons.Default.Delete, "Remove ${target.exercise.name}")
        }
    }
}

@Composable
private fun CustomExerciseDialog(
    existingNames: List<String>,
    onCreate: (Exercise) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Custom") }
    var muscles by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("custom-exercise-name"),
                    label = { Text("Name") }
                )
                TextButton(
                    onClick = { details = !details },
                    modifier = Modifier.testTag("custom-exercise-details")
                ) { Text("Details") }
                if (details) {
                    OutlinedTextField(category, { category = it }, label = { Text("Category") })
                    OutlinedTextField(muscles, { muscles = it }, label = { Text("Primary muscles") })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    when {
                        trimmedName.isEmpty() -> error = "Exercise name cannot be blank."
                        existingNames.any { it.trim().equals(trimmedName, ignoreCase = true) } -> {
                            error = "An exercise with this name already exists."
                        }
                        else -> onCreate(
                            Exercise(
                                id = "exercise-${UUID.randomUUID()}",
                                name = trimmedName,
                                category = category.trim().ifEmpty { "Custom" },
                                primaryMuscles = muscles.trim(),
                                secondaryMuscles = "",
                                instructions = "",
                                safetyNote = ""
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("save-custom-exercise"),
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
