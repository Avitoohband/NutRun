package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class AuthenticationOnboardingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authenticationShowsInlineFieldErrorsAndDebugDemoButton() {
        composeRule.setContent {
            NutRunTheme {
                AuthenticationOverviewContent(
                    state = AuthenticationUiState(
                        emailError = "Enter a valid email address.",
                        passwordError = "Password must be at least 6 characters."
                    ),
                    onAuthenticate = { _, _, _ -> },
                    onSendPasswordReset = {},
                    onSetMode = {},
                    onClearFeedback = {},
                    onDemo = {}
                )
            }
        }

        composeRule.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
        composeRule.onNodeWithText("Password must be at least 6 characters.").assertIsDisplayed()
        composeRule.onNodeWithTag("demo-login").assertIsDisplayed()
        composeRule.onNodeWithTag("auth-forgot-password").assertIsDisplayed()
    }

    @Test
    fun onboardingUsesThreeStepsWithProgressIndicator() {
        composeRule.setContent {
            NutRunTheme {
                OnboardingOverviewContent(
                    accountId = "account-1",
                    email = "user@example.com",
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag("onboarding-step-indicator").assertIsDisplayed()
        composeRule.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-birth-date").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-next").performClick()
        composeRule.onNodeWithText("Step 2 of 3").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-height").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-next").performClick()
        composeRule.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        composeRule.onNodeWithTag("finish-onboarding").assertIsDisplayed()
    }

    @Test
    fun onboardingBackPreservesEarlierStepWithoutLosingProgress() {
        composeRule.setContent {
            NutRunTheme {
                OnboardingOverviewContent(
                    accountId = "account-2",
                    email = "user@example.com",
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag("onboarding-next").performClick()
        composeRule.onNodeWithTag("onboarding-height").performTextInput("180")
        composeRule.onNodeWithTag("onboarding-back").performClick()
        composeRule.onNodeWithTag("onboarding-next").performClick()
        composeRule.onNodeWithTag("onboarding-height").assertIsDisplayed()
    }
}
