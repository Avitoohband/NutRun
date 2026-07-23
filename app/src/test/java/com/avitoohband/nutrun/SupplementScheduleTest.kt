package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
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
}
