package com.avitoohband.nutrun

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.ui.graphics.vector.ImageVector
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.domain.UserProfile

const val CURRENT_TUTORIAL_VERSION = 1

data class TutorialPage(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String,
    val icon: ImageVector
)

val tutorialPages: List<TutorialPage> = listOf(
    TutorialPage(
        id = "today",
        title = "Today",
        summary = "Your daily command center",
        detail = "See training, nutrition, water, supplements, and quick actions for the day. Tap any metric to jump straight into the related screen.",
        icon = Icons.Default.Home
    ),
    TutorialPage(
        id = "training",
        title = "Training",
        summary = "Plan workouts and log sets",
        detail = "Assign workouts across the week, edit your library, and use focused set logging with rest timers during active workouts.",
        icon = Icons.Default.FitnessCenter
    ),
    TutorialPage(
        id = "nutrition",
        title = "Nutrition",
        summary = "Track food, macros, and water",
        detail = "Search and log meals, monitor macro progress, add water quickly, and reuse favorite foods and meal templates.",
        icon = Icons.Default.LocalDining
    ),
    TutorialPage(
        id = "walk",
        title = "Walk",
        summary = "Record routes and review history",
        detail = "Start a walk to capture distance, steps, and route points. Pause, finish, or discard safely. Finished walks stay in History.",
        icon = Icons.AutoMirrored.Filled.DirectionsRun
    ),
    TutorialPage(
        id = "progress",
        title = "Progress",
        summary = "Review trends and drill down",
        detail = "Track weight, training volume, walk activity, and nutrition trends. Open charts and recent workouts for deeper detail.",
        icon = Icons.AutoMirrored.Filled.ShowChart
    )
)

fun shouldShowTutorialWelcomePrompt(
    profile: UserProfile?,
    session: SessionPreferences
): Boolean {
    val userId = session.authenticatedUserId
    if (profile == null || userId == null) return false
    if (isDemoAccount(userId)) return false
    return session.tutorialAcknowledgedVersion < CURRENT_TUTORIAL_VERSION
}
