package com.avitoohband.nutrun

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NutRunRoot() }
    }
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
    app: NutRunViewModel = hiltViewModel(),
    training: PrototypeViewModel = hiltViewModel()
) {
    val state by app.state.collectAsStateWithLifecycle()
    val message by app.message.collectAsState()
    val dark = state.session.darkMode
    val colors = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()

    MaterialTheme(colorScheme = colors) {
        when {
            state.session.authenticatedEmail == null -> AuthenticationScreen(app::authenticate)
            state.profile == null -> OnboardingScreen(
                email = state.session.authenticatedEmail.orEmpty(),
                onSave = app::saveProfile
            )
            else -> MainApp(app, training, state)
        }
        message?.let {
            AlertDialog(
                onDismissRequest = app::clearMessage,
                title = { Text("NutRun") },
                text = { Text(it) },
                confirmButton = { TextButton(onClick = app::clearMessage) { Text("OK") } }
            )
        }
    }
}

@Composable
private fun AuthenticationScreen(onAuthenticate: (String, String, Boolean) -> Unit) {
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
            label = { Text("Email") },
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
private fun MainApp(app: NutRunViewModel, training: PrototypeViewModel, state: NutRunUiState) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "today"
    var accountMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (route == "profile") "Profile" else "NutRun", fontWeight = FontWeight.Bold) },
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
            if (route != "profile") {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
            composable("today") { TodayScreen(state, training) }
            composable("training") { TrainingScreen(training) }
            composable("nutrition") { NutritionScreen(app, state) }
            composable("walk") { WalkScreen(app, state) }
            composable("progress") { ProgressScreen(app, state) }
            composable("profile") { ProfileScreen(app, state) { navController.popBackStack() } }
        }
    }
}

@Composable
private fun TodayScreen(state: NutRunUiState, training: PrototypeViewModel) {
    val profile = state.profile ?: return
    var addSupplement by remember { mutableStateOf(false) }
    if (addSupplement) {
        AddSupplementDialog(
            onDismiss = { addSupplement = false },
            onAdd = { name, dose ->
                training.addSupplement(name, dose, SupplementSchedule(RecurrenceType.DAILY))
                addSupplement = false
            }
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Today", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${state.nutrition.calories} of ${profile.calorieTarget} kcal")
            LinearProgressIndicator(
                progress = { (state.nutrition.calories / profile.calorieTarget.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("${state.waterMl}", "mL water", Modifier.weight(1f), Color(0xFFDDEFFC))
                SummaryCard("${state.nutrition.proteinGrams.roundToInt()} g", "protein", Modifier.weight(1f), Color(0xFFE3F3E8))
                SummaryCard("${state.walks.firstOrNull()?.distanceMeters?.div(1_000)?.let { "%.1f".format(it) } ?: "0"} km", "last walk", Modifier.weight(1f), Color(0xFFFFE7DE))
            }
        }
        item { SectionHeading("Next training") }
        item {
            val next = training.sessions.firstOrNull()
            ActionCard(
                title = next?.name ?: "Create your first session",
                subtitle = next?.let { "${it.exercises.size} exercises" } ?: "Training is ready when you are.",
                icon = Icons.Default.FitnessCenter
            )
        }
        item { SectionHeading("Hydration") }
        item {
            ActionCard(
                "${state.waterMl} / ${state.hydrationPlan.goalMl} mL",
                if (state.waterMl >= state.hydrationPlan.goalMl) "Daily goal reached" else "Keep a steady pace through your waking window.",
                Icons.Default.WaterDrop
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("Supplements", Modifier.weight(1f))
                IconButton(onClick = { addSupplement = true }) { Icon(Icons.Default.Add, "Add supplement") }
            }
        }
        items(training.supplements.filter { it.schedule.isDueOn(LocalDate.now()) }, key = { it.id }) { supplement ->
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = supplement.completedToday,
                        onCheckedChange = { training.toggleSupplement(supplement.id, it) }
                    )
                    Column {
                        Text(supplement.name, fontWeight = FontWeight.SemiBold)
                        Text("${supplement.dose} | ${supplement.schedule.label()}", fontSize = 12.sp)
                    }
                }
            }
        }
        if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) item { AdPlacement() }
    }
}

