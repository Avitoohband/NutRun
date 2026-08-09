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
            var userId: String? = null
            try {
                userId = AppPreferences(context).currentSession().authenticatedUserId ?: return@launch
                val outcome = rescheduleReminderSystemsForUser(context, userId)
                if (outcome.requiresRecovery) {
                    ReminderRescheduleRecoveryScheduler(context).schedule(userId, outcome.failedSystems)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                userId?.let {
                    ReminderRescheduleRecoveryScheduler(context).schedule(it, ReminderSystem.entries.toSet())
                }
            } finally {
                pending.finish()
            }
        }
    }
}

private suspend fun rescheduleReminderSystemsForUser(
    context: Context,
    userId: String,
    systems: Set<ReminderSystem> = ReminderSystem.entries.toSet()
): ReminderRescheduleOutcome {
    val dao = NutRunDatabase.getInstance(context).dao()
    return rescheduleReminderSystems(
        hydration = {
            val hydration = dao.hydrationPlan(userId) ?: HydrationPlanEntity(userId = userId)
            HydrationScheduler(context).schedule(hydration)
        },
        training = {
            val training = (dao.trainingReminderSettings(userId)
                ?: TrainingReminderSettingsEntity(userId = userId))
                .copy(timezoneId = ZoneId.systemDefault().id)
            TrainingReminderScheduler(context).schedule(userId, training)
        },
        supplements = {
            val supplements = (dao.supplementReminderSettings(userId)
                ?: SupplementReminderSettingsEntity(userId = userId))
                .copy(
                    id = "supplement-reminders:$userId",
                    userId = userId,
                    timezoneId = ZoneId.systemDefault().id
                )
            dao.saveSupplementReminderSettings(supplements)
            SupplementReminderScheduler(context).schedule(userId, supplements)
        },
        systems = systems
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
            val systems = inputData.getString(KEY_SYSTEMS)
                ?.split(',')
                ?.mapNotNull { runCatching { ReminderSystem.valueOf(it) }.getOrNull() }
                ?.toSet()
                .orEmpty()
                .ifEmpty { ReminderSystem.entries.toSet() }
            val outcome = rescheduleReminderSystemsForUser(applicationContext, userId, systems)
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

enum class ReminderRecoveryResult { Success, Retry, Failure }

const val MAX_REMINDER_RECOVERY_ATTEMPTS = 3

fun recoveryWorkResult(attempt: Int, requiresRecovery: Boolean): ReminderRecoveryResult = when {
    !requiresRecovery -> ReminderRecoveryResult.Success
    attempt >= MAX_REMINDER_RECOVERY_ATTEMPTS - 1 -> ReminderRecoveryResult.Failure
    else -> ReminderRecoveryResult.Retry
}

class ReminderRescheduleRecoveryScheduler(
    private val enqueuer: ReminderWorkEnqueuer
) {
    constructor(context: Context) : this(WorkManagerReminderWorkEnqueuer(context))

    fun schedule(userId: String, systems: Set<ReminderSystem>) {
        if (userId.isBlank() || systems.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<ReminderRescheduleRecoveryWorker>()
            .setInputData(
                workDataOf(
                    ReminderRescheduleRecoveryWorker.KEY_USER_ID to userId,
                    ReminderRescheduleRecoveryWorker.KEY_SYSTEMS to
                        systems.sortedBy(ReminderSystem::name).joinToString(",") { it.name }
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
