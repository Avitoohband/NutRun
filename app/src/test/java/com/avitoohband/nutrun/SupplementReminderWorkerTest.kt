package com.avitoohband.nutrun

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import com.avitoohband.nutrun.reminders.ReminderRescheduleOutcome
import com.avitoohband.nutrun.reminders.ReminderRescheduleReceiverDispatcher
import com.avitoohband.nutrun.reminders.ReminderRescheduleRecoveryScheduler
import com.avitoohband.nutrun.reminders.ReminderRescheduleUserSchedulers
import com.avitoohband.nutrun.reminders.ReminderRescheduleUserStore
import com.avitoohband.nutrun.reminders.ReminderSystem
import com.avitoohband.nutrun.reminders.ReminderWorkEnqueuer
import com.avitoohband.nutrun.reminders.SupplementDeliveryClaim
import com.avitoohband.nutrun.reminders.SupplementDeliveryDecision
import com.avitoohband.nutrun.reminders.SupplementDeliveryStore
import com.avitoohband.nutrun.reminders.SupplementNotificationDeliveryResult
import com.avitoohband.nutrun.reminders.SupplementReminderScheduler
import com.avitoohband.nutrun.reminders.SupplementReminderSchedulerStore
import com.avitoohband.nutrun.reminders.SupplementReminderScheduleDecision
import com.avitoohband.nutrun.reminders.claimedSupplementDelivery
import com.avitoohband.nutrun.reminders.deliverSupplementNotification
import com.avitoohband.nutrun.reminders.isSupplementDeliveryDateValid
import com.avitoohband.nutrun.reminders.rescheduleReminderSystems
import com.avitoohband.nutrun.reminders.rescheduleReminderSystemsForUser
import com.avitoohband.nutrun.reminders.runReminderRecoveryAttempt
import com.avitoohband.nutrun.reminders.supplementDeliveryId
import com.avitoohband.nutrun.reminders.supplementDeliveryDecision
import com.avitoohband.nutrun.reminders.supplementSchedulingDecision
import com.avitoohband.nutrun.reminders.recoveryWorkResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
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

        assertEquals(ReminderRescheduleOutcome(setOf(ReminderSystem.HYDRATION)), outcome)
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

    @Test
    fun schedulerEnqueuesActualReplaceWorkRequestWithExpectedInputs() = runBlocking {
        val enqueuer = RecordingWorkEnqueuer()
        val scheduler = SupplementReminderScheduler(
            store = FakeSchedulerStore("user", listOf(dailySupplement("Vitamin D", 480))),
            enqueuer = enqueuer,
            now = { ZonedDateTime.of(2026, 8, 10, 7, 0, 0, 0, it) }
        )

        scheduler.schedule("user", enabledSettings())

        assertEquals(1, enqueuer.enqueued.size)
        val work = enqueuer.enqueued.single()
        assertEquals("supplement-reminder:user", work.name)
        assertEquals(ExistingWorkPolicy.REPLACE, work.policy)
        assertEquals("user", work.request.workSpec.input.getString("user_id"))
        assertEquals("2026-08-10", work.request.workSpec.input.getString("date"))
        assertEquals(480, work.request.workSpec.input.getInt("minute", -1))
    }

    @Test
    fun actualSchedulerCancelsDisabledNoEligibleAndSignedOutWork() = runBlocking {
        val scenarios = listOf(
            FakeSchedulerStore("user", listOf(dailySupplement("Vitamin D", 480))) to enabledSettings(false),
            FakeSchedulerStore("user", emptyList()) to enabledSettings(),
            FakeSchedulerStore(null, listOf(dailySupplement("Vitamin D", 480))) to enabledSettings()
        )

        scenarios.forEach { (store, settings) ->
            val enqueuer = RecordingWorkEnqueuer()
            SupplementReminderScheduler(
                store = store,
                enqueuer = enqueuer,
                now = { ZonedDateTime.of(2026, 8, 10, 7, 0, 0, 0, it) }
            ).schedule("user", settings)
            assertEquals(listOf("supplement-reminder:user"), enqueuer.cancelled)
            assertTrue(enqueuer.enqueued.isEmpty())
        }
    }

    @Test
    fun claimedDeliveryReleasesPendingClaimAfterPostFailure() = runBlocking {
        val store = FakeDeliveryStore()

        val result = claimedSupplementDelivery(
            store = store,
            claim = claim(),
            nowMillis = 1_000,
            postNotification = { throw IllegalStateException("post failed") }
        )

        assertEquals(SupplementNotificationDeliveryResult.Retry, result)
        assertEquals(1, store.releaseCalls)
        assertEquals(SupplementDeliveryClaim.Acquired, store.acquire(claim(), 2_000))
    }

    @Test
    fun claimedDeliveryDoesNotRepostWhenFinalizeFails() = runBlocking {
        val store = FakeDeliveryStore(finalizeSucceeds = false)
        var posts = 0

        val result = claimedSupplementDelivery(store, claim(), 1_000) { posts += 1 }

        assertEquals(SupplementNotificationDeliveryResult.FinalizePending, result)
        assertEquals(1, posts)
        assertEquals(SupplementDeliveryClaim.Posted, store.acquire(claim(), 1_001))
    }

    @Test
    fun thrownFinalizeAfterPostDoesNotReleaseOrRepeatTheNotification() = runBlocking {
        val store = FakeDeliveryStore(throwOnFinalize = true)
        var posts = 0

        assertEquals(SupplementNotificationDeliveryResult.FinalizePending, claimedSupplementDelivery(store, claim(), 1_000) { posts += 1 })
        assertEquals(SupplementNotificationDeliveryResult.FinalizePending, claimedSupplementDelivery(store, claim(), 2_000) { posts += 1 })
        assertEquals(1, posts)
    }

    @Test
    fun releaseFailureAfterPostFailureRemainsRetryableUntilClaimExpires() = runBlocking {
        val store = FakeDeliveryStore(throwOnRelease = true)
        assertEquals(SupplementNotificationDeliveryResult.Retry, claimedSupplementDelivery(store, claim(), 1_000) { throw IllegalStateException() })
        assertEquals(SupplementNotificationDeliveryResult.Retry, claimedSupplementDelivery(store, claim(), 1_001) {})
        assertEquals(SupplementNotificationDeliveryResult.Delivered, claimedSupplementDelivery(store, claim(), 1_000 + 15 * 60_000) {})
    }

    @Test
    fun pendingClaimSuppressesConcurrentDeliveryAndExpiresForRetry() = runBlocking {
        val store = FakeDeliveryStore()
        assertEquals(SupplementDeliveryClaim.Acquired, store.acquire(claim(), 1_000))

        assertEquals(SupplementDeliveryClaim.Pending, store.acquire(claim(), 1_001))
        assertEquals(SupplementDeliveryClaim.Acquired, store.acquire(claim(), 1_000 + 15 * 60_000))
    }

    @Test
    fun finalizedClaimSuppressesFutureDelivery() = runBlocking {
        val store = FakeDeliveryStore()
        assertEquals(SupplementNotificationDeliveryResult.Delivered, claimedSupplementDelivery(store, claim(), 1_000) {})

        assertEquals(SupplementDeliveryClaim.Delivered, store.acquire(claim(), 2_000))
    }

    @Test
    fun recoverySchedulerKeepsUniqueExponentialBackoffWork() = runBlocking {
        val enqueuer = RecordingWorkEnqueuer()
        val state = FakeRecoveryStateStore()
        val scheduler = ReminderRescheduleRecoveryScheduler(enqueuer, state)

        scheduler.schedule("user", setOf(ReminderSystem.SUPPLEMENTS))

        val work = enqueuer.enqueued.single()
        assertEquals("reminder-reschedule-recovery:user", work.name)
        assertEquals(ExistingWorkPolicy.KEEP, work.policy)
        assertEquals("state", work.request.workSpec.input.getString("systems"))
        assertEquals(BackoffPolicy.EXPONENTIAL, work.request.workSpec.backoffPolicy)
        assertEquals(15_000L, work.request.workSpec.backoffDelayDuration)
        assertEquals(setOf(ReminderSystem.SUPPLEMENTS), state.current("user"))
    }

    @Test
    fun recoveryStateMergesDisjointFailuresPerAccountAndDropsRecoveredSystems() = runBlocking {
        val state = FakeRecoveryStateStore()
        val enqueuer = RecordingWorkEnqueuer()
        val scheduler = ReminderRescheduleRecoveryScheduler(enqueuer, state)
        scheduler.schedule("user", setOf(ReminderSystem.HYDRATION))
        scheduler.schedule("user", setOf(ReminderSystem.SUPPLEMENTS))
        scheduler.schedule("other", setOf(ReminderSystem.TRAINING))
        state.completeAttempt(
            "user",
            attempted = setOf(ReminderSystem.HYDRATION),
            stillFailing = emptySet()
        )

        assertEquals(setOf(ReminderSystem.SUPPLEMENTS), state.current("user"))
        assertEquals(setOf(ReminderSystem.TRAINING), state.current("other"))
        assertEquals(
            listOf(
                "reminder-reschedule-recovery:user",
                "reminder-reschedule-recovery:user",
                "reminder-reschedule-recovery:other"
            ),
            enqueuer.enqueued.map { it.name }
        )
        assertTrue(enqueuer.enqueued.all { it.policy == ExistingWorkPolicy.KEEP })
    }

    @Test
    fun recoveryAttemptReadsMergedFailuresAndClearsOnlySystemsThatRecovered() = runBlocking {
        val state = FakeRecoveryStateStore()
        state.merge("user", setOf(ReminderSystem.HYDRATION))
        state.merge("other", setOf(ReminderSystem.TRAINING))
        val attempted = mutableListOf<Set<ReminderSystem>>()

        runReminderRecoveryAttempt("user", state) { systems ->
            attempted += systems
            state.merge("user", setOf(ReminderSystem.SUPPLEMENTS))
            ReminderRescheduleOutcome(emptySet())
        }
        runReminderRecoveryAttempt("user", state) { systems ->
            attempted += systems
            ReminderRescheduleOutcome(setOf(ReminderSystem.SUPPLEMENTS))
        }
        runReminderRecoveryAttempt("user", state) { systems ->
            attempted += systems
            ReminderRescheduleOutcome.Complete
        }

        assertEquals(
            listOf(
                setOf(ReminderSystem.HYDRATION),
                setOf(ReminderSystem.SUPPLEMENTS),
                setOf(ReminderSystem.SUPPLEMENTS)
            ),
            attempted
        )
        assertTrue(state.current("user").isEmpty())
        assertEquals(setOf(ReminderSystem.TRAINING), state.current("other"))
    }

    @Test
    fun receiverDispatcherRunsRealUserRescheduleAndHandsCurrentFailuresToRecovery() = runBlocking {
        val store = FakeReceiverStore()
        val schedulers = FakeReceiverSchedulers(failHydration = true)
        val recovered = mutableListOf<Pair<String, Set<ReminderSystem>>>()
        val dispatcher = ReminderRescheduleReceiverDispatcher(
            authenticatedUserId = { "user" },
            reschedule = { userId, systems ->
                rescheduleReminderSystemsForUser(userId, systems, store, schedulers, zone)
            },
            scheduleRecovery = { userId, failures -> recovered += userId to failures }
        )

        dispatcher.dispatch()

        assertEquals(zone.id, store.savedSupplementSettings.single().timezoneId)
        assertEquals(listOf("supplements"), schedulers.calls.filter { it == "supplements" })
        assertEquals(listOf("user" to setOf(ReminderSystem.HYDRATION)), recovered)
    }

    @Test
    fun recoveryExhaustionReturnsFailureAtTheNamedAttemptLimit() {
        assertEquals(
            com.avitoohband.nutrun.reminders.ReminderRecoveryResult.Failure,
            recoveryWorkResult(attempt = 2, requiresRecovery = true)
        )
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

    private fun enabledSettings(enabled: Boolean = true) =
        com.avitoohband.nutrun.data.SupplementReminderSettingsEntity(
            userId = "user",
            enabled = enabled,
            timezoneId = zone.id
        )

    private fun claim() = com.avitoohband.nutrun.reminders.SupplementDeliveryClaimRequest(
        id = "user:SUPPLEMENT:480:2026-08-10",
        userId = "user",
        reminderType = "SUPPLEMENT:480",
        trainingDate = "2026-08-10"
    )

    private class FakeSchedulerStore(
        private val userId: String?,
        private val supplements: List<Supplement>
    ) : SupplementReminderSchedulerStore {
        override suspend fun authenticatedUserId(): String? = userId
        override suspend fun supplements(userId: String): List<Supplement> = supplements
    }

    private class RecordingWorkEnqueuer : ReminderWorkEnqueuer {
        data class Enqueued(
            val name: String,
            val policy: ExistingWorkPolicy,
            val request: OneTimeWorkRequest
        )

        val enqueued = mutableListOf<Enqueued>()
        val cancelled = mutableListOf<String>()

        override fun enqueue(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
            enqueued += Enqueued(name, policy, request)
        }

        override fun cancel(name: String) {
            cancelled += name
        }
    }

    private class FakeDeliveryStore(
        private val finalizeSucceeds: Boolean = true,
        private val throwOnFinalize: Boolean = false,
        private val throwOnRelease: Boolean = false
    ) : SupplementDeliveryStore {
        private var state = SupplementDeliveryClaim.Available
        private var claimedAtMillis = 0L
        var releaseCalls = 0

        override suspend fun acquire(
            claim: com.avitoohband.nutrun.reminders.SupplementDeliveryClaimRequest,
            nowMillis: Long
        ): SupplementDeliveryClaim {
            if (state == SupplementDeliveryClaim.Pending && nowMillis - claimedAtMillis >= 15 * 60_000) {
                state = SupplementDeliveryClaim.Available
            }
            return when (state) {
                SupplementDeliveryClaim.Available -> {
                    state = SupplementDeliveryClaim.Pending
                    claimedAtMillis = nowMillis
                    SupplementDeliveryClaim.Acquired
                }
                else -> state
            }
        }

        override suspend fun finalize(claimId: String, deliveredAtMillis: Long): Boolean {
            if (throwOnFinalize) throw IllegalStateException()
            if (!finalizeSucceeds) return false
            state = SupplementDeliveryClaim.Delivered
            return true
        }

        override suspend fun markPosted(claimId: String): Boolean {
            state = SupplementDeliveryClaim.Posted
            return true
        }

        override suspend fun release(claimId: String) {
            if (throwOnRelease) throw IllegalStateException()
            releaseCalls += 1
            state = SupplementDeliveryClaim.Available
        }
    }

    private class FakeRecoveryStateStore : com.avitoohband.nutrun.reminders.ReminderRecoveryStateStore {
        private val values = mutableMapOf<String, Set<ReminderSystem>>()
        override suspend fun current(userId: String): Set<ReminderSystem> = values[userId].orEmpty()
        override suspend fun merge(userId: String, systems: Set<ReminderSystem>) {
            values[userId] = values[userId].orEmpty() + systems
        }
        override suspend fun completeAttempt(
            userId: String,
            attempted: Set<ReminderSystem>,
            stillFailing: Set<ReminderSystem>
        ) {
            values[userId] = (values[userId].orEmpty() - attempted) + stillFailing
        }
    }

    private class FakeReceiverStore : ReminderRescheduleUserStore {
        val savedSupplementSettings = mutableListOf<com.avitoohband.nutrun.data.SupplementReminderSettingsEntity>()
        override suspend fun hydrationPlan(userId: String) = null
        override suspend fun trainingSettings(userId: String) = null
        override suspend fun supplementSettings(userId: String) = null
        override suspend fun saveSupplementSettings(
            settings: com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
        ) {
            savedSupplementSettings += settings
        }
    }

    private class FakeReceiverSchedulers(
        private val failHydration: Boolean = false
    ) : ReminderRescheduleUserSchedulers {
        val calls = mutableListOf<String>()
        override suspend fun scheduleHydration(settings: com.avitoohband.nutrun.data.HydrationPlanEntity) {
            calls += "hydration"
            if (failHydration) throw IllegalStateException("hydration unavailable")
        }
        override suspend fun scheduleTraining(
            userId: String,
            settings: com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
        ) {
            calls += "training"
        }
        override suspend fun scheduleSupplements(
            userId: String,
            settings: com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
        ) {
            calls += "supplements"
        }
    }
}
