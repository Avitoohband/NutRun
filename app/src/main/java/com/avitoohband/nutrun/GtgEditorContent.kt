package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.util.UUID
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GtgPlanEditorDialog(
    model: TrainingViewModel,
    existing: GtgPlan? = null,
    onDismiss: () -> Unit
) {
    var selectedExerciseId by remember(existing?.id) {
        mutableStateOf(existing?.exerciseId ?: model.exerciseLibrary.firstOrNull()?.id)
    }
    var query by remember(existing?.id) { mutableStateOf("") }
    var measure by remember(existing?.id) { mutableStateOf(existing?.measure ?: GtgMeasure.REPS) }
    var target by remember(existing?.id) { mutableStateOf(existing?.targetPerSet?.toString() ?: "5") }
    var goal by remember(existing?.id) { mutableStateOf(existing?.dailySetGoal?.toString() ?: "5") }
    var weight by remember(existing?.id, model.usesMetricUnits) {
        mutableStateOf(existing?.weightKg?.let { formatWeightForUnits(it, model.usesMetricUnits) }.orEmpty())
    }
    var weekdays by remember(existing?.id) {
        mutableStateOf(existing?.weekdays ?: DayOfWeek.entries.toSet())
    }
    var error by remember(existing?.id) { mutableStateOf<String?>(null) }
    val matchingExercises = model.exerciseLibrary.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
    }.take(12)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New GTG plan" else "Edit GTG plan") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp).testTag("gtg-plan-editor"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search exercise") },
                        singleLine = true
                    )
                }
                items(matchingExercises, key = Exercise::id) { exercise ->
                    FilterChip(
                        selected = selectedExerciseId == exercise.id,
                        onClick = { selectedExerciseId = exercise.id },
                        label = { Text(exercise.name) }
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GtgMeasure.entries.forEach { option ->
                            FilterChip(
                                selected = measure == option,
                                onClick = { measure = option },
                                label = { Text(if (option == GtgMeasure.REPS) "Repetitions" else "Hold") }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (measure == GtgMeasure.REPS) "Reps per set" else "Seconds per hold") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { goal = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Daily set goal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("External load (${if (model.usesMetricUnits) "kg" else "lb"}, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                item {
                    Text("Practice days")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = {
                                    weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                },
                                label = { Text(day.gtgDisplayName().take(3)) }
                            )
                        }
                    }
                }
                error?.let { message -> item { Text(message) } }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val exercise = model.exerciseLibrary.firstOrNull { it.id == selectedExerciseId }
                    val parsedWeight = weight.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
                    val plan = runCatching {
                        require(exercise != null) { "Choose an exercise." }
                        require(weight.isBlank() || parsedWeight != null) { "Enter a valid weight." }
                        GtgPlan(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            measure = measure,
                            targetPerSet = target.toIntOrNull() ?: 0,
                            dailySetGoal = goal.toIntOrNull() ?: 0,
                            weekdays = weekdays,
                            weightKg = parsedWeight?.let { convertWeightInputToKg(it, model.usesMetricUnits) },
                            archived = existing?.archived ?: false
                        )
                    }.getOrElse {
                        error = it.message ?: "Check the plan details."
                        return@Button
                    }
                    when (val result = model.saveGtgPlan(plan)) {
                        TrainingMutationResult.Success -> onDismiss()
                        is TrainingMutationResult.ValidationError -> error = result.message
                        else -> error = "GTG is still loading."
                    }
                },
                modifier = Modifier.testTag("gtg-save-plan")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal fun DayOfWeek.gtgDisplayName(): String =
    name.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
