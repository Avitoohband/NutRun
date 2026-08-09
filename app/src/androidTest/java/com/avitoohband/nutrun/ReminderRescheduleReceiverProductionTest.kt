package com.avitoohband.nutrun

import android.content.Intent
import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.reminders.HydrationReminderWorker
import com.avitoohband.nutrun.reminders.ReminderRescheduleReceiver
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRescheduleReceiverProductionTest {
    @Test
    fun defaultReceiverFactoryHandlesBootTimezoneAndIgnoresOtherActions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "receiver-${UUID.randomUUID()}"
        val preferences = AppPreferences(context)
        val dao = NutRunDatabase.getInstance(context).dao()
        val workName = "supplement-reminder:$userId"
        val receiver = ReminderRescheduleReceiver()
        val zone = ZoneId.systemDefault().id

        preferences.signIn(userId, "$userId@example.com", 1L, subscriber = false)
        dao.saveSupplementReminderSettings(
            SupplementReminderSettingsEntity(
                id = "supplement-reminders:$userId",
                userId = userId,
                enabled = false,
                timezoneId = "UTC"
            )
        )
        val workManager = WorkManager.getInstance(context)
        val workStates = mutableListOf<WorkInfo.State>()
        val observer = Observer<List<WorkInfo>> { infos ->
            workStates.clear()
            workStates += infos.map { it.state }
        }
        workManager.getWorkInfosForUniqueWorkLiveData(workName).observeForever(observer)
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<HydrationReminderWorker>()
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
        )
        awaitReceiverWork { WorkInfo.State.ENQUEUED in workStates }

        receiver.onReceive(context, Intent("com.avitoohband.nutrun.UNRELATED"))
        assertEquals("UTC", dao.supplementReminderSettings(userId)?.timezoneId)

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        awaitReceiverWork {
            dao.supplementReminderSettings(userId)?.timezoneId == zone &&
                workStates.none { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.RUNNING }
        }

        dao.saveSupplementReminderSettings(
            dao.supplementReminderSettings(userId)!!.copy(timezoneId = "UTC")
        )
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        awaitReceiverWork { dao.supplementReminderSettings(userId)?.timezoneId == zone }

        assertFalse(
            workStates.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.RUNNING }
        )
        workManager.getWorkInfosForUniqueWorkLiveData(workName).removeObserver(observer)
        workManager.cancelUniqueWork(workName)
        preferences.clearAccount(userId)
    }

    private suspend fun awaitReceiverWork(condition: suspend () -> Boolean) {
        withTimeout(10_000) {
            while (!condition()) delay(50)
        }
    }
}
