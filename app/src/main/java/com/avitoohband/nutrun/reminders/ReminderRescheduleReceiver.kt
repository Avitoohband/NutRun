package com.avitoohband.nutrun.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import java.time.ZoneId
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ReminderSystem { HYDRATION, TRAINING, SUPPLEMENTS }

interface ReminderRecoveryStateStore {
    suspend fun current(userId: String): Set<ReminderSystem>
    suspend fun merge(userId: String, systems: Set<ReminderSystem>)
    suspend fun completeAttempt(
        userId: String,
        attempted: Set<ReminderSystem>,
        stillFailing: Set<ReminderSystem>
    )
}

private class PreferenceReminderRecoveryStateStore(
    private val preferences: AppPreferences
) : ReminderRecoveryStateStore {
    override suspend fun current(userId: String): Set<ReminderSystem> = preferences
        .reminderRecoverySystems(userId)
        .mapNotNull { runCatching { ReminderSystem.valueOf(it) }.getOrNull() }
        .toSet()

    override suspend fun merge(userId: String, systems: Set<ReminderSystem>) {
        preferences.mergeReminderRecoverySystems(userId, systems.map(ReminderSystem::name).toSet())
    }

    override suspend fun completeAttempt(
        userId: String,
        attempted: Set<ReminderSystem>,
        stillFailing: Set<ReminderSystem>
    ) {
        preferences.completeReminderRecoveryAttempt(
            userId,
            attempted.map(ReminderSystem::name).toSet(),
            stillFailing.map(ReminderSystem::name).toSet()
        )
    }
}

data class ReminderRescheduleOutcome(val failedSystems: Set<ReminderSystem>) {
    val requiresRecovery: Boolean get() = failedSystems.isNotEmpty()

    companion object {
        val Complete = ReminderRescheduleOutcome(emptySet())
        val Failed = ReminderRescheduleOutcome(ReminderSystem.entries.toSet())
    }
}

suspend fun rescheduleReminderSystems(
    hydration: suspend () -> Unit,
    training: suspend () -> Unit,
    supplements: suspend () -> Unit,
    systems: Set<ReminderSystem> = ReminderSystem.entries.toSet()
): ReminderRescheduleOutcome {
    val failed = mutableSetOf<ReminderSystem>()
    listOf(
        ReminderSystem.HYDRATION to hydration,
        ReminderSystem.TRAINING to training,
        ReminderSystem.SUPPLEMENTS to supplements
    ).filter { it.first in systems }.forEach { (system, reschedule) ->
        try {
            reschedule()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed += system
        }
    }
    return ReminderRescheduleOutcome(failed)
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reminderRescheduleReceiverDispatcher(context).dispatch()
            } catch (error: CancellationException) {
                throw error
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderRescheduleReceiverDispatcher(
    private val authenticatedUserId: suspend () -> String?,
    private val reschedule: suspend (String, Set<ReminderSystem>) -> ReminderRescheduleOutcome,
    private val scheduleRecovery: suspend (String, Set<ReminderSystem>) -> Unit
) {
    suspend fun dispatch() {
        val userId = authenticatedUserId() ?: return
        try {
            val outcome = reschedule(userId, ReminderSystem.entries.toSet())
            if (outcome.requiresRecovery) scheduleRecovery(userId, outcome.failedSystems)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            scheduleRecovery(userId, ReminderSystem.entries.toSet())
        }
    }
}

private fun reminderRescheduleReceiverDispatcher(context: Context) = ReminderRescheduleReceiverDispatcher(
    authenticatedUserId = { AppPreferences(context).currentSession().authenticatedUserId },
    reschedule = { userId, systems -> rescheduleReminderSystemsForUser(context, userId, systems) },
    scheduleRecovery = { userId, systems -> ReminderRescheduleRecoveryScheduler(context).schedule(userId, systems) }
)

interface ReminderRescheduleUserStore {
    suspend fun hydrationPlan(userId: String): HydrationPlanEntity?
    suspend fun trainingSettings(userId: String): TrainingReminderSettingsEntity?
    suspend fun supplementSettings(userId: String): SupplementReminderSettingsEntity?
    suspend fun saveSupplementSettings(settings: SupplementReminderSettingsEntity)
}

interface ReminderRescheduleUserSchedulers {
    suspend fun scheduleHydration(settings: HydrationPlanEntity)
    suspend fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity)
    suspend fun scheduleSupplements(userId: String, settings: SupplementReminderSettingsEntity)
}

