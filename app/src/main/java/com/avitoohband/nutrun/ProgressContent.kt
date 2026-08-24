package com.avitoohband.nutrun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.domain.EntitlementKind
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.domain.WalkState
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun ProgressOverviewContent(
    state: NutRunUiState,
    training: TrainingViewModel,
    healthConnect: HealthConnectUiState,
    onWorkoutClick: (String) -> Unit,
    onLegacyWorkoutClick: (String) -> Unit,
    onAddWeight: () -> Unit,
    onNavigateToTraining: () -> Unit,
    onNavigateToWalk: () -> Unit,
    onNavigateToNutrition: () -> Unit,
    onConnectHealthConnect: () -> Unit,
    onSyncHealthConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = state.profile ?: return
    val estimate = state.healthEstimate ?: return
    val metric = profile.unitSystem == UnitSystem.METRIC
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    var range by rememberSaveable { mutableStateOf(ProgressRange.DAYS_30) }
    var selectedExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseQuery by rememberSaveable { mutableStateOf("") }

    selectedExerciseId?.let { exerciseId ->
        ExerciseProgressDetailScreen(
            exerciseId = exerciseId,
            exerciseName = training.exerciseLibrary.firstOrNull { it.id == exerciseId }?.name ?: exerciseId,
            workouts = training.workoutHistory,
            range = range,
            metric = metric,
            today = today,
            onBack = { selectedExerciseId = null }
        )
        return
    }

    val weightSeries = remember(state.weights, range, today) {
        weightSeries(state.weights, range, zoneId, today)
    }
    val workoutFrequency = remember(training.workoutHistory, range, today) {
        workoutFrequencySeries(training.workoutHistory, range, today)
    }
    val trainingVolume = remember(training.workoutHistory, range, today) {
        trainingVolumeSeries(training.workoutHistory, range, today)
    }
    val walkingDistance = remember(state.walks, range, today) {
        walkingDistanceSeries(state.walks, range, zoneId, today)
    }
    val calorieAdherence = remember(state.food, range, today, profile.calorieTarget) {
        calorieAdherenceSeries(state.food, profile.calorieTarget, range, today)
    }
    val hydrationAdherence = remember(state.water, range, today, state.hydrationPlan.goalMl) {
        hydrationAdherenceSeries(state.water, state.hydrationPlan.goalMl, range, today)
    }
    val exerciseIds = remember(training.workoutHistory) {
        exercisesWithProgressHistory(training.workoutHistory)
    }
    val filteredExercises = remember(exerciseIds, exerciseQuery) {
        exerciseIds.filter { id ->
            val name = training.exerciseLibrary.firstOrNull { it.id == id }?.name ?: id
            exerciseQuery.isBlank() || name.contains(exerciseQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NutRunSpacing.lg)
            .testTag("progress-list"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
    ) {
        item {
            Text(
                "Progress",
                modifier = Modifier.testTag("progress-heading"),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            ProgressRangeSelector(
                selected = range,
                onSelected = { range = it }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)) {
                ProgressSummaryChip(
                    value = "${workoutFrequency.points.sumOf { it.value }.roundToInt()}",
                    label = "workouts",
                    modifier = Modifier.weight(1f),
                    containerColor = Color(0xFFE3F3E8)
                )
                ProgressSummaryChip(
                    value = displayVolumeTotal(trainingVolume.points.sumOf { it.value }, metric),
                    label = "volume",
                    modifier = Modifier.weight(1f),
                    containerColor = Color(0xFFDDEFFC)
                )
                ProgressSummaryChip(
                    value = displayDistanceTotal(walkingDistance.points.sumOf { it.value }, metric),
                    label = "walked",
                    modifier = Modifier.weight(1f),
                    containerColor = Color(0xFFFFE7DE)
                )
            }
        }
        item {
            ProgressTrendCard(
                series = workoutFrequency,
                range = range,
                valueFormatter = { it.roundToInt().toString() },
                testTag = "progress-chart-workouts",
                emptyMessage = "Complete a workout to see your training frequency.",
                emptyActionLabel = "Open Training",
                onEmptyAction = onNavigateToTraining
            )
        }
        item {
            ProgressTrendCard(
                series = trainingVolume,
                range = range,
                valueFormatter = { displayVolumeTotal(it, metric) },
                testTag = "progress-chart-volume",
                emptyMessage = "Logged set volume will appear here after workouts.",
                emptyActionLabel = "Open Training",
                onEmptyAction = onNavigateToTraining
            )
        }
        item { ProgressSectionHeading("Exercise progression", Modifier.testTag("exercise-progression-heading")) }
        if (exerciseIds.isEmpty()) {
            item {
                NutRunEmptyState(
                    title = "No exercise history yet",
                    message = "Complete workouts with logged sets to track exercise trends.",
                    actionLabel = "Open Training",
                    onAction = onNavigateToTraining,
                    testTag = "progress-exercise-empty"
                )
            }
        } else {
            item {
                ExercisePickerRow(
                    exercises = filteredExercises,
                    exerciseLibrary = training.exerciseLibrary,
                    query = exerciseQuery,
                    onQueryChange = { exerciseQuery = it },
                    onSelect = { selectedExerciseId = it }
                )
            }
        }
        item { ProgressSectionHeading("Weight") }
        item {
            ProgressTrendCard(
                series = weightSeries,
                range = range,
                valueFormatter = { formatWeightValue(it, metric) },
                testTag = "progress-chart-weight",
                emptyMessage = "Add weight entries to see your trend.",
                emptyActionLabel = "Add weight",
                onEmptyAction = onAddWeight
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressSectionHeading("Weight log", Modifier.weight(1f))
                TextButton(onClick = onAddWeight, modifier = Modifier.testTag("progress-add-weight")) {
                    Text("Add weight")
                }
            }
        }
        if (state.weights.isEmpty()) {
            item {
                Text(
                    "No weight entries yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.weights.take(12), key = { it.id }) { entry ->
                val date = java.time.Instant.ofEpochMilli(entry.recordedAtMillis)
                    .atZone(zoneId)
                    .toLocalDate()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(formatProgressDate(date), Modifier.weight(1f))
                    Text(
                        displayWeight(entry.weightKg, metric),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item { ProgressSectionHeading("Walking") }
        item {
            ProgressTrendCard(
                series = walkingDistance,
                range = range,
                valueFormatter = { displayDistanceTotal(it, metric) },
                testTag = "progress-chart-walking",
                emptyMessage = "Finished walks will show distance trends here.",
                emptyActionLabel = "Open Walk",
                onEmptyAction = onNavigateToWalk
            )
        }
        item {
            val finishedCount = state.walks.count { it.state == WalkState.FINISHED.name }
            ProgressActionSummaryCard(
                title = "$finishedCount completed walks",
                subtitle = displayDistanceTotal(
                    state.walks.filter { it.state == WalkState.FINISHED.name }.sumOf(WalkSessionEntity::distanceMeters),
                    metric
                ) + " total",
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                onClick = onNavigateToWalk,
                testTag = "progress-walk-summary"
            )
        }
        item { ProgressSectionHeading("Nutrition and hydration") }
        item {
            ProgressTrendCard(
                series = calorieAdherence,
                range = range,
                valueFormatter = { "${it.roundToInt()}%" },
                testTag = "progress-chart-calories",
                emptyMessage = "Log food to see calorie goal adherence.",
                emptyActionLabel = "Open Nutrition",
                onEmptyAction = onNavigateToNutrition
            )
        }
        item {
            ProgressTrendCard(
                series = hydrationAdherence,
                range = range,
                valueFormatter = { "${it.roundToInt()}%" },
                testTag = "progress-chart-hydration",
                emptyMessage = "Log water to see hydration adherence.",
                emptyActionLabel = "Open Nutrition",
                onEmptyAction = onNavigateToNutrition,
                icon = Icons.Default.WaterDrop
            )
        }
        item { ProgressSectionHeading("Recent training", Modifier.testTag("recent-training-heading")) }
        val structuredHistory = training.workoutHistory.take(10)
        val legacyHistory = recentTrainingHistory(training.history).take(10)
        if (structuredHistory.isEmpty() && legacyHistory.isEmpty()) {
            item {
                NutRunEmptyState(
                    title = "No workouts logged",
                    message = "Your completed workouts appear here with volume and targets.",
                    actionLabel = "Open Training",
                    onAction = onNavigateToTraining,
                    testTag = "progress-training-empty"
                )
            }
        } else if (structuredHistory.isNotEmpty()) {
            items(structuredHistory, key = WorkoutRecord::id) { workout ->
                ProgressActionSummaryCard(
                    title = workout.sessionName,
                    subtitle = "${formatToday(workout.performedOn)} | " +
                        "${workout.completedLogicalTargets}/${workout.totalLogicalTargets} targets | " +
                        displayVolumeTotal(workout.totalVolumeKg, metric) + " volume",
                    icon = Icons.Default.FitnessCenter,
                    onClick = { onWorkoutClick(workout.id) },
                    testTag = "recent-workout-card"
                )
            }
        } else {
            items(legacyHistory) { entry ->
                ProgressActionSummaryCard(
                    title = entry,
                    subtitle = "Workout completed",
                    icon = Icons.Default.FitnessCenter,
                    onClick = { onLegacyWorkoutClick(entry) }
                )
            }
        }
        val records = training.personalRecords().take(8)
        if (records.isNotEmpty()) {
            item { ProgressSectionHeading("Personal records") }
            items(records, key = ExerciseRecord::exerciseId) { record ->
                val exercise = training.exerciseLibrary.firstOrNull { it.id == record.exerciseId }
                ProgressActionSummaryCard(
                    title = exercise?.name ?: record.exerciseId,
                    subtitle = "Best ${displayWeight(record.bestWeightKg, metric)} | " +
                        "Estimated 1RM ${displayWeight(record.estimatedOneRepMaxKg, metric)}",
                    icon = Icons.Default.FitnessCenter
                )
            }
        }
        item {
            Text(
                "BMI ${"%.1f".format(estimate.bmi)} | BMR ${estimate.bmrKcal} kcal | TDEE ${estimate.tdeeKcal} kcal",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                "General guidance only, not medical advice.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        item { ProgressSectionHeading("Health Connect", Modifier.testTag("health-connect-heading")) }
        item {
            HealthConnectStatusCard(
                healthConnect = healthConnect,
                onConnect = onConnectHealthConnect,
                onSync = onSyncHealthConnect
            )
        }
        if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) {
            item { AdPlacement() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressRangeSelector(
    selected: ProgressRange,
    onSelected: (ProgressRange) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("progress-range-selector"),
        horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
    ) {
        ProgressRange.entries.forEach { option ->
            val label = when (option) {
                ProgressRange.DAYS_7 -> "7 days"
                ProgressRange.DAYS_30 -> "30 days"
                ProgressRange.DAYS_90 -> "90 days"
                ProgressRange.ALL -> "All time"
            }
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(label) },
                modifier = Modifier.testTag(
                    when (option) {
                        ProgressRange.DAYS_7 -> "progress-range-7d"
                        ProgressRange.DAYS_30 -> "progress-range-30d"
                        ProgressRange.DAYS_90 -> "progress-range-90d"
                        ProgressRange.ALL -> "progress-range-all"
                    }
                )
            )
        }
    }
}

@Composable
private fun ProgressTrendCard(
    series: ProgressSeries,
    range: ProgressRange,
    valueFormatter: (Double) -> String,
    testTag: String,
    emptyMessage: String,
    emptyActionLabel: String,
    onEmptyAction: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
  var showData by rememberSaveable(series.label) { mutableStateOf(false) }
    Card(shape = MaterialTheme.shapes.small) {
        Column(
            Modifier.fillMaxWidth().padding(NutRunSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
        ) {
            if (series.points.isEmpty()) {
                NutRunEmptyState(
                    title = series.label,
                    message = emptyMessage,
                    actionLabel = emptyActionLabel,
                    onAction = onEmptyAction,
                    testTag = "$testTag-empty"
                )
            } else {
                AccessibleLineChart(
                    series = series,
                    valueFormatter = valueFormatter,
                    showDataList = showData,
                    onToggleData = { showData = !showData },
                    testTag = testTag,
                    leadingIcon = icon
                )
            }
        }
    }
}

@Composable
fun AccessibleLineChart(
    series: ProgressSeries,
    valueFormatter: (Double) -> String,
    showDataList: Boolean,
    onToggleData: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "accessible-line-chart",
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val summary = progressSeriesAccessibilitySummary(series, valueFormatter)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = summary }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(series.label, fontWeight = FontWeight.SemiBold)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .testTag(testTag)
        ) {
            val points = series.points
            if (points.isEmpty()) return@Canvas
            val values = points.map { it.value }
            val minY = values.minOrNull() ?: 0.0
            val maxY = values.maxOrNull() ?: 0.0
            val span = (maxY - minY).let { if (it == 0.0) 1.0 else it }
            val chartLeft = 8f
            val chartRight = size.width - 8f
            val chartTop = 8f
            val chartBottom = size.height - 20f
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) {
                    (chartLeft + chartRight) / 2f
                } else {
                    chartLeft + (chartRight - chartLeft) * index / (points.size - 1)
                }
                val normalized = ((point.value - minY) / span).toFloat()
                val y = chartBottom - normalized * (chartBottom - chartTop)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(0xFF0B6E69),
                style = Stroke(width = 3f)
            )
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) {
                    (chartLeft + chartRight) / 2f
                } else {
                    chartLeft + (chartRight - chartLeft) * index / (points.size - 1)
                }
                val normalized = ((point.value - minY) / span).toFloat()
                val y = chartBottom - normalized * (chartBottom - chartTop)
                drawCircle(Color(0xFF0B6E69), radius = 5f, center = Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatProgressDate(series.points.first().date),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatProgressDate(series.points.last().date),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onToggleData,
            modifier = Modifier.testTag("$testTag-view-data")
        ) {
            Text(if (showDataList) "Hide data" else "View data")
        }
        if (showDataList) {
            series.points.forEach { point ->
                Text(
                    "${formatProgressDate(point.date)}: ${valueFormatter(point.value)} ${series.unit}",
                    modifier = Modifier.testTag("$testTag-data-${point.date}")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExercisePickerRow(
    exercises: List<String>,
    exerciseLibrary: List<Exercise>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("exercise-progress-search"),
            label = { Text("Search exercises") },
            singleLine = true
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm),
            modifier = Modifier.testTag("exercise-progress-picker")
        ) {
            exercises.take(12).forEach { exerciseId ->
                val name = exerciseLibrary.firstOrNull { it.id == exerciseId }?.name ?: exerciseId
                FilterChip(
                    selected = false,
                    onClick = { onSelect(exerciseId) },
                    label = { Text(name) },
                    modifier = Modifier.testTag("exercise-progress-$exerciseId")
                )
            }
        }
    }
}

@Composable
private fun ExerciseProgressDetailScreen(
    exerciseId: String,
    exerciseName: String,
    workouts: List<WorkoutRecord>,
    range: ProgressRange,
    metric: Boolean,
    today: LocalDate,
    onBack: () -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = NutRunSpacing.lg)
            .testTag("exercise-progress-detail"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("exercise-progress-back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to progress")
                }
                Text(
                    exerciseName,
                    modifier = Modifier.testTag("exercise-progress-title"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        ExerciseProgressMetric.entries.forEach { metricKind ->
            val series = exerciseProgressSeries(workouts, exerciseId, metricKind, range, today)
            item {
                ProgressTrendCard(
                    series = series,
                    range = range,
                    valueFormatter = { value ->
                        when (metricKind) {
                            ExerciseProgressMetric.MAX_WEIGHT,
                            ExerciseProgressMetric.ESTIMATED_ONE_REP_MAX,
                            ExerciseProgressMetric.VOLUME ->
                                formatWeightValue(value, metric)
                            ExerciseProgressMetric.MAX_REPS -> value.roundToInt().toString()
                        }
                    },
                    testTag = "exercise-chart-${metricKind.name}",
                    emptyMessage = "No completed sets for this exercise in the selected range.",
                    emptyActionLabel = "Back",
                    onEmptyAction = onBack
                )
            }
        }
    }
}

@Composable
private fun HealthConnectStatusCard(
    healthConnect: HealthConnectUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit
) {
    Card(shape = MaterialTheme.shapes.small) {
        Column(
            Modifier.fillMaxWidth().padding(NutRunSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
        ) {
            Text(
                when {
                    !healthConnect.available -> "Health Connect is unavailable on this device."
                    healthConnect.permissionGranted -> "Connected"
                    else -> "Optional: share approved health records."
                },
                fontWeight = FontWeight.SemiBold
            )
            if (healthConnect.importedSteps > 0) {
                Text("${healthConnect.importedSteps} steps imported today")
            }
            healthConnect.lastSyncMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (healthConnect.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            OutlinedButton(
                onClick = if (healthConnect.permissionGranted) onSync else onConnect,
                enabled = healthConnect.available && !healthConnect.busy,
                modifier = Modifier.fillMaxWidth().testTag("health-connect-action")
            ) {
                Text(if (healthConnect.permissionGranted) "Sync Health Connect" else "Connect Health Connect")
            }
        }
    }
}

@Composable
private fun ProgressSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, fontWeight = FontWeight.Bold, fontSize = 19.sp)
}

@Composable
private fun ProgressSummaryChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(Modifier.padding(NutRunSpacing.md)) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProgressActionSummaryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val tagged = testTag?.let { Modifier.testTag(it) } ?: Modifier
    Card(
        modifier = tagged.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = NutRunSpacing.md)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

private fun formatWeightValue(valueKg: Double, metric: Boolean): String =
    displayWeight(valueKg, metric)

private fun displayVolumeTotal(volumeKg: Double, metric: Boolean): String =
    formatWeightValue(volumeKg, metric)

private fun displayDistanceTotal(distanceMeters: Double, metric: Boolean): String =
    if (metric) {
        "%.2f km".format(distanceMeters / 1_000.0)
    } else {
        "%.2f mi".format(distanceMeters / 1_609.344)
    }
