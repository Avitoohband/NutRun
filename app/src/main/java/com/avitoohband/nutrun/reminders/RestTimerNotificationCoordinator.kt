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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.avitoohband.nutrun.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface RestTimerNotifier {
    fun schedule(userId: String, activeWorkoutId: String, endAtMillis: Long)
    fun cancel(userId: String)
}

@Singleton
class RestTimerNotificationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) : RestTimerNotifier {
    @android.annotation.SuppressLint("MissingPermission")
    override fun schedule(userId: String, activeWorkoutId: String, endAtMillis: Long) {
        if (!notificationsAllowed()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ACTIVE_CHANNEL,
                "Rest timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FINISHED_CHANNEL,
                "Rest complete",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            ONGOING_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, "training")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            ONGOING_NOTIFICATION_ID,
            NotificationCompat.Builder(context, ACTIVE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Rest timer")
                .setContentText("Return to your active workout")
                .setWhen(endAtMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
        )
        val delayMillis = (endAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(userId),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RestTimerCompletionWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        RestTimerCompletionWorker.KEY_USER_ID to userId,
                        RestTimerCompletionWorker.KEY_ACTIVE_WORKOUT_ID to activeWorkoutId,
                        RestTimerCompletionWorker.KEY_END_AT_MILLIS to endAtMillis
                    )
                )
                .build()
        )
    }

    override fun cancel(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(userId))
        NotificationManagerCompat.from(context).cancel(ONGOING_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(COMPLETION_NOTIFICATION_ID)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun showCompletionNotification() {
        if (!notificationsAllowed()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                FINISHED_CHANNEL,
                "Rest complete",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            COMPLETION_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, "training")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            COMPLETION_NOTIFICATION_ID,
            NotificationCompat.Builder(context, FINISHED_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Rest complete")
                .setContentText("Your next set is ready.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notificationsAllowed(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTIVE_CHANNEL = "rest_timer_active_v1"
        const val FINISHED_CHANNEL = "rest_timer_finished_v1"
        const val ONGOING_NOTIFICATION_ID = 4_201
        const val COMPLETION_NOTIFICATION_ID = 4_202

        fun workName(userId: String): String = "rest-timer:$userId"
    }
}
