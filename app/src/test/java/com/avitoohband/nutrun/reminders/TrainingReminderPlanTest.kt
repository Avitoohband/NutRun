package com.avitoohband.nutrun.reminders

import com.avitoohband.nutrun.WeeklyDayPlan
import com.avitoohband.nutrun.TrainingScheduleOverride
import com.avitoohband.nutrun.WorkoutTemplate
import com.avitoohband.nutrun.builtInExerciseCatalog
import com.avitoohband.nutrun.decodeTrainingState
import com.avitoohband.nutrun.encodeTrainingState
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingReminderPlanTest {
    private val monday = LocalDate.of(2026, 8, 17)

    @Test
    fun reminderListsEveryAssignedWorkoutInPlanOrder() {
        val push = WorkoutTemplate("push", "Push")
        val walk = WorkoutTemplate("walk", "Walk")
        val state = persistedState(
            templates = listOf(push, walk),
            plans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf(push.id, walk.id)))
        )

        assertEquals(listOf("Push", "Walk"), trainingReminderNames(state, monday))
    }

    @Test
    fun explicitRestDayAndUnplannedDayProduceNoNames() {
        val resting = persistedState(plans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, isRestDay = true)))

        assertTrue(trainingReminderNames(resting, monday).isEmpty())
        assertTrue(trainingReminderNames(persistedState(), monday.plusDays(1)).isEmpty())
    }

    @Test
    fun movedAndSkippedOverridesAffectOnlyTheirTargetedOccurrence() {
        val push = WorkoutTemplate("push", "Push")
        val moved = persistedState(
            templates = listOf(push),
            plans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf(push.id))),
            overrides = listOf(TrainingScheduleOverride(push.id, monday, monday.plusDays(1)))
        )
        val skipped = persistedState(
            templates = listOf(push),
            plans = listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf(push.id))),
            overrides = listOf(TrainingScheduleOverride(push.id, monday, null, skipped = true))
        )

        assertTrue(trainingReminderNames(moved, monday).isEmpty())
        assertEquals(listOf("Push"), trainingReminderNames(moved, monday.plusDays(1)))
        assertTrue(trainingReminderNames(skipped, monday).isEmpty())
    }

    private fun persistedState(
        templates: List<WorkoutTemplate> = emptyList(),
        plans: List<WeeklyDayPlan> = emptyList(),
        overrides: List<TrainingScheduleOverride> = emptyList()
    ) = requireNotNull(
        decodeTrainingState(
            encodeTrainingState(
                workoutTemplates = templates,
                weeklyDayPlans = plans,
                scheduleOverrides = overrides
            ),
            builtInExerciseCatalog()
        )
    )
}