@Composable
private fun TrainingScreen(model: PrototypeViewModel) {
    var addSession by remember { mutableStateOf(false) }
    var editSessionId by remember { mutableStateOf<String?>(null) }
    if (addSession) {
        AddTrainingSessionDialog(
            onDismiss = { addSession = false },
            onSave = { name, day ->
                model.addSession(name, day)
                addSession = false
            }
        )
    }
    editSessionId?.let { sessionId ->
        ExercisePickerDialog(
            model = model,
            sessionId = sessionId,
            onDismiss = { editSessionId = null }
        )
    }
    model.lastWorkoutSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = model::dismissWorkoutSummary,
            title = { Text("Workout saved") },
            text = { Text("${summary.completedExercises} of ${summary.totalExercises} exercises completed.") },
            confirmButton = { TextButton(onClick = model::dismissWorkoutSummary) { Text("Done") } }
        )
    }
    model.activeSession()?.let { session ->
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text(session.name, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            items(session.exercises, key = { it.id }) { target ->
                Card(shape = RoundedCornerShape(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            model.completedExerciseIds[target.id] == true,
                            { model.toggleExerciseComplete(target.id, it) }
                        )
                        Column {
                            Text(target.exercise.name, fontWeight = FontWeight.SemiBold)
                            Text(target.summary(model.usesMetricUnits))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::pauseOrResumeWorkout, modifier = Modifier.weight(1f)) {
                        Icon(if (model.isWorkoutPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                        Text(if (model.isWorkoutPaused) "Resume" else "Pause")
                    }
                    Button(onClick = model::finishWorkout, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null)
                        Text("Finish")
                    }
                }
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Training", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        items(model.sessions, key = { it.id }) { session ->
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.name, fontWeight = FontWeight.Bold)
                            Text("${session.weekday.name.lowercase().replaceFirstChar(Char::uppercase)} | ${session.exercises.size} exercises")
                        }
                        IconButton(onClick = { model.startWorkout(session.id) }) {
                            Icon(Icons.Default.PlayArrow, "Start ${session.name}")
                        }
                    }
                    TextButton(onClick = {
                        model.selectSession(session.id)
                        editSessionId = session.id
                    }) {
                        Icon(Icons.Default.Edit, null)
                        Text("Exercises")
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { addSession = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("Add training session")
            }
        }
    }
}

@Composable
private fun NutritionScreen(app: NutRunViewModel, state: NutRunUiState) {
    var showFood by remember { mutableStateOf<FoodLogEntity?>(null) }
    var draftFood by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var createFood by remember { mutableStateOf(false) }
    var hydrationSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    val searchResults by app.foodSearchResults.collectAsState()
    val searchBusy by app.foodSearchBusy.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Button(onClick = { app.addWater(state.hydrationPlan.servingMl) }) {
                        Text("+${state.hydrationPlan.servingMl}")
                    }
                }
            }
        }
        MealType.entries.forEach { meal ->
            item { SectionHeading(meal.name.lowercase().replaceFirstChar(Char::uppercase)) }
            val entries = state.food.filter { it.mealType == meal.name }
            if (entries.isEmpty()) item { Text("Nothing logged", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(entries, key = { it.id }) { entry ->
                FoodLogRow(entry, { showFood = entry }, { app.duplicateFood(entry.id) }, { app.deleteFood(entry) })
            }
        }
        if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) item { AdPlacement() }
    }
}

