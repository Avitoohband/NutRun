package com.avitoohband.nutrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.time.DayOfWeek
import java.time.LocalDate
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
}
