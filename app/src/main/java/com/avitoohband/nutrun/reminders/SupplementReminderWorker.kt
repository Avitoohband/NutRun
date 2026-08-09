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
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.ReminderDeliveryEntity
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.decodeTrainingState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private const val SUPPLEMENT_CHANNEL = "supplements"
private const val EXTRA_SUPPLEMENTS_SECTION = "supplements_section"

fun supplementDeliveryId(userId: String, date: LocalDate, minute: Int): String =
    "$userId:SUPPLEMENT:$minute:$date"

fun isSupplementDeliveryDateValid(intended: LocalDate, current: LocalDate): Boolean =
    intended == current

class SupplementReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        val intendedDate = inputData.getString(KEY_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.success()
        val minute = inputData.getInt(KEY_MINUTE, -1)
        if (minute !in 0 until 24 * 60) return Result.success()

        val scheduler = SupplementReminderScheduler(applicationContext)
        try {
            if (AppPreferences(applicationContext).currentSession().authenticatedUserId != userId) {
                scheduler.cancel(userId)
                return Result.success()
            }

            val dao = NutRunDatabase.getInstance(applicationContext).dao()
            val settings = dao.supplementReminderSettings(userId)
                ?: SupplementReminderSettingsEntity(userId = userId)
            if (!settings.enabled) {
                scheduler.cancel(userId)
                return Result.success()
            }

            val zone = settings.zone()
            if (!isSupplementDeliveryDateValid(intendedDate, LocalDate.now(zone))) {
                scheduler.schedule(userId, settings)
                return Result.success()
            }

            val supplements = dao.observeTrainingState(userId).first()
                ?.let { decodeTrainingState(it.payloadJson, builtInExerciseCatalog()) }
                ?.supplements
                .orEmpty()
            val dueSupplements = supplementsDueForReminder(supplements, intendedDate, minute)
            if (dueSupplements.isEmpty()) {
                scheduler.schedule(userId, settings)
                return Result.success()
            }

            val reminderType = "SUPPLEMENT:$minute"
            if (dao.reminderDelivered(userId, reminderType, intendedDate.toString())) {
                scheduler.schedule(userId, settings)
                return Result.success()
            }

            if (notificationsAllowed()) {
                val delivery = ReminderDeliveryEntity(
                    id = supplementDeliveryId(userId, intendedDate, minute),
                    userId = userId,
                    reminderType = reminderType,
                    trainingDate = intendedDate.toString(),
                    deliveredAtMillis = System.currentTimeMillis()
                )
                if (dao.recordReminderDelivery(delivery) != -1L) {
                    showNotification(dueSupplements, userId, intendedDate, minute)
                }
            }
            scheduler.schedule(userId, settings)
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        }
    }

    private fun notificationsAllowed(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    private fun showNotification(
        supplements: List<com.avitoohband.nutrun.Supplement>,
        userId: String,
        date: LocalDate,
        minute: Int
    ) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                SUPPLEMENT_CHANNEL,
                "Supplement reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val text = supplements.joinToString(", ") { "${it.name} (${it.dose})" }
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, "today")
            .putExtra(EXTRA_SUPPLEMENTS_SECTION, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId(userId, date, minute),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(applicationContext).notify(
            notificationId(userId, date, minute),
            NotificationCompat.Builder(applicationContext, SUPPLEMENT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Supplement reminder")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_DATE = "date"
        const val KEY_MINUTE = "minute"
    }
}

@Singleton
class SupplementReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun schedule(userId: String, settings: SupplementReminderSettingsEntity) {
        if (userId.isBlank() || !settings.enabled ||
            AppPreferences(context).currentSession().authenticatedUserId != userId
        ) {
            cancel(userId)
            return
        }

        val supplements = NutRunDatabase.getInstance(context).dao().observeTrainingState(userId).first()
            ?.let { decodeTrainingState(it.payloadJson, builtInExerciseCatalog()) }
            ?.supplements
            .orEmpty()
        val now = ZonedDateTime.now(settings.zone())
        val next = nextSupplementReminder(supplements, now)
        if (next == null) {
            cancel(userId)
            return
        }

        val minute = next.hour * 60 + next.minute
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<SupplementReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    SupplementReminderWorker.KEY_USER_ID to userId,
                    SupplementReminderWorker.KEY_DATE to next.toLocalDate().toString(),
                    SupplementReminderWorker.KEY_MINUTE to minute
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(userId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(userId: String) {
        if (userId.isNotBlank()) WorkManager.getInstance(context).cancelUniqueWork(workName(userId))
    }

    companion object {
        fun workName(userId: String): String = "supplement-reminder:$userId"
    }
}

private fun SupplementReminderSettingsEntity.zone(): ZoneId =
    runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())

private fun notificationId(userId: String, date: LocalDate, minute: Int): Int =
    supplementDeliveryId(userId, date, minute).hashCode() and Int.MAX_VALUE
