package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.SessionPreferences
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccessibilityResponsiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactTodayMetricsStackVerticallyAt320Dp() {
        composeRule.setContent {
            NutRunTheme {
                Box(Modifier.width(320.dp)) {
                    TodayScreen(
                        state = demoTodayState(),
                        training = TrainingViewModel(null, null),
                        onTrainingClick = {},
                        onNutritionClick = {},
                        onWaterClick = {},
                        onWalkClick = {},
                        onFoodClick = {},
                        onWorkoutClick = {},
                        onQuickAddWater = {},
                        onLogWaterAmount = {},
                        onManageSupplements = {}
                    )
                }
            }
        }

        val waterY = composeRule
            .onNodeWithTag("today-metric-water")
            .fetchSemanticsNode()
            .positionInRoot
            .y
        val proteinY = composeRule
            .onNodeWithTag("today-metric-protein")
            .fetchSemanticsNode()
            .positionInRoot
            .y
        assertTrue(proteinY > waterY)
    }

    @Test
    fun compactTodayMetricsReadableAtLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                NutRunTheme {
                    Box(Modifier.width(320.dp).height(900.dp)) {
                        TodayScreen(
                            state = demoTodayState(),
                            training = TrainingViewModel(null, null),
                            onTrainingClick = {},
                            onNutritionClick = {},
                            onWaterClick = {},
                            onWalkClick = {},
                            onFoodClick = {},
                            onWorkoutClick = {},
                            onQuickAddWater = {},
                            onLogWaterAmount = {},
                            onManageSupplements = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("today-metric-water").assertIsDisplayed()
        composeRule.onNodeWithTag("today-quick-add-water").assertIsDisplayed()
        composeRule.onNodeWithTag("today-quick-add-food").assertIsDisplayed()
    }

    @Test
    fun supplementReminderSwitchExposesOnOffState() {
        composeRule.setContent {
            NutRunTheme {
                SupplementReminderSettingsCard(
                    masterEnabled = true,
                    onMasterEnabledChange = {},
                    supplements = emptyList(),
                    drafts = emptyMap(),
                    onDraftsChange = {},
                    onPermissionRequest = {},
                    onManageSupplements = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("supplement-reminders-master")
            .assertIsOn()
    }

    @Test
    fun workoutLibraryStartDisabledExplainsWhy() {
        val model = TrainingViewModel(null, null)
        val emptyTemplate = WorkoutTemplate.userCreated(
            name = "Empty workout",
            exercises = emptyList()
        )
        model.workoutTemplates.add(emptyTemplate)

        composeRule.setContent {
            NutRunTheme {
                TrainingPlanningContent(
                    model = model,
                    mode = TrainingPlanningMode.WORKOUTS,
                    onOpenTemplate = {},
                    onEditTemplate = {},
                    onDuplicateTemplate = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("training-list")
            .performScrollToNode(hasTestTag("start-session-${emptyTemplate.id}"))

        composeRule
            .onNodeWithTag("start-session-${emptyTemplate.id}")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun workoutCardClickableAreaHasAccessibleLabel() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first { it.exercises.isNotEmpty() }

        composeRule.setContent {
            NutRunTheme {
                TrainingPlanningContent(
                    model = model,
                    mode = TrainingPlanningMode.WORKOUTS,
                    onOpenTemplate = {},
                    onEditTemplate = {},
                    onDuplicateTemplate = {}
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Open ${template.name} workout details")
            .assertIsDisplayed()
    }

    @Test
    fun activeWorkoutSetCompletionRowHasStateSemantics() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        val target = session.exercises.first()
        val set = model.activeSetLogs.getValue(target.id).first()

        composeRule.setContent {
            NutRunTheme {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    ActiveWorkoutContent(
                        model = model,
                        onEditRestTimer = {},
                        onCancelRequest = {},
                        onFinishRequest = {}
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Mark set ${set.setNumber} complete")
            .assertIsDisplayed()
    }

    @Test
    fun configuredSupplementCompletedStateIsAnnounced() {
        val training = TrainingViewModel(null, null)
        training.supplements.clear()
        training.addSupplement(
            name = "Vitamin D",
            dose = "1 capsule",
            schedule = SupplementSchedule(
                type = RecurrenceType.DAILY,
                weekdays = emptySet()
            ),
            reminderEnabled = false,
            reminderMinute = 8 * 60
        )
        val supplement = training.supplements.first()
        training.toggleSupplement(supplement.id, true)

        composeRule.setContent {
            NutRunTheme {
                TodayScreen(
                    state = demoTodayState(),
                    training = training,
                    onTrainingClick = {},
                    onNutritionClick = {},
                    onWaterClick = {},
                    onWalkClick = {},
                    onFoodClick = {},
                    onWorkoutClick = {},
                    onQuickAddWater = {},
                    onLogWaterAmount = {},
                    onManageSupplements = {}
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Vitamin D, completed")
            .assertIsDisplayed()
    }

    private fun demoTodayState(): NutRunUiState {
        val profile = defaultDemoProfile()
        return NutRunUiState(
            session = SessionPreferences(
                authenticatedUserId = DEMO_USER_ID,
                authenticatedEmail = DEMO_EMAIL,
                trialStartedAtMillis = System.currentTimeMillis()
            ),
            sessionResolved = true,
            profile = profile,
            hydrationPlan = HydrationPlanEntity(
                userId = DEMO_USER_ID,
                goalMl = 2_000,
                servingMl = 250
            )
        )
    }
}
