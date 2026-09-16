package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Composable
internal fun GtgLogDialog(
    model: TrainingViewModel,
    plan: GtgPlan,
    existing: GtgLog? = null,
    onDismiss: () -> Unit
) {
    var measurement by remember(existing?.id) {
        mutableStateOf((existing?.reps ?: existing?.durationSeconds ?: plan.targetPerSet).toString())
    }
    var weight by remember(existing?.id, model.usesMetricUnits) {
        mutableStateOf(
            (existing?.weightKg ?: plan.weightKg)
                ?.let { formatWeightForUnits(it, model.usesMetricUnits) }
                .orEmpty()
        )
    }
    var rir by remember(existing?.id) { mutableStateOf(existing?.rir?.toString().orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var error by remember(existing?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log GTG set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plan.exerciseName)
                OutlinedTextField(
                    value = measurement,
                    onValueChange = { measurement = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (plan.measure == GtgMeasure.REPS) "Repetitions" else "Hold seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("External load (${if (model.usesMetricUnits) "kg" else "lb"}, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rir,
                    onValueChange = { rir = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repetitions in reserve (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 1_000) notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes (optional)") }
                )
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = measurement.toIntOrNull() ?: 0
                    val parsedWeight = weight.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
                    val now = System.currentTimeMillis()
                    val zone = ZoneId.systemDefault()
                    val log = runCatching {
                        require(weight.isBlank() || parsedWeight != null) { "Enter a valid weight." }
                        GtgLog(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            planId = plan.id,
                            exerciseId = plan.exerciseId,
                            exerciseName = plan.exerciseName,
                            measure = plan.measure,
                            reps = amount.takeIf { plan.measure == GtgMeasure.REPS },
                            durationSeconds = amount.takeIf { plan.measure == GtgMeasure.HOLD_SECONDS },
                            weightKg = parsedWeight?.let { convertWeightInputToKg(it, model.usesMetricUnits) },
                            rir = rir.takeIf(String::isNotBlank)?.toIntOrNull(),
                            notes = notes.trim(),
                            performedAtMillis = existing?.performedAtMillis ?: now,
                            zoneId = existing?.zoneId ?: zone.id,
                            performedOn = existing?.performedOn
                                ?: Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
                            dailySetGoalSnapshot = existing?.dailySetGoalSnapshot ?: plan.dailySetGoal
                        )
                    }.getOrElse {
                        error = it.message ?: "Check the set details."
                        return@Button
                    }
                    when (val result = model.saveGtgLog(log)) {
                        TrainingMutationResult.Success -> onDismiss()
                        is TrainingMutationResult.ValidationError -> error = result.message
                        else -> error = "GTG is still loading."
                    }
                },
                modifier = Modifier.testTag("gtg-save-log")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
