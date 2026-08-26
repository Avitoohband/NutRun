package com.avitoohband.nutrun

import androidx.compose.runtime.Composable
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
import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.domain.UserProfile
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileSectionsAndDeleteConfirmationRequireTypedEmail() {
        composeRule.setContent {
            NutRunTheme {
                profileOverviewContent()
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
        composeRule.onNodeWithTag("profile-delete-email-confirm").performTextInput(testProfile().email)
        composeRule.onNodeWithTag("profile-delete-confirm").assertIsDisplayed()
    }

    @Test
    fun demoProfileShowsLocalOnlyDeletionCopy() {
        composeRule.setContent {
            NutRunTheme {
                profileOverviewContent(
                    profile = defaultDemoProfile(),
                    entitlementLabel = "Ad-free subscriber",
                    authenticatedUserId = DEMO_USER_ID
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
                profileOverviewContent(onRunTutorial = { tutorialRequested = true })
            }
        }

        composeRule.onNodeWithTag("profile-list")
            .performScrollToNode(hasTestTag("profile-run-tutorial"))
        composeRule.onNodeWithTag("profile-run-tutorial").performClick()
        assertTrue(tutorialRequested)
    }

    @Test
    fun helpAndDataSectionsExposePolicyLinks() {
        var privacyOpened = false
        var termsOpened = false
        composeRule.setContent {
            NutRunTheme {
                profileOverviewContent(
                    privacyPolicyUrl = "https://example.com/privacy",
                    termsOfServiceUrl = "https://example.com/terms",
                    onOpenPrivacyPolicy = { privacyOpened = true },
                    onOpenTermsOfService = { termsOpened = true }
                )
            }
        }

        composeRule.onNodeWithTag("profile-list")
            .performScrollToNode(hasTestTag("profile-privacy-policy"))
        composeRule.onNodeWithTag("profile-privacy-policy").performClick()
        assertTrue(privacyOpened)

        composeRule.onNodeWithTag("profile-list")
            .performScrollToNode(hasTestTag("profile-terms-of-service"))
        composeRule.onNodeWithTag("profile-terms-of-service").performClick()
        assertTrue(termsOpened)
    }

    @Composable
    private fun profileOverviewContent(
        profile: UserProfile = testProfile(),
        entitlementLabel: String = "30 trial days remaining",
        authenticatedUserId: String = "user-1",
        privacyPolicyUrl: String = "",
        termsOfServiceUrl: String = "",
        onOpenPrivacyPolicy: () -> Unit = {},
        onOpenTermsOfService: () -> Unit = {},
        onRunTutorial: () -> Unit = {}
    ) {
        ProfileOverviewContent(
            profile = profile,
            entitlementLabel = entitlementLabel,
            darkMode = false,
            authenticatedUserId = authenticatedUserId,
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
            onRunTutorial = onRunTutorial,
            privacyPolicyUrl = privacyPolicyUrl,
            termsOfServiceUrl = termsOfServiceUrl,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenTermsOfService = onOpenTermsOfService
        )
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
