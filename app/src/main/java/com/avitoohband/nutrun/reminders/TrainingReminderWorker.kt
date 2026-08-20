package com.avitoohband.nutrun.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.avitoohband.nutrun.MainActivity
import com.avitoohband.nutrun.builtInExerciseCatalog
import com.avitoohband.nutrun.PersistedTrainingState
import com.avitoohband.nutrun.templatesForDate
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.ReminderDeliveryEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.decodeTrainingState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class TrainingReminderType { PREVIOUS_DAY, SAME_DAY }

fun trainingReminderNames(
    state: PersistedTrainingState?,
    date: LocalDate
): List<String> = state?.let {
    templatesForDate(it.workoutTemplates, it.weeklyDayPlans, it.scheduleOverrides, date)
        .map { template -> template.name }
}.orEmpty()

class TrainingReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        val type = inputData.getString(KEY_TYPE)
            ?.let { runCatching { TrainingReminderType.valueOf(it) }.getOrNull() }
            ?: return Result.success()
        val database = NutRunDatabase.getInstance(applicationContext)
        val dao = database.dao()
        val settings = dao.trainingReminderSettings(userId)
            ?: TrainingReminderSettingsEntity(userId = userId)

        try {
            if (
                !settings.enabled ||
                AppPreferences(applicationContext).currentSession().authenticatedUserId != userId ||
                !notificationsAllowed()
            ) return Result.success()

            val trainingDate = when (type) {
                TrainingReminderType.PREVIOUS_DAY -> LocalDate.now().plusDays(1)
                TrainingReminderType.SAME_DAY -> LocalDate.now()
            }
            val state = dao.observeTrainingState(userId).first()
                ?.let { decodeTrainingState(it.payloadJson, builtInExerciseCatalog()) }
            val names = trainingReminderNames(state, trainingDate)
            if (names.isEmpty()) return Result.success()
            if (dao.reminderDelivered(userId, type.name, trainingDate.toString())) {
                return Result.success()
            }

            val delivery = ReminderDeliveryEntity(
                id = "$userId:${type.name}:$trainingDate",
                userId = userId,
                reminderType = type.name,
                trainingDate = trainingDate.toString(),
                deliveredAtMillis = System.currentTimeMillis()
            )
            if (dao.recordReminderDelivery(delivery) != -1L) {
                showNotification(type, names)
            }
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        } finally {
            TrainingReminderScheduler.scheduleNext(applicationContext, userId, type, settings)
        }
    }

    private fun notificationsAllowed(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    private fun showNotification(type: TrainingReminderType, sessionNames: List<String>) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Training reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, "training")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            3_000 + type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (type == TrainingReminderType.PREVIOUS_DAY) {
            "Training tomorrow"
        } else {
            "Training today"
        }
        val text = sessionNames.joinToString(", ")
        NotificationManagerCompat.from(applicationContext).notify(
            3_000 + type.ordinal,
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_TYPE = "type"
        private const val CHANNEL = "training"
    }
}

@Singleton
class TrainingReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(userId: String, settings: TrainingReminderSettingsEntity) {
        if (!settings.enabled) {
            cancel(userId)
            return
        }
        TrainingReminderType.entries.forEach { scheduleNext(context, userId, it, settings) }
    }

    fun cancel(userId: String) {
        TrainingReminderType.entries.forEach {
            WorkManager.getInstance(context).cancelUniqueWork(workName(userId, it))
        }
    }

    companion object {
        fun scheduleNext(
            context: Context,
            userId: String,
            type: TrainingReminderType,
            settings: TrainingReminderSettingsEntity
        ) {
            if (!settings.enabled || userId.isBlank()) return
            val zone = runCatching { ZoneId.of(settings.timezoneId) }.getOrDefault(ZoneId.systemDefault())
            val now = LocalDateTime.now(zone)
            val minute = when (type) {
                TrainingReminderType.PREVIOUS_DAY -> settings.previousDayMinute
                TrainingReminderType.SAME_DAY -> settings.sameDayMinute
            }
            val time = LocalTime.of(minute / 60, minute % 60)
            var next = LocalDateTime.of(now.toLocalDate(), time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<TrainingReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        TrainingReminderWorker.KEY_USER_ID to userId,
                        TrainingReminderWorker.KEY_TYPE to type.name
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(userId, type),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun workName(userId: String, type: TrainingReminderType) =
            "training-reminder:$userId:${type.name}"
    }
}
