package com.avitoohband.nutrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.SessionPreferences
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayDashboardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun proteinMetricOpensNutritionWithoutWaterFocus() {
        var nutritionClicks = 0
        var waterClicks = 0
        composeRule.setContent {
            MaterialTheme {
                TodayScreen(
                    state = demoTodayState(),
                    training = TrainingViewModel(null, null),
                    onTrainingClick = {},
                    onNutritionClick = { nutritionClicks += 1 },
                    onWaterClick = { waterClicks += 1 },
                    onWalkClick = {},
                    onFoodClick = {},
                    onWorkoutClick = {},
                    onQuickAddWater = {},
                    onLogWaterAmount = {},
                    onManageSupplements = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open Nutrition, 0 g protein")
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, nutritionClicks)
            assertEquals(0, waterClicks)
        }
    }

    @Test
    fun waterMetricOpensNutritionWaterSection() {
        var waterClicks = 0
        composeRule.setContent {
            MaterialTheme {
                TodayScreen(
                    state = demoTodayState(),
                    training = TrainingViewModel(null, null),
                    onTrainingClick = {},
                    onNutritionClick = {},
                    onWaterClick = { waterClicks += 1 },
                    onWalkClick = {},
                    onFoodClick = {},
                    onWorkoutClick = {},
                    onQuickAddWater = {},
                    onLogWaterAmount = {},
                    onManageSupplements = {}
                )
            }
        }

        composeRule.onNodeWithTag("today-metric-water").performClick()
        composeRule.runOnIdle {
            assertEquals(1, waterClicks)
        }
    }

    @Test
    fun walkMetricOpensWalkWithAccessibleLabelWhenNoHistory() {
        var walkClicks = 0
        composeRule.setContent {
            MaterialTheme {
                TodayScreen(
                    state = demoTodayState(),
                    training = TrainingViewModel(null, null),
                    onTrainingClick = {},
                    onNutritionClick = {},
                    onWaterClick = {},
                    onWalkClick = { walkClicks += 1 },
                    onFoodClick = {},
                    onWorkoutClick = {},
                    onQuickAddWater = {},
                    onLogWaterAmount = {},
                    onManageSupplements = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open Walk, — No completed walks")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, walkClicks)
        }
    }

    @Test
    fun quickActionsTriggerFoodAndWaterHandlers() {
        var foodClicks = 0
        var quickWaterClicks = 0
        var customWaterMl: Int? = null
        composeRule.setContent {
            MaterialTheme {
                TodayScreen(
                    state = demoTodayState(),
                    training = TrainingViewModel(null, null),
                    onTrainingClick = {},
                    onNutritionClick = {},
                    onWaterClick = {},
                    onWalkClick = {},
                    onFoodClick = { foodClicks += 1 },
                    onWorkoutClick = {},
                    onQuickAddWater = { quickWaterClicks += 1 },
                    onLogWaterAmount = { customWaterMl = it },
                    onManageSupplements = {}
                )
            }
        }

        composeRule.onNodeWithTag("today-quick-add-food").performClick()
        composeRule.onNodeWithTag("today-quick-add-water").performClick()
        composeRule.runOnIdle {
            assertEquals(1, foodClicks)
            assertEquals(1, quickWaterClicks)
        }
    }

    @Test
    fun configuredSupplementsWithNoneDueShowsEmptyState() {
        val training = TrainingViewModel(null, null)
        training.supplements.clear()
        training.addSupplement(
            name = "Vitamin D",
            dose = "1 capsule",
            schedule = SupplementSchedule(
                type = RecurrenceType.WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY)
            ),
            reminderEnabled = false,
            reminderMinute = 8 * 60
        )
        composeRule.setContent {
            MaterialTheme {
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

        composeRule.onNodeWithTag("today-supplements-none-due").assertIsDisplayed()
        composeRule.onNodeWithText("Manage supplements").assertIsDisplayed()
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
