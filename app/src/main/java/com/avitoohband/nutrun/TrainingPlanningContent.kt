package com.avitoohband.nutrun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
internal fun TrainingPlanningContent(
    model: TrainingViewModel,
    onOpenTemplate: (WorkoutTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    var assignmentDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var createWorkout by remember { mutableStateOf(false) }
    var deleteTemplate by remember { mutableStateOf<WorkoutTemplate?>(null) }
    val today = LocalDate.now().dayOfWeek
    val templatesById = model.workoutTemplates.associateBy(WorkoutTemplate::id)

    assignmentDay?.let { day ->
        AssignmentDialog(
            day = day,
            templates = model.workoutTemplates,
            initialTemplateIds = model.weeklyDayPlans.firstOrNull { it.weekday == day }?.templateIds.orEmpty(),
            onDismiss = { assignmentDay = null },
            onSave = { templateIds ->
                model.replaceAssignments(day, templateIds)
                assignmentDay = null
            }
        )
    }
    if (createWorkout) {
        CreateWorkoutDialog(
            onDismiss = { createWorkout = false },
            onSave = { name ->
                if (model.createWorkout(name) is TrainingMutationResult.Success) createWorkout = false
            }
        )
    }
    deleteTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTemplate = null },
            title = { Text("Delete workout?") },
            text = { Text("This removes it from future weekday assignments. Completed workout history is kept.") },
            confirmButton = {
                Button(onClick = {
                    if (model.deleteWorkout(template.id) is TrainingMutationResult.Success) deleteTemplate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTemplate = null }) { Text("Keep") } }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("training-list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TrainingPlanningHeading("Weekly schedule", Modifier.testTag("weekly-schedule")) }
        items(DayOfWeek.entries, key = { it.name }) { day ->
            val plan = model.weeklyDayPlans.firstOrNull { it.weekday == day }
            val assignments = plan?.templateIds.orEmpty().mapNotNull(templatesById::get)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("day-plan-${day.name}"),
                border = if (day == today) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.displayName(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (day == today) Text("Today", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    when {
                        plan?.isRestDay == true -> Text("Rest day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        assignments.isEmpty() -> Text("Unplanned", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> assignments.forEach { template ->
                            Text(
                                template.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTemplate(template) }
                                    .testTag("assigned-workout-${template.id}"),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { assignmentDay = day },
                            modifier = Modifier.testTag("assign-day-${day.name}")
                        ) { Text("Assign") }
                        TextButton(
                            onClick = { model.setRestDay(day) },
                            modifier = Modifier.testTag("rest-day-${day.name}")
                        ) { Text("Rest day") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("workout-library")) {
                TrainingPlanningHeading("Workout library", Modifier.weight(1f))
                IconButton(onClick = { createWorkout = true }, modifier = Modifier.testTag("create-workout")) {
                    Icon(Icons.Default.Add, "Create workout")
                }
            }
        }
        items(model.workoutTemplates, key = WorkoutTemplate::id) { template ->
            val isActive = model.activeSession()?.id == template.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workout-card-${template.id}")
                    .clickable { onOpenTemplate(template) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(template.name, fontWeight = FontWeight.Bold)
                        Text("${template.logicalTargetCount()} planned targets", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        if (template.exercises.isEmpty()) {
                            Text("Add exercises to start", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        val weekdays = model.weeklyDayPlans.filter { template.id in it.templateIds }.joinToString { it.weekday.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
                        if (weekdays.isNotBlank()) Text(weekdays, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        template.exercises.forEach { target ->
                            model.progressionSuggestion(target)?.let { suggestion ->
                                val action = when (suggestion.action) {
                                    ProgressionAction.INCREASE -> "Increase to"
                                    ProgressionAction.KEEP -> "Keep"
                                    ProgressionAction.REDUCE -> "Reduce to"
                                }
                                Text(
                                    "${target.exercise.name}: $action ${displayWeight(suggestion.suggestedWeightKg, model.usesMetricUnits)}",
                                    modifier = Modifier.testTag("program-progression-${target.id}"),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    IconButton(onClick = { model.startWorkout(template.id) }, enabled = template.exercises.isNotEmpty(), modifier = Modifier.testTag("start-session-${template.id}")) {
                        Icon(Icons.Default.PlayArrow, "Start ${template.name}")
                    }
                    IconButton(onClick = { onOpenTemplate(template) }, modifier = Modifier.testTag("edit-workout-${template.id}")) {
                        Icon(Icons.Default.Edit, "Edit ${template.name}")
                    }
                    IconButton(
                        onClick = { deleteTemplate = template },
                        enabled = !isActive,
                        modifier = Modifier.testTag("delete-workout-${template.id}")
                    ) { Icon(Icons.Default.Delete, "Delete ${template.name}") }
                }
            }
        }
    }
}

@Composable
internal fun WorkoutTemplateDetailsDialog(
    template: WorkoutTemplate,
    usesMetricUnits: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(template.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (template.exercises.isEmpty()) Text("Add exercises before starting this workout.")
                template.exercises.forEach { target ->
                    Text("${target.exercise.name}: ${target.summary(usesMetricUnits)}")
                }
                template.guidance.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = onStart, enabled = template.exercises.isNotEmpty(), modifier = Modifier.testTag("start-session-${template.id}")) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    )
}

@Composable
private fun AssignmentDialog(
    day: DayOfWeek,
    templates: List<WorkoutTemplate>,
    initialTemplateIds: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selectedIds by remember(day, initialTemplateIds) { mutableStateOf(initialTemplateIds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign ${day.displayName()} workouts") },
        text = {
            Column {
                templates.forEach { template ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        selectedIds = if (template.id in selectedIds) selectedIds - template.id else selectedIds + template.id
                    }) {
                        Checkbox(checked = template.id in selectedIds, onCheckedChange = null)
                        Text(template.name)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(selectedIds) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateWorkoutDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create workout") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Workout name") }) },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable
private fun TrainingPlanningHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    )
}
