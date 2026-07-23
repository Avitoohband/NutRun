package com.avitoohband.nutrun

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class PrototypeFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emailRegistrationOpensTheNotificationChoice() {
        composeRule.setContent { NutRunApp() }

        composeRule.onNodeWithText("Continue with email").performClick()
        composeRule.onNodeWithText("Workout reminders").assertIsDisplayed()
    }

    @Test
    fun createsTuesdayTrainingSession() {
        launchAuthenticatedApp()

        composeRule.onNodeWithText("Program").performClick()
        composeRule.onNodeWithText("Add training session").performClick()
        composeRule.onNodeWithText("Session name").performTextInput("Tuesday strength")
        composeRule.onNodeWithText("TUE").performClick()
        composeRule.onNodeWithText("Create").performClick()

        composeRule.onNodeWithText("Tuesday strength").assertIsDisplayed()
    }

    @Test
    fun trialExpirySwitchesProfileToFreePlan() {
        launchAuthenticatedApp()

        composeRule.onNodeWithText("Profile").performClick()
        composeRule.onNodeWithText("Simulate trial expiry").performClick()

        composeRule.onNodeWithText("Free plan with ads").assertIsDisplayed()
    }

    private fun launchAuthenticatedApp() {
        composeRule.setContent { NutRunApp() }
        composeRule.onNodeWithText("Continue with email").performClick()
        composeRule.onNodeWithText("Allow").performClick()
    }
}
