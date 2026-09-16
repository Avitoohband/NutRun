package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GtgContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateOpensPlanEditor() {
        val model = TrainingViewModel(null, null)
        composeRule.setContent {
            NutRunTheme { GtgContent(model) }
        }

        composeRule.onNodeWithTag("gtg-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("gtg-create-plan").performClick()
        composeRule.onNodeWithText("New GTG plan").assertIsDisplayed()
    }

    @Test
    fun scheduledPlanCanOpenLogDialog() {
        val model = TrainingViewModel(null, null)
        val exercise = model.exerciseLibrary.first()
        model.saveGtgPlan(
            GtgPlan(
                id = "plan-1",
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                measure = GtgMeasure.REPS,
                targetPerSet = 5,
                dailySetGoal = 4,
                weekdays = setOf(LocalDate.now().dayOfWeek)
            )
        )
        composeRule.setContent {
            NutRunTheme { GtgTodaySection(model) }
        }

        composeRule.onNodeWithTag("today-gtg-plan-plan-1").assertIsDisplayed()
        composeRule.onNodeWithTag("today-gtg-log-plan-1").performClick()
        composeRule.onNodeWithText("Log GTG set").assertIsDisplayed()
    }

    @Test
    fun progressKeepsRepsSeparateFromHoldSeconds() {
        val model = TrainingViewModel(null, null)
        val exercises = model.exerciseLibrary.take(2)
        val date = LocalDate.now()
        val repsPlan = plan("reps-plan", exercises[0], GtgMeasure.REPS)
        val holdPlan = plan("hold-plan", exercises[1], GtgMeasure.HOLD_SECONDS)
        model.saveGtgPlan(repsPlan)
        model.saveGtgPlan(holdPlan)
        model.saveGtgLog(log("reps-log", repsPlan, date, reps = 5))
        model.saveGtgLog(log("hold-log", holdPlan, date, durationSeconds = 30))

        composeRule.setContent {
            NutRunTheme { GtgProgressContent(model, ProgressRange.DAYS_7) }
        }

        composeRule.onNodeWithText("5 reps", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("30 sec", substring = true).assertIsDisplayed()
    }

    @Test
    fun progressPlanOpensDatedLogsForEditingAndDeletion() {
        val model = TrainingViewModel(null, null)
        val date = LocalDate.now()
        val plan = plan("review-plan", model.exerciseLibrary.first(), GtgMeasure.REPS)
        model.saveGtgPlan(plan)
        model.saveGtgLog(log("review-log", plan, date, reps = 7))

        composeRule.setContent {
            NutRunTheme { GtgProgressContent(model, ProgressRange.DAYS_7) }
        }

        composeRule.onNodeWithTag("gtg-progress-review-plan").performClick()
        composeRule.onNodeWithTag("gtg-progress-day-review-plan-$date").performClick()
        composeRule.onNodeWithTag("gtg-progress-log-review-log").assertIsDisplayed()
        composeRule.onNodeWithTag("gtg-progress-edit-review-log").performClick()
        composeRule.onNodeWithText("Log GTG set").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag("gtg-progress-review-plan").performClick()
        composeRule.onNodeWithTag("gtg-progress-day-review-plan-$date").performClick()
        composeRule.onNodeWithTag("gtg-progress-delete-review-log").performClick()
        composeRule.onNodeWithTag("gtg-progress-log-review-log").assertDoesNotExist()
        assertEquals(0, model.gtgState.logs.size)
    }

    private fun plan(id: String, exercise: Exercise, measure: GtgMeasure) = GtgPlan(
        id = id,
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        measure = measure,
        targetPerSet = 5,
        dailySetGoal = 4,
        weekdays = setOf(DayOfWeek.MONDAY)
    )

    private fun log(
        id: String,
        plan: GtgPlan,
        date: LocalDate,
        reps: Int? = null,
        durationSeconds: Int? = null
    ) = GtgLog(
        id = id,
        planId = plan.id,
        exerciseId = plan.exerciseId,
        exerciseName = plan.exerciseName,
        measure = plan.measure,
        reps = reps,
        durationSeconds = durationSeconds,
        weightKg = null,
        rir = null,
        notes = "",
        performedAtMillis = System.currentTimeMillis() - 1_000L,
        zoneId = java.time.ZoneId.systemDefault().id,
        performedOn = date,
        dailySetGoalSnapshot = plan.dailySetGoal
    )
}
