package com.avitoohband.nutrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
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
import androidx.test.espresso.Espresso.pressBack
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.UserProfileEntity
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
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

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("bottom-nav-walk").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("bottom-nav-walk").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("walk-history-card").fetchSemanticsNodes().size == 2
            }

            composeRule.onAllNodesWithTag("walk-history-card")[0]
                .assertTextContains("4500 steps")
            composeRule.onAllNodesWithTag("walk-history-card")[1]
                .assertTextContains("Steps unavailable")
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

        composeRule.onNodeWithTag("training-list").performScrollToNode(
            hasTestTag("session-card-${emptySession.id}")
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

        val programTag = "program-progression-${target.id}"
        composeRule.onNodeWithTag("training-list").performScrollToNode(hasTestTag(programTag))
        composeRule.onNodeWithTag(programTag)
            .assertIsDisplayed()
            .assertTextContains("Increase to 62.5 kg")

        composeRule.onNodeWithTag("start-session-${updatedSession.id}").performClick()

        val activeTag = "active-progression-${target.id}"
        composeRule.onNodeWithTag(activeTag)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Increase to 62.5 kg")
    }
}
