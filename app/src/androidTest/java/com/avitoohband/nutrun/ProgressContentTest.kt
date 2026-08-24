package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.SessionPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProgressContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rangeSelectorUpdatesSelection() {
        composeRule.setContent {
            NutRunTheme {
                ProgressOverviewContent(
                    state = demoState(),
                    training = demoTraining(),
                    healthConnect = HealthConnectUiState(),
                    onWorkoutClick = {},
                    onLegacyWorkoutClick = {},
                    onAddWeight = {},
                    onNavigateToTraining = {},
                    onNavigateToWalk = {},
                    onNavigateToNutrition = {},
                    onConnectHealthConnect = {},
                    onSyncHealthConnect = {}
                )
            }
        }

        composeRule.onNodeWithTag("progress-range-30d").assertIsSelected()
        composeRule.onNodeWithTag("progress-range-7d").performClick()
        composeRule.onNodeWithTag("progress-range-7d").assertIsSelected()
    }

    @Test
    fun weightChartShowsAccessibleSummary() {
        composeRule.setContent {
            NutRunTheme {
                ProgressOverviewContent(
                    state = demoState(weights = listOf(weight(82.0))),
                    training = demoTraining(),
                    healthConnect = HealthConnectUiState(),
                    onWorkoutClick = {},
                    onLegacyWorkoutClick = {},
                    onAddWeight = {},
                    onNavigateToTraining = {},
                    onNavigateToWalk = {},
                    onNavigateToNutrition = {},
                    onConnectHealthConnect = {},
                    onSyncHealthConnect = {}
                )
            }
        }

        composeRule
            .onNode(hasContentDescription("Weight", substring = true))
            .assertExists()
    }

    @Test
    fun emptyTrainingStateNavigatesToTraining() {
        var trainingClicks = 0
        composeRule.setContent {
            NutRunTheme {
                ProgressOverviewContent(
                    state = demoState(),
                    training = TrainingViewModel(null, null),
                    healthConnect = HealthConnectUiState(),
                    onWorkoutClick = {},
                    onLegacyWorkoutClick = {},
                    onAddWeight = {},
                    onNavigateToTraining = { trainingClicks += 1 },
                    onNavigateToWalk = {},
                    onNavigateToNutrition = {},
                    onConnectHealthConnect = {},
                    onSyncHealthConnect = {}
                )
            }
        }

        composeRule
            .onNode(hasParent(hasTestTag("progress-chart-workouts-empty")) and hasText("Open Training"))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, trainingClicks)
        }
    }

    @Test
    fun exerciseDrillDownOpensDetailCharts() {
        val training = demoTraining()
        val session = training.sessions.first { it.id == "session-monday-push-biceps" }
        training.startWorkout(session.id)
        val target = session.exercises.first()
        val set = training.activeSetLogs.getValue(target.id).first()
        training.updateWorkoutSet(
            targetId = target.id,
            setNumber = set.setNumber,
            reps = set.reps ?: 10,
            weightKg = set.weightKg ?: 40.0,
            durationSeconds = set.durationSeconds,
            rpe = set.rpe,
            completed = true
        )
        training.finishWorkout()

        val exerciseId = training.workoutHistory.first().sets.first { it.completed }.exerciseId

        composeRule.setContent {
            NutRunTheme {
                ProgressOverviewContent(
                    state = demoState(),
                    training = training,
                    healthConnect = HealthConnectUiState(),
                    onWorkoutClick = {},
                    onLegacyWorkoutClick = {},
                    onAddWeight = {},
                    onNavigateToTraining = {},
                    onNavigateToWalk = {},
                    onNavigateToNutrition = {},
                    onConnectHealthConnect = {},
                    onSyncHealthConnect = {}
                )
            }
        }

        composeRule.onNodeWithTag("exercise-progress-$exerciseId").performScrollTo().performClick()
        composeRule.onNodeWithTag("exercise-progress-detail").assertIsDisplayed()
        composeRule.onNodeWithTag("exercise-progress-title").assertIsDisplayed()
    }

    private fun demoTraining(): TrainingViewModel = TrainingViewModel(null, null)

    private fun demoState(
        weights: List<com.avitoohband.nutrun.data.WeightEntryEntity> = emptyList()
    ): NutRunUiState {
        val profile = defaultDemoProfile()
        return NutRunUiState(
            session = SessionPreferences(
                authenticatedUserId = DEMO_USER_ID,
                authenticatedEmail = DEMO_EMAIL,
                trialStartedAtMillis = System.currentTimeMillis()
            ),
            sessionResolved = true,
            profile = profile,
            weights = weights,
            hydrationPlan = HydrationPlanEntity(
                userId = DEMO_USER_ID,
                goalMl = 2_000,
                servingMl = 250
            )
        )
    }

    private fun weight(weightKg: Double) = com.avitoohband.nutrun.data.WeightEntryEntity(
        id = "weight-1",
        userId = DEMO_USER_ID,
        weightKg = weightKg,
        recordedAtMillis = System.currentTimeMillis()
    )
}
