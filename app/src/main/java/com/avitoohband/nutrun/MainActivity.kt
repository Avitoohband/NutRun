package com.avitoohband.nutrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek

private val lavender = Color(0xFF7758E8)
private val mint = Color(0xFF3ABF9A)
private val coral = Color(0xFFF47F73)
private val sky = Color(0xFF4D9DE0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NutRunApp() }
    }
}

@Composable
fun NutRunApp(prototype: PrototypeViewModel = viewModel()) {
    var darkMode by rememberSaveable { mutableStateOf(false) }
    var showNotificationPrompt by rememberSaveable { mutableStateOf(false) }
    val colors = if (darkMode) {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFB9A9FF), secondary = Color(0xFF8AE7CD), tertiary = Color(0xFFFFB3AA)
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = lavender, secondary = mint, tertiary = coral, surface = Color(0xFFFAF9FF)
        )
    }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            if (prototype.isAuthenticated) {
                MainPrototype(prototype, darkMode, { darkMode = !darkMode })
            } else {
                RegistrationScreen(onRegister = {
                    prototype.completeRegistration()
                    showNotificationPrompt = true
                })
            }
            if (showNotificationPrompt && prototype.isAuthenticated) {
                NotificationPermissionDialog(
                    onDecision = { granted ->
                        prototype.setNotificationPermission(granted)
                        showNotificationPrompt = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RegistrationScreen(onRegister: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Welcome to NutRun", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Build a plan, track your training, and keep your momentum.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Continue with email") }
        Spacer(Modifier.height(20.dp))
        Text("Start a 30-day ad-free trial. No payment details needed.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotificationPermissionDialog(onDecision: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text("Workout reminders") },
        text = { Text("Allow optional workout and supplement reminders? You can change this later in Profile.") },
        confirmButton = { Button(onClick = { onDecision(true) }) { Text("Allow") } },
        dismissButton = { TextButton(onClick = { onDecision(false) }) { Text("Not now") } }
    )
}

@Composable
private fun MainPrototype(prototype: PrototypeViewModel, darkMode: Boolean, toggleTheme: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var showAddSession by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { AppBar(darkMode, toggleTheme) },
        bottomBar = { BottomNavigation(tab) { tab = it } },
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(onClick = { showAddSession = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Create program", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> TodayScreen(prototype, onStartWorkout = { id -> prototype.startWorkout(id); tab = 1 })
                1 -> ProgramScreen(prototype, showAddSession, { showAddSession = true }, { showAddSession = false }, onEditExercises = { id -> prototype.selectSession(id); tab = 2 })
                2 -> ExercisesScreen(prototype)
                3 -> ProgressScreen(prototype)
                4 -> ProfileScreen(prototype, darkMode, toggleTheme)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(darkMode: Boolean, onTheme: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp)); Text("NutRun", fontWeight = FontWeight.Bold)
        } },
        actions = { IconButton(onClick = onTheme) { Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme") } }
    )
}

@Composable
private fun BottomNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val destinations = listOf(
        "Today" to Icons.Default.Home, "Program" to Icons.Default.CalendarMonth,
        "Exercises" to Icons.Default.MenuBook, "Progress" to Icons.Default.ShowChart,
        "Profile" to Icons.Default.Person
    )
    NavigationBar { destinations.forEachIndexed { index, item ->
        NavigationBarItem(selected == index, { onSelect(index) }, { Icon(item.second, item.first) }, label = { Text(item.first) })
    } }
}

