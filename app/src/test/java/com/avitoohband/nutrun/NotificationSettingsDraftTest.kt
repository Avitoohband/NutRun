package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsDraftTest {
    private val saved = NotificationSettingsDraft(
        waterEnabled = true,
        intervalMinutes = "60",
        firstReminder = "08:00",
        lastReminder = "22:00",
        trainingEnabled = true,
        dayBeforeReminder = "20:00",
        trainingDayReminder = "08:00",
        trainingDays = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
        supplementMasterEnabled = true,
        supplementDrafts = mapOf(
            "d3" to SupplementReminderDraft(enabled = true, time = "08:00")
        )
    )

    @Test
    fun unchangedDraftIsNotDirty() {
        assertFalse(notificationSettingsDirty(saved, saved.copy()))
    }

    @Test
    fun everySwitchAndTimeCanMakeTheDraftDirty() {
        val changedDrafts = listOf(
            saved.copy(waterEnabled = false),
            saved.copy(intervalMinutes = "90"),
            saved.copy(firstReminder = "07:30"),
            saved.copy(lastReminder = "21:30"),
            saved.copy(trainingEnabled = false),
            saved.copy(dayBeforeReminder = "19:45"),
            saved.copy(trainingDayReminder = "07:15"),
            saved.copy(supplementMasterEnabled = false),
            saved.copy(
                supplementDrafts = saved.supplementDrafts +
                    ("d3" to SupplementReminderDraft(enabled = false, time = "08:00"))
            ),
            saved.copy(
                supplementDrafts = saved.supplementDrafts +
                    ("d3" to SupplementReminderDraft(enabled = true, time = "09:00"))
            )
        )

        changedDrafts.forEach { draft ->
            assertTrue("Expected $draft to be dirty", notificationSettingsDirty(saved, draft))
        }
    }

    @Test
    fun disablingSectionsRetainsTheirConfiguredValues() {
        val disabled = saved.copy(
            waterEnabled = false,
            trainingEnabled = false,
            supplementMasterEnabled = false
        )

        assertEquals("60", disabled.intervalMinutes)
        assertEquals("08:00", disabled.firstReminder)
        assertEquals("22:00", disabled.lastReminder)
        assertEquals("20:00", disabled.dayBeforeReminder)
        assertEquals("08:00", disabled.trainingDayReminder)
        assertEquals(saved.supplementDrafts, disabled.supplementDrafts)
    }

    @Test
    fun summariesShowTheNextWaterTrainingAndSupplementReminder() {
        val now = zoned(2026, 8, 22, 9, 10)
        val supplements = listOf(
            supplement(
                id = "d3",
                weekdays = setOf(DayOfWeek.MONDAY),
                reminderEnabled = false,
                reminderMinute = 12 * 60
            )
        )

        val summaries = nextReminderSummary(saved, supplements, now)

        assertEquals("Next today at 10:00", summaries.water)
        assertEquals("Next today at 20:00 (day before)", summaries.training)
        assertEquals("Next Monday at 08:00", summaries.supplements)
    }

    @Test
    fun summariesAdvancePastTimesThatAlreadyPassed() {
        val now = zoned(2026, 8, 22, 22, 30)

        val summaries = nextReminderSummary(saved, emptyList(), now)

        assertEquals("Next tomorrow at 08:00", summaries.water)
        assertEquals("Next tomorrow at 08:00 (training day)", summaries.training)
    }

    @Test
    fun disabledSummaryShowsTheRetainedScheduleAsSaved() {
        val now = zoned(2026, 8, 22, 22, 30)
        val disabled = saved.copy(
            waterEnabled = false,
            trainingEnabled = false,
            supplementMasterEnabled = false
        )

        val summaries = nextReminderSummary(disabled, emptyList(), now)

        assertEquals("Off - next saved tomorrow at 08:00", summaries.water)
        assertEquals("Off - next saved tomorrow at 08:00 (training day)", summaries.training)
        assertEquals("Off - no supplement reminders configured", summaries.supplements)
    }

    @Test
    fun supplementsSummaryHandlesNoConfiguredReminders() {
        val now = zoned(2026, 8, 22, 9, 10)

        val summaries = nextReminderSummary(
            saved.copy(supplementDrafts = emptyMap()),
            supplements = listOf(
                supplement(
                    id = "d3",
                    weekdays = setOf(DayOfWeek.MONDAY),
                    reminderEnabled = false,
                    reminderMinute = 8 * 60
                )
            ),
            now = now
        )

        assertEquals("No supplement reminders configured", summaries.supplements)
    }

    @Test
    fun invalidTypedTimesProduceSectionSpecificFeedback() {
        val now = zoned(2026, 8, 22, 9, 10)
        val invalid = saved.copy(
            firstReminder = "25:00",
            trainingDayReminder = "tomorrow",
            supplementDrafts = mapOf(
                "d3" to SupplementReminderDraft(enabled = true, time = "8am")
            )
        )

        val summaries = nextReminderSummary(
            invalid,
            supplements = listOf(
                supplement(
                    id = "d3",
                    weekdays = setOf(DayOfWeek.MONDAY),
                    reminderEnabled = true,
                    reminderMinute = 8 * 60
                )
            ),
            now = now
        )

        assertEquals("Check water reminder times", summaries.water)
        assertEquals("Check training reminder times", summaries.training)
        assertEquals("Check supplement reminder times", summaries.supplements)
    }

    private fun supplement(
        id: String,
        weekdays: Set<DayOfWeek>,
        reminderEnabled: Boolean,
        reminderMinute: Int
    ) = Supplement(
        id = id,
        name = id.uppercase(),
        dose = "1 tablet",
        schedule = SupplementSchedule(
            type = RecurrenceType.WEEKDAYS,
            startDate = LocalDate.of(2026, 1, 1),
            weekdays = weekdays
        ),
        reminderEnabled = reminderEnabled,
        reminderMinute = reminderMinute
    )

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): ZonedDateTime = ZonedDateTime.of(
        year,
        month,
        day,
        hour,
        minute,
        0,
        0,
        ZoneId.of("Asia/Jerusalem")
    )
}