suspend fun rescheduleReminderSystemsForUser(
    userId: String,
    systems: Set<ReminderSystem> = ReminderSystem.entries.toSet(),
    store: ReminderRescheduleUserStore,
    schedulers: ReminderRescheduleUserSchedulers,
    timezone: ZoneId = ZoneId.systemDefault()
): ReminderRescheduleOutcome = rescheduleReminderSystems(
    hydration = {
        val hydration = store.hydrationPlan(userId) ?: HydrationPlanEntity(userId = userId)
        schedulers.scheduleHydration(hydration)
    },
    training = {
        val training = (store.trainingSettings(userId) ?: TrainingReminderSettingsEntity(userId = userId))
            .copy(timezoneId = timezone.id)
        schedulers.scheduleTraining(userId, training)
    },
    supplements = {
        val supplements = (store.supplementSettings(userId)
            ?: SupplementReminderSettingsEntity(userId = userId))
            .copy(
                id = "supplement-reminders:$userId",
                userId = userId,
                timezoneId = timezone.id
            )
        store.saveSupplementSettings(supplements)
        schedulers.scheduleSupplements(userId, supplements)
    },
    systems = systems
)

private suspend fun rescheduleReminderSystemsForUser(
    context: Context,
    userId: String,
    systems: Set<ReminderSystem> = ReminderSystem.entries.toSet()
): ReminderRescheduleOutcome {
    val dao = NutRunDatabase.getInstance(context).dao()
    return rescheduleReminderSystemsForUser(
        userId = userId,
        systems = systems,
        store = object : ReminderRescheduleUserStore {
            override suspend fun hydrationPlan(userId: String) = dao.hydrationPlan(userId)
            override suspend fun trainingSettings(userId: String) = dao.trainingReminderSettings(userId)
            override suspend fun supplementSettings(userId: String) = dao.supplementReminderSettings(userId)
            override suspend fun saveSupplementSettings(settings: SupplementReminderSettingsEntity) {
                dao.saveSupplementReminderSettings(settings)
            }
        },
        schedulers = object : ReminderRescheduleUserSchedulers {
            override suspend fun scheduleHydration(settings: HydrationPlanEntity) {
                HydrationScheduler(context).schedule(settings)
            }
            override suspend fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity) {
                TrainingReminderScheduler(context).schedule(userId, settings)
            }
            override suspend fun scheduleSupplements(userId: String, settings: SupplementReminderSettingsEntity) {
                SupplementReminderScheduler(context).schedule(userId, settings)
            }
        }
    )
}

class ReminderRescheduleRecoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        if (AppPreferences(applicationContext).currentSession().authenticatedUserId != userId) {
            return Result.success()
        }
        return try {
            val recoveryStore = PreferenceReminderRecoveryStateStore(AppPreferences(applicationContext))
            val outcome = runReminderRecoveryAttempt(userId, recoveryStore) { systems ->
                rescheduleReminderSystemsForUser(applicationContext, userId, systems)
            }
            when (recoveryWorkResult(runAttemptCount, outcome.requiresRecovery)) {
                ReminderRecoveryResult.Success -> Result.success()
                ReminderRecoveryResult.Retry -> Result.retry()
                ReminderRecoveryResult.Failure -> Result.failure()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            when (recoveryWorkResult(runAttemptCount, requiresRecovery = true)) {
                ReminderRecoveryResult.Failure -> Result.failure()
                else -> Result.retry()
            }
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_SYSTEMS = "systems"
    }
}

suspend fun runReminderRecoveryAttempt(
    userId: String,
    stateStore: ReminderRecoveryStateStore,
    reschedule: suspend (Set<ReminderSystem>) -> ReminderRescheduleOutcome
): ReminderRescheduleOutcome {
    val systems = stateStore.current(userId).ifEmpty { ReminderSystem.entries.toSet() }
    val outcome = reschedule(systems)
    stateStore.completeAttempt(userId, systems, outcome.failedSystems)
    return outcome
}

enum class ReminderRecoveryResult { Success, Retry, Failure }

const val MAX_REMINDER_RECOVERY_ATTEMPTS = 3

fun recoveryWorkResult(attempt: Int, requiresRecovery: Boolean): ReminderRecoveryResult = when {
    !requiresRecovery -> ReminderRecoveryResult.Success
    attempt >= MAX_REMINDER_RECOVERY_ATTEMPTS - 1 -> ReminderRecoveryResult.Failure
    else -> ReminderRecoveryResult.Retry
}

class ReminderRescheduleRecoveryScheduler(
    private val enqueuer: ReminderWorkEnqueuer,
    private val stateStore: ReminderRecoveryStateStore? = null
) {
    constructor(context: Context) : this(
        WorkManagerReminderWorkEnqueuer(context),
        PreferenceReminderRecoveryStateStore(AppPreferences(context))
    )

    suspend fun schedule(userId: String, systems: Set<ReminderSystem>) {
        if (userId.isBlank() || systems.isEmpty()) return
        stateStore?.merge(userId, systems)
        val request = OneTimeWorkRequestBuilder<ReminderRescheduleRecoveryWorker>()
            .setInputData(
                workDataOf(
                    ReminderRescheduleRecoveryWorker.KEY_USER_ID to userId,
                    ReminderRescheduleRecoveryWorker.KEY_SYSTEMS to "state"
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        enqueuer.enqueue(
            "reminder-reschedule-recovery:$userId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
