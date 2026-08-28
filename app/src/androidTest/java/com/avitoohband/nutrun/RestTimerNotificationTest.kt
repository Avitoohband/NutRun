package com.avitoohband.nutrun

import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avitoohband.nutrun.reminders.RestTimerNotificationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestTimerNotificationTest {
    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} " +
                    android.Manifest.permission.POST_NOTIFICATIONS
            ).close()
        }
    }

    @Test
    fun scheduleCreatesLowImportanceActiveChannelAndOngoingNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator = RestTimerNotificationCoordinator(context)
        val userId = "rest-timer-test-user"
        coordinator.cancel(userId)

        val endAt = System.currentTimeMillis() + 90_000L
        coordinator.schedule(userId, "active-test", endAt)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(
            RestTimerNotificationCoordinator.ACTIVE_CHANNEL
        )
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel?.importance)

        val posted = notificationManager.activeNotifications.firstOrNull {
            it.id == RestTimerNotificationCoordinator.ONGOING_NOTIFICATION_ID
        }
        assertNotNull(posted)
        assertTrue(posted!!.isOngoing)

        coordinator.cancel(userId)
        assertNull(
            notificationManager.activeNotifications.firstOrNull {
                it.id == RestTimerNotificationCoordinator.ONGOING_NOTIFICATION_ID
            }
        )
    }

    @Test
    fun replacingTimerKeepsSingleOngoingNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator = RestTimerNotificationCoordinator(context)
        val userId = "rest-timer-replace-user"
        coordinator.cancel(userId)

        coordinator.schedule(userId, "active-a", System.currentTimeMillis() + 60_000L)
        coordinator.schedule(userId, "active-a", System.currentTimeMillis() + 120_000L)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val ongoing = notificationManager.activeNotifications.filter {
            it.id == RestTimerNotificationCoordinator.ONGOING_NOTIFICATION_ID
        }
        assertEquals(1, ongoing.size)

        coordinator.cancel(userId)
    }
}
