package com.avitoohband.nutrun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.launch

internal enum class TrainingPlanningMode {
    SCHEDULE,
    WORKOUTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingPlanningContent(
    model: TrainingViewModel,
    mode: TrainingPlanningMode = TrainingPlanningMode.SCHEDULE,
    onModeChange: (TrainingPlanningMode) -> Unit = {},
    onOpenTemplate: (WorkoutTemplate) -> Unit,
    onEditTemplate: (WorkoutTemplate) -> Unit = onOpenTemplate,
    onDuplicateTemplate: (WorkoutTemplate) -> Unit = { model.duplicateWorkout(it.id) },
    onAssignDay: (DayOfWeek) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TrainingPlanningMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { onModeChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, TrainingPlanningMode.entries.size),
                    modifier = Modifier.testTag(
                        when (option) {
                            TrainingPlanningMode.SCHEDULE -> "training-mode-schedule"
                            TrainingPlanningMode.WORKOUTS -> "training-mode-workouts"
                        }
                    )
                ) {
                    Text(
                        when (option) {
                            TrainingPlanningMode.SCHEDULE -> "Schedule"
                            TrainingPlanningMode.WORKOUTS -> "Workouts"
                        }
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (mode) {
                TrainingPlanningMode.SCHEDULE -> TrainingScheduleContent(
                    model = model,
                    onOpenTemplate = onOpenTemplate,
                    onAssignDay = onAssignDay
                )
                TrainingPlanningMode.WORKOUTS -> WorkoutLibraryContent(
                    model = model,
                    onOpenTemplate = onOpenTemplate,
                    onEditTemplate = onEditTemplate,
                    onDuplicateTemplate = onDuplicateTemplate
                )
            }
        }
    }
}

@Composable
private fun TrainingScheduleContent(
    model: TrainingViewModel,
    onOpenTemplate: (WorkoutTemplate) -> Unit,
    onAssignDay: (DayOfWeek) -> Unit
) {
    val today = LocalDate.now().dayOfWeek
    val templatesById = model.workoutTemplates.associateBy(WorkoutTemplate::id)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expandedDayName by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("training-list"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("weekly-schedule"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrainingPlanningHeading("Weekly schedule", Modifier.weight(1f))
                IconButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(DayOfWeek.entries.indexOf(today) + 1)
                        }
                    },
                    modifier = Modifier.testTag("today-day-shortcut")
                ) {
                    Icon(Icons.Default.MyLocation, "Go to today")
                }
            }
        }
        items(DayOfWeek.entries, key = DayOfWeek::name) { day ->
            val plan = model.weeklyDayPlans.firstOrNull { it.weekday == day }
            val assignments = plan?.templateIds.orEmpty().mapNotNull(templatesById::get)
            val expanded = expandedDayName == day.name
            Card(
                modifier = Modifier.fillMaxWidth().testTag("day-plan-${day.name}"),
                border = if (day == today) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(day.displayName(), fontWeight = FontWeight.Bold)
                                if (day == today) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Today",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            when {
                                plan?.isRestDay == true -> Text(
                                    "Rest day",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                                assignments.isEmpty() -> Text(
                                    "Unplanned",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                                else -> assignments.forEach { template ->
                                    Text(
                                        template.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenTemplate(template) }
                                            .testTag("assigned-workout-${day.name}-${template.id}"),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                expandedDayName = if (expanded) null else day.name
                            },
                            modifier = Modifier.testTag("day-actions-${day.name}")
                        ) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                if (expanded) "Hide ${day.displayName()} actions" else "Show ${day.displayName()} actions"
                            )
                        }
                    }
                    if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onAssignDay(day) },
                                modifier = Modifier.testTag("assign-day-${day.name}")
                            ) {
                                Text("Assign")
                            }
                            TextButton(
                                onClick = {
                                    model.setRestDay(day)
                                    expandedDayName = null
                                },
                                modifier = Modifier.testTag("rest-day-${day.name}")
                            ) {
                                Text("Rest day")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutLibraryContent(
    model: TrainingViewModel,
    onOpenTemplate: (WorkoutTemplate) -> Unit,
    onEditTemplate: (WorkoutTemplate) -> Unit,
    onDuplicateTemplate: (WorkoutTemplate) -> Unit
) {
    var createWorkout by remember { mutableStateOf(false) }
    var deleteTemplate by remember { mutableStateOf<WorkoutTemplate?>(null) }

    if (createWorkout) {
        CreateWorkoutDialog(
            onDismiss = { createWorkout = false },
            onSave = { name ->
                if (model.createWorkout(name) is TrainingMutationResult.Success) {
                    createWorkout = false
                }
            }
        )
    }
    deleteTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTemplate = null },
            title = { Text("Delete workout?") },
            text = {
                Text("This removes it from future weekday assignments. Completed workout history is kept.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (model.deleteWorkout(template.id) is TrainingMutationResult.Success) {
                            deleteTemplate = null
                        }
                    },
                    modifier = Modifier.testTag("confirm-delete-workout")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTemplate = null }) {
                    Text("Keep")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("training-list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().testTag("workout-library")
            ) {
                TrainingPlanningHeading("Workout library", Modifier.weight(1f))
                IconButton(
                    onClick = { createWorkout = true },
                    modifier = Modifier.testTag("create-workout")
                ) {
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
            ) {
                Column {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTemplate(template) }
                            .padding(12.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Open ${template.name} workout details"
                            }
                    ) {
                        Text(template.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${template.logicalTargetCount()} planned targets",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        if (template.exercises.isEmpty()) {
                            Text(
                                "Add exercises to start",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        val weekdays = model.weeklyDayPlans
                            .filter { template.id in it.templateIds }
                            .joinToString { it.weekday.displayName().take(3) }
                        if (weekdays.isNotBlank()) {
                            Text(
                                weekdays,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
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
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        TextButton(
                            onClick = { model.startWorkout(template.id) },
                            enabled = template.exercises.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start-session-${template.id}")
                                .semantics {
                                    if (template.exercises.isEmpty()) {
                                        contentDescription =
                                            "Start ${template.name}, disabled because workout has no exercises"
                                    }
                                }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start workout")
                            Spacer(Modifier.width(4.dp))
                            Text("Start")
                        }
                        TextButton(
                            onClick = { onEditTemplate(template) },
                            modifier = Modifier.weight(1f).testTag("edit-workout-${template.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${template.name}")
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        TextButton(
                            onClick = { onDuplicateTemplate(template) },
                            modifier = Modifier.weight(1f).testTag("duplicate-workout-${template.id}")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate ${template.name}")
                            Spacer(Modifier.width(4.dp))
                            Text("Duplicate")
                        }
                        TextButton(
                            onClick = { deleteTemplate = template },
                            enabled = !isActive,
                            modifier = Modifier.weight(1f).testTag("delete-workout-${template.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${template.name}")
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }        }
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
                if (template.exercises.isEmpty()) {
                    Text("Add exercises before starting this workout.")
                }
                template.exercises.forEach { target ->
                    Text("${target.exercise.name}: ${target.summary(usesMetricUnits)}")
                }
                template.guidance.forEach {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                enabled = template.exercises.isNotEmpty(),
                modifier = Modifier.testTag("start-session-${template.id}")
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onEdit) {
                Text("Edit")
            }
        }
    )
}

@Composable
private fun CreateWorkoutDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create workout") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Workout name") }
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
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
