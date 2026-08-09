package com.avitoohband.nutrun.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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

enum class ReminderRescheduleOutcome(val requiresRecovery: Boolean) {
    Complete(false),
    Failed(true)
}

suspend fun rescheduleReminderSystems(
    hydration: suspend () -> Unit,
    training: suspend () -> Unit,
    supplements: suspend () -> Unit
): ReminderRescheduleOutcome {
    var failed = false
    listOf(hydration, training, supplements).forEach { reschedule ->
        try {
            reschedule()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }
    return if (failed) ReminderRescheduleOutcome.Failed else ReminderRescheduleOutcome.Complete
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != Intent.ACTION_BOOT_COMPLETED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var userId: String? = null
            try {
                userId = AppPreferences(context).currentSession().authenticatedUserId
                    ?: return@launch
                val outcome = rescheduleReminderSystemsForUser(context, userId)
                if (outcome.requiresRecovery) ReminderRescheduleRecoveryScheduler(context).schedule(userId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                userId?.let { ReminderRescheduleRecoveryScheduler(context).schedule(it) }
            } finally {
                pending.finish()
            }
        }
    }
}

private suspend fun rescheduleReminderSystemsForUser(
    context: Context,
    userId: String
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
            if (rescheduleReminderSystemsForUser(applicationContext, userId).requiresRecovery) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
    }
}

private class ReminderRescheduleRecoveryScheduler(private val context: Context) {
    fun schedule(userId: String) {
        if (userId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<ReminderRescheduleRecoveryWorker>()
            .setInputData(workDataOf(ReminderRescheduleRecoveryWorker.KEY_USER_ID to userId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-reschedule-recovery:$userId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
