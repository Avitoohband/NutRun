package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementReminderUiRulesTest {
    @Test
    fun draftReconciliationRetainsExistingIdsAndOnlyAddsOrRemovesMembershipChanges() {
        val retained = Supplement(
            id = "retained",
            name = "Retained",
            dose = "100 mg",
            schedule = SupplementSchedule(RecurrenceType.DAILY),
            reminderEnabled = false,
            reminderMinute = 480
        )
        val added = Supplement(
            id = "added",
            name = "Added",
            dose = "200 mg",
            schedule = SupplementSchedule(RecurrenceType.DAILY),
            reminderEnabled = true,
            reminderMinute = 615
        )
        val drafts = mapOf(
            retained.id to SupplementReminderDraft(enabled = true, time = "09:45"),
            "removed" to SupplementReminderDraft(enabled = true, time = "22:10")
        )

        val reconciled = reconcileSupplementReminderDrafts(
            drafts = drafts,
            supplements = listOf(retained, added)
        )

        assertEquals(setOf("retained", "added"), reconciled.keys)
        assertEquals(SupplementReminderDraft(true, "09:45"), reconciled.getValue("retained"))
        assertEquals(SupplementReminderDraft(true, "10:15"), reconciled.getValue("added"))
    }

    @Test
    fun notificationPermissionIsRequestedOnlyForDisabledToEnabledTransitions() {
        assertTrue(shouldRequestSupplementReminderPermission(previousEnabled = null, enabled = true))
        assertTrue(shouldRequestSupplementReminderPermission(previousEnabled = false, enabled = true))
        assertFalse(shouldRequestSupplementReminderPermission(previousEnabled = true, enabled = true))
        assertFalse(shouldRequestSupplementReminderPermission(previousEnabled = true, enabled = false))
        assertFalse(shouldRequestSupplementReminderPermission(previousEnabled = false, enabled = false))
    }

    @Test
    fun reminderOnlyEditPreservesEveryNDaysScheduleCompletionAndUnrelatedData() {
        val original = Supplement(
            id = "interval",
            name = "Iron",
            dose = "18 mg",
            schedule = SupplementSchedule(
                type = RecurrenceType.EVERY_N_DAYS,
                startDate = LocalDate.of(2025, 12, 29),
                intervalDays = 4,
                weekdays = setOf(DayOfWeek.SUNDAY)
            ),
            completedOn = LocalDate.of(2026, 8, 10),
            reminderEnabled = false,
            reminderMinute = 480
        )
        val model = modelWith(original)

        model.updateSupplement(
            id = original.id,
            name = original.name,
            dose = original.dose,
            schedule = resolveSupplementEditSchedule(
                existing = original.schedule,
                selectedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                scheduleChanged = false,
                today = LocalDate.of(2026, 8, 11)
            ),
            reminderEnabled = true,
            reminderMinute = 9 * 60 + 35
        )

        assertEquals(
            original.copy(reminderEnabled = true, reminderMinute = 9 * 60 + 35),
            model.supplements.single()
        )
    }

    @Test
    fun reminderOnlyEditPreservesWeekdayScheduleCompletionAndUnrelatedData() {
        val original = Supplement(
            id = "weekdays",
            name = "Zinc",
            dose = "15 mg",
            schedule = SupplementSchedule(
                type = RecurrenceType.WEEKDAYS,
                startDate = LocalDate.of(2026, 1, 5),
                intervalDays = 3,
                weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
            ),
            completedOn = LocalDate.of(2026, 8, 9),
            reminderEnabled = true,
            reminderMinute = 700
        )
        val model = modelWith(original)

        model.updateSupplement(
            id = original.id,
            name = original.name,
            dose = original.dose,
            schedule = resolveSupplementEditSchedule(
                existing = original.schedule,
                selectedDays = original.schedule.weekdays,
                scheduleChanged = false,
                today = LocalDate.of(2026, 8, 11)
            ),
            reminderEnabled = true,
            reminderMinute = 745
        )

        assertEquals(
            original.copy(reminderMinute = 745),
            model.supplements.single()
        )
    }

    private fun modelWith(supplement: Supplement): TrainingViewModel =
        TrainingViewModel(null, null).also { model ->
            model.supplements.clear()
            model.supplements += supplement
        }
}
