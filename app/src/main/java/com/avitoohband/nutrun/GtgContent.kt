package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun GtgContent(model: TrainingViewModel) {
    var editing by remember { mutableStateOf<GtgPlan?>(null) }
    var creating by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf<GtgPlan?>(null) }
    var editingLog by remember { mutableStateOf<Pair<GtgPlan, GtgLog>?>(null) }
    if (creating || editing != null) {
        GtgPlanEditorDialog(model, editing) {
            creating = false
            editing = null
        }
    }
    logging?.let { plan -> GtgLogDialog(model, plan) { logging = null } }
    editingLog?.let { (plan, log) ->
        GtgLogDialog(model, plan, log) { editingLog = null }
    }
    val plans = model.gtgState.plans
    val logs = model.gtgState.logs
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("gtg-list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Grease the Groove", fontWeight = FontWeight.Bold)
                    Text(
                        "Practice small sets through the day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { creating = true },
                    modifier = Modifier.testTag("gtg-create-plan")
                ) { Icon(Icons.Default.Add, "Create GTG plan") }
            }
        }
        if (plans.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("gtg-empty"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No GTG plans yet", fontWeight = FontWeight.Bold)
                    Text("Choose an exercise and a daily set goal.")
                    Button(onClick = { creating = true }) { Text("Create GTG plan") }
                }
            }
        }
        items(plans.sortedBy(GtgPlan::archived), key = GtgPlan::id) { plan ->
            val planLogs = logs.filter { it.planId == plan.id }.sortedByDescending(GtgLog::performedAtMillis)
            val today = gtgDayTotals(planLogs, plan.id, LocalDate.now())
            Card(Modifier.fillMaxWidth().testTag("gtg-plan-${plan.id}")) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.exerciseName, fontWeight = FontWeight.Bold)
                            Text(gtgPlanSummary(plan, model.usesMetricUnits))
                            Text("Today: ${today.sets} / ${plan.dailySetGoal} sets")
                            if (plan.archived) Text("Archived", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = plan }) { Icon(Icons.Default.Edit, "Edit ${plan.exerciseName}") }
                        IconButton(onClick = { model.archiveGtgPlan(plan.id, !plan.archived) }) {
                            Icon(Icons.Default.Archive, if (plan.archived) "Restore ${plan.exerciseName}" else "Archive ${plan.exerciseName}")
                        }
                    }
                    if (!plan.archived) {
                        Button(
                            onClick = { logging = plan },
                            modifier = Modifier.testTag("gtg-log-${plan.id}")
                        ) { Text("Log set") }
                    }
                    planLogs.take(10).forEach { log ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(gtgLogSummary(log, model.usesMetricUnits), Modifier.weight(1f))
                            TextButton(onClick = { editingLog = plan to log }) { Text("Edit") }
                            IconButton(onClick = { model.deleteGtgLog(log.id) }) {
                                Icon(Icons.Default.Delete, "Delete GTG log")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GtgTodaySection(model: TrainingViewModel) {
    val today = LocalDate.now()
    val plans = model.gtgState.plans.filter { !it.archived && today.dayOfWeek in it.weekdays }
    var logging by remember { mutableStateOf<GtgPlan?>(null) }
    logging?.let { plan -> GtgLogDialog(model, plan) { logging = null } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plans.forEach { plan ->
            val totals = gtgDayTotals(model.gtgState.logs, plan.id, today)
            Card(Modifier.fillMaxWidth().testTag("today-gtg-plan-${plan.id}")) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.exerciseName, fontWeight = FontWeight.Bold)
                        Text("${totals.sets} of ${plan.dailySetGoal} sets today")
                    }
                    TextButton(
                        onClick = { logging = plan },
                        modifier = Modifier.testTag("today-gtg-log-${plan.id}")
                    ) { Text("Log set") }
                }
            }
        }
    }
}

internal fun gtgPlanSummary(plan: GtgPlan, metric: Boolean): String = buildString {
    append(plan.targetPerSet)
    append(if (plan.measure == GtgMeasure.REPS) " reps" else " sec")
    plan.weightKg?.let { append(" at ${displayWeight(it, metric)}") }
    append(" · ${plan.weekdays.joinToString { it.gtgDisplayName().take(3) }}")
}

internal fun gtgLogSummary(log: GtgLog, metric: Boolean): String = buildString {
    append(log.performedOn.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)))
    append(" · ${log.reps?.let { "$it reps" } ?: "${log.durationSeconds} sec"}")
    log.weightKg?.let { append(" · ${displayWeight(it, metric)}") }
    log.rir?.let { append(" · RIR $it") }
}
