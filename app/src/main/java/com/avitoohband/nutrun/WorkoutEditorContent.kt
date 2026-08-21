package com.avitoohband.nutrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkoutEditorContent(model: TrainingViewModel, templateId: String, onDone: () -> Unit) {
    val template = model.workoutTemplates.firstOrNull { it.id == templateId } ?: return onDone()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var createCustom by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ExerciseTarget?>(null) }
    var name by remember(template.id) { mutableStateOf(template.name) }
    var nameError by remember { mutableStateOf<String?>(null) }
    if (createCustom) CustomExerciseDialog(model, templateId, onDismiss = { createCustom = false })
    removeTarget?.let { target -> AlertDialog(onDismissRequest = { removeTarget = null }, title = { Text("Remove ${target.exercise.name}?") }, text = { Text("This removes it only from this workout.") }, confirmButton = { Button(onClick = { model.selectSession(templateId); model.removeExerciseFromSelectedSession(target.id); removeTarget = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Edit workout") },
        text = { LazyColumn(Modifier.testTag("workout-editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it; nameError = null }, Modifier.fillMaxWidth().testTag("workout-name"), label = { Text("Workout name") }, isError = nameError != null) }
            nameError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Text("Current exercises", fontWeight = FontWeight.Bold) }
            items(template.exercises, key = ExerciseTarget::id) { target -> TargetEditorRow(target, model.usesMetricUnits, { sets -> model.updateTargetSets(templateId, target.id, sets) }, { removeTarget = target }) }
            item { Text("Exercise catalog", fontWeight = FontWeight.Bold) }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("exercise-search"), label = { Text("Search exercises") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All") .plus(model.exerciseLibrary.map(Exercise::category).distinct()).forEach { value -> FilterChip(category == value, { category = value }, { Text(value) }, modifier = Modifier.testTag("exercise-category-$value")) } } }
            items(filterExercises(model.exerciseLibrary, query, category), key = Exercise::id) { exercise -> val included = template.exercises.any { it.exercise.id == exercise.id }; ListItem(headlineContent = { Text(exercise.name) }, supportingContent = { Text("${exercise.category} | ${exercise.primaryMuscles}") }, modifier = Modifier.fillMaxWidth().clickable(enabled = !included) { model.selectSession(templateId); model.addExerciseToSelectedSession(exercise) }, trailingContent = { if (included) Text("Added") }) }
        } },
        confirmButton = { Row { TextButton(onClick = { createCustom = true }, modifier = Modifier.testTag("create-custom-exercise")) { Icon(Icons.Default.Add, null); Text("Custom") }; Button(onClick = { val result = model.renameWorkout(templateId, name); if (result is TrainingMutationResult.Success) onDone() else if (result is TrainingMutationResult.ValidationError) nameError = result.message }) { Text("Done") } } }
    )
}

@Composable private fun TargetEditorRow(target: ExerciseTarget, metric: Boolean, onSets: (Int) -> Unit, onRemove: () -> Unit) {
    var text by remember(target.id, target.sets) { mutableStateOf(target.sets.toString()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(target.exercise.name); Text(target.summary(metric), style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = { onSets((target.sets - 1).coerceAtLeast(1)) }) { Icon(Icons.Default.Remove, "Fewer sets") }; OutlinedTextField(text, { value -> text = value; value.toIntOrNull()?.takeIf { it in 1..20 }?.let(onSets) }, Modifier.width(72.dp).testTag("target-sets-${target.id}"), singleLine = true); IconButton(onClick = { onSets((target.sets + 1).coerceAtMost(20)) }) { Icon(Icons.Default.Add, "More sets") }; IconButton(onClick = onRemove, modifier = Modifier.testTag("remove-target-${target.id}")) { Icon(Icons.Default.Delete, "Remove ${target.exercise.name}") } }
}

@Composable private fun CustomExerciseDialog(model: TrainingViewModel, templateId: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var details by remember { mutableStateOf(false) }; var category by remember { mutableStateOf("Custom") }; var muscles by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Custom exercise") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().testTag("custom-exercise-name"), label = { Text("Name") }); TextButton(onClick = { details = !details }, modifier = Modifier.testTag("custom-exercise-details")) { Text("Details") }; if(details) { OutlinedTextField(category, { category = it }, label = { Text("Category") }); OutlinedTextField(muscles, { muscles = it }, label = { Text("Primary muscles") }) }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button(onClick = { when(val result = model.createCustomExerciseAndAdd(templateId, CustomExerciseDraft(name = name, category = category, primaryMuscles = muscles))) { is TrainingMutationResult.Success -> onDismiss(); is TrainingMutationResult.ValidationError -> error = result.message; else -> error = "Unable to save exercise." } }, modifier = Modifier.testTag("save-custom-exercise"), enabled = name.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
