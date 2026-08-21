package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanningTest {
    private val sunday = LocalDate.of(2026, 7, 19)
    private val tuesday = LocalDate.of(2026, 7, 21)

    @Test
    fun oneTemplateCanBeAssignedToSeveralDays() {
        val walk = WorkoutTemplate("walk", "Walk", emptyList())
        val plans = listOf(
            WeeklyDayPlan(DayOfWeek.SUNDAY, listOf("walk")),
            WeeklyDayPlan(DayOfWeek.TUESDAY, listOf("walk"))
        )

        assertEquals(listOf(walk), templatesForDate(listOf(walk), plans, emptyList(), sunday))
        assertEquals(listOf(walk), templatesForDate(listOf(walk), plans, emptyList(), tuesday))
    }

    @Test
    fun severalTemplatesPreserveAssignmentOrder() {
        val plans = replaceDayAssignments(emptyList(), DayOfWeek.MONDAY, listOf("push", "walk", "push"))

        assertEquals(listOf("push", "walk"), plans.single().templateIds)
    }

    @Test
    fun restDayClearsAssignmentsAndAssignmentClearsRestDay() {
        val resting = markRestDay(
            listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push"))),
            DayOfWeek.MONDAY
        )

        assertTrue(resting.single().isRestDay)
        assertTrue(resting.single().templateIds.isEmpty())

        val assigned = replaceDayAssignments(resting, DayOfWeek.MONDAY, listOf("push"))

        assertFalse(assigned.single().isRestDay)
    }

    @Test
    fun removingFinalAssignmentLeavesDayUnplanned() {
        val plans = replaceDayAssignments(
            listOf(WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push"))),
            DayOfWeek.MONDAY,
            emptyList()
        )

        assertFalse(plans.single().isRestDay)
        assertTrue(plans.single().templateIds.isEmpty())
    }

    @Test
    fun overridesSkipRecurringAssignmentsAndAppendMovedTemplates() {
        val walk = WorkoutTemplate("walk", "Walk")
        val push = WorkoutTemplate("push", "Push")
        val pull = WorkoutTemplate("pull", "Pull")
        val plans = listOf(
            WeeklyDayPlan(DayOfWeek.SUNDAY, listOf("walk")),
            WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push", "pull"))
        )
        val overrides = listOf(
            TrainingScheduleOverride("walk", sunday, tuesday.plusDays(6)),
            TrainingScheduleOverride("push", tuesday.plusDays(6), null, skipped = true)
        )

        assertEquals(listOf(pull, walk), templatesForDate(listOf(walk, push, pull), plans, overrides, tuesday.plusDays(6)))
        assertTrue(templatesForDate(listOf(walk, push, pull), plans, overrides, sunday).isEmpty())
    }

    @Test
    fun exerciseTargetsRejectSetCountsOutsideOneToTwenty() {
        val exercise = Exercise("test", "Test", "Test", "Test", "Test", "Test", "Test")

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ExerciseTarget("too-few", exercise, sets = 0)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ExerciseTarget("too-many", exercise, sets = 21)
        }
    }

    @Test
    fun userCreatedWorkoutGetsCanonicalUuidBackedId() {
        val workout = WorkoutTemplate.userCreated("Custom workout")

        assertTrue(workout.isUserCreated)
        assertTrue(workout.id.startsWith("workout-"))
        assertEquals(workout.id.removePrefix("workout-"), java.util.UUID.fromString(workout.id.removePrefix("workout-")).toString())
    }

    @Test
    fun userCreatedWorkoutRejectsNonUuidId() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WorkoutTemplate.userCreated(name = "Custom workout", id = "workout-not-a-uuid")
        }
    }

    @Test
    fun replaceDayAssignmentsNormalizesDuplicateWeekdays() {
        val plans = replaceDayAssignments(
            listOf(
                WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push")),
                WeeklyDayPlan(DayOfWeek.TUESDAY, listOf("walk")),
                WeeklyDayPlan(DayOfWeek.MONDAY, listOf("pull"))
            ),
            DayOfWeek.MONDAY,
            listOf("legs")
        )

        assertEquals(
            listOf(
                WeeklyDayPlan(DayOfWeek.MONDAY, listOf("legs")),
                WeeklyDayPlan(DayOfWeek.TUESDAY, listOf("walk"))
            ),
            plans
        )
    }

    @Test
    fun markRestDayNormalizesDuplicateWeekdays() {
        val plans = markRestDay(
            listOf(
                WeeklyDayPlan(DayOfWeek.MONDAY, listOf("push")),
                WeeklyDayPlan(DayOfWeek.MONDAY, listOf("pull"))
            ),
            DayOfWeek.MONDAY
        )

        assertEquals(listOf(WeeklyDayPlan(DayOfWeek.MONDAY, isRestDay = true)), plans)
    }

    @Test
    fun templatesForDateRejectsDuplicateWeekdays() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            templatesForDate(
                listOf(WorkoutTemplate("push", "Push"), WorkoutTemplate("pull", "Pull")),
                listOf(
                    WeeklyDayPlan(DayOfWeek.SUNDAY, listOf("push")),
                    WeeklyDayPlan(DayOfWeek.SUNDAY, listOf("pull"))
                ),
                emptyList(),
                sunday
            )
        }
    }

    @Test
    fun workoutTemplateSearchIsCaseInsensitiveAndStable() {
        val templates = listOf(
            WorkoutTemplate.userCreated("Evening Push"),
            WorkoutTemplate.userCreated("Morning Pull"),
            WorkoutTemplate.userCreated("Push Accessories")
        )

        assertEquals(
            listOf(templates[0], templates[2]),
            filterWorkoutTemplates(templates, "  PUSH ")
        )
        assertEquals(templates, filterWorkoutTemplates(templates, ""))
    }

    @Test
    fun assignedWorkoutMovementPreservesOrderAndRemovesDuplicates() {
        assertEquals(
            listOf("pull", "push", "legs"),
            moveAssignedWorkout(listOf("push", "pull", "push", "legs"), 0, 1)
        )
        assertEquals(
            listOf("push", "pull", "legs"),
            moveAssignedWorkout(listOf("push", "pull", "legs"), -1, 2)
        )
    }
}
