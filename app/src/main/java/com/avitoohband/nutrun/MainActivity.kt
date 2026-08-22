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
import java.time.format.DateTimeParseException
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
    var hydrationCelebration by remember {
        mutableStateOf<HydrationGoalCelebration?>(null)
    }
    LaunchedEffect(app) {
        app.hydrationGoalCelebrations.collect {
            hydrationCelebration = it
        }
    }
    val dark = state.session.darkMode
    val colors = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()

    MaterialTheme(colorScheme = colors) {
        when {
            state.session.authenticatedEmail == null -> AuthenticationScreen(
                onAuthenticate = app::authenticate,
                onDemo = app::enterDemo
            )
            state.profile == null -> OnboardingScreen(
                email = state.session.authenticatedEmail.orEmpty(),
                onSave = app::saveProfile
            )
            else -> MainApp(app, training, state, navigationRequest, onNavigationConsumed)
        }
        message?.let {
            AlertDialog(
                onDismissRequest = app::clearMessage,
                title = { Text("NutRun") },
                text = { Text(it) },
                confirmButton = { TextButton(onClick = app::clearMessage) { Text("OK") } }
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
private fun AuthenticationScreen(
    onAuthenticate: (String, String, Boolean) -> Unit,
    onDemo: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("NutRun", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Sign in to keep your training and health log together.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (BuildConfig.DEBUG) "Email or demo username" else "Email") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAuthenticate(email, password, false) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sign in") }
        OutlinedButton(
            onClick = { onAuthenticate(email, password, true) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create account") }
        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = onDemo,
                modifier = Modifier.fillMaxWidth().testTag("demo-login")
            ) { Text("Enter demo") }
        }
        Text(
            "A 30-day ad-free trial starts when the account is first created. No payment details required.",
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun OnboardingScreen(email: String, onSave: (UserProfile) -> Unit) {
    var birthDate by rememberSaveable { mutableStateOf("1995-01-01") }
    var sex by rememberSaveable { mutableStateOf(BiologicalSex.MALE) }
    var height by rememberSaveable { mutableStateOf("175") }
    var weight by rememberSaveable { mutableStateOf("75") }
    var activity by rememberSaveable { mutableStateOf(ActivityLevel.MODERATE) }
    var goal by rememberSaveable { mutableStateOf(HealthGoal.MAINTAIN) }
    var units by rememberSaveable { mutableStateOf(UnitSystem.METRIC) }
    var target by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Set up your profile", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("These details calculate your starting BMI and energy estimates.")
        }
        item {
            OutlinedTextField(
                birthDate,
                { birthDate = it },
                Modifier.fillMaxWidth(),
                label = { Text("Birth date (YYYY-MM-DD)") },
                singleLine = true
            )
        }
        item { ChoiceRow("Biological sex", BiologicalSex.entries, sex, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { sex = it } }
        item { ChoiceRow("Units", UnitSystem.entries, units, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { units = it } }
        item {
            OutlinedTextField(
                height,
                { height = it },
                Modifier.fillMaxWidth(),
                label = { Text(if (units == UnitSystem.METRIC) "Height (cm)" else "Height (inches)") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                weight,
                { weight = it },
                Modifier.fillMaxWidth(),
                label = { Text(if (units == UnitSystem.METRIC) "Weight (kg)" else "Weight (lb)") },
                singleLine = true
            )
        }
        item { ChoiceRow("Activity", ActivityLevel.entries, activity, { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) }) { activity = it } }
        item { ChoiceRow("Goal", HealthGoal.entries, goal, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { goal = it } }
        item {
            OutlinedTextField(
                target,
                { target = it },
                Modifier.fillMaxWidth(),
                label = { Text("Daily calorie target (optional)") },
                singleLine = true
            )
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = {
                    try {
                        val date = LocalDate.parse(birthDate)
                        val enteredHeight = height.toDouble()
                        val enteredWeight = weight.toDouble()
                        val heightCm = if (units == UnitSystem.METRIC) enteredHeight else enteredHeight * 2.54
                        val weightKg = if (units == UnitSystem.METRIC) enteredWeight else enteredWeight / KG_TO_POUNDS
                        val estimate = calculateHealthEstimate(date, sex, heightCm, weightKg, activity, goal)
                        onSave(
                            UserProfile(
                                email,
                                date,
                                sex,
                                heightCm,
                                weightKg,
                                activity,
                                goal,
                                units,
                                target.toIntOrNull() ?: estimate.calorieTarget
                            )
                        )
                    } catch (_: DateTimeParseException) {
                        error = "Use a valid date in YYYY-MM-DD format."
                    } catch (_: Exception) {
                        error = "Check that your date, height, weight, and target are valid."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Finish setup") }
        }
        item {
            Text(
                "Estimates are general guidance and are not medical advice.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
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
    onNavigationConsumed: (Long) -> Unit
) {
    val navController = rememberNavController()
    var waterFocusRequest by rememberSaveable { mutableStateOf(0) }
    var pendingSupplementsFocusRequestId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    fun navigateTo(destination: String, focusWater: Boolean = false) {
        if (focusWater) waterFocusRequest += 1
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
                            modifier = Modifier.testTag("bottom-nav-${destination.route}"),
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
                    onWaterClick = { navigateTo("nutrition", focusWater = true) },
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
            composable("nutrition") { NutritionScreen(app, state, waterFocusRequest) }
            composable("walk") { WalkScreen(app, state) }
            composable("progress") { ProgressScreen(app, state, training) }
            composable("supplements") {
                SupplementsScreen(
                    training = training,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("profile") {
                ProfileScreen(
                    app,
                    state,
                    onBack = { navController.popBackStack() },
                    onEditHealth = { navController.navigate("edit-health") },
                    onNotifications = { navController.navigate("notifications") }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayScreen(
    state: NutRunUiState,
    training: TrainingViewModel,
    onTrainingClick: () -> Unit,
    onWaterClick: () -> Unit,
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
        listState.animateScrollToItem(6)
        supplementsHeadingRequester.bringIntoView()
        onSupplementsFocusConsumed(requestId)
    }
    var addSupplement by remember { mutableStateOf(false) }
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
    LazyColumn(
        Modifier.fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("today-list"),
        state = listState,
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Today",
                modifier = Modifier.testTag("today-heading"),
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "${state.waterMl}",
                    "mL water",
                    Modifier.weight(1f),
                    Color(0xFFDDEFFC),
                    onClick = onWaterClick
                )
                SummaryCard("${state.nutrition.proteinGrams.roundToInt()} g", "protein", Modifier.weight(1f), Color(0xFFE3F3E8))
                SummaryCard("${state.walks.firstOrNull()?.distanceMeters?.div(1_000)?.let { "%.1f".format(it) } ?: "0"} km", "last walk", Modifier.weight(1f), Color(0xFFFFE7DE))
            }
        }
        item { SectionHeading("Today's training") }
        item {
            val today = LocalDate.now()
            val todaySessions = training.sessionsForDate(today)
            val upcoming = training.nextScheduledSession(today.plusDays(1))
            ActionCard(
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
        item { SectionHeading("Hydration") }
        item {
            ActionCard(
                "${state.waterMl} / ${state.hydrationPlan.goalMl} mL",
                if (state.waterMl >= state.hydrationPlan.goalMl) "Daily goal reached" else "Keep a steady pace through your waking window.",
                Icons.Default.WaterDrop,
                onClick = onWaterClick,
                testTag = "today-hydration-card"
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading(
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
                IconButton(
                    onClick = { addSupplement = true },
                    enabled = trainingReady
                ) {
                    Icon(Icons.Default.Add, "Add supplement")
                }
            }
        }
        val today = LocalDate.now()
        val dueSupplements = if (trainingReady) dueSupplementsForDate(training.supplements, today) else emptyList()
        if (!trainingReady) item {
            Text("Loading supplements...", modifier = Modifier.testTag("today-supplements-loading"))
        }
        items(dueSupplements, key = { it.id }) { supplement ->
            val completed = supplement.isCompletedOn(today)
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (completed) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
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
        if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) item { AdPlacement() }
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
    waterFocusRequest: Int = 0
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
    val searchResults by app.foodSearchResults.collectAsState()
    val searchBusy by app.foodSearchBusy.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val waterHeadingIndex = 2 + searchResults.size + if (searchBusy) 1 else 0
    LaunchedEffect(waterFocusRequest, waterHeadingIndex) {
        if (waterFocusRequest > 0) listState.animateScrollToItem(waterHeadingIndex)
    }

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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nutrition", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${state.nutrition.calories} kcal | P ${state.nutrition.proteinGrams.roundToInt()} | C ${state.nutrition.carbohydrateGrams.roundToInt()} | F ${state.nutrition.fatGrams.roundToInt()}")
                }
                IconButton(onClick = { createFood = true }) { Icon(Icons.Default.Add, "Add food") }
            }
        }
        item {
            OutlinedTextField(
                query,
                {
                    query = it
                    app.searchFood(it)
                },
                Modifier.fillMaxWidth(),
                label = { Text("Search food") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
        }
        if (searchBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(searchResults, key = { it.id }) { result ->
            Card(
                Modifier.fillMaxWidth().clickable {
                    draftFood = result
                    query = ""
                    app.searchFood("")
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(result.name, fontWeight = FontWeight.SemiBold)
                        Text("${result.servingGrams.roundToInt()} g serving")
                    }
                    Text("${result.calories} kcal")
                }
            }
        }
        item(key = "water-heading") {
            Row(
                modifier = Modifier.testTag("water-section"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading("Water", Modifier.weight(1f))
                TextButton(onClick = { hydrationSettings = true }) { Text("Settings") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.waterMl} / ${state.hydrationPlan.goalMl} mL", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { (state.waterMl / state.hydrationPlan.goalMl.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.combinedClickable(
                            onClick = { app.addWater(state.hydrationPlan.servingMl) },
                            onLongClick = { waterAmounts = true }
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "+${state.hydrationPlan.servingMl} mL",
                            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        if (state.foodTemplates.isNotEmpty() || state.recentFoods.isNotEmpty()) {
            item { SectionHeading("Quick add") }
        }
        items(state.foodTemplates, key = { "template:${it.id}" }) { template ->
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (template.kind == "FAVORITE") Icons.Default.Star else Icons.Default.LocalDining,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (template.kind == "FAVORITE") "Favorite food" else "Saved meal",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { app.logFoodTemplate(template) }) {
                        Icon(Icons.Default.Add, "Add ${template.name}")
                    }
                    IconButton(onClick = { app.deleteFoodTemplate(template) }) {
                        Icon(Icons.Default.Delete, "Delete ${template.name}")
                    }
                }
            }
        }
        val recentFoods = state.recentFoods
            .distinctBy { "${it.catalogId}:${it.name}:${it.servingGrams}" }
            .take(5)
        items(recentFoods, key = { "recent:${it.id}" }) { entry ->
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Recent | ${entry.calories} kcal",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { app.saveFavoriteFood(entry) }) {
                        Icon(Icons.Default.Star, "Favorite ${entry.name}")
                    }
                    IconButton(onClick = { app.logRecentFood(entry) }) {
                        Icon(Icons.Default.Add, "Add ${entry.name}")
                    }
                }
            }
        }
        MealType.entries.forEach { meal ->
            val entries = state.food.filter { it.mealType == meal.name }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeading(
                        meal.name.lowercase().replaceFirstChar(Char::uppercase),
                        Modifier.weight(1f)
                    )
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { saveMealType = meal }) { Text("Save meal") }
                    }
                }
            }
            if (entries.isEmpty()) item { Text("Nothing logged", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(entries, key = { it.id }) { entry ->
                FoodLogRow(
                    entry,
                    { showFood = entry },
                    { app.duplicateFood(entry.id) },
                    { app.saveFavoriteFood(entry) },
                    { app.deleteFood(entry) }
                )
            }
        }
        if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) item { AdPlacement() }
    }
}

@Composable
private fun FoodLogRow(
    entry: FoodLogEntity,
    edit: () -> Unit,
    duplicate: () -> Unit,
    favorite: () -> Unit,
    delete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold)
                Text("${entry.servingGrams.roundToInt()} g | ${entry.calories} kcal")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Food actions") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Edit") }, { menu = false; edit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem({ Text("Duplicate") }, { menu = false; duplicate() }, leadingIcon = { Icon(Icons.Default.Add, null) })
                    DropdownMenuItem({ Text("Add to favorites") }, { menu = false; favorite() }, leadingIcon = { Icon(Icons.Default.Star, null) })
                    DropdownMenuItem({ Text("Delete") }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Log food" else "Edit food") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Food name") }, singleLine = true) }
                item { ChoiceRow("Meal", MealType.entries, meal, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { meal = it } }
                item { OutlinedTextField(serving, { serving = it }, label = { Text("Serving (g)") }, singleLine = true) }
                item { OutlinedTextField(calories, { calories = it }, label = { Text("Calories") }, singleLine = true) }
                item { OutlinedTextField(protein, { protein = it }, label = { Text("Protein (g)") }, singleLine = true) }
                item { OutlinedTextField(carbs, { carbs = it }, label = { Text("Carbohydrates (g)") }, singleLine = true) }
                item { OutlinedTextField(fat, { fat = it }, label = { Text("Fat (g)") }, singleLine = true) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && calories.toIntOrNull() != null,
                onClick = {
                    onSave(
                        FoodCatalogItem(
                            existing?.catalogId ?: draft?.id ?: "manual-${System.currentTimeMillis()}",
                            name,
                            existing?.brand ?: draft?.brand,
                            serving.toDoubleOrNull() ?: 100.0,
                            calories.toIntOrNull() ?: 0,
                            protein.toDoubleOrNull() ?: 0.0,
                            carbs.toDoubleOrNull() ?: 0.0,
                            fat.toDoubleOrNull() ?: 0.0
                        ),
                        meal,
                        existing?.id
                    )
                }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Water settings") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(goal, { goal = it }, label = { Text("Daily goal (mL)") }) }
                item { OutlinedTextField(serving, { serving = it }, label = { Text("Quick serving (mL)") }) }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    initial.copy(
                        goalMl = goal.toIntOrNull() ?: initial.goalMl,
                        servingMl = serving.toIntOrNull() ?: initial.servingMl
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WaterAmountDialog(onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
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
    val context = LocalContext.current
    val active = state.activeWalk
    val points by appViewModel.routePoints.collectAsStateWithLifecycle()
    val selectedWalkId by appViewModel.selectedWalkId.collectAsStateWithLifecycle()
    val selectedWalkRoutePoints by appViewModel.selectedWalkRoutePoints.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted) {
            sendWalkAction(
                context,
                WalkRecordingService.ACTION_START,
                state.session.authenticatedUserId
            )
        }
        else permissionDenied = true
    }

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

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Recorded walks", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Route tracking runs only after you press Start.")
        }
        item { RouteMap(points, testTag = "active-walk-route-map") }
        active?.let { walk ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("%.2f".format(walk.distanceMeters / 1_000), "km", Modifier.weight(1f), Color(0xFFDDEFFC))
                    SummaryCard(walk.steps?.toString() ?: "--", "steps", Modifier.weight(1f), Color(0xFFE3F3E8))
                    SummaryCard((walk.accumulatedDurationMillis / 60_000).toString(), "minutes", Modifier.weight(1f), Color(0xFFFFE7DE))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            sendWalkAction(
                                context,
                                if (walk.state == WalkState.PAUSED.name) WalkRecordingService.ACTION_RESUME
                                else WalkRecordingService.ACTION_PAUSE,
                                state.session.authenticatedUserId
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (walk.state == WalkState.PAUSED.name) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                        Text(if (walk.state == WalkState.PAUSED.name) "Resume" else "Pause")
                    }
                    Button(
                        onClick = {
                            sendWalkAction(
                                context,
                                WalkRecordingService.ACTION_FINISH,
                                state.session.authenticatedUserId
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Text("Finish")
                    }
                }
            }
        } ?: item {
            Button(
                onClick = {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (android.os.Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                        if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        sendWalkAction(
                            context,
                            WalkRecordingService.ACTION_START,
                            state.session.authenticatedUserId
                        )
                    }
                    else permissionLauncher.launch(permissions.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Text("Start walk")
            }
        }
        if (permissionDenied) item {
            Text("Location permission is required to record a route. Step count may be unavailable if activity permission or the sensor is missing.", color = MaterialTheme.colorScheme.error)
        }
        item { SectionHeading("History") }
        items(state.walks.filter { it.state == WalkState.FINISHED.name }, key = { it.id }) { walk ->
            ActionCard(
                title = "%.2f km".format(walk.distanceMeters / 1_000),
                subtitle = "${formatWalkDate(walk.startedAtMillis)} | " +
                    "${formatWalkDuration(walk.accumulatedDurationMillis)} | " +
                    formatWalkStepSummary(walk.steps),
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                onClick = { appViewModel.selectCompletedWalk(walk.id) },
                testTag = "walk-history-card"
            )
        }
    }
}

private fun sendWalkAction(context: Context, action: String, userId: String?) {
    val intent = Intent(context, WalkRecordingService::class.java)
        .setAction(action)
        .putExtra(WalkRecordingService.EXTRA_USER_ID, userId)
    ContextCompat.startForegroundService(context, intent)
}

@Composable
private fun RouteMap(points: List<WalkPointEntity>, testTag: String? = null) {
    val modifier = (testTag?.let(Modifier::testTag) ?: Modifier).semantics {
        contentDescription = if (points.size > 1) {
            "Saved route with ${points.size} points"
        } else {
            "No saved route"
        }
    }
    Card(
        modifier.fillMaxWidth().height(240.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (BuildConfig.MAPS_CONFIGURED) {
            val cameraPositionState = rememberCameraPositionState()
            val framing = walkRouteCameraFraming(points)
            var mapLoaded by remember { mutableStateOf(false) }
            var mapSize by remember { mutableStateOf(IntSize.Zero) }
            LaunchedEffect(mapLoaded, mapSize, framing) {
                if (!mapLoaded) return@LaunchedEffect
                when (framing) {
                    WalkRouteCameraFraming.None -> Unit
                    is WalkRouteCameraFraming.Center -> cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(framing.latitude, framing.longitude),
                            16f
                        )
                    )
                    is WalkRouteCameraFraming.Bounds -> {
                        if (mapSize.width <= 192 || mapSize.height <= 192) return@LaunchedEffect
                        val bounds = LatLngBounds(
                            LatLng(framing.south, framing.west),
                            LatLng(framing.north, framing.east)
                        )
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngBounds(
                                bounds,
                                mapSize.width,
                                mapSize.height,
                                96
                            )
                        )
                    }
                }
            }
            GoogleMap(
                modifier = Modifier.fillMaxSize().onSizeChanged { mapSize = it },
                cameraPositionState = cameraPositionState,
                onMapLoaded = { mapLoaded = true }
            ) {
                if (points.size > 1) {
                    Polyline(points = points.map { LatLng(it.latitude, it.longitude) })
                }
            }
        } else {
            RouteFallback(points)
        }
    }
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
        item { RouteMap(points, testTag = "walk-details-route-map") }
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
private fun RouteFallback(points: List<WalkPointEntity>) {
    Box(Modifier.fillMaxSize().background(Color(0xFFE9EEF1)), contentAlignment = Alignment.Center) {
        if (points.size < 2) {
            Text("Your route will appear here", color = Color(0xFF4F5B62))
        } else {
            Canvas(Modifier.fillMaxSize().padding(20.dp)) {
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
                val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 1.0
                points.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        color = Color(0xFF0B6E69),
                        start = Offset(
                            ((a.longitude - minLon) / lonRange * size.width).toFloat(),
                            (size.height - (a.latitude - minLat) / latRange * size.height).toFloat()
                        ),
                        end = Offset(
                            ((b.longitude - minLon) / lonRange * size.width).toFloat(),
                            (size.height - (b.latitude - minLat) / latRange * size.height).toFloat()
                        ),
                        strokeWidth = 7f
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressScreen(
    app: NutRunViewModel,
    state: NutRunUiState,
    training: TrainingViewModel
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
    val estimate = state.healthEstimate ?: return
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
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Progress", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("%.1f".format(estimate.bmi), "BMI", Modifier.weight(1f), Color(0xFFE3F3E8))
                SummaryCard("${estimate.bmrKcal}", "BMR kcal", Modifier.weight(1f), Color(0xFFDDEFFC))
                SummaryCard("${estimate.tdeeKcal}", "TDEE kcal", Modifier.weight(1f), Color(0xFFFFE7DE))
            }
        }
        item {
            Text("General guidance only, not medical advice.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        item { SectionHeading("Health Connect") }
        item {
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        when {
                            !healthConnect.available -> "Health Connect is unavailable on this device."
                            healthConnect.permissionGranted -> "Connected"
                            else -> "Share only the health records you approve."
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (healthConnect.importedSteps > 0) {
                        Text("${healthConnect.importedSteps} steps imported today")
                    }
                    healthConnect.lastSyncMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (healthConnect.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = {
                            if (healthConnect.permissionGranted) {
                                app.synchronizeHealthConnect(training.workoutHistory)
                            } else {
                                healthPermissionLauncher.launch(app.healthConnectPermissions)
                            }
                        },
                        enabled = healthConnect.available && !healthConnect.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (healthConnect.permissionGranted) "Sync Health Connect" else "Connect Health Connect")
                    }
                }
            }
        }
        item {
            SectionHeading("Training overview", Modifier.testTag("training-overview"))
        }
        item {
            val week = trainingWeek()
            val planned = week.sumOf { training.sessionsForDate(it).size }
            val completed = training.workoutHistory.count { it.performedOn in week.first()..week.last() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "$completed / $planned",
                    "workouts this week",
                    Modifier.weight(1f),
                    Color(0xFFE3F3E8)
                )
                SummaryCard(
                    "${training.weeklyVolume().roundToInt()} kg",
                    "weekly volume",
                    Modifier.weight(1f),
                    Color(0xFFDDEFFC)
                )
            }
        }
        item {
            SectionHeading("Recent training", Modifier.testTag("recent-training-heading"))
        }
        val structuredHistory = training.workoutHistory.take(10)
        val legacyHistory = recentTrainingHistory(training.history).take(10)
        if (structuredHistory.isEmpty() && legacyHistory.isEmpty()) {
            item {
                Text(
                    "Completed workouts will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (structuredHistory.isNotEmpty()) {
            items(structuredHistory, key = WorkoutRecord::id) { workout ->
                ActionCard(
                    workout.sessionName,
                    "${formatToday(workout.performedOn)} | " +
                        "${workout.completedLogicalTargets}/${workout.totalLogicalTargets} targets | " +
                        "${workout.totalVolumeKg.roundToInt()} kg volume",
                    Icons.Default.FitnessCenter,
                    onClick = { selectedWorkoutId = workout.id },
                    testTag = "recent-workout-card"
                )
            }
        } else {
            items(legacyHistory) { entry ->
                ActionCard(
                    entry,
                    "Workout completed",
                    Icons.Default.FitnessCenter,
                    onClick = { selectedLegacyWorkout = entry }
                )
            }
        }
        val records = training.personalRecords().take(8)
        if (records.isNotEmpty()) {
            item { SectionHeading("Personal records") }
            items(records, key = ExerciseRecord::exerciseId) { record ->
                val exercise = training.exerciseLibrary.firstOrNull { it.id == record.exerciseId }
                ActionCard(
                    exercise?.name ?: record.exerciseId,
                    "Best ${displayWeight(record.bestWeightKg, training.usesMetricUnits)} | " +
                        "Estimated 1RM ${displayWeight(record.estimatedOneRepMaxKg, training.usesMetricUnits)}",
                    Icons.Default.FitnessCenter
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("Weight history", Modifier.weight(1f))
                TextButton(onClick = { editWeight = true }) { Text("Add weight") }
            }
        }
        items(state.weights.take(12), key = { it.id }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(java.time.Instant.ofEpochMilli(it.recordedAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), Modifier.weight(1f))
                Text(displayWeight(it.weightKg, profile.unitSystem == UnitSystem.METRIC), fontWeight = FontWeight.SemiBold)
            }
        }
        item { SectionHeading("Walking") }
        item {
            ActionCard(
                "${state.walks.count { it.state == WalkState.FINISHED.name }} completed walks",
                "%.1f km total".format(state.walks.sumOf { it.distanceMeters } / 1_000),
                Icons.AutoMirrored.Filled.DirectionsRun
            )
        }
        if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) item { AdPlacement() }
    }
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
    var performedOn by rememberSaveable(workout.id) {
        mutableStateOf(workout.performedOn.toString())
    }
    var draftSets by remember(workout.id) { mutableStateOf(workout.sets) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val setsByExercise = draftSets.groupBy(WorkoutSetLog::exerciseId)

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
                    onClick = onCancel,
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
            OutlinedTextField(
                value = performedOn,
                onValueChange = { performedOn = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true
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
                    val date = runCatching { LocalDate.parse(performedOn) }.getOrNull()
                    when {
                        sessionName.isBlank() ->
                            validationError = "Workout name is required."
                        date == null ->
                            validationError = "Enter a valid date as YYYY-MM-DD."
                        else -> {
                            validationError = null
                            onSave(
                                workout.copy(
                                    sessionName = sessionName.trim(),
                                    performedOn = date,
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
        mutableStateOf((if (metric) profile.weightKg else profile.weightKg * KG_TO_POUNDS).let { "%.1f".format(it) })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add weight") },
        text = { OutlinedTextField(value, { value = it }, label = { Text(if (metric) "Weight (kg)" else "Weight (lb)") }) },
        confirmButton = {
            Button(onClick = {
                val entered = value.toDoubleOrNull() ?: return@Button
                onSave(profile.copy(weightKg = if (metric) entered else entered / KG_TO_POUNDS))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProfileScreen(
    app: NutRunViewModel,
    state: NutRunUiState,
    onBack: () -> Unit,
    onEditHealth: () -> Unit,
    onNotifications: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val profile = state.profile ?: return
    val billing by app.billingState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account and data?") },
            text = { Text("This permanently removes local logs. The configured backend is also responsible for deleting cloud records, routes, and MCP tokens.") },
            confirmButton = { Button(onClick = app::deleteAccount) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item {
            ActionCard(
                profile.email,
                when (state.session.entitlement()) {
                    EntitlementKind.TRIAL -> "${state.session.trialDaysRemaining()} trial days remaining"
                    EntitlementKind.SUBSCRIBER -> "Ad-free subscriber"
                    EntitlementKind.FREE_AD_SUPPORTED -> "Free plan with ads"
                },
                Icons.Default.Person
            )
        }
        item {
            OutlinedButton(onClick = onEditHealth, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, null)
                Text("Edit health details")
            }
        }
        item {
            OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.WaterDrop, null)
                Text("Notification settings")
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark theme", Modifier.weight(1f))
                    Switch(state.session.darkMode, app::setDarkMode)
                }
            }
        }
        if (!isDemoAccount(state.session.authenticatedUserId)) item {
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Ad-free subscription", fontWeight = FontWeight.Bold)
                    billing.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { activity?.let { app.purchase(it, BillingManager.MONTHLY) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = activity != null && billing.connected
                    ) { Text("Monthly ad-free") }
                    OutlinedButton(
                        onClick = { activity?.let { app.purchase(it, BillingManager.ANNUAL) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = activity != null && billing.connected
                    ) { Text("Annual ad-free") }
                    TextButton(onClick = app::restorePurchases, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore purchases")
                    }
                }
            }
        }
        if (BuildConfig.DEBUG && !isDemoAccount(state.session.authenticatedUserId)) item {
            OutlinedButton(
                onClick = { app.setSubscriberForDevelopment(state.session.entitlement() != EntitlementKind.SUBSCRIBER) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Toggle test subscription") }
        }
        item {
            OutlinedButton(onClick = app::signOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
        item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null)
                Text("Delete account")
            }
        }
    }
}

@Composable
private fun EditHealthDetailsScreen(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onBack: () -> Unit
) {
    var birthDate by rememberSaveable { mutableStateOf(profile.birthDate.toString()) }
    var sex by rememberSaveable { mutableStateOf(profile.biologicalSex) }
    var units by rememberSaveable { mutableStateOf(profile.unitSystem) }
    var height by rememberSaveable {
        mutableStateOf(
            "%.1f".format(
                if (profile.unitSystem == UnitSystem.METRIC) profile.heightCm else profile.heightCm / 2.54
            )
        )
    }
    var weight by rememberSaveable {
        mutableStateOf(
            "%.1f".format(
                if (profile.unitSystem == UnitSystem.METRIC) profile.weightKg else profile.weightKg * KG_TO_POUNDS
            )
        )
    }
    var activity by rememberSaveable { mutableStateOf(profile.activityLevel) }
    var goal by rememberSaveable { mutableStateOf(profile.goal) }
    var calorieTarget by rememberSaveable { mutableStateOf(profile.calorieTarget.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val parsedDate = runCatching { LocalDate.parse(birthDate) }.getOrNull()
    val enteredHeight = height.toDoubleOrNull()
    val enteredWeight = weight.toDoubleOrNull()
    val heightCm = enteredHeight?.let { if (units == UnitSystem.METRIC) it else it * 2.54 }
    val weightKg = enteredWeight?.let { if (units == UnitSystem.METRIC) it else it / KG_TO_POUNDS }
    val estimate = if (parsedDate != null && heightCm != null && weightKg != null) {
        runCatching {
            calculateHealthEstimate(parsedDate, sex, heightCm, weightKg, activity, goal)
        }.getOrNull()
    } else null

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { OutlinedTextField(profile.email, {}, Modifier.fillMaxWidth(), label = { Text("Email") }, readOnly = true) }
        item { OutlinedTextField(birthDate, { birthDate = it }, Modifier.fillMaxWidth(), label = { Text("Birth date (YYYY-MM-DD)") }) }
        item { ChoiceRow("Biological sex", BiologicalSex.entries, sex, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { sex = it } }
        item {
            ChoiceRow("Units", UnitSystem.entries, units, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { selected ->
                if (selected != units) {
                    val currentHeightCm = height.toDoubleOrNull()?.let {
                        if (units == UnitSystem.METRIC) it else it * 2.54
                    }
                    val currentWeightKg = weight.toDoubleOrNull()?.let {
                        if (units == UnitSystem.METRIC) it else it / KG_TO_POUNDS
                    }
                    units = selected
                    currentHeightCm?.let {
                        height = "%.1f".format(if (selected == UnitSystem.METRIC) it else it / 2.54)
                    }
                    currentWeightKg?.let {
                        weight = "%.1f".format(if (selected == UnitSystem.METRIC) it else it * KG_TO_POUNDS)
                    }
                }
            }
        }
        item { OutlinedTextField(height, { height = it }, Modifier.fillMaxWidth(), label = { Text(if (units == UnitSystem.METRIC) "Height (cm)" else "Height (inches)") }) }
        item { OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text(if (units == UnitSystem.METRIC) "Weight (kg)" else "Weight (lb)") }) }
        item { ChoiceRow("Activity", ActivityLevel.entries, activity, { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) }) { activity = it } }
        item { ChoiceRow("Goal", HealthGoal.entries, goal, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { goal = it } }
        item {
            OutlinedTextField(
                calorieTarget,
                { calorieTarget = it.filter(Char::isDigit) },
                Modifier.fillMaxWidth(),
                label = { Text("Daily calorie target") }
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
                    val target = calorieTarget.toIntOrNull()
                    if (
                        parsedDate == null || heightCm == null || weightKg == null ||
                        heightCm <= 0 || weightKg <= 0 || target == null || target <= 0
                    ) {
                        error = "Check the date, measurements, and calorie target."
                    } else {
                        onSave(
                            profile.copy(
                                birthDate = parsedDate,
                                biologicalSex = sex,
                                heightCm = heightCm,
                                weightKg = weightKg,
                                activityLevel = activity,
                                goal = goal,
                                unitSystem = units,
                                calorieTarget = target
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
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
    val parsed = seconds.toIntOrNull()
    val valid = parsed in 15..600
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
                OutlinedTextField(
                    value = seconds,
                    onValueChange = { seconds = it.filter(Char::isDigit).take(3) },
                    label = { Text("Seconds") },
                    singleLine = true
                )
                if (!valid) {
                    Text(
                        "Enter a duration from 15 to 600 seconds.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
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
                enabled = valid
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
    var date by rememberSaveable(session.id, originalDate) {
        mutableStateOf(originalDate.plusDays(1).toString())
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${session.name}") },
        text = {
            Column {
                Text("Originally ${formatToday(originalDate)}")
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it; error = null },
                    label = { Text("New date (YYYY-MM-DD)") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
                if (parsed == null) error = "Enter a valid date."
                else onMove(parsed)
            }) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatMeasurementForInput(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
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
private fun AdPlacement() {
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
