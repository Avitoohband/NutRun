package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActiveWorkoutContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactLargeTextWorkoutKeepsFocusNavigationAndActionsStable() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        assertEquals(6, session.exercises.size)
        model.startWorkout(session.id)
        val firstTarget = session.exercises.first()
        val secondTarget = session.exercises[1]
        val firstSet = model.activeSetLogs.getValue(firstTarget.id).first()

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        ActiveWorkoutContent(
                            model = model,
                            onEditRestTimer = {},
                            onCancelRequest = {},
                            onFinishRequest = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Exercise 1 of 6").assertIsDisplayed()
        composeRule.onNodeWithTag("active-exercise-${firstTarget.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("active-exercise-${secondTarget.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("active-workout-previous").assertIsNotEnabled()
        composeRule.onNodeWithTag("active-workout-next").assertIsEnabled()
        composeRule.onNodeWithTag("cancel-workout").assertIsDisplayed()
        composeRule.onNodeWithTag("finish-workout").assertIsDisplayed()

        composeRule.onNodeWithTag("workout-weight-${firstSet.id}").performTextInput("75")
        composeRule.onNodeWithTag("active-workout-next").performClick()

        composeRule.onNodeWithText("Exercise 2 of 6").assertIsDisplayed()
        composeRule.onNodeWithTag("active-exercise-${firstTarget.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("active-exercise-${secondTarget.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-workout").assertIsDisplayed()
        composeRule.onNodeWithTag("finish-workout").assertIsDisplayed()
        composeRule.onNodeWithTag("active-workout-previous").performClick()

        composeRule.onNodeWithText("Exercise 1 of 6").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-weight-${firstSet.id}")
            .assertTextContains("75")
    }
}
