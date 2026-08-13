package com.avitoohband.nutrun

import com.avitoohband.nutrun.reminders.nextSupplementReminder
import com.avitoohband.nutrun.reminders.supplementsDueForReminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupplementReminderRulesTest {
    @Test
    fun disabledSupplementDoesNotWinTheNextReminderSlot() {
        val now = ZonedDateTime.of(2026, 8, 10, 7, 0, 0, 0, zone)

        val reminder = nextSupplementReminder(
            listOf(
                dailySupplement("Disabled", 8 * 60, reminderEnabled = false),
                dailySupplement("Evening", 18 * 60)
            ),
            now
        )

        assertEquals(ZonedDateTime.of(2026, 8, 10, 18, 0, 0, 0, zone), reminder)
    }

    @Test
    fun weekdayReminderSkipsToTheNextMatchingWeekdayAfterTheConfiguredTime() {
        val now = ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 17, 8, 0, 0, 0, zone),
            nextSupplementReminder(
                listOf(mondaySupplement("Vitamin D", 8 * 60)),
                now
            )
        )
    }

    @Test
    fun midnightRolloverUsesTheNextLocalDay() {
        val now = ZonedDateTime.of(2026, 8, 10, 23, 50, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 0, 10, 0, 0, zone),
            nextSupplementReminder(
                listOf(dailySupplement("Midnight", 10)),
                now
            )
        )
    }

    @Test
    fun noEligibleSupplementsReturnsNull() {
        val now = ZonedDateTime.of(2026, 8, 10, 8, 0, 0, 0, zone)

        assertNull(
            nextSupplementReminder(
                listOf(
                    dailySupplement(
                        "Far Future",
                        8 * 60,
                        startDate = LocalDate.of(2037, 1, 1)
                    )
                ),
                now
            )
        )
    }

    @Test
    fun completedSupplementIsSkippedForTodaysReminder() {
        val now = ZonedDateTime.of(2026, 8, 10, 7, 0, 0, 0, zone)
        val today = now.toLocalDate()

        assertEquals(
            ZonedDateTime.of(2026, 8, 10, 18, 0, 0, 0, zone),
            nextSupplementReminder(
                listOf(
                    dailySupplement("Vitamin C", 8 * 60, completedOn = today),
                    dailySupplement("Magnesium", 18 * 60)
                ),
                now
            )
        )
    }

    @Test
    fun dueItemsGroupUntakenSupplementsAtOneMinute() {
        val date = LocalDate.of(2026, 8, 10)
        val due = supplementsDueForReminder(
            listOf(
                dailySupplement("Vitamin D", 8 * 60),
                dailySupplement("Omega-3", 8 * 60),
                dailySupplement("Vitamin C", 9 * 60)
            ),
            date,
            8 * 60
        )

        assertEquals(listOf("Vitamin D", "Omega-3"), due.map(Supplement::name))
    }

    @Test
    fun completedSupplementIsSuppressedAtTheReminderMinute() {
        val date = LocalDate.of(2026, 8, 10)
        val due = supplementsDueForReminder(
            listOf(
                dailySupplement("Vitamin C", 8 * 60, completedOn = date),
                dailySupplement("Magnesium", 8 * 60)
            ),
            date,
            8 * 60
        )

        assertEquals(listOf("Magnesium"), due.map(Supplement::name))
    }

    @Test
    fun nonMatchingMinuteReturnsNoSupplements() {
        val date = LocalDate.of(2026, 8, 10)

        assertEquals(
            emptyList<Supplement>(),
            supplementsDueForReminder(
                listOf(
                    dailySupplement("Vitamin D", 8 * 60),
                    dailySupplement("Omega-3", 8 * 60)
                ),
                date,
                7 * 60
            )
        )
    }

    private fun dailySupplement(
        name: String,
        minute: Int,
        reminderEnabled: Boolean = true,
        completedOn: LocalDate? = null,
        startDate: LocalDate = LocalDate.of(2026, 8, 1)
    ): Supplement = Supplement(
        id = name.lowercase().replace(' ', '-'),
        name = name,
        dose = "1 capsule",
        schedule = SupplementSchedule(RecurrenceType.DAILY, startDate = startDate),
        completedOn = completedOn,
        reminderEnabled = reminderEnabled,
        reminderMinute = minute
    )

    private fun mondaySupplement(
        name: String,
        minute: Int,
        reminderEnabled: Boolean = true,
        completedOn: LocalDate? = null,
        startDate: LocalDate = LocalDate.of(2026, 8, 1)
    ): Supplement = Supplement(
        id = name.lowercase().replace(' ', '-'),
        name = name,
        dose = "1 capsule",
        schedule = SupplementSchedule(
            type = RecurrenceType.WEEKDAYS,
            startDate = startDate,
            weekdays = setOf(DayOfWeek.MONDAY)
        ),
        completedOn = completedOn,
        reminderEnabled = reminderEnabled,
        reminderMinute = minute
    )

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
}
