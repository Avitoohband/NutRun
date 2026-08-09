package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementScheduleTest {
    @Test
    fun everyNDaysUsesTheFixedStartDate() {
        val schedule = SupplementSchedule(
            type = RecurrenceType.EVERY_N_DAYS,
            startDate = LocalDate.of(2026, 7, 1),
            intervalDays = 3
        )

        assertTrue(schedule.isDueOn(LocalDate.of(2026, 7, 1)))
        assertFalse(schedule.isDueOn(LocalDate.of(2026, 7, 2)))
        assertTrue(schedule.isDueOn(LocalDate.of(2026, 7, 4)))
        assertTrue(schedule.isDueOn(LocalDate.of(2026, 7, 7)))
    }

    @Test
    fun weekdayScheduleOnlyIncludesSelectedDays() {
        val schedule = SupplementSchedule(
            type = RecurrenceType.WEEKDAYS,
            startDate = LocalDate.of(2026, 7, 1),
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        )

        assertTrue(schedule.isDueOn(LocalDate.of(2026, 7, 3)))
        assertFalse(schedule.isDueOn(LocalDate.of(2026, 7, 4)))
    }

    @Test
    fun dueListOnlyShowsSupplementsScheduledForTheRequestedWeekday() {
        val startDate = LocalDate.of(2026, 7, 1)
        val sundayMondayFriday = SupplementSchedule(
            type = RecurrenceType.WEEKDAYS,
            startDate = startDate,
            weekdays = setOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.FRIDAY
            )
        )
        val tuesdayOnly = SupplementSchedule(
            type = RecurrenceType.WEEKDAYS,
            startDate = startDate,
            weekdays = setOf(DayOfWeek.TUESDAY)
        )
        val supplements = listOf(
            Supplement("d3", "Vitamin D3", "2,000 IU", sundayMondayFriday),
            Supplement("iron", "Iron", "18 mg", tuesdayOnly)
        )

        assertEquals(
            listOf("Vitamin D3"),
            dueSupplementsForDate(supplements, LocalDate.of(2026, 7, 3))
                .map(Supplement::name)
        )
        assertTrue(
            dueSupplementsForDate(supplements, LocalDate.of(2026, 7, 4))
                .isEmpty()
        )
        assertEquals(
            listOf("Vitamin D3"),
            dueSupplementsForDate(supplements, LocalDate.of(2026, 7, 5))
                .map(Supplement::name)
        )
    }

    @Test
    fun weekdayLabelIsOrderedForManagementDisplay() {
        val schedule = SupplementSchedule(
            type = RecurrenceType.WEEKDAYS,
            weekdays = setOf(
                DayOfWeek.FRIDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.SUNDAY
            )
        )

        assertEquals("Mon, Fri, Sun", schedule.label())
    }

    @Test
    fun dailyScheduleSelectsEveryWeekdayForEditing() {
        val schedule = SupplementSchedule(type = RecurrenceType.DAILY)

        assertEquals(DayOfWeek.entries.toSet(), schedule.selectedWeekdays())
    }

    @Test
    fun legacyIntervalScheduleSelectsDueDaysInTheCurrentWeekForEditing() {
        val schedule = SupplementSchedule(
            type = RecurrenceType.EVERY_N_DAYS,
            startDate = LocalDate.of(2026, 7, 20),
            intervalDays = 2
        )

        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SUNDAY
            ),
            schedule.selectedWeekdays(LocalDate.of(2026, 7, 23))
        )
    }
}
