package com.avitoohband.nutrun

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.billing.BillingManager
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.EntitlementKind
import com.avitoohband.nutrun.domain.FoodCatalogItem
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.MealType
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.domain.WalkState
import com.avitoohband.nutrun.domain.calculateHealthEstimate
import com.avitoohband.nutrun.walk.WalkRecordingService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.DayOfWeek
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var navigationRequest by mutableStateOf<NavigationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationRequest = intent.toNavigationRequest()
        setContent {
            NutRunRoot(
                navigationRequest = navigationRequest,
                onNavigationConsumed = { consumedId ->
                    if (navigationRequest?.id == consumedId) {
                        navigationRequest = null
                        intent.removeExtra(EXTRA_DESTINATION)
                        intent.removeExtra(EXTRA_WATER_SECTION)
                        intent.removeExtra(EXTRA_SUPPLEMENTS_SECTION)
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationRequest = intent.toNavigationRequest()
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_WATER_SECTION = "water_section"
        const val EXTRA_SUPPLEMENTS_SECTION = "supplements_section"
    }
}

data class NavigationRequest(
    val id: Long = System.nanoTime(),
    val destination: String,
    val focusWater: Boolean = false,
    val focusSupplements: Boolean = false
)

private fun Intent.toNavigationRequest(): NavigationRequest? =
    getStringExtra(MainActivity.EXTRA_DESTINATION)?.let {
        NavigationRequest(
            destination = it,
            focusWater = getBooleanExtra(MainActivity.EXTRA_WATER_SECTION, false),
            focusSupplements = getBooleanExtra(MainActivity.EXTRA_SUPPLEMENTS_SECTION, false)
        )
    }

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("today", "Today", Icons.Default.Home),
    Destination("training", "Training", Icons.Default.FitnessCenter),
    Destination("nutrition", "Nutrition", Icons.Default.LocalDining),
    Destination("walk", "Walk", Icons.AutoMirrored.Filled.DirectionsRun),
    Destination("progress", "Progress", Icons.AutoMirrored.Filled.ShowChart)
)

@Composable
fun NutRunRoot(
    navigationRequest: NavigationRequest? = null,
    onNavigationConsumed: (Long) -> Unit = {},
    app: NutRunViewModel = hiltViewModel(),
    training: TrainingViewModel = hiltViewModel()
) {
    val state by app.state.collectAsStateWithLifecycle()
    val message by app.message.collectAsState()
    val authState by app.authenticationUiState.collectAsStateWithLifecycle()
    var hydrationCelebration by remember {
        mutableStateOf<HydrationGoalCelebration?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(app) {
        app.hydrationGoalCelebrations.collect {
            hydrationCelebration = it
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            app.clearMessage()
        }
    }
    val dark = state.session.darkMode

    NutRunTheme(darkTheme = dark) {
        when {
            state.session.authenticatedEmail == null -> AuthenticationOverviewContent(
                state = authState,
                onAuthenticate = app::authenticate,
                onSendPasswordReset = app::sendPasswordReset,
                onSetMode = app::setAuthenticationMode,
                onClearFeedback = app::clearAuthenticationFeedback,
                onDemo = app::enterDemo
            )
            state.profile == null -> OnboardingOverviewContent(
                accountId = state.session.authenticatedUserId.orEmpty(),
                email = state.session.authenticatedEmail.orEmpty(),
                onSave = app::saveProfile
            )
            else -> MainApp(
                app,
                training,
                state,
                navigationRequest,
                onNavigationConsumed,
                snackbarHostState = snackbarHostState
            )
        }
        hydrationCelebration?.let { celebration ->
            HydrationGoalTrophyDialog(
                celebration = celebration,
                onDismiss = { hydrationCelebration = null }
            )
        }
    }
}

@Composable
internal fun HydrationGoalTrophyDialog(
    celebration: HydrationGoalCelebration,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.EmojiEvents,
                "Hydration trophy",
                modifier = Modifier.size(64.dp),
                tint = Color(0xFFD49A00)
            )
        },
        title = {
            Text(
                "Hydration goal reached!",
                modifier = Modifier.testTag("hydration-goal-trophy")
            )
        },
        text = {
            Text(
                "You reached your %,d mL water goal today. Great work!"
                    .format(celebration.goalMl)
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Nice!")
            }
        }
    )
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        values.chunked(2).forEach { rowValues ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowValues.forEach {
                    FilterChip(
                        selected = it == selected,
                        onClick = { onSelected(it) },
                        label = { Text(label(it), fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    app: NutRunViewModel,
    training: TrainingViewModel,
    state: NutRunUiState,
    navigationRequest: NavigationRequest?,
    onNavigationConsumed: (Long) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val navController = rememberNavController()
    var waterFocusRequest by rememberSaveable { mutableStateOf(0) }
    var foodFocusRequest by rememberSaveable { mutableStateOf(0) }
    var pendingSupplementsFocusRequestId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    fun navigateTo(
        destination: String,
        focusWater: Boolean = false,
        focusFood: Boolean = false
    ) {
        if (focusWater) waterFocusRequest += 1
        if (focusFood) foodFocusRequest += 1
        navController.navigate(destination) {
            launchSingleTop = true
        }
    }
    LaunchedEffect(navigationRequest?.id) {
        val request = navigationRequest
            ?.takeIf { candidate -> destinations.any { it.route == candidate.destination } }
            ?: return@LaunchedEffect
        navController.currentBackStackEntryFlow.first()
        if (request.focusWater) waterFocusRequest += 1
        if (request.focusSupplements) {
            pendingSupplementsFocusRequestId = request.id
        }
        navController.navigate(request.destination) {
            launchSingleTop = true
        }
        onNavigationConsumed(request.id)
    }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "today"
    var accountMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (route) {
                            "profile" -> "Profile"
                            "edit-health" -> "Health details"
                            "notifications" -> "Notifications"
                            "supplements" -> "Supplements"
                            else -> "NutRun"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { accountMenu = true }) {
                            Icon(Icons.Default.Person, "Profile")
                        }
                        DropdownMenu(accountMenu, { accountMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Profile and settings") },
                                onClick = {
                                    accountMenu = false
                                    navController.navigate("profile")
                                }
                            )
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = {
                                accountMenu = false
                                app.signOut()
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (route !in setOf("profile", "edit-health", "notifications", "supplements")) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                val returnedToToday =
                                    destination.route == "today" &&
                                        navController.popBackStack("today", inclusive = false)
                                if (!returnedToToday) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = destination.route != "today"
                                    }
                                }
                            },
                            modifier = Modifier
                                .testTag("bottom-nav-${destination.route}")
                                .semantics {
                                    selected = route == destination.route
                                    stateDescription = if (route == destination.route) {
                                        "Selected"
                                    } else {
                                        "Not selected"
                                    }
                                    contentDescription = "${destination.label} tab"
                                },
                            icon = { Icon(destination.icon, destination.label) },
                            label = { Text(destination.label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") {
                TodayScreen(
                    state,
                    training,
                    onTrainingClick = { navigateTo("training") },
                    onNutritionClick = { navigateTo("nutrition") },
                    onWaterClick = { navigateTo("nutrition", focusWater = true) },
                    onWalkClick = { navigateTo("walk") },
                    onFoodClick = { navigateTo("nutrition", focusFood = true) },
                    onWorkoutClick = { navigateTo("training") },
                    onQuickAddWater = { app.addWater(state.hydrationPlan.servingMl) },
                    onLogWaterAmount = app::addWater,
                    onManageSupplements = { navController.navigate("supplements") },
                    supplementsFocusRequestId = pendingSupplementsFocusRequestId,
                    onSupplementsFocusConsumed = { requestId ->
                        if (pendingSupplementsFocusRequestId == requestId) {
                            pendingSupplementsFocusRequestId = null
                        }
                    }
                )
            }
            composable("training") { TrainingScreen(training) }
            composable("nutrition") {
                NutritionScreen(app, state, waterFocusRequest, foodFocusRequest)
            }
            composable("walk") { WalkScreen(app, state) }
            composable("progress") {
                ProgressScreen(
                    app = app,
                    state = state,
                    training = training,
                    onNavigateToTraining = {
                        navController.navigate("training") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToWalk = {
                        navController.navigate("walk") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToNutrition = {
                        navController.navigate("nutrition") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("supplements") {
                SupplementsScreen(
                    training = training,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("profile") {
                val billing by app.billingState.collectAsStateWithLifecycle()
                val accountDeletionState by app.accountDeletionState.collectAsStateWithLifecycle()
                val activity = LocalActivity.current
                ProfileOverviewContent(
                    profile = state.profile ?: return@composable,
                    entitlementLabel = when (state.session.entitlement()) {
                        EntitlementKind.TRIAL -> "${state.session.trialDaysRemaining()} trial days remaining"
                        EntitlementKind.SUBSCRIBER -> "Ad-free subscriber"
                        EntitlementKind.FREE_AD_SUPPORTED -> "Free plan with ads"
                    },
                    darkMode = state.session.darkMode,
                    authenticatedUserId = state.session.authenticatedUserId,
                    billing = billing,
                    accountDeletionState = accountDeletionState,
                    billingActionsEnabled = activity != null && billing.connected,
                    onBack = { navController.popBackStack() },
                    onEditHealth = { navController.navigate("edit-health") },
                    onNotifications = { navController.navigate("notifications") },
                    onDarkModeChange = app::setDarkMode,
                    onPurchaseMonthly = { activity?.let { app.purchase(it, BillingManager.MONTHLY) } },
                    onPurchaseAnnual = { activity?.let { app.purchase(it, BillingManager.ANNUAL) } },
                    onRestorePurchases = app::restorePurchases,
                    onSignOut = app::signOut,
                    onDeleteAccount = app::deleteAccount,
                    onClearAccountDeletionState = app::clearAccountDeletionState,
                    onDevToggleSubscription = if (
                        BuildConfig.DEBUG && !isDemoAccount(state.session.authenticatedUserId)
                    ) {
                        {
                            app.setSubscriberForDevelopment(
                                state.session.entitlement() != EntitlementKind.SUBSCRIBER
                            )
                        }
                    } else {
                        null
                    }
                )
            }
            composable("edit-health") {
                EditHealthDetailsScreen(
                    state.profile ?: return@composable,
                    onSave = {
                        app.saveProfile(it)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("notifications") {
                NotificationSettingsScreen(
                    app,
                    state,
                    training,
                    onBack = { navController.popBackStack() },
                    onManageSupplements = { navController.navigate("supplements") }
                )
            }
        }
    }
}

@Composable
private fun SupplementsScreen(
    training: TrainingViewModel,
    onBack: () -> Unit
) {
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
    var addSupplement by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Supplement?>(null) }
    var editingSupplement by remember { mutableStateOf<Supplement?>(null) }

    LaunchedEffect(trainingReady) {
        if (!trainingReady) {
            addSupplement = false
            pendingRemoval = null
            editingSupplement = null
        }
    }
    if (!trainingReady) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("supplements-loading")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Manage supplements", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading supplements...")
            }
        }
        return
    }

    if (addSupplement) {
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
    editingSupplement?.let { supplement ->
        AddSupplementDialog(
            existing = supplement,
            onDismiss = { editingSupplement = null },
            onAdd = { name, dose, schedule, reminderEnabled, reminderMinute ->
                training.updateSupplement(
                    supplement.id,
                    name,
                    dose,
                    schedule,
                    reminderEnabled,
                    reminderMinute
                )
                if (
                    shouldRequestSupplementReminderPermission(
                        supplement.reminderEnabled,
                        reminderEnabled
                    )
                ) requestNotificationPermission()
                editingSupplement = null
            }
        )
    }
    pendingRemoval?.let { supplement ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${supplement.name}?") },
            text = { Text("This removes the supplement from every scheduled day.") },
            confirmButton = {
                Button(
                    onClick = {
                        training.removeSupplement(supplement.id)
                        pendingRemoval = null
                    },
                    modifier = Modifier.testTag("confirm-remove-supplement")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("manage-supplements-list"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text("Manage supplements", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "All supplements and their scheduled days",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { addSupplement = true },
                    modifier = Modifier.testTag("add-managed-supplement")
                ) {
                    Icon(Icons.Default.Add, "Add supplement")
                }
            }
        }
        if (training.supplements.isEmpty()) {
            item {
                Text(
                    "No supplements added.",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(training.supplements, key = { it.id }) { supplement ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(supplement.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            supplement.dose,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            supplement.schedule.label(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { editingSupplement = supplement },
                        modifier = Modifier.testTag("edit-supplement-${supplement.id}")
                    ) {
                        Icon(Icons.Default.Edit, "Edit ${supplement.name}")
                    }
                    IconButton(onClick = { pendingRemoval = supplement }) {
                        Icon(Icons.Default.Delete, "Remove ${supplement.name}")
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrainingScreen(model: TrainingViewModel) {
    if (!model.trainingMutationsReady) {
        Box(
            modifier = Modifier.fillMaxSize().testTag("training-loading"),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading training...")
        }
        return
    }

    var editSessionId by remember { mutableStateOf<String?>(null) }
    var editRestTimer by remember { mutableStateOf(false) }
    var confirmCancelWorkout by remember { mutableStateOf(false) }
    var restTimerFinished by remember { mutableStateOf(false) }
    var templateDetailsId by remember { mutableStateOf<String?>(null) }
    var planningMode by rememberSaveable {
        mutableStateOf(TrainingPlanningMode.SCHEDULE)
    }
    var assignmentDay by remember { mutableStateOf<DayOfWeek?>(null) }
    assignmentDay?.let { day ->
        WorkoutAssignmentContent(
            day = day,
            templates = model.workoutTemplates,
            selectedIds = model.weeklyDayPlans
                .firstOrNull { it.weekday == day }
                ?.templateIds
                .orEmpty(),
            onSave = { templateIds ->
                if (model.replaceAssignments(day, templateIds) is TrainingMutationResult.Success) {
                    assignmentDay = null
                }
            },
            onCancel = { assignmentDay = null }
        )
        return
    }
    if (editRestTimer) {
        RestTimerSettingsDialog(
            currentSeconds = model.defaultRestTimerSeconds,
            onSave = {
                model.updateDefaultRestTimerSeconds(it)
                editRestTimer = false
            },
            onDismiss = { editRestTimer = false }
        )
    }
    if (confirmCancelWorkout) {
        AlertDialog(
            onDismissRequest = { confirmCancelWorkout = false },
            title = { Text("Cancel workout?") },
            text = {
                Text("Your set entries from this workout will be discarded.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        model.cancelWorkout()
                        confirmCancelWorkout = false
                    }
                ) {
                    Text("Cancel workout")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelWorkout = false }) {
                    Text("Keep training")
                }
            }
        )
    }
    editSessionId?.let { sessionId ->
        WorkoutEditorContent(
            model = model,
            templateId = sessionId,
            onBack = { editSessionId = null },
            onSaved = { editSessionId = null }
        )
        return
    }
    templateDetailsId?.let { templateId ->
        model.workoutTemplates.firstOrNull { it.id == templateId }?.let { template ->
            WorkoutTemplateDetailsDialog(
                template = template,
                usesMetricUnits = model.usesMetricUnits,
                onStart = { model.startWorkout(template.id); templateDetailsId = null },
                onEdit = {
                    model.selectSession(template.id)
                    editSessionId = template.id
                    templateDetailsId = null
                },
                onDismiss = { templateDetailsId = null }
            )
        }
    }
    model.lastWorkoutSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = model::dismissWorkoutSummary,
            title = { Text("Workout saved") },
            text = { Text("${summary.completedExercises} of ${summary.totalExercises} exercises completed.") },
            confirmButton = { TextButton(onClick = model::dismissWorkoutSummary) { Text("Done") } }
        )
    }
    if (restTimerFinished) {
        AlertDialog(
            onDismissRequest = { restTimerFinished = false },
            title = { Text("Rest complete") },
            text = { Text("Your next set is ready.") },
            confirmButton = {
                Button(onClick = { restTimerFinished = false }) {
                    Text("Continue workout")
                }
            }
        )
    }
    model.activeSession()?.let {
        val context = LocalContext.current
        val timerEnd = model.restTimerEndAtMillis
        var currentTime by remember(timerEnd) { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(timerEnd) {
            while (timerEnd != null && currentTime < timerEnd) {
                delay(1_000)
                currentTime = System.currentTimeMillis()
            }
            if (
                timerEnd != null &&
                currentTime >= timerEnd &&
                model.restTimerEndAtMillis == timerEnd
            ) {
                model.skipRestTimer()
                restTimerFinished = true
                playRestTimerFinishedFeedback(context)
            }
        }
        ActiveWorkoutContent(
            model = model,
            onEditRestTimer = { editRestTimer = true },
            onCancelRequest = { confirmCancelWorkout = true },
            onFinishRequest = model::finishWorkout
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Training",
                modifier = Modifier.weight(1f).testTag("training-heading"),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { editRestTimer = true }, modifier = Modifier.testTag("rest-timer-settings")) {
                Text("Rest ${model.defaultRestTimerSeconds}s")
            }
        }
        TrainingPlanningContent(
            model = model,
            mode = planningMode,
            onModeChange = { planningMode = it },
            onOpenTemplate = { templateDetailsId = it.id },
            onEditTemplate = { template ->
                editSessionId = template.id
            },
            onDuplicateTemplate = { template ->
                model.duplicateWorkout(template.id)
            },
            onAssignDay = { assignmentDay = it },
            modifier = Modifier.weight(1f)
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NutritionScreen(
    app: NutRunViewModel,
    state: NutRunUiState,
    waterFocusRequest: Int = 0,
    foodFocusRequest: Int = 0
) {
    var showFood by remember { mutableStateOf<FoodLogEntity?>(null) }
    var draftFood by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var createFood by remember { mutableStateOf(false) }
    var hydrationSettings by remember { mutableStateOf(false) }
    var waterAmounts by remember { mutableStateOf(false) }
    var saveMealType by remember { mutableStateOf<MealType?>(null) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    val foodSearchState by app.foodSearchState.collectAsState()
    val pendingDeletion by app.pendingNutritionDeletion.collectAsState()

    if (createFood || showFood != null || draftFood != null) {
        FoodEntryDialog(
            existing = showFood,
            draft = draftFood,
            onDismiss = { createFood = false; showFood = null; draftFood = null },
            onSave = { item, meal, id ->
                app.saveFood(item, meal, id)
                createFood = false
                showFood = null
                draftFood = null
            }
        )
    }
    if (hydrationSettings) {
        HydrationSettingsDialog(
            state.hydrationPlan,
            {
                app.saveHydrationPlan(it)
                if (
                    it.remindersEnabled &&
                    android.os.Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                hydrationSettings = false
            },
            { hydrationSettings = false }
        )
    }
    if (waterAmounts) {
        WaterAmountDialog(
            onSelect = {
                app.setQuickServingAndAddWater(it)
                waterAmounts = false
            },
            onDismiss = { waterAmounts = false }
        )
    }
    saveMealType?.let { meal ->
        SaveMealDialog(
            meal = meal,
            onDismiss = { saveMealType = null },
            onSave = { name ->
                app.saveMealTemplate(name, meal)
                saveMealType = null
            }
        )
    }

    NutritionOverviewContent(
        state = state,
        foodSearchState = foodSearchState,
        pendingDeletion = pendingDeletion,
        onSearchFood = app::searchFood,
        onClearFoodSearch = app::clearFoodSearch,
        onSaveFood = app::saveFood,
        onDuplicateFood = app::duplicateFood,
        onLogRecentFood = app::logRecentFood,
        onSaveFavoriteFood = app::saveFavoriteFood,
        onSaveMealTemplate = app::saveMealTemplate,
        onLogFoodTemplate = app::logFoodTemplate,
        onRequestFoodDeletion = app::requestFoodDeletion,
        onRequestTemplateDeletion = app::requestTemplateDeletion,
        onUndoNutritionDeletion = app::undoNutritionDeletion,
        onAddWater = app::addWater,
        onSetQuickServingAndAddWater = app::setQuickServingAndAddWater,
        onHydrationSettings = { hydrationSettings = true },
        onWaterAmounts = { waterAmounts = true },
        onCreateFood = { createFood = true },
        onEditFood = { showFood = it },
        onDraftFood = { draftFood = it },
        onSaveMeal = { saveMealType = it },
        waterFocusRequest = waterFocusRequest,
        foodFocusRequest = foodFocusRequest
    )
}

@Composable
private fun SaveMealDialog(
    meal: MealType,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val defaultName = meal.name.lowercase().replaceFirstChar(Char::uppercase)
    var name by rememberSaveable(meal) { mutableStateOf("$defaultName meal") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save meal") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Meal name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FoodEntryDialog(
    existing: FoodLogEntity?,
    draft: FoodCatalogItem?,
    onDismiss: () -> Unit,
    onSave: (FoodCatalogItem, MealType, String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: draft?.name.orEmpty()) }
    var serving by remember { mutableStateOf((existing?.servingGrams ?: draft?.servingGrams)?.toString() ?: "100") }
    var calories by remember { mutableStateOf((existing?.calories ?: draft?.calories)?.toString().orEmpty()) }
    var protein by remember { mutableStateOf((existing?.proteinGrams ?: draft?.proteinGrams)?.toString() ?: "0") }
    var carbs by remember { mutableStateOf((existing?.carbohydrateGrams ?: draft?.carbohydrateGrams)?.toString() ?: "0") }
    var fat by remember { mutableStateOf((existing?.fatGrams ?: draft?.fatGrams)?.toString() ?: "0") }
    var meal by remember { mutableStateOf(existing?.mealType?.let(MealType::valueOf) ?: MealType.SNACK) }
    val servingValidation = validateDecimalInput(serving, FormValidationRules.foodServingRule)
    val caloriesValidation = validateDecimalInput(calories, FormValidationRules.caloriesRule, integerOnly = true)
    val proteinValidation = validateDecimalInput(protein, FormValidationRules.macroRule("Protein (g)"))
    val carbsValidation = validateDecimalInput(carbs, FormValidationRules.macroRule("Carbohydrates (g)"))
    val fatValidation = validateDecimalInput(fat, FormValidationRules.macroRule("Fat (g)"))
    val canSave = name.isNotBlank() &&
        servingValidation.error == null &&
        servingValidation.value != null &&
        caloriesValidation.error == null &&
        caloriesValidation.value != null &&
        proteinValidation.error == null &&
        carbsValidation.error == null &&
        fatValidation.error == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Log food" else "Edit food") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("Food name") },
                        singleLine = true,
                        modifier = Modifier.testTag("food-entry-name")
                    )
                }
                item { ChoiceRow("Meal", MealType.entries, meal, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { meal = it } }
                item {
                    ValidatedNumberField(
                        value = serving,
                        onValueChange = { serving = it },
                        rule = FormValidationRules.foodServingRule,
                        testTag = "food-entry-serving"
                    )
                }
                item {
                    ValidatedNumberField(
                        value = calories,
                        onValueChange = { calories = it },
                        rule = FormValidationRules.caloriesRule,
                        integerOnly = true,
                        testTag = "food-entry-calories"
                    )
                }
                item {
                    ValidatedNumberField(
                        value = protein,
                        onValueChange = { protein = it },
                        rule = FormValidationRules.macroRule("Protein (g)"),
                        testTag = "food-entry-protein"
                    )
                }
                item {
                    ValidatedNumberField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        rule = FormValidationRules.macroRule("Carbohydrates (g)"),
                        testTag = "food-entry-carbs"
                    )
                }
                item {
                    ValidatedNumberField(
                        value = fat,
                        onValueChange = { fat = it },
                        rule = FormValidationRules.macroRule("Fat (g)"),
                        testTag = "food-entry-fat"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        FoodCatalogItem(
                            existing?.catalogId ?: draft?.id ?: "manual-${System.currentTimeMillis()}",
                            name,
                            existing?.brand ?: draft?.brand,
                            servingValidation.value!!,
                            caloriesValidation.value!!.toInt(),
                            proteinValidation.value ?: 0.0,
                            carbsValidation.value ?: 0.0,
                            fatValidation.value ?: 0.0
                        ),
                        meal,
                        existing?.id
                    )
                },
                modifier = Modifier.testTag("food-entry-save")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HydrationSettingsDialog(
    initial: HydrationPlanEntity,
    onSave: (HydrationPlanEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var goal by remember { mutableStateOf(initial.goalMl.toString()) }
    var serving by remember { mutableStateOf(initial.servingMl.toString()) }
    val goalValidation = validateDecimalInput(goal, FormValidationRules.hydrationGoalRule, integerOnly = true)
    val servingValidation = validateDecimalInput(serving, FormValidationRules.hydrationServingRule, integerOnly = true)
    val canSave = goalValidation.error == null &&
        goalValidation.value != null &&
        servingValidation.error == null &&
        servingValidation.value != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Water settings") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ValidatedNumberField(
                        value = goal,
                        onValueChange = { goal = it },
                        rule = FormValidationRules.hydrationGoalRule,
                        integerOnly = true,
                        testTag = "hydration-goal"
                    )
                }
                item {
                    ValidatedNumberField(
                        value = serving,
                        onValueChange = { serving = it },
                        rule = FormValidationRules.hydrationServingRule,
                        integerOnly = true,
                        testTag = "hydration-serving"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        initial.copy(
                            goalMl = goalValidation.value!!.toInt(),
                            servingMl = servingValidation.value!!.toInt()
                        )
                    )
                },
                modifier = Modifier.testTag("hydration-settings-save")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun WaterAmountDialog(onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val parsed = custom.toIntOrNull()
    val customError = showCustom && custom.isNotBlank() && !isValidWaterAmount(parsed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log water") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(180, 240, 300, 500, 750, 1_000)) { amount ->
                    OutlinedButton(
                        onClick = { onSelect(amount) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("$amount mL") }
                }
                item {
                    TextButton(onClick = { showCustom = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Custom")
                    }
                }
                if (showCustom) {
                    item {
                        OutlinedTextField(
                            custom,
                            { custom = it.filter(Char::isDigit) },
                            label = { Text("Custom amount (mL)") },
                            supportingText = {
                                if (customError) Text("Enter an amount from 50 to 2000 mL.")
                            },
                            isError = customError,
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showCustom) {
                Button(
                    onClick = { parsed?.let(onSelect) },
                    enabled = isValidWaterAmount(parsed)
                ) { Text("Log") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WalkScreen(appViewModel: NutRunViewModel, state: NutRunUiState) {
    val points by appViewModel.routePoints.collectAsStateWithLifecycle()
    val selectedWalkId by appViewModel.selectedWalkId.collectAsStateWithLifecycle()
    val selectedWalkRoutePoints by appViewModel.selectedWalkRoutePoints.collectAsStateWithLifecycle()
    val walkGpsState by appViewModel.walkGpsState.collectAsStateWithLifecycle()

    selectedWalkId?.let { id ->
        state.walks.firstOrNull { it.id == id }?.let { walk ->
            WalkDetailsScreen(
                walk = walk,
                points = selectedWalkRoutePoints,
                onBack = appViewModel::clearSelectedWalk
            )
            return
        }
    }

    WalkOverviewContent(
        state = state,
        routePoints = points,
        walkGpsState = walkGpsState,
        onStartGpsMonitoring = appViewModel::startWalkGpsMonitoring,
        onStopGpsMonitoring = appViewModel::stopWalkGpsMonitoring,
        onSelectCompletedWalk = appViewModel::selectCompletedWalk
    )
}

@Composable
private fun WalkDetailsScreen(
    walk: com.avitoohband.nutrun.data.WalkSessionEntity,
    points: List<WalkPointEntity>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val pace = averageWalkPaceMinutesPerKm(walk)?.let { minutesPerKm ->
        val seconds = (minutesPerKm * 60).roundToInt()
        "%d:%02d /km".format(seconds / 60, seconds % 60)
    } ?: "Unavailable"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("walk-details-back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to walks")
                }
                Column {
                    Text(
                        "Walk details",
                        modifier = Modifier.testTag("walk-details-heading"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(formatWalkDate(walk.startedAtMillis), fontWeight = FontWeight.SemiBold)
                    Text(
                        formatWalkTimeRange(walk),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { WalkRouteMap(points, testTag = "walk-details-route-map") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "%.2f".format(walk.distanceMeters / 1_000),
                    "km",
                    Modifier.weight(1f),
                    Color(0xFFDDEFFC)
                )
                SummaryCard(
                    formatWalkDuration(walk.accumulatedDurationMillis),
                    "duration",
                    Modifier.weight(1f),
                    Color(0xFFFFE7DE)
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    walk.steps?.toString() ?: "Unavailable",
                    "steps",
                    Modifier.weight(1f),
                    Color(0xFFE3F3E8)
                )
                SummaryCard(
                    pace,
                    "Average pace",
                    Modifier.weight(1f),
                    Color(0xFFF5E5D7)
                )
            }
        }
    }
}

@Composable
private fun ProgressScreen(
    app: NutRunViewModel,
    state: NutRunUiState,
    training: TrainingViewModel,
    onNavigateToTraining: () -> Unit,
    onNavigateToWalk: () -> Unit,
    onNavigateToNutrition: () -> Unit
) {
    var editWeight by remember { mutableStateOf(false) }
    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLegacyWorkout by rememberSaveable { mutableStateOf<String?>(null) }
    val healthConnect by app.healthConnectState.collectAsState()
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        app.refreshHealthConnectStatus()
        if (granted.containsAll(app.healthConnectPermissions)) {
            app.synchronizeHealthConnect(training.workoutHistory)
        }
    }
    val profile = state.profile ?: return
    training.workoutHistory
        .firstOrNull { it.id == selectedWorkoutId }
        ?.let { workout ->
            WorkoutDetailsScreen(
                workout = workout,
                exerciseLibrary = training.exerciseLibrary,
                metric = training.usesMetricUnits,
                onBack = { selectedWorkoutId = null },
                onSave = training::updateWorkoutRecord,
                onDelete = {
                    training.deleteWorkoutRecord(workout.id)
                    selectedWorkoutId = null
                }
            )
            return
        }
    if (editWeight) {
        WeightDialog(profile, { app.saveProfile(it); editWeight = false }, { editWeight = false })
    }
    selectedLegacyWorkout?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedLegacyWorkout = null },
            title = { Text("Workout details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry)
                    Text(
                        "Detailed exercise and set data was not recorded for this older workout.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLegacyWorkout = null }) {
                    Text("Done")
                }
            }
        )
    }
    ProgressOverviewContent(
        state = state,
        training = training,
        healthConnect = healthConnect,
        onWorkoutClick = { selectedWorkoutId = it },
        onLegacyWorkoutClick = { selectedLegacyWorkout = it },
        onAddWeight = { editWeight = true },
        onNavigateToTraining = onNavigateToTraining,
        onNavigateToWalk = onNavigateToWalk,
        onNavigateToNutrition = onNavigateToNutrition,
        onConnectHealthConnect = {
            healthPermissionLauncher.launch(app.healthConnectPermissions)
        },
        onSyncHealthConnect = { app.synchronizeHealthConnect(training.workoutHistory) }
    )
}

@Composable
private fun WorkoutDetailsScreen(
    workout: WorkoutRecord,
    exerciseLibrary: List<Exercise>,
    metric: Boolean,
    onBack: () -> Unit,
    onSave: (WorkoutRecord) -> Unit,
    onDelete: () -> Unit
) {
    var editing by rememberSaveable(workout.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (editing) {
        WorkoutHistoryEditScreen(
            workout = workout,
            exerciseLibrary = exerciseLibrary,
            metric = metric,
            onCancel = { editing = false },
            onSave = {
                onSave(it)
                editing = false
            }
        )
        return
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = {
                Text("This permanently removes this workout and its set history.")
            },
            confirmButton = {
                Button(onClick = onDelete) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    val elapsedSeconds =
        ((workout.finishedAtMillis - workout.startedAtMillis).coerceAtLeast(0L) / 1_000L)
    val setsByExercise = workout.sets.groupBy(WorkoutSetLog::exerciseId)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("workout-details-back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to progress")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Workout details",
                        modifier = Modifier.testTag("workout-details-heading"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(workout.sessionName, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatToday(workout.performedOn),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { editing = true },
                    modifier = Modifier.testTag("edit-workout-history")
                ) {
                    Icon(Icons.Default.Edit, "Edit workout")
                }
                IconButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.testTag("delete-workout-history")
                ) {
                    Icon(Icons.Default.Delete, "Delete workout")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    formatElapsedTime(elapsedSeconds),
                    "duration",
                    Modifier.weight(1f),
                    Color(0xFFDDEFFC)
                )
                SummaryCard(
                    "${workout.totalVolumeKg.roundToInt()} kg",
                    "volume",
                    Modifier.weight(1f),
                    Color(0xFFE3F3E8)
                )
                SummaryCard(
                    "${workout.completedLogicalTargets}/${workout.totalLogicalTargets}",
                    "targets",
                    Modifier.weight(1f),
                    Color(0xFFFFE7DE)
                )
            }
        }
        item { SectionHeading("Exercises") }
        if (setsByExercise.isEmpty()) {
            item {
                Text(
                    "Detailed set data was not recorded for this workout.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(setsByExercise.entries.toList(), key = { it.key }) { (exerciseId, sets) ->
            val exerciseName = sets.firstNotNullOfOrNull(WorkoutSetLog::exerciseName)
                ?: exerciseLibrary.firstOrNull { it.id == exerciseId }?.name
                ?: exerciseId
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(exerciseName, fontWeight = FontWeight.Bold)
                    sets.sortedBy(WorkoutSetLog::setNumber).forEach { set ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Set ${set.setNumber}",
                                modifier = Modifier.width(54.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                workoutSetDescription(set, metric),
                                modifier = Modifier.weight(1f),
                                color = if (set.completed) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            if (set.completed) {
                                Icon(
                                    Icons.Default.Check,
                                    "Completed",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryEditScreen(
    workout: WorkoutRecord,
    exerciseLibrary: List<Exercise>,
    metric: Boolean,
    onCancel: () -> Unit,
    onSave: (WorkoutRecord) -> Unit
) {
    var sessionName by rememberSaveable(workout.id) {
        mutableStateOf(workout.sessionName)
    }
    var performedOnEpoch by rememberSaveable(workout.id) {
        mutableLongStateOf(workout.performedOn.toEpochDay())
    }
    val initialSessionName = remember(workout.id) { workout.sessionName }
    val initialPerformedOnEpoch = remember(workout.id) { workout.performedOn.toEpochDay() }
    val initialSets = remember(workout.id) { workout.sets }
    var draftSets by remember(workout.id) { mutableStateOf(workout.sets) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    val performedOn = LocalDate.ofEpochDay(performedOnEpoch)
    val workoutDateRange = FormValidationRules.workoutDateRange()
    val setsByExercise = draftSets.groupBy(WorkoutSetLog::exerciseId)
    val isDirty = sessionName != initialSessionName ||
        performedOnEpoch != initialPerformedOnEpoch ||
        draftSets != initialSets

    fun requestCancel() {
        if (isDirty) {
            showDiscard = true
        } else {
            onCancel()
        }
    }

    ConfirmDiscardChangesDialog(
        open = showDiscard,
        onDismiss = { showDiscard = false },
        onDiscard = {
            showDiscard = false
            onCancel()
        }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("edit-workout-list"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = ::requestCancel,
                    modifier = Modifier.testTag("cancel-edit-workout")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel editing")
                }
                Text(
                    "Edit workout",
                    modifier = Modifier.weight(1f).testTag("edit-workout-heading"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item {
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Workout name") },
                singleLine = true
            )
        }
        item {
            ValidatedDateField(
                value = performedOn,
                onValueChange = { selected -> selected?.let { performedOnEpoch = it.toEpochDay() } },
                label = "Date",
                allowedRange = workoutDateRange,
                modifier = Modifier.fillMaxWidth(),
                testTag = "edit-workout-date"
            )
        }
        item { SectionHeading("Exercises and sets") }
        items(setsByExercise.entries.toList(), key = { it.key }) { (exerciseId, sets) ->
            val exerciseName = sets.firstNotNullOfOrNull(WorkoutSetLog::exerciseName)
                ?: exerciseLibrary.firstOrNull { it.id == exerciseId }?.name
                ?: exerciseId
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(exerciseName, fontWeight = FontWeight.Bold)
                    sets.sortedBy(WorkoutSetLog::setNumber).forEach { set ->
                        WorkoutHistorySetEditorRow(
                            set = set,
                            metric = metric,
                            onChange = { updated ->
                                draftSets = draftSets.map { current ->
                                    if (current.id == updated.id) updated else current
                                }
                            }
                        )
                    }
                }
            }
        }
        validationError?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Button(
                onClick = {
                    val dateError = validateDateInRange(performedOn, workoutDateRange, "Date", required = true)
                    when {
                        sessionName.isBlank() ->
                            validationError = "Workout name is required."
                        dateError != null ->
                            validationError = dateError
                        else -> {
                            validationError = null
                            onSave(
                                workout.copy(
                                    sessionName = sessionName.trim(),
                                    performedOn = performedOn,
                                    sets = draftSets
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("save-workout-history")
            ) {
                Text("Save changes")
            }
        }
    }
}

@Composable
private fun WorkoutHistorySetEditorRow(
    set: WorkoutSetLog,
    metric: Boolean,
    onChange: (WorkoutSetLog) -> Unit
) {
    var reps by remember(set.id) { mutableStateOf(set.reps?.toString().orEmpty()) }
    var weight by remember(set.id, metric) {
        mutableStateOf(
            set.weightKg?.let {
                formatMeasurementForInput(if (metric) it else it * KG_TO_POUNDS)
            }.orEmpty()
        )
    }
    var minutes by remember(set.id) {
        mutableStateOf(
            set.durationSeconds?.div(60.0)
                ?.let(::formatMeasurementForInput)
                .orEmpty()
        )
    }
    var rpe by remember(set.id) {
        mutableStateOf(set.rpe?.let(::formatMeasurementForInput).orEmpty())
    }

    fun emit() {
        val parsedReps = reps.toIntOrNull()
        val enteredWeight = weight.toDoubleOrNull()
        val parsedMinutes = minutes.toDoubleOrNull()
        val parsedRpe = rpe.toDoubleOrNull()
        if (reps.isNotBlank() && parsedReps !in 0..1_000) return
        if (weight.isNotBlank() && (enteredWeight == null || enteredWeight !in 0.0..2_000.0)) return
        if (minutes.isNotBlank() && (parsedMinutes == null || parsedMinutes !in 0.0..1_440.0)) return
        if (rpe.isNotBlank() && (parsedRpe == null || parsedRpe !in 0.0..10.0)) return
        onChange(
            set.copy(
                reps = parsedReps,
                weightKg = enteredWeight?.let { if (metric) it else it / KG_TO_POUNDS },
                durationSeconds = parsedMinutes?.times(60)?.roundToInt(),
                rpe = parsedRpe
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Set ${set.setNumber}",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold
            )
            Text("Completed")
            Checkbox(
                checked = set.completed,
                onCheckedChange = { onChange(set.copy(completed = it)) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it; emit() },
                modifier = Modifier.weight(1f),
                label = { Text("Reps") },
                singleLine = true
            )
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it; emit() },
                modifier = Modifier.weight(1f),
                label = { Text(if (metric) "Weight (kg)" else "Weight (lb)") },
                singleLine = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it; emit() },
                modifier = Modifier.weight(1f),
                label = { Text("Minutes") },
                singleLine = true
            )
            OutlinedTextField(
                value = rpe,
                onValueChange = { rpe = it; emit() },
                modifier = Modifier.weight(1f),
                label = { Text("RPE") },
                singleLine = true
            )
        }
    }
}

private fun workoutSetDescription(set: WorkoutSetLog, metric: Boolean): String {
    val details = buildList {
        when {
            set.reps != null && set.weightKg != null ->
                add("${set.reps} reps x ${displayWeight(set.weightKg, metric)}")
            set.reps != null -> add("${set.reps} reps")
            set.weightKg != null -> add(displayWeight(set.weightKg, metric))
        }
        set.durationSeconds?.let { add(formatElapsedTime(it.toLong())) }
        set.rpe?.let { add("RPE ${formatMeasurementForInput(it)}") }
    }
    val measured = details.joinToString(" | ").ifBlank { "No measurements" }
    return if (set.completed) measured else "$measured | Not completed"
}

private fun formatElapsedTime(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = safeSeconds % 3_600L / 60L
    val seconds = safeSeconds % 60L
    return when {
        hours > 0L -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun WeightDialog(profile: UserProfile, onSave: (UserProfile) -> Unit, onDismiss: () -> Unit) {
    val metric = profile.unitSystem == UnitSystem.METRIC
    var value by remember {
        mutableStateOf(formatWeightForUnits(profile.weightKg, metric))
    }
    val validation = validateDecimalInput(value, FormValidationRules.weightRule(metric))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add weight") },
        text = {
            ValidatedNumberField(
                value = value,
                onValueChange = { value = it },
                rule = FormValidationRules.weightRule(metric),
                testTag = "add-weight-field"
            )
        },
        confirmButton = {
            Button(
                enabled = validation.error == null && validation.value != null,
                onClick = {
                    val weightKg = convertWeightInputToKg(validation.value!!, metric)
                    onSave(profile.copy(weightKg = weightKg))
                },
                modifier = Modifier.testTag("add-weight-save")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditHealthDetailsScreen(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onBack: () -> Unit
) {
    var birthDateEpoch by rememberSaveable { mutableLongStateOf(profile.birthDate.toEpochDay()) }
    var sex by rememberSaveable { mutableStateOf(profile.biologicalSex) }
    var units by rememberSaveable { mutableStateOf(profile.unitSystem) }
    var height by rememberSaveable {
        mutableStateOf(formatHeightForUnits(profile.heightCm, profile.unitSystem == UnitSystem.METRIC))
    }
    var weight by rememberSaveable {
        mutableStateOf(formatWeightForUnits(profile.weightKg, profile.unitSystem == UnitSystem.METRIC))
    }
    var activity by rememberSaveable { mutableStateOf(profile.activityLevel) }
    var goal by rememberSaveable { mutableStateOf(profile.goal) }
    var calorieTarget by rememberSaveable { mutableStateOf(profile.calorieTarget.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    val initialBirthDateEpoch = remember(profile) { profile.birthDate.toEpochDay() }
    val initialSex = remember(profile) { profile.biologicalSex }
    val initialUnits = remember(profile) { profile.unitSystem }
    val initialHeight = remember(profile) {
        formatHeightForUnits(profile.heightCm, profile.unitSystem == UnitSystem.METRIC)
    }
    val initialWeight = remember(profile) {
        formatWeightForUnits(profile.weightKg, profile.unitSystem == UnitSystem.METRIC)
    }
    val initialActivity = remember(profile) { profile.activityLevel }
    val initialGoal = remember(profile) { profile.goal }
    val initialCalorieTarget = remember(profile) { profile.calorieTarget.toString() }

    val birthDate = LocalDate.ofEpochDay(birthDateEpoch)
    val birthDateRange = FormValidationRules.birthDateRange()
    val metric = units == UnitSystem.METRIC
    val heightValidation = validateDecimalInput(height, FormValidationRules.heightRule(metric))
    val weightValidation = validateDecimalInput(weight, FormValidationRules.weightRule(metric))
    val calorieTargetValidation = validateDecimalInput(
        calorieTarget,
        FormValidationRules.calorieTargetRule,
        integerOnly = true
    )
    val heightCm = heightValidation.value?.let { convertHeightInputToCm(it, metric) }
    val weightKg = weightValidation.value?.let { convertWeightInputToKg(it, metric) }
    val estimate = if (heightCm != null && weightKg != null) {
        runCatching {
            calculateHealthEstimate(birthDate, sex, heightCm, weightKg, activity, goal)
        }.getOrNull()
    } else {
        null
    }
    val isDirty = birthDateEpoch != initialBirthDateEpoch ||
        sex != initialSex ||
        units != initialUnits ||
        height != initialHeight ||
        weight != initialWeight ||
        activity != initialActivity ||
        goal != initialGoal ||
        calorieTarget != initialCalorieTarget

    fun requestBack() {
        if (isDirty) {
            showDiscard = true
        } else {
            onBack()
        }
    }

    ConfirmDiscardChangesDialog(
        open = showDiscard,
        onDismiss = { showDiscard = false },
        onDiscard = {
            showDiscard = false
            onBack()
        }
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("edit-health-list"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TextButton(onClick = ::requestBack, modifier = Modifier.testTag("edit-health-back")) { Text("Back") } }
        item {
            OutlinedTextField(
                profile.email,
                {},
                Modifier.fillMaxWidth().testTag("edit-health-email"),
                label = { Text("Email") },
                readOnly = true
            )
        }
        item {
            ValidatedDateField(
                value = birthDate,
                onValueChange = { selected -> selected?.let { birthDateEpoch = it.toEpochDay() } },
                label = "Birth date",
                allowedRange = birthDateRange,
                modifier = Modifier.fillMaxWidth(),
                testTag = "edit-health-birth-date"
            )
        }
        item { ChoiceRow("Biological sex", BiologicalSex.entries, sex, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { sex = it } }
        item {
            ChoiceRow("Units", UnitSystem.entries, units, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { selected ->
                if (selected != units) {
                    val currentHeightCm = heightValidation.value?.let { convertHeightInputToCm(it, metric) }
                    val currentWeightKg = weightValidation.value?.let { convertWeightInputToKg(it, metric) }
                    units = selected
                    currentHeightCm?.let { height = formatHeightForUnits(it, selected == UnitSystem.METRIC) }
                    currentWeightKg?.let { weight = formatWeightForUnits(it, selected == UnitSystem.METRIC) }
                }
            }
        }
        item {
            ValidatedNumberField(
                value = height,
                onValueChange = { height = it },
                rule = FormValidationRules.heightRule(metric),
                modifier = Modifier.fillMaxWidth(),
                testTag = "edit-health-height"
            )
        }
        item {
            ValidatedNumberField(
                value = weight,
                onValueChange = { weight = it },
                rule = FormValidationRules.weightRule(metric),
                modifier = Modifier.fillMaxWidth(),
                testTag = "edit-health-weight"
            )
        }
        item { ChoiceRow("Activity", ActivityLevel.entries, activity, { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) }) { activity = it } }
        item { ChoiceRow("Goal", HealthGoal.entries, goal, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { goal = it } }
        item {
            ValidatedNumberField(
                value = calorieTarget,
                onValueChange = { calorieTarget = it },
                rule = FormValidationRules.calorieTargetRule,
                modifier = Modifier.fillMaxWidth(),
                integerOnly = true,
                testTag = "edit-health-calorie-target"
            )
        }
        estimate?.let { health ->
            item {
                Card(shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("BMI %.1f".format(health.bmi), fontWeight = FontWeight.Bold)
                        Text("BMR ${health.bmrKcal} kcal")
                        Text("TDEE ${health.tdeeKcal} kcal")
                        Text("Recommended ${health.calorieTarget} kcal")
                        TextButton(onClick = { calorieTarget = health.calorieTarget.toString() }) {
                            Text("Use recommended target")
                        }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = {
                    val dateError = validateDateInRange(birthDate, birthDateRange, "Birth date", required = true)
                    val firstError = dateError
                        ?: heightValidation.error
                        ?: weightValidation.error
                        ?: calorieTargetValidation.error
                    if (firstError != null || heightCm == null || weightKg == null) {
                        error = firstError ?: "Check the date, measurements, and calorie target."
                    } else {
                        error = null
                        onSave(
                            profile.copy(
                                birthDate = birthDate,
                                biologicalSex = sex,
                                heightCm = heightCm,
                                weightKg = weightKg,
                                activityLevel = activity,
                                goal = goal,
                                unitSystem = units,
                                calorieTarget = calorieTargetValidation.value!!.toInt()
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("save-health-details")
            ) { Text("Save health details") }
        }
    }
}

@Composable
private fun NotificationSettingsScreen(
    app: NutRunViewModel,
    state: NutRunUiState,
    trainingModel: TrainingViewModel,
    onBack: () -> Unit,
    onManageSupplements: () -> Unit
) {
    val context = LocalContext.current
    val accountId = state.session.authenticatedUserId
    var waterEnabled by rememberSaveable(
        accountId,
        state.hydrationPlan.userId,
        state.hydrationPlan.remindersEnabled
    ) {
        mutableStateOf(state.hydrationPlan.remindersEnabled)
    }
    var interval by rememberSaveable(
        accountId,
        state.hydrationPlan.userId,
        state.hydrationPlan.intervalMinutes
    ) {
        mutableStateOf(state.hydrationPlan.intervalMinutes.toString())
    }
    var firstReminder by rememberSaveable(
        accountId,
        state.hydrationPlan.userId,
        state.hydrationPlan.wakingStartMinute
    ) {
        mutableStateOf(formatReminderMinute(state.hydrationPlan.wakingStartMinute))
    }
    var lastReminder by rememberSaveable(
        accountId,
        state.hydrationPlan.userId,
        state.hydrationPlan.wakingEndMinute
    ) {
        mutableStateOf(formatReminderMinute(state.hydrationPlan.wakingEndMinute))
    }
    val training = state.trainingReminderSettings
    var trainingEnabled by rememberSaveable(accountId, training.userId, training.enabled) {
        mutableStateOf(training.enabled)
    }
    var dayBeforeReminder by rememberSaveable(
        accountId,
        training.userId,
        training.previousDayMinute
    ) {
        mutableStateOf(formatReminderMinute(training.previousDayMinute))
    }
    var trainingDayReminder by rememberSaveable(
        accountId,
        training.userId,
        training.sameDayMinute
    ) {
        mutableStateOf(formatReminderMinute(training.sameDayMinute))
    }
    val supplementSettings = state.supplementReminderSettings
    var supplementMasterEnabled by rememberSaveable(
        accountId,
        supplementSettings.userId,
        supplementSettings.enabled
    ) {
        mutableStateOf(supplementSettings.enabled)
    }
    val supplements = trainingModel.supplements.toList()
    val readyAccountId = trainingModel.supplementReminderReadyAccountId
    val accountReady = notificationSettingsAccountReady(
        accountId = accountId,
        hydrationAccountId = state.hydrationPlan.userId,
        trainingAccountId = training.userId,
        supplementAccountId = supplementSettings.userId,
        trainingPayloadAccountId = readyAccountId
    )
    var supplementDraftState by rememberSaveable(
        stateSaver = SupplementReminderDraftStateSaver
    ) {
        mutableStateOf(SupplementReminderDraftState())
    }
    val visibleDraftState = resolveSupplementReminderDraftState(
        state = supplementDraftState,
        screenAccountId = accountId,
        readyAccountId = readyAccountId,
        supplements = supplements
    )
    val supplementSource = supplements.map { supplement ->
        Triple(supplement.id, supplement.reminderEnabled, supplement.reminderMinute)
    }
    LaunchedEffect(state.sessionResolved, accountId) {
        val resolvedOwner = resolveSupplementReminderDraftOwner(
            state = supplementDraftState,
            sessionResolved = state.sessionResolved,
            accountId = accountId
        )
        if (resolvedOwner != supplementDraftState) {
            supplementDraftState = resolvedOwner
        }
    }
    LaunchedEffect(accountId, readyAccountId, supplementSource) {
        if (accountReady && visibleDraftState != supplementDraftState) {
            supplementDraftState = visibleDraftState
        }
    }

    val readySupplements = if (accountReady) supplements else emptyList()
    val trainingDays = trainingModel.weeklyDayPlans
        .filter { plan -> !plan.isRestDay && plan.templateIds.isNotEmpty() }
        .mapTo(linkedSetOf(), WeeklyDayPlan::weekday)
    val savedSupplementDrafts = readySupplements.associate { supplement ->
        supplement.id to SupplementReminderDraft(
            enabled = supplement.reminderEnabled,
            time = formatReminderMinute(supplement.reminderMinute)
        )
    }
    val savedDraft = NotificationSettingsDraft(
        waterEnabled = state.hydrationPlan.remindersEnabled,
        intervalMinutes = state.hydrationPlan.intervalMinutes.toString(),
        firstReminder = formatReminderMinute(state.hydrationPlan.wakingStartMinute),
        lastReminder = formatReminderMinute(state.hydrationPlan.wakingEndMinute),
        trainingEnabled = training.enabled,
        dayBeforeReminder = formatReminderMinute(training.previousDayMinute),
        trainingDayReminder = formatReminderMinute(training.sameDayMinute),
        trainingDays = trainingDays,
        supplementMasterEnabled = supplementSettings.enabled,
        supplementDrafts = savedSupplementDrafts
    )
    val draft = NotificationSettingsDraft(
        waterEnabled = waterEnabled,
        intervalMinutes = interval,
        firstReminder = firstReminder,
        lastReminder = lastReminder,
        trainingEnabled = trainingEnabled,
        dayBeforeReminder = dayBeforeReminder,
        trainingDayReminder = trainingDayReminder,
        trainingDays = trainingDays,
        supplementMasterEnabled = supplementMasterEnabled,
        supplementDrafts = visibleDraftState.drafts
    )

    var permissionGranted by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted = it }
    fun requestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !permissionGranted) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NotificationSettingsContent(
        savedDraft = savedDraft,
        draft = draft,
        supplements = readySupplements,
        accountReady = accountReady,
        permissionGranted = permissionGranted,
        onDraftChange = { updated ->
            waterEnabled = updated.waterEnabled
            interval = updated.intervalMinutes
            firstReminder = updated.firstReminder
            lastReminder = updated.lastReminder
            trainingEnabled = updated.trainingEnabled
            dayBeforeReminder = updated.dayBeforeReminder
            trainingDayReminder = updated.trainingDayReminder
            supplementMasterEnabled = updated.supplementMasterEnabled
            supplementDraftState = applySupplementReminderDraftChanges(
                state = visibleDraftState,
                updated = updated.supplementDrafts,
                supplements = readySupplements
            )
        },
        onPermissionRequest = ::requestPermission,
        onOpenNotificationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        },
        onManageSupplements = onManageSupplements,
        persist = persist@{
            val expectedAccountId = accountId
                ?: return@persist NotificationSettingsSaveResult.NotReady(null)
            val intervalValue = draft.intervalMinutes.toInt()
            val firstMinute = requireNotNull(parseReminderMinute(draft.firstReminder))
            val lastMinute = requireNotNull(parseReminderMinute(draft.lastReminder))
            val dayBeforeMinute = requireNotNull(parseReminderMinute(draft.dayBeforeReminder))
            val trainingDayMinute = requireNotNull(parseReminderMinute(draft.trainingDayReminder))
            val configurations = readySupplements.associate { supplement ->
                val reminderDraft = draft.supplementDrafts[supplement.id]
                    ?: savedSupplementDrafts.getValue(supplement.id)
                supplement.id to SupplementReminderConfig(
                    enabled = reminderDraft.enabled,
                    minute = requireNotNull(parseReminderMinute(reminderDraft.time))
                )
            }
            orchestrateNotificationSettingsSave(
                persistIndividuals = {
                    trainingModel.persistSupplementReminders(
                        expectedAccountId,
                        configurations
                    )
                },
                persistRemainingSettings = {
                    app.persistNotificationSettings(
                        accountId = expectedAccountId,
                        hydration = state.hydrationPlan.copy(
                            remindersEnabled = draft.waterEnabled,
                            intervalMinutes = intervalValue,
                            wakingStartMinute = firstMinute,
                            wakingEndMinute = lastMinute
                        ),
                        training = training.copy(
                            enabled = draft.trainingEnabled,
                            previousDayMinute = dayBeforeMinute,
                            sameDayMinute = trainingDayMinute,
                            timezoneId = java.time.ZoneId.systemDefault().id
                        ),
                        supplements = supplementSettings.copy(
                            enabled = draft.supplementMasterEnabled,
                            timezoneId = java.time.ZoneId.systemDefault().id
                        )
                    )
                }
            )
        },
        currentAccountId = app::currentAuthenticatedAccountId,
        onSaveSuccess = onBack,
        onBack = onBack
    )
}
internal fun notificationSettingsAccountReady(
    accountId: String?,
    hydrationAccountId: String,
    trainingAccountId: String,
    supplementAccountId: String,
    trainingPayloadAccountId: String?
): Boolean = accountId != null &&
    hydrationAccountId == accountId &&
    trainingAccountId == accountId &&
    supplementAccountId == accountId &&
    trainingPayloadAccountId == accountId

internal fun shouldRequestSupplementReminderPermission(
    previousEnabled: Boolean?,
    enabled: Boolean
): Boolean = enabled && previousEnabled != true

@Composable
internal fun NotificationSettingsSaveButton(
    valid: Boolean,
    accountReady: Boolean,
    persist: suspend () -> NotificationSettingsSaveResult,
    currentAccountId: suspend () -> String?,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            enabled = valid && accountReady && !saving,
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    val persisted = persist()
                    val latestAccountId = currentAccountId()
                    withContext(Dispatchers.Main.immediate) {
                        val result = validateNotificationSettingsSaveAccount(
                            persisted,
                            latestAccountId
                        )
                        saving = false
                        if (result.allowsNavigation) {
                            onSuccess()
                        } else {
                            error = result.errorMessage()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("save-notification-settings")
        ) {
            Text(if (saving) "Saving notification settings..." else "Save notification settings")
        }
        error?.let { message ->
            Text(
                message,
                modifier = Modifier.testTag("notification-settings-save-error"),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun AddSupplementDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, SupplementSchedule, Boolean, Int) -> Unit,
    existing: Supplement? = null
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var dose by remember(existing?.id) { mutableStateOf(existing?.dose.orEmpty()) }
    var reminderEnabled by remember(existing?.id) {
        mutableStateOf(existing?.reminderEnabled ?: true)
    }
    var reminderTime by remember(existing?.id) {
        mutableStateOf(formatReminderMinute(existing?.reminderMinute ?: 8 * 60))
    }
    val reminderMinute = parseReminderMinute(reminderTime)
    val initialSelectedDays = remember(existing?.id) {
        existing?.schedule?.selectedWeekdays() ?: setOf(LocalDate.now().dayOfWeek)
    }
    var selectedDays by remember(existing?.id) {
        mutableStateOf(initialSelectedDays)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add supplement" else "Edit supplement") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth().testTag("supplement-dialog-name"),
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    dose,
                    { dose = it },
                    Modifier.fillMaxWidth().testTag("supplement-dialog-dose"),
                    label = { Text("Dose and unit") },
                    singleLine = true
                )
                Text("Take on", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeek.entries.take(4).forEach { day ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            },
                            modifier = Modifier.testTag("supplement-dialog-weekday-${day.name}"),
                            label = {
                                Text(
                                    day.name.take(3).lowercase()
                                        .replaceFirstChar(Char::uppercase)
                                )
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeek.entries.drop(4).forEach { day ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            },
                            modifier = Modifier.testTag("supplement-dialog-weekday-${day.name}"),
                            label = {
                                Text(
                                    day.name.take(3).lowercase()
                                        .replaceFirstChar(Char::uppercase)
                                )
                            }
                        )
                    }
                }
                FilterChip(
                    selected = selectedDays.size == DayOfWeek.entries.size,
                    onClick = {
                        selectedDays = if (selectedDays.size == DayOfWeek.entries.size) {
                            emptySet()
                        } else {
                            DayOfWeek.entries.toSet()
                        }
                    },
                    modifier = Modifier.testTag("supplement-dialog-weekday-all"),
                    label = { Text("All days") }
                )
                if (selectedDays.isEmpty()) {
                    Text(
                        "Choose at least one day.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reminder", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        modifier = Modifier.testTag("supplement-dialog-reminder-enabled")
                    )
                }
                ReminderTimeInput(
                    value = reminderTime,
                    onValueChange = { reminderTime = it },
                    label = "Reminder time",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "supplement-dialog-reminder-time"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        name.trim(),
                        dose.trim(),
                        resolveSupplementEditSchedule(
                            existing = existing?.schedule,
                            selectedDays = selectedDays,
                            scheduleChanged = selectedDays != initialSelectedDays
                        ),
                        reminderEnabled,
                        reminderMinute!!
                    )
                },
                enabled = name.isNotBlank() && dose.isNotBlank() &&
                    selectedDays.isNotEmpty() && reminderMinute != null,
                modifier = Modifier.testTag("supplement-dialog-save")
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal fun resolveSupplementEditSchedule(
    existing: SupplementSchedule?,
    selectedDays: Set<DayOfWeek>,
    scheduleChanged: Boolean,
    today: LocalDate = LocalDate.now()
): SupplementSchedule {
    if (existing != null && !scheduleChanged) return existing
    return existing?.copy(
        type = RecurrenceType.WEEKDAYS,
        weekdays = selectedDays
    ) ?: SupplementSchedule(
        type = RecurrenceType.WEEKDAYS,
        startDate = today,
        weekdays = selectedDays
    )
}

@Composable
private fun AddTrainingSessionDialog(
    onDismiss: () -> Unit,
    onSave: (String, DayOfWeek) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(DayOfWeek.MONDAY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add training session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Session name") }, singleLine = true)
                ChoiceRow(
                    title = "Training day",
                    values = DayOfWeek.entries,
                    selected = day,
                    label = { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) },
                    onSelected = { day = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), day) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ExercisePickerDialog(
    model: TrainingViewModel,
    sessionId: String,
    onDismiss: () -> Unit
) {
    val session = model.sessions.firstOrNull { it.id == sessionId }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var removeTarget by remember { mutableStateOf<ExerciseTarget?>(null) }
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${target.exercise.name}?") },
            text = { Text("This removes it only from ${session?.name.orEmpty()}.") },
            confirmButton = {
                Button(onClick = {
                    model.selectSession(sessionId)
                    model.removeExerciseFromSelectedSession(target.id)
                    removeTarget = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }
    val includedIds = session?.exercises.orEmpty().map { it.exercise.id }.toSet()
    val results = filterExercises(model.exerciseLibrary, query, category)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exercises for ${session?.name.orEmpty()}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Search exercises") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "Strength", "Bodyweight", "Cardio", "Mobility").forEach {
                            FilterChip(
                                selected = category == it,
                                onClick = { category = it },
                                label = { Text(it) }
                            )
                        }
                    }
                }
                if (session?.exercises?.isNotEmpty() == true) {
                    item { Text("In this session", fontWeight = FontWeight.Bold) }
                    items(session.exercises, key = { "current-${it.id}" }) { target ->
                        Card(shape = RoundedCornerShape(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(target.exercise.name, fontWeight = FontWeight.SemiBold)
                                    Text(target.summary(model.usesMetricUnits), fontSize = 12.sp)
                                }
                                IconButton(onClick = { removeTarget = target }) {
                                    Icon(Icons.Default.Delete, "Remove ${target.exercise.name}")
                                }
                            }
                        }
                    }
                }
                item { Text("Exercise catalog", fontWeight = FontWeight.Bold) }
                items(results, key = { it.id }) { exercise ->
                    val included = exercise.id in includedIds
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = !included) {
                            model.selectSession(sessionId)
                            model.addExerciseToSelectedSession(exercise)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (included) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${exercise.category} | ${exercise.primaryMuscles} | ${exercise.secondaryMuscles}",
                                    fontSize = 12.sp
                                )
                            }
                            if (included) Icon(Icons.Default.Check, "Already included")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun RestTimerSettingsDialog(
    currentSeconds: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var seconds by rememberSaveable(currentSeconds) {
        mutableStateOf(currentSeconds.toString())
    }
    val validation = validateDecimalInput(seconds, FormValidationRules.restTimerRule, integerOnly = true)
    val parsed = validation.value?.toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default rest timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceRow(
                    title = "Quick choices",
                    values = listOf(30, 60, 90, 120, 180),
                    selected = parsed,
                    label = { "$it sec" },
                    onSelected = { seconds = it.toString() }
                )
                ValidatedNumberField(
                    value = seconds,
                    onValueChange = { seconds = it },
                    rule = FormValidationRules.restTimerRule,
                    integerOnly = true,
                    testTag = "rest-timer-seconds"
                )
                Text(
                    "The timer starts after you complete a set.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onSave) },
                enabled = validation.error == null && parsed != null,
                modifier = Modifier.testTag("rest-timer-save")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private const val REST_TIMER_CHANNEL_ID = "rest_timer_finished_v1"
private const val REST_TIMER_NOTIFICATION_ID = 3_001
private val REST_TIMER_VIBRATION = longArrayOf(0L, 300L, 150L, 450L, 150L, 600L)

@Suppress("DEPRECATION")
private fun playRestTimerFinishedFeedback(context: Context) {
    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
        NotificationChannel(
            REST_TIMER_CHANNEL_ID,
            "Rest timer alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a workout rest timer finishes"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = REST_TIMER_VIBRATION
        }
    )

    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val notifications = NotificationManagerCompat.from(context)
    if (permissionGranted && notifications.areNotificationsEnabled()) {
        notifications.notify(
            REST_TIMER_NOTIFICATION_ID,
            NotificationCompat.Builder(context, REST_TIMER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Rest complete")
                .setContentText("Your next set is ready.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()
        )
        return
    }

    runCatching {
        RingtoneManager.getRingtone(context, soundUri)?.apply {
            this.audioAttributes = audioAttributes
            play()
        }
    }
    vibrateRestTimerFinished(context)
}

@Suppress("DEPRECATION")
private fun vibrateRestTimerFinished(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(REST_TIMER_VIBRATION, -1))
        }
    }
}

@Composable
private fun RescheduleTrainingDialog(
    session: TrainingSession,
    originalDate: LocalDate,
    onDismiss: () -> Unit,
    onMove: (LocalDate) -> Unit
) {
    var scheduledEpoch by rememberSaveable(session.id, originalDate) {
        mutableLongStateOf(originalDate.plusDays(1).toEpochDay())
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scheduledDate = LocalDate.ofEpochDay(scheduledEpoch)
    val allowedRange = FormValidationRules.workoutDateRange()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${session.name}") },
        text = {
            Column {
                Text("Originally ${formatToday(originalDate)}")
                ValidatedDateField(
                    value = scheduledDate,
                    onValueChange = { selected ->
                        selected?.let { scheduledEpoch = it.toEpochDay() }
                        error = null
                    },
                    label = "New date",
                    allowedRange = allowedRange,
                    error = error,
                    testTag = "reschedule-training-date"
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val dateError = validateDateInRange(scheduledDate, allowedRange, "New date", required = true)
                if (dateError != null) {
                    error = dateError
                } else {
                    onMove(scheduledDate)
                }
            }, modifier = Modifier.testTag("reschedule-training-confirm")) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SummaryCard(
    value: String,
    label: String,
    modifier: Modifier,
    color: Color,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val taggedModifier = testTag?.let { modifier.testTag(it) } ?: modifier
    Card(
        taggedModifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF202426))
            Text(label, fontSize = 11.sp, color = Color(0xFF4F5B62))
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val taggedModifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
    Card(
        modifier = taggedModifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, fontWeight = FontWeight.Bold, fontSize = 19.sp)
}

@Composable
internal fun AdPlacement() {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
