package com.avitoohband.nutrun

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.reminders.ReminderRescheduleRecoveryScheduler
import com.avitoohband.nutrun.reminders.ReminderWorkEnqueuer
import com.avitoohband.nutrun.reminders.SupplementReminderScheduler
import com.avitoohband.nutrun.reminders.SupplementReminderSchedulerStore
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutRunViewModelReminderTest {
    @Test
    fun loginAndMasterSettingsObservationRescheduleTheCurrentAccount() = runBlocking {
        val runtime = ReminderRuntime()
        val model = NutRunViewModel(runtime, reminderTestScope())

        runtime.selectAccount("account-a")
        yield()

        assertEquals("account-a", model.state.value.session.authenticatedUserId)
        assertTrue(runtime.settingsReads > 0)
        assertEquals(listOf("supplement-reminder:account-a"), runtime.work.enqueued)
        runtime.work.clear()

        runtime.updateMasterSettings(enabled = false)
        yield()

        assertEquals(listOf("supplement-reminder:account-a"), runtime.work.cancelled)
        assertTrue(runtime.work.enqueued.isEmpty())
    }

    @Test
    fun masterSaveRejectsAccountAAfterTheSessionSwitchesToBWithoutSchedulingEither() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.work.clear()
        runtime.saveGate = CompletableDeferred()

        model.saveSupplementReminderSettings(testSettings())
        runtime.saveStarted.await()
        runtime.selectAccount("account-b")
        runtime.work.clear()
        runtime.saveGate!!.complete(Unit)

        assertTrue(runtime.savedSettings.isEmpty())
        assertTrue(runtime.work.enqueued.isEmpty())
        assertTrue(runtime.work.cancelled.isEmpty())
        assertTrue(runtime.settingsFor("account-a").enabled)
        assertFalse(runtime.settingsFor("account-b").enabled)
    }

    @Test
    fun signOutCancelsCurrentAccountBeforeSessionClearAndBlocksQueuedReschedule() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.work.clear()
        runtime.clearGate = CompletableDeferred()

        model.signOut()
        runtime.clearStarted.await()

        assertEquals("account-a", runtime.session.value.authenticatedUserId)
        assertEquals(
            listOf(
                "supplement-reminder:account-a",
                "reminder-reschedule-recovery:account-a:HYDRATION",
                "reminder-reschedule-recovery:account-a:TRAINING",
                "reminder-reschedule-recovery:account-a:SUPPLEMENTS"
            ),
            runtime.work.cancelled
        )
        assertEquals(listOf("pause", "auth", "preferences-start"), runtime.events)

        val reschedule = launch(Dispatchers.Unconfined) {
            runtime.schedulingCoordinator.reschedule("account-a")
        }
        runtime.clearGate!!.complete(Unit)
        reschedule.join()

        assertEquals(listOf("pause", "auth", "preferences-start", "preferences-cleared"), runtime.events)
        assertTrue(runtime.work.enqueued.isEmpty())
        assertTrue(runtime.work.names.none { it.contains("account-b") })
        assertTrue(runtime.savedSettings.isEmpty())
    }

    private class ReminderRuntime(initialAccount: String? = null) :
        NutRunViewModelReminderRuntime,
        SupplementReminderSchedulingStore,
        SupplementReminderSchedulerStore {
        private val settings = mutableMapOf(
            "account-a" to testSettings(),
            "account-b" to testSettings(enabled = false)
        )
        override val session = MutableStateFlow(SessionPreferences(authenticatedUserId = initialAccount))
        override val supplementReminderSettings = MutableStateFlow(settingsFor(initialAccount))
        val work = RecordingWork()
        val events = mutableListOf<String>()
        val savedSettings = mutableListOf<Pair<String, SupplementReminderSettingsEntity>>()
        var settingsReads = 0
        var saveGate: CompletableDeferred<Unit>? = null
        val saveStarted = CompletableDeferred<Unit>()
        var clearGate: CompletableDeferred<Unit>? = null
        val clearStarted = CompletableDeferred<Unit>()

        override val schedulingCoordinator by lazy {
            SupplementReminderSchedulingCoordinator(
                store = this,
                supplementReminderScheduler = SupplementReminderScheduler(
                    store = this,
                    enqueuer = work,
                    now = { ZonedDateTime.of(2026, 8, 10, 7, 0, 0, 0, it) }
                ),
                recoveryScheduler = ReminderRescheduleRecoveryScheduler(work)
            )
        }

        fun selectAccount(accountId: String) {
            supplementReminderSettings.value = settingsFor(accountId)
            session.value = SessionPreferences(authenticatedUserId = accountId)
        }

        fun updateMasterSettings(enabled: Boolean) {
            val accountId = session.value.authenticatedUserId ?: return
            settings[accountId] = testSettings(enabled)
            supplementReminderSettings.value = settingsFor(accountId)
        }

        fun settingsFor(accountId: String?): SupplementReminderSettingsEntity =
            accountId?.let(settings::get) ?: SupplementReminderSettingsEntity()

        override suspend fun saveSupplementReminderSettings(
            userId: String,
            settings: SupplementReminderSettingsEntity
        ) {
            saveStarted.complete(Unit)
            saveGate?.await()
            require(session.value.authenticatedUserId == userId)
            this.settings[userId] = settings
            savedSettings += userId to settings
            if (session.value.authenticatedUserId == userId) {
                supplementReminderSettings.value = settings
            }
        }

        override suspend fun pauseWalk() {
            events += "pause"
        }

        override fun signOutAuthentication() {
            events += "auth"
        }

        override suspend fun signOutPreferences() {
            events += "preferences-start"
            clearStarted.complete(Unit)
            clearGate?.await()
            session.value = SessionPreferences()
            supplementReminderSettings.value = SupplementReminderSettingsEntity()
            events += "preferences-cleared"
        }

        override suspend fun currentUserId(): String? = session.value.authenticatedUserId

        override suspend fun supplementReminderSettings(userId: String): SupplementReminderSettingsEntity? {
            settingsReads += 1
            return settings[userId]
        }

        override suspend fun authenticatedUserId(): String? = session.value.authenticatedUserId

        override suspend fun supplements(userId: String): List<Supplement> = listOf(
            Supplement(
                id = "vitamin-d",
                name = "Vitamin D",
                dose = "1000 IU",
                schedule = SupplementSchedule(RecurrenceType.DAILY),
                reminderEnabled = true,
                reminderMinute = 480
            )
        )
    }

    private class RecordingWork : ReminderWorkEnqueuer {
        val enqueued = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val names: List<String>
            get() = enqueued + cancelled

        override fun enqueue(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
            enqueued += name
        }

        override fun cancel(name: String) {
            cancelled += name
        }

        fun clear() {
            enqueued.clear()
            cancelled.clear()
        }
    }

}

private fun testSettings(enabled: Boolean = true) = SupplementReminderSettingsEntity(
    enabled = enabled,
    timezoneId = "UTC"
)

private fun reminderTestScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
