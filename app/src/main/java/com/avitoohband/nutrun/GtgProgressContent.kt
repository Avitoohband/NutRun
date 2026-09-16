package com.avitoohband.nutrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
internal fun GtgProgressContent(model: TrainingViewModel, range: ProgressRange) {
    val state = model.gtgState
    val today = LocalDate.now()
    val start = progressRangeStart(range, today)
    val logs = state.logs
        .distinctBy(GtgLog::id)
        .filter { !it.performedOn.isAfter(today) && (start == null || !it.performedOn.isBefore(start)) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingLog by remember { mutableStateOf<Pair<GtgPlan, GtgLog>?>(null) }

    val selectedPlan = state.plans.firstOrNull { it.id == selectedPlanId }
    if (selectedPlan != null && selectedDate == null) {
        GtgProgressDaysDialog(
            plan = selectedPlan,
            logs = logs.filter { it.planId == selectedPlan.id },
            onSelectDate = { selectedDate = it },
            onDismiss = { selectedPlanId = null }
        )
    }
    if (selectedPlan != null && selectedDate != null) {
        GtgProgressDayDialog(
            model = model,
            plan = selectedPlan,
            date = selectedDate!!,
            logs = logs.filter { it.planId == selectedPlan.id && it.performedOn == selectedDate },
            onEdit = { log ->
                editingLog = selectedPlan to log
                selectedPlanId = null
                selectedDate = null
            },
            onDismiss = { selectedDate = null }
        )
    }
    editingLog?.let { (plan, log) ->
        GtgLogDialog(model, plan, log) { editingLog = null }
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("gtg-progress"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (logs.isEmpty()) {
            Text("Log a GTG set to see practice progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.plans.filter { plan -> logs.any { it.planId == plan.id } }.forEach { plan ->
            val planLogs = logs.filter { it.planId == plan.id }
            val days = planLogs.map(GtgLog::performedOn).distinct().size
            val sets = planLogs.size
            val measurement = if (plan.measure == GtgMeasure.REPS) {
                "${planLogs.sumOf { it.reps ?: 0 }} reps"
            } else {
                "${planLogs.sumOf { it.durationSeconds ?: 0 }} sec"
            }
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { selectedPlanId = plan.id }
                    .testTag("gtg-progress-${plan.id}")
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(plan.exerciseName, fontWeight = FontWeight.Bold)
                    Text("$sets sets · $measurement · $days days practiced")
                    if (plan.archived) Text("Archived plan")
                }
            }
        }
    }
}

@Composable
private fun GtgProgressDaysDialog(
    plan: GtgPlan,
    logs: List<GtgLog>,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${plan.exerciseName} history") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                logs.groupBy(GtgLog::performedOn).toSortedMap(compareByDescending { it }).forEach { (date, dayLogs) ->
                    val totals = gtgDayTotals(dayLogs, plan.id, date)
                    TextButton(
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.fillMaxWidth().testTag("gtg-progress-day-${plan.id}-$date")
                    ) {
                        Text(
                            "${formatGtgDate(date)} · ${totals.sets} sets · " +
                                if (plan.measure == GtgMeasure.REPS) "${totals.reps} reps" else "${totals.holdSeconds} sec"
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun GtgProgressDayDialog(
    model: TrainingViewModel,
    plan: GtgPlan,
    date: LocalDate,
    logs: List<GtgLog>,
    onEdit: (GtgLog) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatGtgDate(date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                logs.sortedByDescending(GtgLog::performedAtMillis).forEach { log ->
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("gtg-progress-log-${log.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(gtgLogSummary(log, model.usesMetricUnits), Modifier.weight(1f))
                        IconButton(
                            onClick = { onEdit(log) },
                            modifier = Modifier.testTag("gtg-progress-edit-${log.id}")
                        ) { Icon(Icons.Default.Edit, "Edit GTG log") }
                        IconButton(
                            onClick = { model.deleteGtgLog(log.id) },
                            modifier = Modifier.testTag("gtg-progress-delete-${log.id}")
                        ) { Icon(Icons.Default.Delete, "Delete GTG log") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}

private fun formatGtgDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH))
