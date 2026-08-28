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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActiveWorkoutContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun layoutToggleShowsListAndGridOptions() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("active-workout-layout-toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("active-workout-layout-list").assertIsDisplayed()
        composeRule.onNodeWithTag("active-workout-layout-grid").assertIsDisplayed()
    }

    @Test
    fun switchingToGridRetainsPartialDraftValues() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        val firstTarget = session.exercises.first()
        val firstSet = model.activeSetLogs.getValue(firstTarget.id).first()

        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("workout-weight-${firstSet.id}").performClick()
        composeRule.onNodeWithTag("workout-weight-${firstSet.id}").performTextClearance()
        composeRule.onNodeWithTag("workout-weight-${firstSet.id}").performTextInput("75")
        composeRule.onNodeWithTag("active-workout-layout-grid").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("active-workout-set-grid").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("workout-weight-${firstSet.id}")
            .assertTextContains("75")
    }

    @Test
    fun gridModeAllowsEditingAndCompletingSets() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        val target = session.exercises.first()
        val set = model.activeSetLogs.getValue(target.id).first()

        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("active-workout-layout-grid").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("active-workout-set-grid").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("workout-reps-${set.id}").performClick()
        composeRule.onNodeWithTag("workout-reps-${set.id}").performTextClearance()
        composeRule.onNodeWithTag("workout-reps-${set.id}").performTextInput("8")
        composeRule.onNodeWithTag("workout-weight-${set.id}").performClick()
        composeRule.onNodeWithTag("workout-weight-${set.id}").performTextClearance()
        composeRule.onNodeWithTag("workout-weight-${set.id}").performTextInput("60")
        composeRule.onNodeWithTag("workout-set-completed-${set.id}").performClick()

        composeRule.runOnIdle {
            val saved = model.activeSetLogs.getValue(target.id).first()
            assertEquals(8, saved.reps)
            assertEquals(60.0, saved.weightKg!!, 0.001)
            assertTrue(saved.completed)
        }
    }

    @Test
    fun gridModeShowsMinutesColumnForDurationTargets() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-sunday-cardio" }
        model.startWorkout(session.id)
        val target = session.exercises.first()
        val set = model.activeSetLogs.getValue(target.id).first()

        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("active-workout-layout-grid").performClick()
        composeRule.onNodeWithTag("workout-minutes-${set.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-reps-${set.id}").assertDoesNotExist()
    }

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
        composeRule.onNodeWithTag("active-workout-elapsed").assertIsDisplayed()
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

        composeRule.onNodeWithTag("workout-weight-${firstSet.id}").performTextClearance()
        composeRule.onNodeWithTag("workout-weight-${firstSet.id}").performTextInput(".")
        composeRule.onNodeWithTag("active-workout-next").performClick()
        composeRule.onNodeWithTag("active-workout-previous").performClick()

        composeRule.onNodeWithTag("workout-weight-${firstSet.id}")
            .assertTextContains(".")
    }

    @Test
    fun incompleteFinishRequiresExplicitConfirmation() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        var finishRequests = 0
        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = { finishRequests += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("finish-workout").performClick()

        composeRule.onNodeWithTag("incomplete-workout-review").assertIsDisplayed()
        composeRule.onNodeWithText("0 of 6 targets complete.").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, finishRequests) }
        composeRule.onNodeWithTag("keep-training").performClick()
        composeRule.onNodeWithTag("incomplete-workout-review").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, finishRequests) }

        composeRule.onNodeWithTag("finish-workout").performClick()
        composeRule.onNodeWithTag("finish-anyway").performClick()
        composeRule.runOnIdle { assertEquals(1, finishRequests) }
    }

    @Test
    fun completeWorkoutFinishesWithoutReview() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-sunday-cardio" }
        model.startWorkout(session.id)
        model.toggleExerciseComplete(session.exercises.first().id, completed = true)
        var finishRequests = 0
        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = { finishRequests += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("finish-workout").performClick()

        composeRule.onNodeWithTag("incomplete-workout-review").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, finishRequests) }
    }

    @Test
    fun quickWorkoutEmptyStateShowsAddFirstExercise() {
        val model = TrainingViewModel(null, null)
        model.startQuickWorkout("Evening extras")
        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("quick-workout-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-workout-add-first").assertIsDisplayed()
    }

    @Test
    fun stickyRestTimerRemainsVisibleAndCanBeSkipped() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        model.startRestTimer(seconds = 90)
        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("active-rest-timer-sticky").assertIsDisplayed()
        composeRule.onNodeWithTag("active-rest-timer-add").performClick()
        composeRule.onNodeWithTag("active-rest-timer-sticky").assertIsDisplayed()
        composeRule.onNodeWithTag("active-rest-timer-skip").performClick()
        composeRule.onNodeWithTag("active-rest-timer-sticky").assertDoesNotExist()
    }

    @Test
    fun skipAndUndoActiveExerciseUpdatesCard() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        model.startWorkout(session.id)
        val target = session.exercises.first()
        composeRule.setContent {
            MaterialTheme {
                ActiveWorkoutContent(
                    model = model,
                    onEditRestTimer = {},
                    onCancelRequest = {},
                    onFinishRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("active-skip-${target.id}").performClick()
        composeRule.onNodeWithTag("skipped-exercise-${target.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("active-undo-skip-${target.id}").performClick()
        composeRule.onNodeWithTag("active-exercise-${target.id}").assertIsDisplayed()
    }
}
