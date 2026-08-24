package com.avitoohband.nutrun

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.FoodTemplateEntity
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WaterLogEntity
import com.avitoohband.nutrun.data.WeightEntryEntity
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.reminders.ReminderRescheduleRecoveryScheduler
import com.avitoohband.nutrun.reminders.ReminderSystem
import com.avitoohband.nutrun.reminders.ReminderWorkEnqueuer
import com.avitoohband.nutrun.reminders.SupplementReminderScheduler
import com.avitoohband.nutrun.reminders.SupplementReminderSchedulerStore
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NutRunViewModelReminderTest {
    @Test
    fun accountSwitchWaitsForEveryTaggedSettingBeforeSchedulingTheNewAccount() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        withTimeout(5_000) {
            while (model.state.value.notificationSettingsReadyAccountId != "account-a") yield()
        }
        runtime.clearScheduling()

        runtime.selectSessionOnly("account-b")
        yield()

        assertNull(model.state.value.notificationSettingsReadyAccountId)
        assertEquals("account-a", model.state.value.hydrationPlan.userId)
        assertEquals("account-a", model.state.value.trainingReminderSettings.userId)
        assertEquals("account-a", model.state.value.supplementReminderSettings.userId)
        runtime.assertNothingScheduled()

        runtime.advanceHydration("account-b", goalMl = 3_300)
        yield()
        assertNull(model.state.value.notificationSettingsReadyAccountId)
        runtime.assertNothingScheduled()

        runtime.advanceTraining("account-b", previousDayMinute = 19 * 60)
        yield()
        assertNull(model.state.value.notificationSettingsReadyAccountId)
        runtime.assertNothingScheduled()

        runtime.advanceSupplements("account-b", enabled = false)
        withTimeout(5_000) {
            while (model.state.value.notificationSettingsReadyAccountId != "account-b") yield()
        }

        assertEquals(listOf(3_300), runtime.hydrationSchedules.map { it.goalMl })
        assertEquals(listOf("account-b"), runtime.hydrationSchedules.map { it.userId })
        assertEquals(listOf(19 * 60), runtime.trainingSchedules.map { it.second.previousDayMinute })
        assertEquals(listOf("account-b"), runtime.trainingSchedules.map { it.first })
        assertEquals(listOf("supplement-reminder:account-b"), runtime.work.cancelled)
    }

    @Test
    fun observerIsolatesEverySchedulerFailureAndContinuesFutureEmissions() = runBlocking {
        ReminderSystem.entries.forEach { failedSystem ->
            val runtime = ReminderRuntime("account-a")
            val scope = reminderTestScope()
            runtime.failNextSchedule(failedSystem)
            NutRunViewModel(runtime, scope)

            withTimeout(5_000) {
                while (runtime.recoverySchedules.none { it.second == failedSystem }) yield()
            }

            assertEquals(ReminderSystem.entries, runtime.scheduleAttempts.take(3))
            assertEquals(
                listOf("account-a" to failedSystem),
                runtime.recoverySchedules
            )
            assertEquals(
                ReminderSystem.entries.filterNot { it == failedSystem },
                runtime.successfulScheduleSystems.take(2)
            )

            runtime.scheduleAttempts.clear()
            runtime.successfulScheduleSystems.clear()
            runtime.advanceHydration("account-a", goalMl = 3_300)
            withTimeout(5_000) {
                while (runtime.successfulScheduleSystems.size < 3) yield()
            }

            assertEquals(ReminderSystem.entries, runtime.scheduleAttempts)
            assertEquals(ReminderSystem.entries, runtime.successfulScheduleSystems)
            scope.cancel()
        }
    }

    @Test
    fun loginAndMasterSettingsObservationRescheduleTheCurrentAccount() = runBlocking {
        val runtime = ReminderRuntime()
        val model = NutRunViewModel(runtime, reminderTestScope())

        runtime.selectAccount("account-a")
        yield()

        assertEquals("account-a", model.state.value.session.authenticatedUserId)
        assertTrue(runtime.settingsReads > 0)
        assertEquals(listOf("account-a"), runtime.hydrationSchedules.map { it.userId })
        assertEquals(listOf("account-a"), runtime.trainingSchedules.map { it.first })
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
    fun remainingSettingsPersistInDocumentedOrderForTheExpectedAccount() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.settingsWrites.clear()

        val result = model.persistNotificationSettings(
            accountId = "account-a",
            hydration = testHydrationPlan(),
            training = testTrainingSettings(),
            supplements = testSettings()
        )

        assertEquals(NotificationSettingsSaveResult.Success("account-a"), result)
        assertEquals(
            listOf("hydration:account-a", "training:account-a", "master:account-a"),
            runtime.settingsWrites
        )
    }

    @Test
    fun remainingSettingsStopAfterFailureAndReportThePartialStage() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.settingsWrites.clear()
        runtime.settingsFailureStage = NotificationSettingsSaveStage.TRAINING

        val result = model.persistNotificationSettings(
            accountId = "account-a",
            hydration = testHydrationPlan(),
            training = testTrainingSettings(),
            supplements = testSettings()
        )

        assertTrue(result is NotificationSettingsSaveResult.Failed)
        val failed = result as NotificationSettingsSaveResult.Failed
        assertEquals(NotificationSettingsSaveStage.TRAINING, failed.stage)
        assertEquals(listOf("hydration:account-a"), runtime.settingsWrites)
    }

    @Test
    fun remainingSettingsDetectAccountSwitchBeforeStartingLaterWrites() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.settingsWrites.clear()
        runtime.hydrationSaveGate = CompletableDeferred()

        val save = async {
            model.persistNotificationSettings(
                accountId = "account-a",
                hydration = testHydrationPlan(),
                training = testTrainingSettings(),
                supplements = testSettings()
            )
        }
        runtime.hydrationSaveStarted.await()
        runtime.selectAccount("account-b")
        runtime.hydrationSaveGate!!.complete(Unit)

        val result = save.await()
        assertTrue(result is NotificationSettingsSaveResult.AccountChanged)
        val changed = result as NotificationSettingsSaveResult.AccountChanged
        assertEquals("account-a", changed.expectedAccountId)
        assertEquals("account-b", changed.actualAccountId)
        assertEquals(
            setOf(
                NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                NotificationSettingsSaveStage.HYDRATION
            ),
            changed.completedStages
        )
        assertEquals(listOf("hydration:account-a"), runtime.settingsWrites)
        assertTrue(runtime.settingsWrites.none { it.endsWith("account-b") })
    }

    @Test
    fun remainingSettingsRejectAnAccountMismatchBeforeHydrationStarts() = runBlocking {
        val runtime = ReminderRuntime("account-b")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.settingsWrites.clear()

        val result = model.persistNotificationSettings(
            accountId = "account-a",
            hydration = testHydrationPlan(),
            training = testTrainingSettings(),
            supplements = testSettings()
        )

        assertTrue(result is NotificationSettingsSaveResult.AccountChanged)
        val changed = result as NotificationSettingsSaveResult.AccountChanged
        assertEquals(NotificationSettingsSaveStage.HYDRATION, changed.stage)
        assertEquals(
            setOf(NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS),
            changed.completedStages
        )
        assertTrue(runtime.settingsWrites.isEmpty())
    }

    @Test
    fun remainingSettingsDetectAccountSwitchAfterTrainingBeforeMasterStarts() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.settingsWrites.clear()
        runtime.trainingSaveGate = CompletableDeferred()

        val save = async {
            model.persistNotificationSettings(
                accountId = "account-a",
                hydration = testHydrationPlan(),
                training = testTrainingSettings(),
                supplements = testSettings()
            )
        }
        runtime.trainingSaveStarted.await()
        runtime.selectAccount("account-b")
        runtime.trainingSaveGate!!.complete(Unit)

        val result = save.await()
        assertTrue(result is NotificationSettingsSaveResult.AccountChanged)
        val changed = result as NotificationSettingsSaveResult.AccountChanged
        assertEquals(NotificationSettingsSaveStage.TRAINING, changed.stage)
        assertEquals(
            setOf(
                NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                NotificationSettingsSaveStage.HYDRATION,
                NotificationSettingsSaveStage.TRAINING
            ),
            changed.completedStages
        )
        assertEquals(
            listOf("hydration:account-a", "training:account-a"),
            runtime.settingsWrites
        )
        assertTrue(runtime.settingsWrites.none { it == "master:account-a" })
    }

    @Test
    fun remainingSettingsDetectAccountSwitchDuringMasterPersistence() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        runtime.settingsWrites.clear()
        runtime.saveGate = CompletableDeferred()

        val save = async {
            model.persistNotificationSettings(
                accountId = "account-a",
                hydration = testHydrationPlan(),
                training = testTrainingSettings(),
                supplements = testSettings()
            )
        }
        runtime.saveStarted.await()
        runtime.selectAccount("account-b")
        runtime.saveGate!!.complete(Unit)

        val result = save.await()
        assertTrue(result is NotificationSettingsSaveResult.AccountChanged)
        val changed = result as NotificationSettingsSaveResult.AccountChanged
        assertEquals(NotificationSettingsSaveStage.SUPPLEMENT_MASTER, changed.stage)
        assertEquals(
            setOf(
                NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                NotificationSettingsSaveStage.HYDRATION,
                NotificationSettingsSaveStage.TRAINING
            ),
            changed.completedStages
        )
        assertEquals(
            listOf("hydration:account-a", "training:account-a"),
            runtime.settingsWrites
        )
    }

    @Test
    fun disabledSettingsPersistTheirConfiguredTimesWithoutResettingThem() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val model = NutRunViewModel(runtime, reminderTestScope())
        val hydration = testHydrationPlan().copy(
            remindersEnabled = false,
            intervalMinutes = 90,
            wakingStartMinute = 7 * 60 + 30,
            wakingEndMinute = 21 * 60 + 15
        )
        val training = testTrainingSettings().copy(
            enabled = false,
            previousDayMinute = 19 * 60 + 45,
            sameDayMinute = 7 * 60 + 5
        )

        val result = model.persistNotificationSettings(
            accountId = "account-a",
            hydration = hydration,
            training = training,
            supplements = testSettings(enabled = false)
        )

        assertEquals(NotificationSettingsSaveResult.Success("account-a"), result)
        assertEquals(hydration, runtime.savedHydrationPlans.single())
        assertEquals(training, runtime.savedTrainingSettings.single())
        assertFalse(runtime.savedSettings.single().second.enabled)
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

    @Test
    fun signOutPropagatesCancellationFromSessionClear() = runBlocking {
        val runtime = ReminderRuntime("account-a")
        val cancellation = CancellationException("cancelled")
        runtime.signOutPreferencesFailure = cancellation
        val model = NutRunViewModel(runtime, reminderTestScope())
        val completionCause = CompletableDeferred<Throwable?>()

        val signOut = model.signOut()
        signOut.invokeOnCompletion(completionCause::complete)

        assertSame(cancellation, completionCause.await())
    }

    private class ReminderRuntime(initialAccount: String? = null) :
        NutRunViewModelReminderRuntime,
        SupplementReminderSchedulingStore,
        SupplementReminderSchedulerStore {
        private val settings = mutableMapOf(
            "account-a" to testSettings(userId = "account-a"),
            "account-b" to testSettings(enabled = false, userId = "account-b")
        )
        override val session = MutableStateFlow(SessionPreferences(authenticatedUserId = initialAccount))
        override val profile = MutableStateFlow<UserProfile?>(null)
        override val hydrationPlan = MutableStateFlow(testHydrationPlan(initialAccount))
        override val weights = MutableStateFlow<List<WeightEntryEntity>>(emptyList())
        override val walks = MutableStateFlow<List<WalkSessionEntity>>(emptyList())
        override val activeWalk = MutableStateFlow<WalkSessionEntity?>(null)
        override val trainingReminderSettings = MutableStateFlow(
            testTrainingSettings(initialAccount)
        )
        override val supplementReminderSettings = MutableStateFlow(settingsFor(initialAccount))
        override val recentFoods = MutableStateFlow<List<FoodLogEntity>>(emptyList())
        override val foodTemplates = MutableStateFlow<List<FoodTemplateEntity>>(emptyList())
        override val nutritionTargets = MutableStateFlow<NutritionTargets?>(null)
        private val food = MutableStateFlow<List<FoodLogEntity>>(emptyList())
        private val water = MutableStateFlow<List<WaterLogEntity>>(emptyList())
        private val walkPoints = MutableStateFlow<List<WalkPointEntity>>(emptyList())
        val work = RecordingWork()
        val events = mutableListOf<String>()
        val savedSettings = mutableListOf<Pair<String, SupplementReminderSettingsEntity>>()
        val hydrationSchedules = mutableListOf<HydrationPlanEntity>()
        val trainingSchedules = mutableListOf<Pair<String, TrainingReminderSettingsEntity>>()
        val settingsWrites = mutableListOf<String>()
        val savedHydrationPlans = mutableListOf<HydrationPlanEntity>()
        val savedTrainingSettings = mutableListOf<TrainingReminderSettingsEntity>()
        val scheduleAttempts = mutableListOf<ReminderSystem>()
        val successfulScheduleSystems = mutableListOf<ReminderSystem>()
        val recoverySchedules = mutableListOf<Pair<String, ReminderSystem>>()
        private val scheduleFailures = mutableMapOf<ReminderSystem, Int>()
        var settingsFailureStage: NotificationSettingsSaveStage? = null
        var hydrationSaveGate: CompletableDeferred<Unit>? = null
        val hydrationSaveStarted = CompletableDeferred<Unit>()
        var trainingSaveGate: CompletableDeferred<Unit>? = null
        val trainingSaveStarted = CompletableDeferred<Unit>()
        var settingsReads = 0
        var saveGate: CompletableDeferred<Unit>? = null
        val saveStarted = CompletableDeferred<Unit>()
        var clearGate: CompletableDeferred<Unit>? = null
        val clearStarted = CompletableDeferred<Unit>()
        var signOutPreferencesFailure: CancellationException? = null

        val schedulingCoordinator by lazy {
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

        override fun food(date: LocalDate): Flow<List<FoodLogEntity>> = food

        override fun water(date: LocalDate): Flow<List<WaterLogEntity>> = water

        override fun walkPoints(sessionId: String): Flow<List<WalkPointEntity>> = walkPoints

        fun selectAccount(accountId: String) {
            hydrationPlan.value = testHydrationPlan(accountId)
            trainingReminderSettings.value = testTrainingSettings(accountId)
            supplementReminderSettings.value = settingsFor(accountId)
            session.value = SessionPreferences(authenticatedUserId = accountId)
        }

        fun selectSessionOnly(accountId: String) {
            session.value = SessionPreferences(authenticatedUserId = accountId)
        }

        fun advanceHydration(accountId: String, goalMl: Int) {
            hydrationPlan.value = testHydrationPlan(accountId).copy(goalMl = goalMl)
        }

        fun advanceTraining(accountId: String, previousDayMinute: Int) {
            trainingReminderSettings.value = testTrainingSettings(accountId).copy(
                previousDayMinute = previousDayMinute
            )
        }

        fun advanceSupplements(accountId: String, enabled: Boolean) {
            settings[accountId] = testSettings(enabled, accountId)
            supplementReminderSettings.value = settingsFor(accountId)
        }

        fun clearScheduling() {
            hydrationSchedules.clear()
            trainingSchedules.clear()
            work.clear()
        }

        fun assertNothingScheduled() {
            assertTrue(hydrationSchedules.isEmpty())
            assertTrue(trainingSchedules.isEmpty())
            assertTrue(work.enqueued.isEmpty())
            assertTrue(work.cancelled.isEmpty())
        }

        fun failNextSchedule(system: ReminderSystem) {
            scheduleFailures[system] = 1
        }

        private fun recordSchedule(system: ReminderSystem) {
            scheduleAttempts += system
            val failuresRemaining = scheduleFailures[system] ?: 0
            if (failuresRemaining > 0) {
                scheduleFailures[system] = failuresRemaining - 1
                error("$system scheduling failed")
            }
            successfulScheduleSystems += system
        }

        fun updateMasterSettings(enabled: Boolean) {
            val accountId = session.value.authenticatedUserId ?: return
            settings[accountId] = testSettings(enabled, accountId)
            supplementReminderSettings.value = settingsFor(accountId)
        }

        override fun scheduleHydration(plan: HydrationPlanEntity) {
            recordSchedule(ReminderSystem.HYDRATION)
            hydrationSchedules += plan
        }

        override fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity) {
            recordSchedule(ReminderSystem.TRAINING)
            trainingSchedules += userId to settings
        }

        override suspend fun rescheduleSupplementReminders(userId: String) {
            recordSchedule(ReminderSystem.SUPPLEMENTS)
            schedulingCoordinator.reschedule(userId)
        }

        override suspend fun scheduleReminderRecovery(
            userId: String,
            system: ReminderSystem
        ) {
            recoverySchedules += userId to system
        }

        override suspend fun currentSession(): SessionPreferences = session.value

        fun settingsFor(accountId: String?): SupplementReminderSettingsEntity =
            accountId?.let(settings::get) ?: SupplementReminderSettingsEntity()

        override suspend fun saveHydrationPlan(userId: String, plan: HydrationPlanEntity) {
            require(session.value.authenticatedUserId == userId)
            hydrationSaveStarted.complete(Unit)
            hydrationSaveGate?.await()
            failSettingsSaveIfRequested(NotificationSettingsSaveStage.HYDRATION)
            savedHydrationPlans += plan
            settingsWrites += "hydration:$userId"
        }

        override suspend fun saveTrainingReminderSettings(
            userId: String,
            settings: TrainingReminderSettingsEntity
        ) {
            require(session.value.authenticatedUserId == userId)
            trainingSaveStarted.complete(Unit)
            trainingSaveGate?.await()
            failSettingsSaveIfRequested(NotificationSettingsSaveStage.TRAINING)
            savedTrainingSettings += settings
            settingsWrites += "training:$userId"
        }

        private fun failSettingsSaveIfRequested(stage: NotificationSettingsSaveStage) {
            if (settingsFailureStage == stage) error("$stage failed")
        }

        override suspend fun saveSupplementReminderSettings(
            userId: String,
            settings: SupplementReminderSettingsEntity
        ) {
            if (settingsFailureStage == NotificationSettingsSaveStage.SUPPLEMENT_MASTER) {
                error("master failed")
            }
            saveStarted.complete(Unit)
            saveGate?.await()
            require(session.value.authenticatedUserId == userId)
            val scoped = settings.copy(
                id = "supplement-reminders:$userId",
                userId = userId
            )
            this.settings[userId] = scoped
            savedSettings += userId to scoped
            settingsWrites += "master:$userId"
            if (session.value.authenticatedUserId == userId) {
                supplementReminderSettings.value = scoped
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
            signOutPreferencesFailure?.let { throw it }
            session.value = SessionPreferences()
            supplementReminderSettings.value = SupplementReminderSettingsEntity()
            events += "preferences-cleared"
        }

        override suspend fun signOutReminders(
            userId: String,
            clearSession: suspend () -> Unit
        ) {
            schedulingCoordinator.signOut(userId, clearSession)
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

private fun testSettings(
    enabled: Boolean = true,
    userId: String = ""
) = SupplementReminderSettingsEntity(
    id = userId.takeIf(String::isNotBlank)?.let { "supplement-reminders:$it" }.orEmpty(),
    userId = userId,
    enabled = enabled,
    timezoneId = "UTC"
)

private fun testHydrationPlan(userId: String? = null) = HydrationPlanEntity(
    id = userId?.let { "hydration:$it" }.orEmpty(),
    userId = userId.orEmpty(),
    goalMl = 2_000,
    servingMl = 250,
    intervalMinutes = 60,
    wakingStartMinute = 8 * 60,
    wakingEndMinute = 22 * 60
)

private fun testTrainingSettings(userId: String? = null) = TrainingReminderSettingsEntity(
    id = userId?.let { "training-reminders:$it" }.orEmpty(),
    userId = userId.orEmpty()
)

private fun reminderTestScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
