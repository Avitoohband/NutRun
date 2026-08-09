package com.avitoohband.nutrun

import com.avitoohband.nutrun.reminders.ReminderRescheduleOutcome
import com.avitoohband.nutrun.reminders.SupplementDeliveryDecision
import com.avitoohband.nutrun.reminders.SupplementNotificationDeliveryResult
import com.avitoohband.nutrun.reminders.SupplementReminderScheduleDecision
import com.avitoohband.nutrun.reminders.deliverSupplementNotification
import com.avitoohband.nutrun.reminders.isSupplementDeliveryDateValid
import com.avitoohband.nutrun.reminders.rescheduleReminderSystems
import com.avitoohband.nutrun.reminders.supplementDeliveryId
import com.avitoohband.nutrun.reminders.supplementDeliveryDecision
import com.avitoohband.nutrun.reminders.supplementSchedulingDecision
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class SupplementReminderWorkerTest {
    @Test
    fun deliveryIdentityIncludesAccountDateAndMinute() {
        assertEquals(
            "user:SUPPLEMENT:480:2026-08-10",
            supplementDeliveryId("user", LocalDate.of(2026, 8, 10), 480)
        )
    }

    @Test
    fun deliveryDateCannotCrossMidnight() {
        assertFalse(
            isSupplementDeliveryDateValid(
                intended = LocalDate.of(2026, 8, 10),
                current = LocalDate.of(2026, 8, 11)
            )
        )
    }

    @Test
    fun schedulerBuildsOneAccountScopedWorkInput() {
        val next = ZonedDateTime.of(2026, 8, 10, 8, 0, 0, 0, zone)

        assertEquals(
            SupplementReminderScheduleDecision.Enqueue(
                uniqueWorkName = "supplement-reminder:user",
                userId = "user",
                intendedDate = LocalDate.of(2026, 8, 10),
                minute = 480
            ),
            supplementSchedulingDecision("user", "user", enabled = true, next)
        )
    }

    @Test
    fun disabledNoEligibleAndSignedOutSchedulingCancelsWork() {
        val next = ZonedDateTime.of(2026, 8, 10, 8, 0, 0, 0, zone)

        listOf(
            supplementSchedulingDecision("user", "user", enabled = false, next),
            supplementSchedulingDecision("user", "user", enabled = true, next = null),
            supplementSchedulingDecision("user", null, enabled = true, next)
        ).forEach { decision ->
            assertEquals(SupplementReminderScheduleDecision.Cancel("supplement-reminder:user"), decision)
        }
    }

    @Test
    fun deliveryRejectsWrongAccountAndStaleDate() {
        val supplements = listOf(dailySupplement("Vitamin D", 480))

        assertEquals(
            SupplementDeliveryDecision.Cancel,
            supplementDeliveryDecision("user", "other", true, date, date, 480, supplements)
        )
        assertEquals(
            SupplementDeliveryDecision.Reschedule,
            supplementDeliveryDecision("user", "user", true, date, date.plusDays(1), 480, supplements)
        )
    }

    @Test
    fun deliveryGroupsEligibleSupplementsAtTheRequestedMinute() {
        val decision = supplementDeliveryDecision(
            userId = "user",
            authenticatedUserId = "user",
            enabled = true,
            intendedDate = date,
            currentDate = date,
            minute = 480,
            supplements = listOf(
                dailySupplement("Vitamin D", 480),
                dailySupplement("Omega-3", 480),
                dailySupplement("Vitamin C", 540, completedOn = date)
            )
        ) as SupplementDeliveryDecision.Deliver

        assertEquals(listOf("Vitamin D", "Omega-3"), decision.supplements.map(Supplement::name))
    }

    @Test
    fun failedNotificationPostDoesNotWriteLedgerAndRequestsRetry() = runBlocking {
        var recordCalls = 0

        val result = deliverSupplementNotification(
            deliveryId = "user:SUPPLEMENT:480:2026-08-10",
            alreadyDelivered = { false },
            postNotification = { throw IllegalStateException("notification manager unavailable") },
            recordDelivery = { recordCalls += 1; true }
        )

        assertEquals(SupplementNotificationDeliveryResult.Retry, result)
        assertEquals(0, recordCalls)
    }

    @Test
    fun successfulNotificationPostWritesLedgerAfterPosting() = runBlocking {
        val events = mutableListOf<String>()

        val result = deliverSupplementNotification(
            deliveryId = "user:SUPPLEMENT:480:2026-08-10",
            alreadyDelivered = { false },
            postNotification = { events += "post" },
            recordDelivery = { events += "record"; true }
        )

        assertEquals(SupplementNotificationDeliveryResult.Delivered, result)
        assertEquals(listOf("post", "record"), events)
    }

    @Test
    fun existingLedgerPreventsAnotherNotificationPost() = runBlocking {
        var posted = false

        val result = deliverSupplementNotification(
            deliveryId = "user:SUPPLEMENT:480:2026-08-10",
            alreadyDelivered = { true },
            postNotification = { posted = true },
            recordDelivery = { true }
        )

        assertEquals(SupplementNotificationDeliveryResult.AlreadyDelivered, result)
        assertFalse(posted)
    }

    @Test
    fun receiverFailureStillRunsSupplementsAndRequestsRecovery() = runBlocking {
        val calls = mutableListOf<String>()

        val outcome = rescheduleReminderSystems(
            hydration = { throw IllegalStateException("hydration database unavailable") },
            training = { calls += "training" },
            supplements = { calls += "supplements" }
        )

        assertEquals(ReminderRescheduleOutcome.Failed, outcome)
        assertTrue(outcome.requiresRecovery)
        assertEquals(listOf("training", "supplements"), calls)
    }

    @Test
    fun receiverReschedulingPropagatesCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                rescheduleReminderSystems(
                    hydration = { throw CancellationException("cancelled") },
                    training = {},
                    supplements = {}
                )
            }
        }
    }

    private fun dailySupplement(
        name: String,
        minute: Int,
        completedOn: LocalDate? = null
    ) = Supplement(
        id = name.lowercase().replace(' ', '-'),
        name = name,
        dose = "1 capsule",
        schedule = SupplementSchedule(
            type = RecurrenceType.WEEKDAYS,
            startDate = LocalDate.of(2026, 8, 1),
            weekdays = setOf(DayOfWeek.MONDAY)
        ),
        completedOn = completedOn,
        reminderEnabled = true,
        reminderMinute = minute
    )

    private val date = LocalDate.of(2026, 8, 10)
    private val zone = ZoneId.of("Asia/Jerusalem")
}
