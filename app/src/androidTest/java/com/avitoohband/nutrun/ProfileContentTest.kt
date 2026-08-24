package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.avitoohband.nutrun.billing.BillingUiState
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.UnitSystem
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class ProfileContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileSectionsAndDeleteConfirmationRequireTypedEmail() {
        val profile = testProfile()
        composeRule.setContent {
            NutRunTheme {
                ProfileOverviewContent(
                    profile = profile,
                    entitlementLabel = "30 trial days remaining",
                    darkMode = false,
                    authenticatedUserId = "user-1",
                    billing = BillingUiState(),
                    accountDeletionState = AccountDeletionUiState(),
                    billingActionsEnabled = false,
                    onBack = {},
                    onEditHealth = {},
                    onNotifications = {},
                    onDarkModeChange = {},
                    onPurchaseMonthly = {},
                    onPurchaseAnnual = {},
                    onRestorePurchases = {},
                    onSignOut = {},
                    onDeleteAccount = {},
                    onClearAccountDeletionState = {},
                    onRunTutorial = {}
                )
            }
        }

        composeRule.onNodeWithTag("profile-section-account").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-section-health").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-section-notifications").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-section-appearance").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-section-subscription").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-section-data").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-sign-out").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-section-help").assertIsDisplayed()

        composeRule.onNodeWithTag("profile-list")
            .performScrollToNode(hasTestTag("profile-delete-account"))
        composeRule.onNodeWithTag("profile-delete-account").performClick()
        composeRule.onNodeWithTag("profile-delete-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("profile-delete-email-confirm").performTextInput(profile.email)
        composeRule.onNodeWithTag("profile-delete-confirm").assertIsDisplayed()
    }

    @Test
    fun demoProfileShowsLocalOnlyDeletionCopy() {
        composeRule.setContent {
            NutRunTheme {
                ProfileOverviewContent(
                    profile = defaultDemoProfile(),
                    entitlementLabel = "Ad-free subscriber",
                    darkMode = false,
                    authenticatedUserId = DEMO_USER_ID,
                    billing = BillingUiState(),
                    accountDeletionState = AccountDeletionUiState(),
                    billingActionsEnabled = false,
                    onBack = {},
                    onEditHealth = {},
                    onNotifications = {},
                    onDarkModeChange = {},
                    onPurchaseMonthly = {},
                    onPurchaseAnnual = {},
                    onRestorePurchases = {},
                    onSignOut = {},
                    onDeleteAccount = {},
                    onClearAccountDeletionState = {},
                    onRunTutorial = {}
                )
            }
        }

        composeRule.onNodeWithTag("profile-list")
            .performScrollToNode(hasTestTag("profile-delete-account"))
        composeRule.onNodeWithTag("profile-delete-account").performClick()
        composeRule.onNodeWithText(
            "Demo account data is removed from this device only. Cloud records are not involved."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("profile-section-subscription").assertDoesNotExist()
    }

    @Test
    fun profileHelpSectionRunsTutorialAgain() {
        var tutorialRequested = false
        composeRule.setContent {
            NutRunTheme {
                ProfileOverviewContent(
                    profile = testProfile(),
                    entitlementLabel = "30 trial days remaining",
                    darkMode = false,
                    authenticatedUserId = "user-1",
                    billing = BillingUiState(),
                    accountDeletionState = AccountDeletionUiState(),
                    billingActionsEnabled = false,
                    onBack = {},
                    onEditHealth = {},
                    onNotifications = {},
                    onDarkModeChange = {},
                    onPurchaseMonthly = {},
                    onPurchaseAnnual = {},
                    onRestorePurchases = {},
                    onSignOut = {},
                    onDeleteAccount = {},
                    onClearAccountDeletionState = {},
                    onRunTutorial = { tutorialRequested = true }
                )
            }
        }

        composeRule.onNodeWithTag("profile-list")
            .performScrollToNode(hasTestTag("profile-run-tutorial"))
        composeRule.onNodeWithTag("profile-run-tutorial").performClick()
        org.junit.Assert.assertTrue(tutorialRequested)
    }

    private fun testProfile(): UserProfile = UserProfile(
        email = "user@example.com",
        birthDate = LocalDate.of(1990, 1, 1),
        biologicalSex = BiologicalSex.MALE,
        heightCm = 175.0,
        weightKg = 75.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = HealthGoal.MAINTAIN,
        unitSystem = UnitSystem.METRIC,
        calorieTarget = 2200
    )
}