@Composable
private fun FoodLogRow(entry: FoodLogEntity, edit: () -> Unit, duplicate: () -> Unit, delete: () -> Unit) {
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
                    DropdownMenuItem({ Text("Delete") }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
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
    var start by remember { mutableStateOf((initial.wakingStartMinute / 60).toString()) }
    var end by remember { mutableStateOf((initial.wakingEndMinute / 60).toString()) }
    var interval by remember { mutableStateOf(initial.intervalMinutes.toString()) }
    var enabled by remember { mutableStateOf(initial.remindersEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hydration settings") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(goal, { goal = it }, label = { Text("Daily goal (mL)") }) }
                item { OutlinedTextField(serving, { serving = it }, label = { Text("Quick serving (mL)") }) }
                item { OutlinedTextField(start, { start = it }, label = { Text("Wake hour (0-23)") }) }
                item { OutlinedTextField(end, { end = it }, label = { Text("Sleep hour (1-24)") }) }
                item { OutlinedTextField(interval, { interval = it }, label = { Text("Reminder interval (minutes)") }) }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Reminders", Modifier.weight(1f)); Switch(enabled, { enabled = it }) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    initial.copy(
                        goalMl = goal.toIntOrNull() ?: initial.goalMl,
                        servingMl = serving.toIntOrNull() ?: initial.servingMl,
                        wakingStartMinute = (start.toIntOrNull() ?: 8) * 60,
                        wakingEndMinute = (end.toIntOrNull() ?: 22) * 60,
                        intervalMinutes = interval.toIntOrNull() ?: 120,
                        remindersEnabled = enabled
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WalkScreen(appViewModel: NutRunViewModel, state: NutRunUiState) {
    val context = LocalContext.current
    val active = state.activeWalk
    val points by appViewModel.routePoints.collectAsStateWithLifecycle()
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

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Recorded walks", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Route tracking runs only after you press Start.")
        }
        item { RouteMap(points) }
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
                subtitle = "${walk.steps?.let { "$it steps" } ?: "Steps unavailable"} | ${walk.accumulatedDurationMillis / 60_000} min",
                icon = Icons.AutoMirrored.Filled.DirectionsRun
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
private fun RouteMap(points: List<WalkPointEntity>) {
    Card(
        Modifier.fillMaxWidth().height(240.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (BuildConfig.MAPS_CONFIGURED) {
            GoogleMap(Modifier.fillMaxSize()) {
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
private fun ProgressScreen(app: NutRunViewModel, state: NutRunUiState) {
    var editWeight by remember { mutableStateOf(false) }
    val profile = state.profile ?: return
    val estimate = state.healthEstimate ?: return
    if (editWeight) {
        WeightDialog(profile, { app.saveProfile(it); editWeight = false }, { editWeight = false })
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
private fun ProfileScreen(app: NutRunViewModel, state: NutRunUiState, onBack: () -> Unit) {
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
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark theme", Modifier.weight(1f))
                    Switch(state.session.darkMode, app::setDarkMode)
                }
            }
        }
        item {
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
        if (BuildConfig.DEBUG) item {
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
private fun AddSupplementDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add daily supplement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(dose, { dose = it }, label = { Text("Dose and unit") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name.trim(), dose.trim()) },
                enabled = name.isNotBlank() && dose.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    model: PrototypeViewModel,
    sessionId: String,
    onDismiss: () -> Unit
) {
    val session = model.sessions.firstOrNull { it.id == sessionId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exercises for ${session?.name.orEmpty()}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(model.exerciseLibrary, key = { it.id }) { exercise ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            model.selectSession(sessionId)
                            model.addExerciseToSelectedSession(exercise)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(exercise.name, fontWeight = FontWeight.SemiBold)
                            Text("${exercise.category} | ${exercise.primaryMuscles}", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SummaryCard(value: String, label: String, modifier: Modifier, color: Color) {
    Card(modifier, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF202426))
            Text(label, fontSize = 11.sp, color = Color(0xFF4F5B62))
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: ImageVector) {
    Card(shape = RoundedCornerShape(8.dp)) {
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
