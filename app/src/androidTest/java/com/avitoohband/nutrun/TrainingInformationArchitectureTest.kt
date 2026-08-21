package com.avitoohband.nutrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainingInformationArchitectureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scheduleAndWorkoutsModesComposeOnlyTheSelectedSection() {
        val model = TrainingViewModel(null, null)
        var mode by mutableStateOf(TrainingPlanningMode.SCHEDULE)

        composeRule.setContent {
            MaterialTheme {
                TrainingPlanningContent(
                    model = model,
                    mode = mode,
                    onModeChange = { mode = it },
                    onOpenTemplate = {},
                    onAssignDay = {}
                )
            }
        }

        composeRule.onNodeWithTag("training-mode-schedule").assertIsSelected()
        composeRule.onNodeWithTag("weekly-schedule").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-library").assertDoesNotExist()

        composeRule.onNodeWithTag("training-mode-workouts").performClick()

        composeRule.onNodeWithTag("training-mode-workouts").assertIsSelected()
        composeRule.onNodeWithTag("weekly-schedule").assertDoesNotExist()
        composeRule.onNodeWithTag("workout-library").assertIsDisplayed()
    }

    @Test
    fun selectedTrainingModeSurvivesStateRestoration() {
        val model = TrainingViewModel(null, null)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme { TrainingScreen(model) }
        }

        composeRule.onNodeWithTag("training-mode-workouts").performClick()
        composeRule.onNodeWithTag("training-mode-workouts").assertIsSelected()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("training-mode-workouts").assertIsSelected()
        composeRule.onNodeWithTag("workout-library").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly-schedule").assertDoesNotExist()
    }

    @Test
    fun compactScheduleShowsEveryDayAndOffersTodayShortcut() {
        val model = TrainingViewModel(null, null)
        var mode by mutableStateOf(TrainingPlanningMode.SCHEDULE)

        composeRule.setContent {
            MaterialTheme {
                TrainingPlanningContent(
                    model = model,
                    mode = mode,
                    onModeChange = { mode = it },
                    onOpenTemplate = {},
                    onAssignDay = {}
                )
            }
        }

        DayOfWeek.entries.forEach { day ->
            composeRule.onNodeWithTag("day-plan-${day.name}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("workout-library").assertDoesNotExist()

        val today = LocalDate.now().dayOfWeek
        composeRule.onNodeWithTag("today-day-shortcut").performClick()
        composeRule.onNodeWithTag("day-plan-${today.name}").assertIsDisplayed()
    }

    @Test
    fun assignmentSearchScalesToTwentyWorkoutsAndReportsSelection() {
        val templates = (1..20).map { WorkoutTemplate.userCreated("Workout $it") }

        composeRule.setContent {
            MaterialTheme {
                WorkoutAssignmentContent(
                    day = DayOfWeek.SUNDAY,
                    templates = templates,
                    selectedIds = listOf(templates[0].id, templates[1].id),
                    onSave = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("assignment-selected-count")
            .assertIsDisplayed()
            .assertTextContains("2 selected")
        composeRule.onNodeWithTag("assignment-search").performTextInput("WORKOUT 20")
        composeRule.onNodeWithTag("assignment-list").performScrollToNode(
            hasTestTag("assignment-option-${templates.last().id}")
        )
        composeRule.onNodeWithTag("assignment-option-${templates.last().id}").assertIsDisplayed()
        composeRule.onNodeWithTag("assignment-option-${templates[2].id}").assertDoesNotExist()
    }

    @Test
    fun assignmentReorderSavesTheVisibleUniqueOrder() {
        val templates = (1..3).map { WorkoutTemplate.userCreated("Workout $it") }
        var savedIds: List<String>? = null

        composeRule.setContent {
            MaterialTheme {
                WorkoutAssignmentContent(
                    day = DayOfWeek.MONDAY,
                    templates = templates,
                    selectedIds = listOf(templates[0].id, templates[1].id, templates[0].id),
                    onSave = { savedIds = it },
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("assignment-move-down-${templates[0].id}").performClick()
        composeRule.onNodeWithTag("assignment-save").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(templates[1].id, templates[0].id), savedIds)
        }
    }

    @Test
    fun assignmentCancelKeepsRestDayAndSaveReplacesIt() {
        val model = TrainingViewModel(null, null)
        val day = DayOfWeek.SUNDAY
        val workout = model.workoutTemplates.first()
        model.setRestDay(day)

        composeRule.setContent { MaterialTheme { TrainingScreen(model) } }

        composeRule.onNodeWithTag("day-actions-${day.name}").performClick()
        composeRule.onNodeWithTag("assign-day-${day.name}").performClick()
        composeRule.onNodeWithTag("assignment-option-${workout.id}").performClick()
        composeRule.onNodeWithTag("assignment-cancel").performClick()

        composeRule.runOnIdle {
            val plan = model.weeklyDayPlans.single { it.weekday == day }
            assertTrue(plan.isRestDay)
            assertTrue(plan.templateIds.isEmpty())
        }

        composeRule.onNodeWithTag("day-actions-${day.name}").performClick()
        composeRule.onNodeWithTag("assign-day-${day.name}").performClick()
        composeRule.onNodeWithTag("assignment-option-${workout.id}").performClick()
        composeRule.onNodeWithTag("assignment-save").performClick()

        composeRule.runOnIdle {
            val plan = model.weeklyDayPlans.single { it.weekday == day }
            assertFalse(plan.isRestDay)
            assertEquals(listOf(workout.id), plan.templateIds)
        }
    }
}
