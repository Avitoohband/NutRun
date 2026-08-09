package com.avitoohband.nutrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HydrationGoalTrophyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trophyShowsGoalAndCanBeDismissed() {
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                HydrationGoalTrophyDialog(
                    celebration = HydrationGoalCelebration(
                        goalMl = 2_500,
                        totalMl = 2_600
                    ),
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("hydration-goal-trophy").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hydration trophy").assertIsDisplayed()
        composeRule.onNodeWithText("You reached your 2,500 mL water goal today. Great work!")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Nice!").performClick()

        assertTrue(dismissed)
    }
}
