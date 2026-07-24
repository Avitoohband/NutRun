package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
                hasTestTag("start-session-session-sunday-cardio")
            )
            composeRule
                .onNodeWithTag("start-session-session-sunday-cardio")
                .performClick()
        }
        composeRule.onNodeWithText("Choose one cardio option.").assertIsDisplayed()
        composeRule.onAllNodesWithText("RPE", useUnmergedTree = true)[0].assertIsDisplayed()
        composeRule.onNodeWithText("Pause").assertDoesNotExist()
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithText("Done").performClick()
    }
}
