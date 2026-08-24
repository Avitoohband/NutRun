package com.avitoohband.nutrun

import android.app.NotificationManager
import android.media.AudioAttributes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingStateEntity
import com.avitoohband.nutrun.data.UserProfileEntity
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.reminders.ReminderSystem
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithTag("auth-email").performTextInput("not-an-email")
        composeRule.onNodeWithTag("auth-password").performTextInput("123")
        composeRule.onNodeWithTag("auth-sign-in").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("Enter a valid email address.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Password must be at least 6 characters.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-screen").assertDoesNotExist()
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
            composeRule.onNodeWithTag("training-mode-workouts").performClick()
            composeRule.onNodeWithTag("training-list").performScrollToNode(
                hasTestTag("workout-card-session-sunday-cardio")
            )
            composeRule
                .onNodeWithTag("start-session-session-sunday-cardio")
                .performClick()
        }
        composeRule.onNodeWithText("Choose one cardio option.").assertIsDisplayed()
        composeRule.onAllNodesWithText("RPE", useUnmergedTree = true)[0].assertIsDisplayed()
        composeRule.onNodeWithText("Pause").assertDoesNotExist()
        composeRule.onNodeWithTag("cancel-workout").assertIsDisplayed()
        composeRule.onNodeWithText("Kilograms (kg)").assertDoesNotExist()
        composeRule.onNodeWithText("Pounds (lb)").assertDoesNotExist()
        composeRule.onNodeWithTag("rest-timer-settings").performClick()
        composeRule.onNodeWithText("Default rest timer").assertIsDisplayed()
        composeRule.onNodeWithText("120 sec").performClick()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithTag("finish-anyway").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag("bottom-nav-progress").performClick()
        composeRule.onNodeWithTag("progress-list").performScrollToNode(
            hasTestTag("recent-training-heading")
        )
        composeRule.onNodeWithTag("progress-list").performScrollToNode(
            hasTestTag("recent-workout-card")
        )
        composeRule.onAllNodesWithTag("recent-workout-card")[0]
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
    fun editHealthDetailsUsesValidatedBirthDateField() {
        enterDemo()
        composeRule.onAllNodesWithContentDescription("Profile")[0].performClick()
        composeRule.onNodeWithText("Profile and settings").performClick()
        composeRule.onNodeWithTag("profile-edit-health").performClick()
        composeRule.onNodeWithTag("edit-health-birth-date").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-health-email").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-health-birth-date-calendar").performClick()
        composeRule.onNodeWithTag("edit-health-birth-date-confirm").performClick()
    }

    @Test
    fun editHealthDetailsPromptsBeforeDiscardingChanges() {
        enterDemo()
        composeRule.onAllNodesWithContentDescription("Profile")[0].performClick()
        composeRule.onNodeWithText("Profile and settings").performClick()
        composeRule.onNodeWithTag("profile-edit-health").performClick()
        composeRule.onNodeWithTag("edit-health-height").performTextClearance()
        composeRule.onNodeWithTag("edit-health-height").performTextInput("180")
        composeRule.onNodeWithTag("edit-health-back").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-discard-changes").performClick()
        composeRule.onNodeWithTag("edit-health-height").assertIsDisplayed()
    }

    @Test
    fun editWorkoutHistoryUsesValidatedDateField() {
        enterDemo()
        composeRule.onNodeWithTag("bottom-nav-progress").performClick()
        composeRule.onNodeWithTag("progress-list").performScrollToNode(
            hasTestTag("recent-training-heading")
        )
        composeRule.onNodeWithTag("progress-list").performScrollToNode(
            hasTestTag("recent-workout-card")
        )
        composeRule.onAllNodesWithTag("recent-workout-card")[0]
            .performClick()
        composeRule.onNodeWithTag("edit-workout-history").performClick()
        composeRule.onNodeWithTag("edit-workout-date").assertIsDisplayed()
    }

    @Test
    fun savedWalkRouteAndMetricsFlowFromHistorySelectionIntoDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppPreferences(context)
        val dao = NutRunDatabase.getInstance(context).dao()
        val originalTimeZone = TimeZone.getDefault()

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            runBlocking {
                preferences.signOut()
                dao.clearAccountData(WALK_HISTORY_TEST_USER_ID)
                preferences.clearAccount(WALK_HISTORY_TEST_USER_ID)
                dao.saveProfile(testWalkHistoryProfile())
                dao.saveWalks(testWalkHistorySessions())
                dao.saveWalkPoints(testWalkHistoryPoints())
                preferences.signIn(
                    userId = WALK_HISTORY_TEST_USER_ID,
                    email = WALK_HISTORY_TEST_EMAIL,
                    trialStartedAtMillis = WALK_HISTORY_START_MILLIS,
                    subscriber = true
                )
            }

            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("bottom-nav-walk").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("bottom-nav-walk").performClick()
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithText("Recorded walks").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithTag("walk-history-card").fetchSemanticsNodes().size == 2
            }

            composeRule.onAllNodesWithTag("walk-history-card")[0]
                .assertTextContains("4500 steps", substring = true)
            composeRule.onAllNodesWithTag("walk-history-card")[1]
                .assertTextContains("Steps unavailable", substring = true)
            composeRule.onAllNodesWithTag("walk-history-card")[0].performClick()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule
                    .onAllNodesWithContentDescription("Saved route with 3 points")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithTag("walk-details-heading").assertIsDisplayed()
            composeRule.onNodeWithTag("walk-details-route-map")
                .assertContentDescriptionEquals("Saved route with 3 points")
            composeRule.onNodeWithText("Your route will appear here").assertDoesNotExist()
            composeRule.onNodeWithText("Sunday, August 9, 2026").assertIsDisplayed()
            composeRule.onNodeWithText("7:30 AM - 8:15 AM").assertIsDisplayed()
            composeRule.onNodeWithText("3.00").assertIsDisplayed()
            composeRule.onNodeWithText("45:00").assertIsDisplayed()
            composeRule.onNodeWithText("4500").assertIsDisplayed()
            composeRule.onNodeWithText("15:00 /km").assertIsDisplayed()

            composeRule.onNodeWithTag("walk-details-back").performClick()
            composeRule.onNodeWithTag("walk-details-heading").assertDoesNotExist()
            composeRule.onAllNodesWithTag("walk-history-card")[0].performClick()
            composeRule.onNodeWithTag("walk-details-heading").assertIsDisplayed()
            pressBack()
            composeRule.onNodeWithTag("walk-details-heading").assertDoesNotExist()
        } finally {
            runBlocking {
                preferences.signOut()
                dao.clearAccountData(WALK_HISTORY_TEST_USER_ID)
                preferences.clearAccount(WALK_HISTORY_TEST_USER_ID)
            }
            TimeZone.setDefault(originalTimeZone)
        }
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
            composeRule.onAllNodesWithTag("nutrition-macro-progress").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("bottom-nav-nutrition").fetchSemanticsNodes().isNotEmpty()
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
        cleanupTask5SupplementsFromToday()
        openManageSupplementsFromToday()

        composeRule.onNodeWithText("Manage supplements").assertIsDisplayed()
        composeRule.onNodeWithText("All supplements and their scheduled days").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-supplement-supplement-1").performClick()
        composeRule.onNodeWithText("Edit supplement").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("add-managed-supplement").performClick()
        composeRule.onNodeWithText("Take on").assertIsDisplayed()
        DayOfWeek.entries.forEach { day ->
            composeRule.onNodeWithTag("supplement-dialog-weekday-${day.name}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("supplement-dialog-weekday-all").performClick()
    }

    @Test
    fun demoConfiguresSupplementRemindersAndOpensManagement() {
        grantNotificationPermission()
        enterDemo()
        cleanupTask5SupplementsFromToday()
        composeRule.onAllNodesWithContentDescription("Profile")[0].performClick()
        composeRule.onNodeWithText("Profile and settings").performClick()
        composeRule.onNodeWithTag("profile-notification-settings").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("notification-settings-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("notification-settings-list")
            .performScrollToNode(hasText("Supplement reminders"))
        composeRule.onNodeWithText("Supplement reminders").assertIsDisplayed()
        composeRule.onNodeWithTag("supplement-reminders-master").assertIsOff()
        // Task 12 collapses per-supplement controls when the master is off; enable it first.
        composeRule.onNodeWithTag("supplement-reminders-master").performClick().assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-enabled")
            .performScrollTo()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time")
            .performTextClearance()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time")
            .performTextInput("09:45")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Supplement reminders").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Loading supplement reminders").fetchSemanticsNodes().isEmpty()
        }
        if (runCatching {
                composeRule.onNodeWithTag("supplement-reminders-master").assertIsOff()
            }.isSuccess
        ) {
            composeRule.onNodeWithTag("supplement-reminders-master").performClick().assertIsOn()
        }
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time")
            .performScrollTo()
            .assertTextContains("09:45")

        composeRule.onNodeWithTag("supplement-reminders-master").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminders-master").performClick().assertIsOff()
        // Task 12 hides per-supplement controls when master is off; re-enable to verify retained drafts.
        composeRule.onNodeWithTag("supplement-reminders-master").performClick().assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time")
            .assertTextContains("09:45")

        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time-clock")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("supplement-reminder-supplement-1-time-picker")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time-picker")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("manage-supplements-from-notifications")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Manage supplements").assertIsDisplayed()
        composeRule.onNodeWithTag("add-managed-supplement").performClick()
        composeRule.onNodeWithTag("supplement-dialog-name").performTextInput("Reconcile Zinc")
        composeRule.onNodeWithTag("supplement-dialog-dose").performTextInput("15 mg")
        composeRule.onNodeWithTag("supplement-dialog-save").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time")
            .performScrollTo()
            .assertTextContains("09:45")
        composeRule.onNodeWithText("Reconcile Zinc reminder time")
            .performScrollTo()
            .assertTextContains("08:00")
        composeRule.onNodeWithTag("supplement-reminders-master")
            .performScrollTo()
        if (runCatching {
                composeRule.onNodeWithTag("supplement-reminders-master").assertIsOff()
            }.isSuccess
        ) {
            composeRule.onNodeWithTag("supplement-reminders-master").performClick().assertIsOn()
        }
        composeRule.onNodeWithTag("save-notification-settings").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("profile-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("notification-settings-list").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("profile-screen").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("profile-notification-settings").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("notification-settings-list").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Loading supplement reminders").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("notification-settings-list")
            .performScrollToNode(hasText("Supplement reminders"))
        composeRule.onNodeWithText("Supplement reminders").assertIsDisplayed()
        composeRule.onNodeWithTag("supplement-reminders-master").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-supplement-1-time")
            .assertTextContains("09:45")
        composeRule.onNodeWithTag("manage-supplements-from-notifications")
            .performScrollTo().performClick()
        removeTask5SupplementsFromManagement()

    }

    @Test
    fun supplementNotificationFocusesTodaySupplements() {
        enterDemo()
        composeRule.onNodeWithTag("bottom-nav-training").performClick()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.intent
                .putExtra(MainActivity.EXTRA_DESTINATION, "today")
                .putExtra(MainActivity.EXTRA_SUPPLEMENTS_SECTION, true)
        }
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("today-supplements-heading")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today-supplements-heading").assertIsDisplayed()

        composeRule.onNodeWithTag("today-list").performScrollToNode(hasText("Today"))
        composeRule.onNodeWithTag("today-heading").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-nav-training").performClick()
        composeRule.onNodeWithTag("bottom-nav-today").performClick()
        composeRule.onNodeWithTag("today-heading").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("today-heading").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today-heading").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.intent
                .putExtra(MainActivity.EXTRA_DESTINATION, "today")
                .putExtra(MainActivity.EXTRA_SUPPLEMENTS_SECTION, true)
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("today-supplements-heading")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today-supplements-heading").assertIsDisplayed()
    }

    @Test
    fun demoAddsAndEditsSupplementReminderConfiguration() {
        grantNotificationPermission()
        enterDemo()
        cleanupTask5SupplementsFromToday()
        composeRule.onNodeWithContentDescription("Add supplement").performClick()

        composeRule.onNodeWithTag("supplement-dialog-reminder-enabled")
            .performScrollTo()
            .assertIsOn()
        composeRule.onNodeWithTag("supplement-dialog-reminder-time")
            .performScrollTo()
            .assertTextContains("08:00")
        composeRule.onNodeWithTag("supplement-dialog-name").performTextInput("Task 5 Magnesium")
        composeRule.onNodeWithTag("supplement-dialog-dose").performTextInput("200 mg")
        composeRule.onNodeWithTag("supplement-dialog-reminder-time")
            .performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag("supplement-dialog-reminder-time").performTextInput("25:00")
        composeRule.onNodeWithTag("supplement-dialog-save").assertIsNotEnabled()
        composeRule.onNodeWithTag("supplement-dialog-reminder-time").performTextClearance()
        composeRule.onNodeWithTag("supplement-dialog-reminder-time").performTextInput("09:30")
        composeRule.onNodeWithTag("supplement-dialog-save").performClick()

        openManageSupplementsFromToday()
        composeRule.onNodeWithContentDescription("Edit Task 5 Magnesium").performClick()
        composeRule.onNodeWithTag("supplement-dialog-reminder-enabled")
            .performScrollTo()
            .assertIsOn()
        composeRule.onNodeWithTag("supplement-dialog-reminder-time")
            .performScrollTo()
            .assertTextContains("09:30")
        composeRule.onNodeWithTag("supplement-dialog-reminder-enabled").performScrollTo().performClick()
        composeRule.onNodeWithTag("supplement-dialog-save").performClick()
        removeTask5SupplementsFromManagement()
    }

    private fun grantNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} " +
                    android.Manifest.permission.POST_NOTIFICATIONS
            ).close()
        }
    }

    private fun navigateToTodayDashboard() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("bottom-nav-today").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("bottom-nav-today").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openManageSupplementsFromToday() {
        navigateToTodayDashboard()
        composeRule.onNodeWithTag("today-list")
            .performScrollToNode(hasTestTag("manage-supplements"))
        composeRule.onNodeWithTag("manage-supplements").performClick()
    }

    private fun cleanupTask5SupplementsFromToday() {
        openManageSupplementsFromToday()
        removeTask5SupplementsFromManagement()
        composeRule.onNodeWithContentDescription("Back").performClick()
    }

    private fun removeTask5SupplementsFromManagement() {
        listOf("Task 5 Magnesium", "Reconcile Zinc").forEach { name ->
            val matcher = hasContentDescription("Remove $name")
            val found = runCatching {
                composeRule.onNodeWithTag("manage-supplements-list")
                    .performScrollToNode(matcher)
                composeRule.onNode(matcher).performClick()
            }.isSuccess
            if (found) {
                composeRule.onNodeWithTag("confirm-remove-supplement").performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodesWithContentDescription("Remove $name")
                        .fetchSemanticsNodes().isEmpty()
                }
            }
        }
    }

    private fun enterDemo() {
        composeRule.onNodeWithTag("demo-login").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun testWalkHistoryProfile() = UserProfileEntity(
        id = "profile:$WALK_HISTORY_TEST_USER_ID",
        userId = WALK_HISTORY_TEST_USER_ID,
        email = WALK_HISTORY_TEST_EMAIL,
        birthDate = "1990-01-01",
        biologicalSex = "MALE",
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = "MODERATE",
        goal = "MAINTAIN",
        unitSystem = "METRIC",
        calorieTarget = 2_400,
        updatedAtMillis = WALK_HISTORY_START_MILLIS
    )

    private fun testWalkHistorySessions() = listOf(
        WalkSessionEntity(
            id = WALK_WITH_ROUTE_ID,
            userId = WALK_HISTORY_TEST_USER_ID,
            state = "FINISHED",
            startedAtMillis = WALK_HISTORY_START_MILLIS,
            endedAtMillis = WALK_HISTORY_END_MILLIS,
            accumulatedDurationMillis = 45 * 60_000L,
            resumedAtMillis = null,
            distanceMeters = 3_000.0,
            stepBaseline = null,
            steps = 4_500L,
            pendingSync = false
        ),
        WalkSessionEntity(
            id = WALK_WITHOUT_STEPS_ID,
            userId = WALK_HISTORY_TEST_USER_ID,
            state = "FINISHED",
            startedAtMillis = WALK_HISTORY_START_MILLIS - 60 * 60_000L,
            endedAtMillis = WALK_HISTORY_START_MILLIS - 30 * 60_000L,
            accumulatedDurationMillis = 30 * 60_000L,
            resumedAtMillis = null,
            distanceMeters = 2_000.0,
            stepBaseline = null,
            steps = null,
            pendingSync = false
        )
    )

    private fun testWalkHistoryPoints() = listOf(
        WalkPointEntity(
            id = "walk-point-1",
            userId = WALK_HISTORY_TEST_USER_ID,
            sessionId = WALK_WITH_ROUTE_ID,
            latitude = 32.0853,
            longitude = 34.7818,
            accuracyMeters = 4f,
            recordedAtMillis = WALK_HISTORY_START_MILLIS
        ),
        WalkPointEntity(
            id = "walk-point-2",
            userId = WALK_HISTORY_TEST_USER_ID,
            sessionId = WALK_WITH_ROUTE_ID,
            latitude = 32.0860,
            longitude = 34.7830,
            accuracyMeters = 4f,
            recordedAtMillis = WALK_HISTORY_START_MILLIS + 60_000L
        ),
        WalkPointEntity(
            id = "walk-point-3",
            userId = WALK_HISTORY_TEST_USER_ID,
            sessionId = WALK_WITH_ROUTE_ID,
            latitude = 32.0870,
            longitude = 34.7840,
            accuracyMeters = 4f,
            recordedAtMillis = WALK_HISTORY_START_MILLIS + 120_000L
        )
    )

    private companion object {
        const val WALK_HISTORY_TEST_USER_ID = "android-test-walk-history"
        const val WALK_HISTORY_TEST_EMAIL = "walk-history@android.test"
        const val WALK_WITH_ROUTE_ID = "android-test-walk-with-route"
        const val WALK_WITHOUT_STEPS_ID = "android-test-walk-without-steps"
        val WALK_HISTORY_START_MILLIS = Instant.parse("2026-08-09T07:30:00Z").toEpochMilli()
        val WALK_HISTORY_END_MILLIS = Instant.parse("2026-08-09T08:15:00Z").toEpochMilli()
    }
}

class SupplementReminderSettingsCardComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun individualBulkAndMasterTogglesPreserveTimeDrafts() {
        val supplement = Supplement(
            id = "magnesium",
            name = "Magnesium",
            dose = "200 mg",
            schedule = SupplementSchedule(RecurrenceType.DAILY),
            reminderEnabled = false,
            reminderMinute = 10 * 60 + 15
        )
        var permissionRequests = 0

        composeRule.setContent {
            var masterEnabled by remember { mutableStateOf(false) }
            var drafts by remember {
                mutableStateOf(
                    mapOf(
                        supplement.id to SupplementReminderDraft(
                            enabled = false,
                            time = "10:15"
                        )
                    )
                )
            }
            MaterialTheme {
                SupplementReminderSettingsCard(
                    masterEnabled = masterEnabled,
                    onMasterEnabledChange = { masterEnabled = it },
                    supplements = listOf(supplement),
                    drafts = drafts,
                    onDraftsChange = { drafts = it },
                    onPermissionRequest = { permissionRequests += 1 },
                    onManageSupplements = {}
                )
            }
        }

        composeRule.onNodeWithTag("supplement-reminder-magnesium-enabled").assertIsOff()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-enabled").performClick()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-enabled").performClick()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-enabled").assertIsOff()
        composeRule.onNodeWithTag("supplement-reminders-toggle-all").performClick()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-time")
            .assertTextContains("10:15")
        composeRule.onNodeWithTag("supplement-reminders-master").performClick().assertIsOn()
        composeRule.onNodeWithTag("supplement-reminders-master").performClick().assertIsOff()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-magnesium-time")
            .assertTextContains("10:15")
        composeRule.runOnIdle { assertEquals(3, permissionRequests) }
    }

    @Test
    fun invalidTypedTimeShowsInlineValidationAndClockPicker() {
        val supplement = Supplement(
            id = "vitamin-d",
            name = "Vitamin D",
            dose = "2,000 IU",
            schedule = SupplementSchedule(RecurrenceType.DAILY)
        )
        composeRule.setContent {
            var drafts by remember {
                mutableStateOf(
                    mapOf(
                        supplement.id to SupplementReminderDraft(false, "08:00")
                    )
                )
            }
            MaterialTheme {
                SupplementReminderSettingsCard(
                    masterEnabled = false,
                    onMasterEnabledChange = {},
                    supplements = listOf(supplement),
                    drafts = drafts,
                    onDraftsChange = { drafts = it },
                    onPermissionRequest = {},
                    onManageSupplements = {}
                )
            }
        }

        composeRule.onNodeWithTag("supplement-reminder-vitamin-d-time")
            .performTextClearance()
        composeRule.onNodeWithTag("supplement-reminder-vitamin-d-time")
            .performTextInput("25:00")
        composeRule.onNodeWithText("Use a valid 24-hour time (HH:mm).")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("supplement-reminder-vitamin-d-time-clock").performClick()
        composeRule.onNodeWithTag("supplement-reminder-vitamin-d-time-picker")
            .assertIsDisplayed()
    }

    @Test
    fun emptyStateKeepsManageSupplementsAction() {
        var managed = false
        composeRule.setContent {
            MaterialTheme {
                SupplementReminderSettingsCard(
                    masterEnabled = false,
                    onMasterEnabledChange = {},
                    supplements = emptyList(),
                    drafts = emptyMap(),
                    onDraftsChange = {},
                    onPermissionRequest = {},
                    onManageSupplements = { managed = true }
                )
            }
        }

        composeRule.onNodeWithText("No supplements configured").assertIsDisplayed()
        composeRule.onNodeWithTag("manage-supplements-from-notifications").performClick()
        composeRule.runOnIdle { assertTrue(managed) }
    }

    @Test
    fun loadingReminderCardDisablesMutationNavigation() {
        composeRule.setContent {
            MaterialTheme {
                SupplementReminderSettingsCard(
                    masterEnabled = false,
                    onMasterEnabledChange = {},
                    supplements = emptyList(),
                    drafts = emptyMap(),
                    onDraftsChange = {},
                    onPermissionRequest = {},
                    onManageSupplements = {},
                    loading = true
                )
            }
        }

        composeRule.onNodeWithText("Loading supplement reminders...").assertIsDisplayed()
        composeRule.onNodeWithTag("supplement-reminders-master").assertIsNotEnabled()
        composeRule.onNodeWithTag("manage-supplements-from-notifications").assertIsNotEnabled()
    }

    @Test
    fun trainingScreenHidesMutationControlsUntilFirstPayload() {
        val runtime = PendingTrainingRuntime()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val model = TrainingViewModel(runtime, scope)
        composeRule.setContent {
            MaterialTheme {
                TrainingScreen(model)
            }
        }

        composeRule.onNodeWithTag("training-loading").assertIsDisplayed()
        composeRule.onNodeWithTag("training-list").assertDoesNotExist()
        runBlocking { runtime.trainingStates.emit(null) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            model.trainingMutationsReady
        }
        composeRule.onNodeWithTag("training-loading").assertDoesNotExist()
        composeRule.onNodeWithTag("training-list").assertIsDisplayed()
        scope.cancel()
    }

    @Test
    fun notificationSaveWaitsForTheActiveTrainingRestore() {
        val runtime = PendingTrainingRuntime()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val model = TrainingViewModel(runtime, scope)
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsSaveButton(
                    valid = true,
                    accountReady = model.supplementReminderReadyAccountId == "account-a",
                    persist = { NotificationSettingsSaveResult.Success("account-a") },
                    currentAccountId = { "account-a" },
                    onSuccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("save-notification-settings").assertIsNotEnabled()
        runBlocking { runtime.trainingStates.emit(null) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            model.supplementReminderUpdatesReady
        }
        composeRule.onNodeWithTag("save-notification-settings").assertIsEnabled()
        scope.cancel()
    }

    @Test
    fun notificationSaveWaitsForAllNewAccountSnapshots() {
        var hydrationAccountId by mutableStateOf("account-a")
        var trainingAccountId by mutableStateOf("account-a")
        var supplementAccountId by mutableStateOf("account-a")
        var trainingPayloadAccountId by mutableStateOf("account-a")
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsSaveButton(
                    valid = true,
                    accountReady = notificationSettingsAccountReady(
                        accountId = "account-b",
                        hydrationAccountId = hydrationAccountId,
                        trainingAccountId = trainingAccountId,
                        supplementAccountId = supplementAccountId,
                        trainingPayloadAccountId = trainingPayloadAccountId
                    ),
                    persist = { NotificationSettingsSaveResult.Success("account-b") },
                    currentAccountId = { "account-b" },
                    onSuccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("save-notification-settings").assertIsNotEnabled()
        composeRule.runOnIdle { hydrationAccountId = "account-b" }
        composeRule.onNodeWithTag("save-notification-settings").assertIsNotEnabled()
        composeRule.runOnIdle { trainingAccountId = "account-b" }
        composeRule.onNodeWithTag("save-notification-settings").assertIsNotEnabled()
        composeRule.runOnIdle { supplementAccountId = "account-b" }
        composeRule.onNodeWithTag("save-notification-settings").assertIsNotEnabled()
        composeRule.runOnIdle { trainingPayloadAccountId = "account-b" }
        composeRule.onNodeWithTag("save-notification-settings").assertIsEnabled()
    }

    @Test
    fun notificationSaveWaitsForFailureAndKeepsTheScreenOpenWithAnError() {
        val completion = CompletableDeferred<NotificationSettingsSaveResult>()
        var navigated = false
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsSaveButton(
                    valid = true,
                    accountReady = true,
                    persist = { completion.await() },
                    currentAccountId = { "account-a" },
                    onSuccess = { navigated = true }
                )
            }
        }

        composeRule.onNodeWithTag("save-notification-settings").performClick()
        composeRule.onNodeWithTag("save-notification-settings").assertIsNotEnabled()
        composeRule.runOnIdle { assertFalse(navigated) }
        completion.complete(
            NotificationSettingsSaveResult.Failed(
                expectedAccountId = "account-a",
                stage = NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                message = "disk full"
            )
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("notification-settings-save-error")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("notification-settings-save-error")
            .assertTextContains(
                "No other notification settings were saved",
                substring = true
            )
        composeRule.onNodeWithTag("save-notification-settings").assertIsEnabled()
        composeRule.runOnIdle { assertFalse(navigated) }
    }

    @Test
    fun notificationSaveNavigatesOnlyAfterDurableSuccess() {
        val completion = CompletableDeferred<NotificationSettingsSaveResult>()
        var navigated = false
        composeRule.setContent {
            NotificationSettingsSaveButton(
                valid = true,
                accountReady = true,
                persist = { completion.await() },
                currentAccountId = { "account-a" },
                onSuccess = { navigated = true }
            )
        }

        composeRule.onNodeWithTag("save-notification-settings").performClick()
        composeRule.runOnIdle { assertFalse(navigated) }
        completion.complete(NotificationSettingsSaveResult.Success("account-a"))
        composeRule.waitUntil(timeoutMillis = 5_000) { navigated }
        composeRule.runOnIdle { assertTrue(navigated) }
    }

    @Test
    fun notificationSaveKeepsScreenOpenWhenAccountChangesAfterFinalStage() {
        var currentAccountId: String? = "account-a"
        var navigated = false
        composeRule.setContent {
            NotificationSettingsSaveButton(
                valid = true,
                accountReady = true,
                persist = {
                    currentAccountId = "account-b"
                    NotificationSettingsSaveResult.Success("account-a")
                },
                currentAccountId = { currentAccountId },
                onSuccess = { navigated = true }
            )
        }

        composeRule.onNodeWithTag("save-notification-settings").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("notification-settings-save-error")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("notification-settings-save-error")
            .assertTextContains("active account changed", substring = true)
        composeRule.runOnIdle { assertFalse(navigated) }
    }

    @Test
    fun reminderOnlyDialogEditReturnsTheExactIntervalSchedule() {
        val existing = Supplement(
            id = "interval-dialog",
            name = "Iron",
            dose = "18 mg",
            schedule = SupplementSchedule(
                type = RecurrenceType.EVERY_N_DAYS,
                startDate = LocalDate.of(2026, 1, 3),
                intervalDays = 4
            ),
            completedOn = LocalDate.of(2026, 8, 10),
            reminderEnabled = true,
            reminderMinute = 480
        )
        var savedSchedule: SupplementSchedule? = null
        composeRule.setContent {
            MaterialTheme {
                AddSupplementDialog(
                    existing = existing,
                    onDismiss = {},
                    onAdd = { _, _, schedule, _, _ -> savedSchedule = schedule }
                )
            }
        }

        composeRule.onNodeWithTag("supplement-dialog-reminder-time")
            .performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag("supplement-dialog-reminder-time").performTextInput("09:40")
        composeRule.onNodeWithTag("supplement-dialog-save").performClick()

        composeRule.runOnIdle { assertEquals(existing.schedule, savedSchedule) }
    }

    private class PendingTrainingRuntime : TrainingViewModelRuntime {
        private val sessionState = MutableStateFlow(
            SessionPreferences(authenticatedUserId = "account-a")
        )
        override val session: Flow<SessionPreferences> = sessionState
        val trainingStates = MutableSharedFlow<TrainingStateEntity?>(replay = 1)

        override fun trainingState(userId: String): Flow<TrainingStateEntity?> = trainingStates
        override suspend fun currentUserId(): String? = sessionState.value.authenticatedUserId
        override suspend fun saveTrainingState(userId: String, payload: String) = Unit
        override suspend fun currentTrainingReminderSettings(
            userId: String
        ): TrainingReminderSettingsEntity? = null
        override suspend fun currentSupplementReminderSettings(
            userId: String
        ) = SupplementReminderSettingsEntity(userId = userId)
        override fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity) = Unit
        override suspend fun scheduleSupplement(
            userId: String,
            settings: SupplementReminderSettingsEntity
        ) = Unit
        override suspend fun scheduleRecovery(userId: String, system: ReminderSystem) = Unit
    }
}

class TrainingPlanningComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun weeklyScheduleAndWorkoutLibraryUseCalendarOrder() {
        val model = TrainingViewModel(null, null)

        composeRule.setContent {
            MaterialTheme {
                TrainingScreen(model)
            }
        }

        composeRule.onNodeWithTag("weekly-schedule").assertIsDisplayed()
        composeRule.onNodeWithTag("training-list").performScrollToNode(hasTestTag("day-plan-SUNDAY"))
        composeRule.onNodeWithTag("day-plan-SUNDAY").assertIsDisplayed()
        composeRule.onNodeWithTag("training-list").performScrollToNode(hasTestTag("day-plan-MONDAY"))
        composeRule.onNodeWithTag("day-plan-MONDAY").assertIsDisplayed()
        composeRule.onNodeWithTag("training-list").performScrollToNode(hasTestTag("day-plan-SATURDAY"))
        composeRule.onNodeWithTag("day-plan-SATURDAY").assertIsDisplayed()
        composeRule.onNodeWithTag("training-mode-workouts").performClick()
        composeRule.onNodeWithTag("training-list").performScrollToNode(hasTestTag("workout-library"))
        composeRule.onNodeWithTag("workout-library").assertIsDisplayed()
        composeRule.onNodeWithTag("training-list").performScrollToNode(
            hasTestTag("workout-card-session-monday-push-biceps")
        )
        composeRule.onNodeWithTag("workout-card-session-monday-push-biceps").assertIsDisplayed()
    }

    @Test
    fun assignmentClearsRestDayAndPersistsSelectedWorkout() {
        val model = TrainingViewModel(null, null)
        val day = DayOfWeek.SUNDAY
        val workout = model.workoutTemplates.first { it.id == "session-monday-push-biceps" }
        model.setRestDay(day)
        composeRule.setContent {
            MaterialTheme { TrainingScreen(model) }
        }

        composeRule.onNodeWithTag("training-list").performScrollToNode(hasTestTag("day-plan-SUNDAY"))
        composeRule.onNodeWithTag("day-actions-SUNDAY").performClick()
        composeRule.onNodeWithTag("assign-day-SUNDAY").performClick()
        composeRule.onNodeWithTag("assignment-option-${workout.id}").performClick()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            val plan = model.weeklyDayPlans.single { it.weekday == day }
            assertFalse(plan.isRestDay)
            assertEquals(listOf(workout.id), plan.templateIds)
        }
    }

    @Test
    fun assignedWorkoutDetailsOpenEditorWithoutStartingWorkout() {
        val model = TrainingViewModel(null, null)
        val workout = model.workoutTemplates.first { it.id == "session-sunday-cardio" }
        composeRule.setContent { MaterialTheme { TrainingScreen(model) } }

        composeRule.onNodeWithTag("training-list").performScrollToNode(
            hasTestTag("assigned-workout-SUNDAY-${workout.id}")
        )
        composeRule.onNodeWithTag("assigned-workout-SUNDAY-${workout.id}").performClick()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onNodeWithText("Edit").performClick()

        composeRule.onNodeWithTag("workout-editor").assertIsDisplayed()
        assertEquals(null, model.activeWorkoutSessionId)
    }

    @Test
    fun deletingWorkoutThroughLibraryKeepsHistory() {
        val model = TrainingViewModel(null, null)
        val workout = model.workoutTemplates.first { it.exercises.isNotEmpty() }
        val record = WorkoutRecord(
            id = "ui-history",
            sessionId = workout.id,
            sessionName = workout.name,
            performedOn = LocalDate.of(2026, 8, 20),
            startedAtMillis = 1L,
            finishedAtMillis = 2L,
            completedTargetIds = emptySet(),
            completedLogicalTargets = 0,
            totalLogicalTargets = workout.logicalTargetCount(),
            sets = emptyList()
        )
        model.workoutHistory += record
        composeRule.setContent {
            MaterialTheme {
                TrainingPlanningContent(
                    model = model,
                    mode = TrainingPlanningMode.WORKOUTS,
                    onOpenTemplate = {}
                )
            }
        }

        composeRule.onNodeWithTag("training-list").performScrollToNode(
            hasTestTag("delete-workout-${workout.id}")
        )
        composeRule.onNodeWithTag("delete-workout-${workout.id}").performClick()
        composeRule.onNodeWithText("Delete workout?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-delete-workout").performClick()

        composeRule.runOnIdle {
            assertFalse(model.workoutTemplates.any { it.id == workout.id })
            assertEquals(listOf(record), model.workoutHistory)
        }
    }
}

class WorkoutEditorComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun customExerciseCanSaveWithOnlyANameAndIsAddedImmediately() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        composeRule.setContent { MaterialTheme { WorkoutEditorContent(model, template.id, {}) } }

        composeRule.onNodeWithTag("create-custom-exercise").performClick()
        composeRule.onNodeWithTag("custom-exercise-name").performTextInput("Suitcase march")
        composeRule.onNodeWithTag("save-custom-exercise").performClick()
        composeRule.onNodeWithText("Suitcase march").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-editor-save").performClick()
        composeRule.runOnIdle {
            assertEquals("Suitcase march", model.workoutTemplates.first { it.id == template.id }.exercises.last().exercise.name)
        }
    }

    @Test
    fun setControlsChangeOnlyTheSelectedTarget() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first { it.exercises.size > 1 }
        val target = template.exercises.first()
        val untouched = template.exercises[1]
        composeRule.setContent { MaterialTheme { WorkoutEditorContent(model, template.id, {}) } }

        composeRule.onAllNodesWithContentDescription("More sets")[0].performClick()
        composeRule.onNodeWithTag("workout-editor-save").performClick()
        composeRule.runOnIdle {
            val updated = model.workoutTemplates.first { it.id == template.id }
            assertEquals(target.sets + 1, updated.exercises.first { it.id == target.id }.sets)
            assertEquals(untouched.sets, updated.exercises.first { it.id == untouched.id }.sets)
        }
    }

    @Test
    fun duplicateCustomNameShowsInlineErrorAndKeepsDialogOpen() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        composeRule.setContent { MaterialTheme { WorkoutEditorContent(model, template.id, {}) } }

        composeRule.onNodeWithTag("create-custom-exercise").performClick()
        composeRule.onNodeWithTag("custom-exercise-name").performTextInput("Pull up")
        composeRule.onNodeWithTag("save-custom-exercise").performClick()

        composeRule.onNodeWithText("An exercise with this name already exists.").assertIsDisplayed()
        composeRule.onNodeWithTag("custom-exercise-name").assertIsDisplayed()
    }

    @Test
    fun blankWorkoutNameShowsInlineError() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        composeRule.setContent { MaterialTheme { WorkoutEditorContent(model, template.id, {}) } }

        composeRule.onNodeWithTag("workout-name").performTextClearance()
        composeRule.onNodeWithTag("workout-editor-save").performClick()

        composeRule.onNodeWithText("Workout name cannot be blank.").assertIsDisplayed()
        assertEquals(template.name, model.workoutTemplates.first { it.id == template.id }.name)
    }

    @Test
    fun searchMatchesMusclesCaseInsensitively() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        composeRule.setContent { MaterialTheme { WorkoutEditorContent(model, template.id, {}) } }

        composeRule.onNodeWithTag("exercise-search").performTextInput("ANTERIOR DELTOIDS")
        composeRule.onNodeWithTag("workout-editor").performScrollToNode(hasText("Pike push-up"))
        composeRule.onNodeWithText("Pike push-up").assertIsDisplayed()
    }

    @Test
    fun removingTargetRequiresConfirmationAndOnlyChangesThisWorkout() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first { it.exercises.isNotEmpty() }
        val target = template.exercises.first()
        val otherTemplate = model.workoutTemplates.first { it.id != template.id }
        val otherTargetIds = otherTemplate.exercises.map(ExerciseTarget::id)
        composeRule.setContent { MaterialTheme { WorkoutEditorContent(model, template.id, {}) } }

        composeRule.onNodeWithContentDescription("Remove ${target.exercise.name}").performClick()
        composeRule.onNodeWithText("This removes it only from this workout.").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithTag("workout-editor-save").performClick()

        composeRule.runOnIdle {
            assertFalse(model.workoutTemplates.first { it.id == template.id }.exercises.any { it.id == target.id })
            assertEquals(otherTargetIds, model.workoutTemplates.first { it.id == otherTemplate.id }.exercises.map(ExerciseTarget::id))
        }
    }

    @Test
    fun editorIsFullScreenAndBackConfirmsBeforeDiscardingUnsavedChanges() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        var backed = false
        composeRule.setContent {
            MaterialTheme {
                WorkoutEditorContent(
                    model = model,
                    templateId = template.id,
                    onBack = { backed = true },
                    onSaved = {}
                )
            }
        }

        composeRule.onNodeWithTag("workout-editor-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-name").performTextInput(" changed")
        composeRule.onNodeWithTag("workout-editor-back").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.runOnIdle { assertFalse(backed) }

        composeRule.onNodeWithTag("workout-editor-back").performClick()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.runOnIdle {
            assertTrue(backed)
            assertEquals(template, model.workoutTemplates.first { it.id == template.id })
        }
    }

    @Test
    fun searchDraftSurvivesMultipleExerciseAdditionsUntilSave() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        val choices = model.exerciseLibrary
            .filter { exercise -> template.exercises.none { it.exercise.id == exercise.id } }
            .take(2)
        var saved = false
        composeRule.setContent {
            MaterialTheme {
                WorkoutEditorContent(
                    model = model,
                    templateId = template.id,
                    onBack = {},
                    onSaved = { saved = true }
                )
            }
        }

        choices.forEach { exercise ->
            composeRule.onNodeWithTag("exercise-search").performTextClearance()
            composeRule.onNodeWithTag("exercise-search").performTextInput(exercise.name)
            composeRule.onNodeWithTag("catalog-exercise-${exercise.id}").performClick()
            composeRule.onNodeWithTag("exercise-search").assertTextContains(exercise.name)
        }
        composeRule.runOnIdle {
            assertEquals(template, model.workoutTemplates.first { it.id == template.id })
        }

        composeRule.onNodeWithTag("workout-editor-save").performClick()
        composeRule.runOnIdle {
            assertTrue(saved)
            val exerciseIds = model.workoutTemplates.first { it.id == template.id }
                .exercises.map { it.exercise.id }
            choices.forEach { assertTrue(it.id in exerciseIds) }
        }
    }

    @Test
    fun workoutLibraryProvidesExplicitStartEditDuplicateAndDeleteActions() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first { it.exercises.isNotEmpty() }
        composeRule.setContent {
            MaterialTheme {
                TrainingPlanningContent(
                    model = model,
                    mode = TrainingPlanningMode.WORKOUTS,
                    onOpenTemplate = {}
                )
            }
        }

        composeRule.onNodeWithTag("start-session-${template.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-workout-${template.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("duplicate-workout-${template.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-workout-${template.id}").assertIsDisplayed()
    }
}

class ProgressionSuggestionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptySessionRequiresExercisesBeforeItCanStart() {
        val model = TrainingViewModel(null, null)
        model.addSession("Regular", java.time.DayOfWeek.SUNDAY)
        val emptySession = requireNotNull(model.selectedSession())

        composeRule.setContent {
            MaterialTheme {
                TrainingScreen(model)
            }
        }

        composeRule.onNodeWithTag("training-mode-workouts").performClick()
        composeRule.onNodeWithTag("training-list").performScrollToNode(
            hasTestTag("workout-card-${emptySession.id}")
        )
        composeRule.onNodeWithTag("start-session-${emptySession.id}").assertIsNotEnabled()
        composeRule.onNodeWithText("Add exercises to start").assertIsDisplayed()
    }

    @Test
    fun progressionSuggestionAppearsBeforeStartAndDuringLogging() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val originalTarget = session.exercises.first()
        model.selectSession(session.id)
        model.updateSelectedExercise(
            targetId = originalTarget.id,
            sets = originalTarget.sets,
            reps = originalTarget.reps,
            weightKg = 60.0,
            durationMinutes = originalTarget.durationMinutes,
            distanceKm = originalTarget.distanceKm
        )
        val updatedSession = requireNotNull(model.selectedSession())
        val target = updatedSession.exercises.first { it.id == originalTarget.id }
        model.startWorkout(updatedSession.id)
        repeat(target.sets) { index ->
            model.updateWorkoutSet(
                targetId = target.id,
                setNumber = index + 1,
                reps = requireNotNull(target.maximumReps),
                weightKg = 60.0,
                durationSeconds = null,
                rpe = 8.0,
                completed = true
            )
        }
        model.finishWorkout()
        model.dismissWorkoutSummary()

        composeRule.setContent {
            MaterialTheme {
                TrainingScreen(model)
            }
        }

        composeRule.onNodeWithTag("training-mode-workouts").performClick()
        val programTag = "program-progression-${target.id}"
        composeRule.onNodeWithTag("training-list", useUnmergedTree = true).performScrollToNode(hasTestTag(programTag))
        composeRule.onNodeWithTag(programTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Increase to 62.5 kg", substring = true)

        composeRule.onNodeWithTag("start-session-${updatedSession.id}").performClick()

        val activeTag = "active-progression-${target.id}"
        composeRule.onNodeWithTag(activeTag)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Increase to 62.5 kg", substring = true)
    }

    @Test
    fun zeroEffortInputCanCompleteAWorkoutSet() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val target = session.exercises.first()
        model.startWorkout(session.id)
        val set = model.activeSetLogs.getValue(target.id).first()

        composeRule.setContent {
            MaterialTheme {
                TrainingScreen(model)
            }
        }

        composeRule.onNodeWithTag("workout-effort-${set.id}").performTextInput("0")
        composeRule.onNodeWithTag("workout-set-completed-${set.id}").performClick()

        composeRule.runOnIdle {
            val savedSet = model.activeSetLogs.getValue(target.id).first()
            assertTrue(savedSet.completed)
            assertEquals(0.0, savedSet.rpe!!, 0.001)
        }
    }

    @Test
    fun expiredRestTimerShowsAlertAndCreatesAlarmChannel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.deleteNotificationChannel("rest_timer_finished_v1")
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        model.startRestTimer(seconds = 1)
        Thread.sleep(1_100L)

        composeRule.setContent {
            MaterialTheme {
                TrainingScreen(model)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Rest complete").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Rest complete").assertIsDisplayed()
        composeRule.runOnIdle {
            val channel = notificationManager.getNotificationChannel("rest_timer_finished_v1")
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
            assertEquals(AudioAttributes.USAGE_ALARM, channel?.audioAttributes?.usage)
            assertTrue(channel?.shouldVibrate() == true)
        }
    }

    @Test
    fun trainingScreenDoesNotSaveIncompleteWorkoutBeforeConfirmation() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        composeRule.setContent {
            MaterialTheme {
                TrainingScreen(model)
            }
        }

        composeRule.onNodeWithTag("finish-workout").performClick()

        composeRule.onNodeWithTag("incomplete-workout-review").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(model.workoutHistory.isEmpty())
            assertEquals(session.id, model.activeWorkoutSessionId)
        }
        composeRule.onNodeWithTag("finish-anyway").performClick()
        composeRule.runOnIdle {
            assertEquals(1, model.workoutHistory.size)
            assertEquals(null, model.activeWorkoutSessionId)
        }
    }
}
