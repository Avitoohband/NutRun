package com.avitoohband.nutrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutAssignmentContent(
    day: DayOfWeek,
    templates: List<WorkoutTemplate>,
    selectedIds: List<String>,
    onSave: (List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val templatesById = templates.associateBy(WorkoutTemplate::id)
    var query by rememberSaveable(day.name) { mutableStateOf("") }
    var orderedIds by rememberSaveable(day.name, selectedIds) {
        mutableStateOf(selectedIds.distinct().filter(templatesById::containsKey))
    }
    val matchingTemplates = filterWorkoutTemplates(templates, query)
    val matchingIds = matchingTemplates.map(WorkoutTemplate::id).toSet()
    val selectedTemplates = orderedIds
        .mapNotNull(templatesById::get)
        .filter { it.id in matchingIds }
    val unselectedTemplates = matchingTemplates.filter { it.id !in orderedIds }
    val displayedTemplates = selectedTemplates + unselectedTemplates

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("workout-assignment"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Assign ${day.displayNameForAssignment()}")
                        Text(
                            "${orderedIds.size} selected",
                            modifier = Modifier.testTag("assignment-selected-count"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag("assignment-close")) {
                        Icon(Icons.Default.Close, "Close assignment")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("assignment-cancel")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(orderedIds) },
                        modifier = Modifier.testTag("assignment-save")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("assignment-search"),
                label = { Text("Search workouts") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("assignment-list"),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(displayedTemplates, key = WorkoutTemplate::id) { template ->
                    val selected = template.id in orderedIds
                    val selectedIndex = orderedIds.indexOf(template.id)
                    ListItem(
                        headlineContent = { Text(template.name) },
                        supportingContent = {
                            Text("${template.logicalTargetCount()} planned targets")
                        },
                        leadingContent = {
                            Checkbox(checked = selected, onCheckedChange = null)
                        },
                        trailingContent = {
                            if (selected) {
                                Row {
                                    IconButton(
                                        onClick = {
                                            orderedIds = moveAssignedWorkout(
                                                orderedIds,
                                                selectedIndex,
                                                selectedIndex - 1
                                            )
                                        },
                                        enabled = selectedIndex > 0,
                                        modifier = Modifier.testTag(
                                            "assignment-move-up-${template.id}"
                                        )
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, "Move ${template.name} up")
                                    }
                                    IconButton(
                                        onClick = {
                                            orderedIds = moveAssignedWorkout(
                                                orderedIds,
                                                selectedIndex,
                                                selectedIndex + 1
                                            )
                                        },
                                        enabled = selectedIndex < orderedIds.lastIndex,
                                        modifier = Modifier.testTag(
                                            "assignment-move-down-${template.id}"
                                        )
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, "Move ${template.name} down")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("assignment-option-${template.id}")
                            .clickable {
                                orderedIds = if (selected) {
                                    orderedIds - template.id
                                } else {
                                    orderedIds + template.id
                                }
                            }
                    )
                }
            }
        }
    }
}

private fun DayOfWeek.displayNameForAssignment(): String =
    name.lowercase().replaceFirstChar(Char::uppercase)
