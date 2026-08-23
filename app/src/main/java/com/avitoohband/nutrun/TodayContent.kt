package com.avitoohband.nutrun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.avitoohband.nutrun.domain.EntitlementKind
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    state: NutRunUiState,
    training: TrainingViewModel,
    onTrainingClick: () -> Unit,
    onNutritionClick: () -> Unit,
    onWaterClick: () -> Unit,
    onWalkClick: () -> Unit,
    onFoodClick: () -> Unit,
    onWorkoutClick: () -> Unit,
    onQuickAddWater: () -> Unit,
    onLogWaterAmount: (Int) -> Unit,
    onManageSupplements: () -> Unit,
    supplementsFocusRequestId: Long? = null,
    onSupplementsFocusConsumed: (Long) -> Unit = {}
) {
    val profile = state.profile ?: return
    val trainingReady = training.trainingMutationsReady
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val listState = rememberLazyListState()
    val supplementsHeadingRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(supplementsFocusRequestId) {
        val requestId = supplementsFocusRequestId ?: return@LaunchedEffect
        listState.animateScrollToItem(8)
        supplementsHeadingRequester.bringIntoView()
        onSupplementsFocusConsumed(requestId)
    }
    var addSupplement by remember { mutableStateOf(false) }
    var showWaterPicker by remember { mutableStateOf(false) }
    LaunchedEffect(trainingReady) {
        if (!trainingReady) addSupplement = false
    }
    if (addSupplement && trainingReady) {
        AddSupplementDialog(
            onDismiss = { addSupplement = false },
            onAdd = { name, dose, schedule, reminderEnabled, reminderMinute ->
                training.addSupplement(name, dose, schedule, reminderEnabled, reminderMinute)
                if (shouldRequestSupplementReminderPermission(null, reminderEnabled)) {
                    requestNotificationPermission()
                }
                addSupplement = false
            }
        )
    }
    if (showWaterPicker) {
        WaterAmountDialog(
            onSelect = {
                onLogWaterAmount(it)
                showWaterPicker = false
            },
            onDismiss = { showWaterPicker = false }
        )
    }
    val lastWalk = state.walks.firstOrNull()
    val lastWalkLabel = if (lastWalk == null) "No completed walks" else "last walk"
    val lastWalkValue = lastWalk?.distanceMeters?.div(1_000)?.let { "%.1f km".format(it) } ?: "—"
    val today = LocalDate.now()
    val todaySessions = training.sessionsForDate(today)
    val upcoming = training.nextScheduledSession(today.plusDays(1))
    val dueSupplements = if (trainingReady) dueSupplementsForDate(training.supplements, today) else emptyList()
    val configuredSupplements = if (trainingReady) training.supplements else emptyList()

    LazyColumn(
        Modifier.fillMaxSize()
            .padding(horizontal = NutRunSpacing.lg)
            .testTag("today-list"),
        state = listState,
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
    ) {
        item {
            Text(
                "Today",
                modifier = Modifier
                    .testTag("today-heading")
                    .semantics { heading() },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(formatToday(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${state.nutrition.calories} of ${profile.calorieTarget} kcal")
            LinearProgressIndicator(
                progress = { (state.nutrition.calories / profile.calorieTarget.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 360.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)) {
                        NutRunMetric(
                            value = "${state.waterMl}",
                            label = "mL water",
                            icon = Icons.Default.WaterDrop,
                            actionLabel = "Open Nutrition water",
                            onClick = onWaterClick,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0xFFDDEFFC),
                            testTag = "today-metric-water"
                        )
                        NutRunMetric(
                            value = "${state.nutrition.proteinGrams.roundToInt()} g",
                            label = "protein",
                            icon = Icons.Default.LocalDining,
                            actionLabel = "Open Nutrition",
                            onClick = onNutritionClick,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0xFFE3F3E8),
                            testTag = "today-metric-protein"
                        )
                        NutRunMetric(
                            value = lastWalkValue,
                            label = lastWalkLabel,
                            icon = Icons.AutoMirrored.Filled.DirectionsRun,
                            actionLabel = "Open Walk",
                            onClick = onWalkClick,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0xFFFFE7DE),
                            testTag = "today-metric-walk"
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)) {
                        NutRunMetric(
                            value = "${state.waterMl}",
                            label = "mL water",
                            icon = Icons.Default.WaterDrop,
                            actionLabel = "Open Nutrition water",
                            onClick = onWaterClick,
                            modifier = Modifier.weight(1f),
                            containerColor = Color(0xFFDDEFFC),
                            testTag = "today-metric-water"
                        )
                        NutRunMetric(
                            value = "${state.nutrition.proteinGrams.roundToInt()} g",
                            label = "protein",
                            icon = Icons.Default.LocalDining,
                            actionLabel = "Open Nutrition",
                            onClick = onNutritionClick,
                            modifier = Modifier.weight(1f),
                            containerColor = Color(0xFFE3F3E8),
                            testTag = "today-metric-protein"
                        )
                        NutRunMetric(
                            value = lastWalkValue,
                            label = lastWalkLabel,
                            icon = Icons.AutoMirrored.Filled.DirectionsRun,
                            actionLabel = "Open Walk",
                            onClick = onWalkClick,
                            modifier = Modifier.weight(1f),
                            containerColor = Color(0xFFFFE7DE),
                            testTag = "today-metric-walk"
                        )
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onQuickAddWater,
                    modifier = Modifier.testTag("today-quick-add-water")
                ) {
                    Text("+${state.hydrationPlan.servingMl} mL")
                }
                TextButton(
                    onClick = { showWaterPicker = true },
                    modifier = Modifier.testTag("today-choose-water-amount")
                ) {
                    Text("Choose water amount")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
            ) {
                TextButton(
                    onClick = onFoodClick,
                    modifier = Modifier.testTag("today-quick-add-food")
                ) {
                    Icon(Icons.Default.LocalDining, contentDescription = null)
                    Text("Log food")
                }
                TextButton(
                    onClick = onWorkoutClick,
                    modifier = Modifier.testTag("today-quick-workout")
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null)
                    Text(
                        if (todaySessions.isNotEmpty()) "Today's workout" else "Training"
                    )
                }
            }
        }
        item { TodaySectionHeading("Today's training") }
        item {
            TodayActionCard(
                title = when {
                    todaySessions.isNotEmpty() -> todaySessions.joinToString(" + ") { it.name }
                    upcoming != null -> "Rest day"
                    else -> "Create your first session"
                },
                subtitle = when {
                    todaySessions.isNotEmpty() ->
                        "${todaySessions.sumOf { it.logicalTargetCount() }} planned targets"
                    upcoming != null ->
                        "Next: ${upcoming.second.name} on ${formatToday(upcoming.first)}"
                    else -> "Training is ready when you are."
                },
                icon = Icons.Default.FitnessCenter,
                onClick = onTrainingClick,
                testTag = "today-training-card"
            )
        }
        item { TodaySectionHeading("Hydration") }
        item {
            TodayActionCard(
                "${state.waterMl} / ${state.hydrationPlan.goalMl} mL",
                if (state.waterMl >= state.hydrationPlan.goalMl) {
                    "Daily goal reached"
                } else {
                    "Keep a steady pace through your waking window."
                },
                Icons.Default.WaterDrop,
                onClick = onWaterClick,
                testTag = "today-hydration-card"
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TodaySectionHeading(
                    "Supplements",
                    Modifier
                        .weight(1f)
                        .bringIntoViewRequester(supplementsHeadingRequester)
                        .testTag("today-supplements-heading")
                )
                TextButton(
                    onClick = onManageSupplements,
                    enabled = trainingReady,
                    modifier = Modifier.testTag("manage-supplements")
                ) {
                    Text("Manage")
                }
                TextButton(
                    onClick = { addSupplement = true },
                    enabled = trainingReady,
                    modifier = Modifier.testTag("today-add-supplement")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add supplement")
                    Text("Add")
                }
            }
        }
        if (!trainingReady) {
            item {
                NutRunLoadingState(
                    message = "Loading supplements...",
                    modifier = Modifier.testTag("today-supplements-loading")
                )
            }
        }
        if (trainingReady && configuredSupplements.isEmpty()) {
            item {
                NutRunEmptyState(
                    title = "Add your supplements",
                    message = "Track daily vitamins and doses from Today.",
                    actionLabel = "Add supplement",
                    onAction = { addSupplement = true },
                    titleTestTag = "today-supplements-empty-configured"
                )
            }
        }
        if (trainingReady && configuredSupplements.isNotEmpty() && dueSupplements.isEmpty()) {
            item {
                NutRunEmptyState(
                    title = "No supplements due today",
                    message = "",
                    actionLabel = "Manage supplements",
                    onAction = onManageSupplements,
                    titleTestTag = "today-supplements-none-due"
                )
            }
        }
        items(dueSupplements, key = { it.id }) { supplement ->
            val completed = supplement.isCompletedOn(today)
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = if (completed) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (completed) {
                                "${supplement.name}, completed"
                            } else {
                                "${supplement.name}, not completed"
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = completed,
                        onCheckedChange = { training.toggleSupplement(supplement.id, it) },
                        enabled = trainingReady
                    )
                    Column {
                        val color = if (completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Text(supplement.name, fontWeight = FontWeight.SemiBold, color = color)
                        Text(supplement.dose, fontSize = 12.sp, color = color)
                    }
                }
            }
        }
        if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) {
            item { TodayAdPlacement() }
        }
    }
}

@Composable
fun DashboardMetric(
    value: String,
    label: String,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    testTag: String = "dashboard-metric"
) {
    NutRunMetric(
        value = value,
        label = label,
        icon = icon,
        actionLabel = actionLabel,
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        testTag = testTag
    )
}

@Composable
private fun TodaySectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

@Composable
private fun TodayActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val taggedModifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
    Card(
        modifier = taggedModifier.then(
            if (onClick != null) {
                Modifier
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$title, $subtitle"
                    }
            } else {
                Modifier
            }
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = NutRunSpacing.md)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodayAdPlacement() {
    AdPlacement()
}
