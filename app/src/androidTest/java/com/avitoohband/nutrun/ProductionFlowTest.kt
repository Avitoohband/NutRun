package com.avitoohband.nutrun

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Before
import org.junit.Test

class ProductionFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun ensureSignedOut() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("Sign in").fetchSemanticsNodes().isEmpty()) {
            val profileButtons = composeRule
                .onAllNodesWithContentDescription("Profile")
                .fetchSemanticsNodes()
            if (profileButtons.isNotEmpty()) {
                composeRule.onAllNodesWithContentDescription("Profile")[0].performClick()
                composeRule.onNodeWithText("Sign out").performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
    }

    @Test
    fun unauthenticatedUserSeesAccountGate() {
        composeRule.onNodeWithText("NutRun").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
    }

    @Test
    fun invalidCredentialsCannotBypassRequiredSetup() {
        composeRule.onNodeWithText("Email or demo username").performTextInput("not-an-email")
        composeRule.onNodeWithText("Password").performTextInput("123")
        composeRule.onNodeWithText("Sign in").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("Enter a valid email and a password with at least 6 characters.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Set up your profile").assertDoesNotExist()
    }

    @Test
    fun demoShowsWeeklyScheduleAndSetLogger() {
        composeRule.onNodeWithTag("demo-login").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today-training-card").performClick()
        if (
            composeRule.onAllNodesWithText("Choose one cardio option.")
                .fetchSemanticsNodes()
                .isEmpty()
        ) {
            composeRule.onNodeWithText("This week").assertIsDisplayed()
            composeRule.onNodeWithTag("training-list").performScrollToNode(
                hasTestTag("session-card-session-sunday-cardio")
            )
            composeRule
                .onNodeWithTag("session-card-session-sunday-cardio")
                .performClick()
        }
        composeRule.onNodeWithText("Choose one cardio option.").assertIsDisplayed()
        composeRule.onAllNodesWithText("RPE", useUnmergedTree = true)[0].assertIsDisplayed()
        composeRule.onNodeWithText("Pause").assertDoesNotExist()
        composeRule.onNodeWithTag("cancel-workout").assertIsDisplayed()
        composeRule.onNodeWithTag("weight-unit-lb").performClick().assertIsSelected()
        composeRule.onNodeWithTag("weight-unit-kg").performClick().assertIsSelected()
        composeRule.onNodeWithTag("rest-timer-settings").performClick()
        composeRule.onNodeWithText("Default rest timer").assertIsDisplayed()
        composeRule.onNodeWithText("120 sec").performClick()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag("bottom-nav-progress").performClick()
        composeRule.onNodeWithTag("recent-training-heading").performScrollTo()
        composeRule.onAllNodesWithTag("recent-workout-card")[0]
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("workout-details-heading").assertIsDisplayed()
        composeRule.onNodeWithText("Exercises").assertIsDisplayed()
        composeRule.onNodeWithText("Brisk walk").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-workout-history").performClick()
        composeRule.onNodeWithTag("edit-workout-heading").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-workout-list")
            .performScrollToNode(hasText("Weight (kg)"))
        composeRule.onAllNodesWithText("Weight (kg)", useUnmergedTree = true)[0]
            .assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-edit-workout").performClick()
        composeRule.onNodeWithTag("delete-workout-history").performClick()
        composeRule.onNodeWithText("Delete workout?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun completedWalkOpensWalkHistoryDetails() {
        enterDemo()
        grantWalkPermissions()
        composeRule.onNodeWithTag("bottom-nav-walk").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Start walk").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Resume").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("Start walk").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Start walk").performClick()
        } else {
            composeRule.onNodeWithText("Resume").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Finish").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Start walk").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithTag("walk-history-card")[0].performClick()
        composeRule.onNodeWithTag("walk-details-heading").assertIsDisplayed()
        composeRule.onNodeWithText("Average pace").assertIsDisplayed()
    }

    @Test
    fun consumedNutritionRequestDoesNotReopenAfterRecreation() {
        enterDemo()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.intent
                .putExtra(MainActivity.EXTRA_DESTINATION, "nutrition")
                .putExtra(MainActivity.EXTRA_WATER_SECTION, true)
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Nutrition").fetchSemanticsNodes().size >= 2
        }

        composeRule.onNodeWithTag("bottom-nav-today").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Today's training").assertIsDisplayed()
    }

    @Test
    fun todayOpensAllSupplementManagement() {
        enterDemo()
        composeRule.onNodeWithTag("manage-supplements").performScrollTo().performClick()

        composeRule.onNodeWithText("Manage supplements").assertIsDisplayed()
        composeRule.onNodeWithText("All supplements and their scheduled days").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-supplement-supplement-1").performClick()
        composeRule.onNodeWithText("Edit supplement").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("add-managed-supplement").performClick()
        composeRule.onNodeWithText("Take on").assertIsDisplayed()
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "All days")
            .forEach { label ->
                composeRule.onNodeWithText(label).assertIsDisplayed()
            }
        composeRule.onNodeWithText("All days").performClick()
    }

    private fun enterDemo() {
        composeRule.onNodeWithTag("demo-login").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun grantWalkPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION)
            .forEach { permission ->
                instrumentation.uiAutomation.executeShellCommand("pm grant $packageName $permission").close()
            }
    }
}