@Composable
private fun TodayScreen(prototype: PrototypeViewModel, onStartWorkout: (String) -> Unit) {
    var showAddSupplement by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    val todaySession = prototype.sessions.firstOrNull { it.weekday == DayOfWeek.WEDNESDAY } ?: prototype.sessions.firstOrNull()
    if (showAddSupplement) AddSupplementDialog(onDismiss = { showAddSupplement = false }, onAdd = { name, dose, schedule ->
        prototype.addSupplement(name, dose, schedule); showAddSupplement = false
    })
    if (showReminder) ReminderDialog(onDismiss = { showReminder = false }, session = prototype.sessions.firstOrNull { it.weekday == DayOfWeek.MONDAY })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("Good evening, Avi", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("You are building momentum.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { todaySession?.let { TodayWorkoutCard(it, { onStartWorkout(it.id) }) } }
        item { SectionTitle("Today's supplements", "${prototype.supplements.count { it.completedToday }}/${prototype.supplements.size} complete", onAction = { showAddSupplement = true }, actionLabel = "Add") }
        items(prototype.supplements, key = { it.id }) { supplement -> SupplementRow(supplement) { prototype.toggleSupplement(supplement.id, it) } }
        item { Text("Supplement tracking is for organization only, not medical or dosage advice.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionTitle("Tomorrow", "Preparation") }
        item { ReminderCard { showReminder = true } }
        if (!prototype.trialState.isTrialActive()) item { AdPreview() }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TodayWorkoutCard(session: TrainingSession, onStart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp)) {
            Text(session.weekday.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp)); Text(session.name, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("${session.exercises.size} exercises", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(18.dp)); Button(onClick = onStart) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start workout") }
        }
    }
}

@Composable
private fun SectionTitle(title: String, caption: String, onAction: (() -> Unit)? = null, actionLabel: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        if (onAction != null && actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) } else Text(caption, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SupplementRow(item: Supplement, onChecked: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.WaterDrop, null, tint = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${item.dose} - ${item.schedule.label()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            Checkbox(item.completedToday, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun ReminderCard(onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.tertiary); Spacer(Modifier.width(12.dp))
            Column { Text("Push + Biceps tomorrow", fontWeight = FontWeight.Bold); Text("Set out your gear and get a good sleep tonight.", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ReminderDialog(onDismiss: () -> Unit, session: TrainingSession?) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tomorrow's reminder") }, text = {
        Text(session?.let { "${it.name} is scheduled for tomorrow. Open the Program tab to review ${it.exercises.size} exercises." } ?: "No workout is scheduled for tomorrow.")
    }, confirmButton = { Button(onClick = onDismiss) { Text("Got it") } })
}

@Composable
private fun AdPreview() {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MoreHoriz, null); Spacer(Modifier.width(8.dp)); Text("Free plan ad placement preview", fontSize = 12.sp) }
    }
}

@Composable
private fun ProgramScreen(prototype: PrototypeViewModel, showAddSession: Boolean, requestAddSession: () -> Unit, dismissAddSession: () -> Unit, onEditExercises: (String) -> Unit) {
    prototype.lastWorkoutSummary?.let { summary ->
        WorkoutSummaryDialog(summary) { prototype.dismissWorkoutSummary() }
    }
    prototype.activeSession()?.let { ActiveWorkoutScreen(prototype, it); return }
    if (showAddSession) AddSessionDialog(onDismiss = dismissAddSession, onCreate = { name, day -> prototype.addSession(name, day); dismissAddSession() })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("My program", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Strength and endurance", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(prototype.sessions, key = { it.id }) { session -> SessionCard(session, onStart = { prototype.startWorkout(session.id) }, onEdit = { onEditExercises(session.id) }) }
        item { OutlinedButton(onClick = requestAddSession, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add training session") } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SessionCard(session: TrainingSession, onStart: () -> Unit, onEdit: () -> Unit) {
    val color = when (session.weekday) { DayOfWeek.MONDAY -> lavender; DayOfWeek.WEDNESDAY -> mint; DayOfWeek.FRIDAY -> coral; else -> sky }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(session.weekday.name.take(3), color = color, fontWeight = FontWeight.Bold); Text("WEEKLY", fontSize = 9.sp) }
                Column(Modifier.weight(1f)) { Text(session.name, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("${session.exercises.size} exercises", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onStart) { Icon(Icons.Default.PlayArrow, "Start ${session.name}", tint = color) }
            }
            if (session.exercises.isNotEmpty()) Text(session.exercises.joinToString(" | ") { it.exercise.name }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onEdit) { Text("Edit exercises") }
        }
    }
}

@Composable
private fun ActiveWorkoutScreen(prototype: PrototypeViewModel, session: TrainingSession) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(if (prototype.isWorkoutPaused) "Workout paused" else "Active workout", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(session.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(session.exercises, key = { it.id }) { target ->
            Card(shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(prototype.completedExerciseIds[target.id] == true, { prototype.toggleExerciseComplete(target.id, it) })
                Column(Modifier.weight(1f)) { Text(target.exercise.name, fontWeight = FontWeight.SemiBold, textDecoration = if (prototype.completedExerciseIds[target.id] == true) TextDecoration.LineThrough else null); Text(target.summary(prototype.usesMetricUnits), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { prototype.pauseOrResumeWorkout() }, modifier = Modifier.weight(1f)) { Icon(if (prototype.isWorkoutPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null); Spacer(Modifier.width(5.dp)); Text(if (prototype.isWorkoutPaused) "Resume" else "Pause") }
            Button(onClick = { prototype.finishWorkout() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Finish") }
        } }
    }
}

@Composable
private fun WorkoutSummaryDialog(summary: WorkoutSummary, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Workout saved") }, text = { Text("${summary.sessionName}: ${summary.completedExercises}/${summary.totalExercises} exercises completed. Your history has been updated.") }, confirmButton = { Button(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun ExercisesScreen(prototype: PrototypeViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Exercise?>(null) }
    val results = prototype.exerciseLibrary.filter { it.name.contains(query, true) || it.category.contains(query, true) || it.primaryMuscles.contains(query, true) }
    selected?.let { ExerciseDialog(it, prototype.selectedSession(), prototype.usesMetricUnits, { sets, reps, weight, duration, distance ->
        prototype.addExerciseToSelectedSession(it, sets, reps, weight, duration, distance)
        selected = null
    }, { selected = null }) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("Exercise library", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(prototype.selectedSession()?.let { "Adding to: ${it.name}" } ?: "Select Edit exercises on a program session first.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search exercises") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
        items(results, key = { it.id }) { exercise -> ExerciseCard(exercise) { selected = exercise } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(if (exercise.category == "Endurance") Icons.Default.DirectionsRun else Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp)); Column { Text(exercise.name, fontWeight = FontWeight.Bold); Text(exercise.category, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp); Text(exercise.primaryMuscles, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ExerciseDialog(exercise: Exercise, session: TrainingSession?, metric: Boolean, onAdd: (Int, Int, Double?, Int?, Double?) -> Unit, onDismiss: () -> Unit) {
    var sets by remember { mutableStateOf(exercise.defaultSets.toString()) }
    var reps by remember { mutableStateOf(exercise.defaultReps.toString()) }
    var weight by remember { mutableStateOf(exercise.defaultWeightKg?.let { if (metric) it.toString() else (it * KG_TO_POUNDS).toString() } ?: "") }
    var duration by remember { mutableStateOf(exercise.defaultDurationMinutes?.toString() ?: "") }
    var distance by remember { mutableStateOf(exercise.defaultDistanceKm?.let { if (metric) it.toString() else (it * KM_TO_MILES).toString() } ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(exercise.name) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Primary: ${exercise.primaryMuscles}", fontWeight = FontWeight.SemiBold); Text("Secondary: ${exercise.secondaryMuscles}", fontSize = 13.sp) }
            item { Text(exercise.instructions, fontSize = 13.sp); Text(exercise.safetyNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { OutlinedTextField(sets, { sets = it }, label = { Text("Sets") }, singleLine = true) }
            item { OutlinedTextField(reps, { reps = it }, label = { Text("Reps") }, singleLine = true) }
            item { OutlinedTextField(weight, { weight = it }, label = { Text("Weight (${if (metric) "kg" else "lb"}), optional") }, singleLine = true) }
            item { OutlinedTextField(duration, { duration = it }, label = { Text("Duration (minutes), optional") }, singleLine = true) }
            item { OutlinedTextField(distance, { distance = it }, label = { Text("Distance (${if (metric) "km" else "mi"}), optional") }, singleLine = true) }
        }
    }, confirmButton = { Button(onClick = {
        val storedWeightKg = weight.toDoubleOrNull()?.let { if (metric) it else it / KG_TO_POUNDS }
        val storedDistanceKm = distance.toDoubleOrNull()?.let { if (metric) it else it / KM_TO_MILES }
        onAdd(sets.toIntOrNull() ?: 3, reps.toIntOrNull() ?: 10, storedWeightKg, duration.toIntOrNull(), storedDistanceKm)
    }, enabled = session != null) { Text("Add to session") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun ProgressScreen(prototype: PrototypeViewModel) {
    var showEdit by remember { mutableStateOf(false) }
    if (showEdit) SuggestionEditDialog(prototype.suggestedWeightKg, prototype.usesMetricUnits, { prototype.decideSuggestion(SuggestionDecision.ACCEPTED, it); showEdit = false }, { showEdit = false })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("Your progress", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Small wins add up.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("8", "workouts", Modifier.weight(1f)); MetricCard("86%", "adherence", Modifier.weight(1f)); MetricCard("3", "week streak", Modifier.weight(1f)) } }
        item { Text("Two-week check-in", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        item { SuggestionCard(prototype, onEdit = { showEdit = true }) }
        item { Text("Recent activity", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        items(prototype.history) { Text(it, Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SuggestionCard(prototype: PrototypeViewModel, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Icon(Icons.Default.ShowChart, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp)); Text("Lat pulldown suggestion", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("You completed your target twice. Consider ${displayWeight(prototype.suggestedWeightKg, prototype.usesMetricUnits)} next time.", fontSize = 14.sp)
            if (prototype.suggestionDecision == SuggestionDecision.PENDING) {
                Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { prototype.decideSuggestion(SuggestionDecision.ACCEPTED) }) { Text("Accept") }; OutlinedButton(onClick = onEdit) { Text("Edit") } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { prototype.decideSuggestion(SuggestionDecision.POSTPONED) }) { Text("Postpone") }; TextButton(onClick = { prototype.decideSuggestion(SuggestionDecision.REJECTED) }) { Text("Reject") } }
            } else Text("Decision: ${prototype.suggestionDecision.name.lowercase().replaceFirstChar(Char::uppercase)}", color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun SuggestionEditDialog(initialWeightKg: Double, metric: Boolean, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var weight by remember { mutableStateOf((if (metric) initialWeightKg else initialWeightKg * KG_TO_POUNDS).toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adjust suggestion") }, text = { OutlinedTextField(weight, { weight = it }, label = { Text("Weight in ${if (metric) "kg" else "lb"}") }, singleLine = true) }, confirmButton = { Button(onClick = {
        val entered = weight.toDoubleOrNull() ?: if (metric) initialWeightKg else initialWeightKg * KG_TO_POUNDS
        onSave(if (metric) entered else entered / KG_TO_POUNDS)
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(12.dp)) { Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun ProfileScreen(prototype: PrototypeViewModel, darkMode: Boolean, toggleTheme: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("Profile", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { Card(shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("A", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(14.dp)); Column { Text("Avi", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(if (prototype.trialState.isTrialActive()) "Ad-free trial: ${prototype.trialState.daysRemaining()} days left" else "Free plan with ads", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        item { PlanCard(prototype) }
        item { SettingsRow(if (darkMode) "Dark theme" else "Light theme", if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, toggleTheme) }
        item { NotificationRow(prototype.notificationPermissionGranted) { prototype.setNotificationPermission(it) } }
        item { SettingsRow(if (prototype.usesMetricUnits) "Units: kilograms" else "Units: pounds", Icons.Default.FitnessCenter, onClick = { prototype.toggleUnits() }) }
    }
}

@Composable
private fun PlanCard(prototype: PrototypeViewModel) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp)) {
            Text("Your plan", fontWeight = FontWeight.Bold)
            Text(if (prototype.trialState.isTrialActive()) "30-day ad-free trial" else "Free plan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(if (prototype.trialState.isTrialActive()) "${prototype.trialState.daysRemaining()} days remaining. No payment details were collected." else "Core features remain available with ad previews.", fontSize = 13.sp)
            if (prototype.trialState.isTrialActive()) TextButton(onClick = { prototype.simulateTrialExpiry() }) { Text("Simulate trial expiry") }
        }
    }
}

@Composable
private fun SettingsRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f)); Icon(Icons.Default.MoreHoriz, null) } }
}

@Composable
private fun NotificationRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text("Workout reminders", Modifier.weight(1f)); Switch(enabled, onChange) } }
}

@Composable
private fun AddSupplementDialog(onDismiss: () -> Unit, onAdd: (String, String, SupplementSchedule) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf(RecurrenceType.DAILY) }
    var interval by remember { mutableStateOf("2") }
    var weekdays by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add supplement") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) }
            item { OutlinedTextField(dose, { dose = it }, label = { Text("Dose and unit") }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RecurrenceType.entries.forEach { type -> FilterChip(recurrence == type, { recurrence = type }, label = { Text(if (type == RecurrenceType.EVERY_N_DAYS) "Every N days" else type.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } }
            if (recurrence == RecurrenceType.EVERY_N_DAYS) item { OutlinedTextField(interval, { interval = it }, label = { Text("Interval in days") }, singleLine = true) }
            if (recurrence == RecurrenceType.WEEKDAYS) item { WeekdayPicker(weekdays, multiple = true) { weekdays = it } }
        }
    }, confirmButton = { Button(onClick = {
        val schedule = SupplementSchedule(recurrence, intervalDays = interval.toIntOrNull() ?: 1, weekdays = weekdays)
        onAdd(name, dose, schedule)
    }, enabled = name.isNotBlank() && dose.isNotBlank() && (recurrence != RecurrenceType.WEEKDAYS || weekdays.isNotEmpty())) { Text("Add") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun AddSessionDialog(onDismiss: () -> Unit, onCreate: (String, DayOfWeek) -> Unit) {
    var name by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(DayOfWeek.MONDAY) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create a session") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Session name") }, singleLine = true)
            WeekdayPicker(setOf(day), multiple = false) { selected -> day = selected.first() }
        }
    }, confirmButton = { Button(onClick = { onCreate(name, day) }, enabled = name.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun WeekdayPicker(selected: Set<DayOfWeek>, multiple: Boolean, onChange: (Set<DayOfWeek>) -> Unit) {
    val days = DayOfWeek.entries
    Column {
        Text("Days", fontWeight = FontWeight.Medium)
        days.chunked(4).forEach { rowDays ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowDays.forEach { day ->
                    FilterChip(
                        selected = day in selected,
                        onClick = { onChange(if (multiple) if (day in selected) selected - day else selected + day else setOf(day)) },
                        label = { Text(day.name.take(3)) }
                    )
                }
            }
        }
    }
}
