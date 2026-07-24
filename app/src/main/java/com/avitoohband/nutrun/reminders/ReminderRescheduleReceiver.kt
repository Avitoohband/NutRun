package com.avitoohband.nutrun.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != Intent.ACTION_BOOT_COMPLETED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = AppPreferences(context).currentSession().authenticatedUserId
                    ?: return@launch
                val dao = NutRunDatabase.getInstance(context).dao()
                val hydration = dao.hydrationPlan(userId) ?: HydrationPlanEntity(userId = userId)
                HydrationScheduler(context).schedule(hydration)
                val training = (dao.trainingReminderSettings(userId)
                    ?: TrainingReminderSettingsEntity(userId = userId))
                    .copy(timezoneId = ZoneId.systemDefault().id)
                TrainingReminderScheduler(context).schedule(userId, training)
            } finally {
                pending.finish()
            }
        }
    }
}
