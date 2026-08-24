package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TutorialContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tutorialShowsFiveStepsWithNavigationControls() {
        var completed = false
        composeRule.setContent {
            NutRunTheme {
                TutorialOverviewContent(
                    accountId = "account-1",
                    onComplete = { completed = true },
                    onSkip = {},
                    onBackFromTutorial = {}
                )
            }
        }

        composeRule.onNodeWithText("Step 1 of 5").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial-next").performClick()
        composeRule.onNodeWithText("Step 2 of 5").assertIsDisplayed()
        composeRule.onNodeWithText("Training").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial-next").performClick()
        composeRule.onNodeWithText("Nutrition").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial-next").performClick()
        composeRule.onNodeWithText("Walk").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial-next").performClick()
        composeRule.onNodeWithText("Progress").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial-done").performClick()
        assertTrue(completed)
    }

    @Test
    fun tutorialBackReturnsToPreviousStep() {
        composeRule.setContent {
            NutRunTheme {
                TutorialOverviewContent(
                    accountId = "account-2",
                    onComplete = {},
                    onSkip = {},
                    onBackFromTutorial = {}
                )
            }
        }

        composeRule.onNodeWithTag("tutorial-next").performClick()
        composeRule.onNodeWithText("Training").assertIsDisplayed()
        composeRule.onNodeWithTag("tutorial-back").performClick()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun welcomeDialogStartInvokesCallback() {
        var started = false
        composeRule.setContent {
            NutRunTheme {
                TutorialWelcomeDialog(
                    onStart = { started = true },
                    onSkip = {}
                )
            }
        }

        composeRule.onNodeWithTag("tutorial-welcome-start").performClick()
        assertTrue(started)
    }

    @Test
    fun welcomeDialogSkipInvokesCallback() {
        var skipped = false
        composeRule.setContent {
            NutRunTheme {
                TutorialWelcomeDialog(
                    onStart = {},
                    onSkip = { skipped = true }
                )
            }
        }
        composeRule.onNodeWithTag("tutorial-welcome-skip").performClick()
        assertTrue(skipped)
    }
}
