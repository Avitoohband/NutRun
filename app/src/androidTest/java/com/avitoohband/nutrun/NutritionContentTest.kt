package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.SessionPreferences
import org.junit.Rule
import org.junit.Test

class NutritionContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun macroProgressAndWaterControlsAreVisible() {
        composeRule.setContent {
            NutRunTheme {
                NutritionOverviewContent(
                    state = demoNutritionState(),
                    foodSearchState = FoodSearchUiState.Idle,
                    pendingDeletion = null,
                    onSearchFood = {},
                    onClearFoodSearch = {},
                    onSaveFood = { _, _, _ -> },
                    onDuplicateFood = {},
                    onLogRecentFood = {},
                    onSaveFavoriteFood = {},
                    onSaveMealTemplate = { _, _ -> },
                    onLogFoodTemplate = {},
                    onRequestFoodDeletion = {},
                    onRequestTemplateDeletion = {},
                    onUndoNutritionDeletion = {},
                    onAddWater = {},
                    onSetQuickServingAndAddWater = {},
                    onHydrationSettings = {},
                    onWaterAmounts = {},
                    onCreateFood = {},
                    onEditFood = {},
                    onDraftFood = {},
                    onSaveMeal = {}
                )
            }
        }

        composeRule.onNodeWithTag("nutrition-macro-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("water-section").assertIsDisplayed()
        composeRule.onNodeWithTag("nutrition-quick-add-water").assertIsDisplayed()
        composeRule.onNodeWithTag("nutrition-choose-water-amount").assertIsDisplayed()
    }

    @Test
    fun quickAddToggleCollapsesSection() {
        composeRule.setContent {
            NutRunTheme {
                NutritionOverviewContent(
                    state = demoNutritionState(
                        templates = listOf(
                            com.avitoohband.nutrun.data.FoodTemplateEntity(
                                id = "template-1",
                                userId = "demo",
                                name = "Breakfast meal",
                                kind = "MEAL",
                                payloadJson = "[]",
                                createdAtMillis = 0L,
                                lastUsedAtMillis = 0L,
                                useCount = 1
                            )
                        )
                    ),
                    foodSearchState = FoodSearchUiState.Idle,
                    pendingDeletion = null,
                    onSearchFood = {},
                    onClearFoodSearch = {},
                    onSaveFood = { _, _, _ -> },
                    onDuplicateFood = {},
                    onLogRecentFood = {},
                    onSaveFavoriteFood = {},
                    onSaveMealTemplate = { _, _ -> },
                    onLogFoodTemplate = {},
                    onRequestFoodDeletion = {},
                    onRequestTemplateDeletion = {},
                    onUndoNutritionDeletion = {},
                    onAddWater = {},
                    onSetQuickServingAndAddWater = {},
                    onHydrationSettings = {},
                    onWaterAmounts = {},
                    onCreateFood = {},
                    onEditFood = {},
                    onDraftFood = {},
                    onSaveMeal = {}
                )
            }
        }

        composeRule.onNodeWithTag("nutrition-quick-add-toggle").performClick()
    }

    private fun demoNutritionState(
        templates: List<com.avitoohband.nutrun.data.FoodTemplateEntity> = emptyList()
    ): NutRunUiState = NutRunUiState(
        session = SessionPreferences(authenticatedUserId = "demo"),
        sessionResolved = true,
        profile = com.avitoohband.nutrun.domain.UserProfile(
            email = "demo@nutrun.local",
            birthDate = java.time.LocalDate.of(1990, 1, 1),
            biologicalSex = com.avitoohband.nutrun.domain.BiologicalSex.MALE,
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = com.avitoohband.nutrun.domain.ActivityLevel.MODERATE,
            goal = com.avitoohband.nutrun.domain.HealthGoal.MAINTAIN,
            unitSystem = com.avitoohband.nutrun.domain.UnitSystem.METRIC,
            calorieTarget = 2_000
        ),
        hydrationPlan = HydrationPlanEntity(
            id = "hydration:demo",
            userId = "demo",
            goalMl = 2_000,
            servingMl = 250
        ),
        foodTemplates = templates,
        nutritionTargets = recommendedNutritionTargets(2_000)
    )
}
