package com.avitoohband.nutrun.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.workDataOf
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.domain.isHydrationReminderEligible
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val HYDRATION_WORK = "hydration-reminders"
private const val HYDRATION_CHANNEL = "hydration"

class HydrationReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        if (AppPreferences(applicationContext).currentSession().authenticatedUserId != userId) {
            return Result.success()
        }
        val database = NutRunDatabase.getInstance(applicationContext)
        return try {
            val dao = database.dao()
            val plan = dao.hydrationPlan(userId) ?: HydrationPlanEntity()
            if (!plan.remindersEnabled) return Result.success()

            val consumed = dao.waterTotal(userId, LocalDate.now().toString())
            val now = LocalTime.now()
            val minuteOfDay = now.hour * 60 + now.minute
            if (
                isHydrationReminderEligible(
                    consumed,
                    plan.goalMl,
                    minuteOfDay,
                    plan.wakingStartMinute,
                    plan.wakingEndMinute
                )
            ) {
                showNotification(consumed, plan.goalMl)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            Unit
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
    }

    private fun showNotification(consumedMl: Int, goalMl: Int) {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                HYDRATION_CHANNEL,
                "Water reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, HYDRATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time for water")
            .setContentText("$consumedMl of $goalMl mL logged today")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(2_002, notification)
    }
}

@Singleton
class HydrationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(plan: HydrationPlanEntity) {
        val manager = WorkManager.getInstance(context)
        if (!plan.remindersEnabled) {
            manager.cancelUniqueWork(HYDRATION_WORK)
            return
        }
        val interval = plan.intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<HydrationReminderWorker>(interval, TimeUnit.MINUTES)
            .setInputData(workDataOf(HydrationReminderWorker.KEY_USER_ID to plan.userId))
            .addTag(HYDRATION_WORK)
            .build()
        manager.enqueueUniquePeriodicWork(
            HYDRATION_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
