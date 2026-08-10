package com.avitoohband.nutrun

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.NutRunDao
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingStateEntity
import com.avitoohband.nutrun.reminders.ReminderRescheduleOutcome
import com.avitoohband.nutrun.reminders.ReminderRescheduleReceiver
import com.avitoohband.nutrun.reminders.ReminderRescheduleReceiverDispatcher
import com.avitoohband.nutrun.reminders.ReminderRescheduleReceiverRuntime
import com.avitoohband.nutrun.reminders.ReminderSystem
import com.avitoohband.nutrun.reminders.TrainingReminderType
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRescheduleReceiverProductionTest {
    @Test
    fun frameworkDeliveredReceiverFinishesAsyncWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val action = "${context.packageName}.test.REMINDER_RESCHEDULE_LIFECYCLE.${UUID.randomUUID()}"
        val workStarted = CountDownLatch(1)
        val releaseWork = CountDownLatch(1)
        val orderedCompletion = CountDownLatch(1)
        val receiver = ReminderRescheduleReceiver(
            dispatcherFactory = {
                ReminderRescheduleReceiverDispatcher(
                    authenticatedUserId = {
                        workStarted.countDown()
                        check(releaseWork.await(5, TimeUnit.SECONDS)) {
                            "Timed out waiting to release receiver work"
                        }
                        null
                    },
                    reschedule = { _, _ -> ReminderRescheduleOutcome.Complete },
                    scheduleRecovery = { _, _ -> }
                )
            },
            acceptedActions = setOf(action)
        )
        val terminalReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                orderedCompletion.countDown()
            }
        }
        var registered = false

        try {
            registerReceiver(context, receiver, IntentFilter(action))
            registered = true
            context.sendOrderedBroadcast(
                Intent(action).setPackage(context.packageName),
                null,
                terminalReceiver,
                null,
                0,
                null,
                null
            )

            assertTrue("Receiver work never started", workStarted.await(5, TimeUnit.SECONDS))
            assertFalse(
                "Ordered broadcast completed before PendingResult.finish()",
                orderedCompletion.await(250, TimeUnit.MILLISECONDS)
            )

            releaseWork.countDown()
            assertTrue(
                "Ordered broadcast did not complete after PendingResult.finish()",
                orderedCompletion.await(5, TimeUnit.SECONDS)
            )
        } finally {
            releaseWork.countDown()
            if (registered) context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun defaultDispatcherSchedulesSupplementsAndOnlyTrainingRecovery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "receiver-${UUID.randomUUID()}"
        val preferences = AppPreferences(context)
        val dao = NutRunDatabase.getInstance(context).dao()
        val workManager = WorkManager.getInstance(context)
        val runtime = ControlledReceiverRuntime()
        val workNames = touchedWorkNames(userId)
        val currentZone = ZoneId.systemDefault().id

        try {
            cleanupFixture(workManager, workNames, dao, preferences, userId)
            preferences.signIn(userId, "$userId@example.com", 1L, subscriber = false)
            dao.saveHydrationPlan(
                HydrationPlanEntity(
                    id = "hydration:$userId",
                    userId = userId,
                    remindersEnabled = false
                )
            )
            dao.saveTrainingReminderSettings(
                TrainingReminderSettingsEntity(
                    id = "training-reminders:$userId",
                    userId = userId,
                    enabled = true,
                    previousDayMinute = -1,
                    sameDayMinute = 8 * 60,
                    timezoneId = "UTC"
                )
            )
            dao.saveTrainingState(
                TrainingStateEntity(
                    userId = userId,
                    payloadJson = validSupplementPayload(),
                    updatedAtMillis = System.currentTimeMillis(),
                    pendingSync = false
                )
            )
            dao.saveSupplementReminderSettings(
                SupplementReminderSettingsEntity(
                    id = "supplement-reminders:$userId",
                    userId = userId,
                    enabled = true,
                    timezoneId = "UTC"
                )
            )

            val receiver = ReminderRescheduleReceiver(runtime = runtime)
            receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
            runtime.runCapturedWork()

            awaitWorkExists(workManager, "supplement-reminder:$userId")
            awaitWorkExists(workManager, "reminder-reschedule-recovery:$userId:TRAINING")

            assertEquals(currentZone, dao.supplementReminderSettings(userId)?.timezoneId)
            assertTrue(workInfos(workManager, "supplement-reminder:$userId").isNotEmpty())
            assertTrue(
                workInfos(workManager, "reminder-reschedule-recovery:$userId:TRAINING").isNotEmpty()
            )
            assertTrue(
                workInfos(workManager, "reminder-reschedule-recovery:$userId:HYDRATION").isEmpty()
            )
            assertTrue(
                workInfos(workManager, "reminder-reschedule-recovery:$userId:SUPPLEMENTS").isEmpty()
            )
        } finally {
            cleanupFixture(workManager, workNames, dao, preferences, userId)
        }
    }

    private class ControlledReceiverRuntime : ReminderRescheduleReceiverRuntime {
        private var capturedWork: (suspend () -> Unit)? = null

        override fun launch(receiver: BroadcastReceiver, work: suspend () -> Unit) {
            check(capturedWork == null) { "Receiver work was already captured" }
            capturedWork = work
        }

        suspend fun runCapturedWork() {
            val work = checkNotNull(capturedWork) { "Receiver did not launch work" }
            capturedWork = null
            work()
        }
    }

    private fun validSupplementPayload() = encodeTrainingState(
        supplements = listOf(
            Supplement(
                id = "daily-test-supplement",
                name = "Daily test supplement",
                dose = "1 capsule",
                schedule = SupplementSchedule(
                    type = RecurrenceType.DAILY,
                    startDate = LocalDate.of(2020, 1, 1)
                ),
                reminderEnabled = true,
                reminderMinute = 12 * 60
            )
        ),
        sessions = emptyList(),
        history = emptyList(),
        selectedSessionId = null,
        activeWorkoutSessionId = null,
        isWorkoutPaused = false,
        completedExerciseIds = emptyMap(),
        suggestionDecision = SuggestionDecision.PENDING,
        suggestedWeightKg = 0.0
    )

    private suspend fun awaitWorkExists(workManager: WorkManager, name: String) {
        withTimeout(10_000) {
            while (workInfos(workManager, name).isEmpty()) delay(50)
        }
    }

    private suspend fun workInfos(workManager: WorkManager, name: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWorkFlow(name).first()

    private fun touchedWorkNames(userId: String) = buildList {
        ReminderSystem.entries.forEach { system ->
            add("reminder-reschedule-recovery:$userId:${system.name}")
        }
        add("supplement-reminder:$userId")
        TrainingReminderType.entries.forEach { type ->
            add("training-reminder:$userId:${type.name}")
        }
        add("hydration-reminders")
    }

    private suspend fun cleanupFixture(
        workManager: WorkManager,
        workNames: List<String>,
        dao: NutRunDao,
        preferences: AppPreferences,
        userId: String
    ) {
        val failures = mutableListOf<Throwable>()
        workNames.forEach { name ->
            try {
                withTimeout(10_000) { workManager.cancelUniqueWork(name).await() }
            } catch (failure: Throwable) {
                failures += failure
            }
        }
        try {
            dao.clearAccountData(userId)
        } catch (failure: Throwable) {
            failures += failure
        }
        try {
            preferences.clearAccount(userId)
        } catch (failure: Throwable) {
            failures += failure
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    @Suppress("DEPRECATION")
    private fun registerReceiver(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}
