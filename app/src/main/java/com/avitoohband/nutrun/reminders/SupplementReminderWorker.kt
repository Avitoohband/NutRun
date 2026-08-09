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
import androidx.work.OneTimeWorkRequest
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
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SUPPLEMENT_CHANNEL = "supplements"
private const val EXTRA_SUPPLEMENTS_SECTION = "supplements_section"

fun supplementDeliveryId(userId: String, date: LocalDate, minute: Int): String =
    "$userId:SUPPLEMENT:$minute:$date"

fun isSupplementDeliveryDateValid(intended: LocalDate, current: LocalDate): Boolean =
    intended == current

sealed interface SupplementReminderScheduleDecision {
    data class Cancel(val uniqueWorkName: String) : SupplementReminderScheduleDecision

    data class Enqueue(
        val uniqueWorkName: String,
        val userId: String,
        val intendedDate: LocalDate,
        val minute: Int
    ) : SupplementReminderScheduleDecision
}

fun supplementSchedulingDecision(
    userId: String,
    authenticatedUserId: String?,
    enabled: Boolean,
    next: ZonedDateTime?
): SupplementReminderScheduleDecision {
    val workName = supplementReminderWorkName(userId)
    if (userId.isBlank() || authenticatedUserId != userId || !enabled || next == null) {
        return SupplementReminderScheduleDecision.Cancel(workName)
    }
    return SupplementReminderScheduleDecision.Enqueue(
        uniqueWorkName = workName,
        userId = userId,
        intendedDate = next.toLocalDate(),
        minute = next.hour * 60 + next.minute
    )
}

sealed interface SupplementDeliveryDecision {
    data object Cancel : SupplementDeliveryDecision
    data object Reschedule : SupplementDeliveryDecision
    data class Deliver(val supplements: List<com.avitoohband.nutrun.Supplement>) :
        SupplementDeliveryDecision
}

fun supplementDeliveryDecision(
    userId: String,
    authenticatedUserId: String?,
    enabled: Boolean,
    intendedDate: LocalDate,
    currentDate: LocalDate,
    minute: Int,
    supplements: List<com.avitoohband.nutrun.Supplement>
): SupplementDeliveryDecision {
    if (authenticatedUserId != userId || !enabled) return SupplementDeliveryDecision.Cancel
    if (!isSupplementDeliveryDateValid(intendedDate, currentDate)) {
        return SupplementDeliveryDecision.Reschedule
    }
    val due = supplementsDueForReminder(supplements, intendedDate, minute)
    return due.takeIf(List<*>::isNotEmpty)
        ?.let(SupplementDeliveryDecision::Deliver)
        ?: SupplementDeliveryDecision.Reschedule
}

enum class SupplementNotificationDeliveryResult { Delivered, AlreadyDelivered, Retry, FinalizePending }

enum class SupplementDeliveryClaim { Available, Acquired, Pending, Posted, Delivered }

data class SupplementDeliveryClaimRequest(
    val id: String,
    val userId: String,
    val reminderType: String,
    val trainingDate: String
)

interface SupplementDeliveryStore {
    suspend fun acquire(claim: SupplementDeliveryClaimRequest, nowMillis: Long): SupplementDeliveryClaim
    suspend fun markPosted(claimId: String): Boolean
    suspend fun finalize(claimId: String, deliveredAtMillis: Long): Boolean
    suspend fun release(claimId: String)
}

suspend fun claimedSupplementDelivery(
    store: SupplementDeliveryStore,
    claim: SupplementDeliveryClaimRequest,
    nowMillis: Long,
    postNotification: suspend () -> Unit
): SupplementNotificationDeliveryResult {
    return when (store.acquire(claim, nowMillis)) {
    SupplementDeliveryClaim.Delivered -> SupplementNotificationDeliveryResult.AlreadyDelivered
    SupplementDeliveryClaim.Posted -> SupplementNotificationDeliveryResult.FinalizePending
    SupplementDeliveryClaim.Pending -> SupplementNotificationDeliveryResult.Retry
    SupplementDeliveryClaim.Available -> SupplementNotificationDeliveryResult.Retry
    SupplementDeliveryClaim.Acquired -> try {
        postNotification()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        runCatching { store.release(claim.id) }
        return SupplementNotificationDeliveryResult.Retry
    }.let {
        val posted = runCatching { store.markPosted(claim.id) }.getOrDefault(false)
        if (!posted) {
            SupplementNotificationDeliveryResult.FinalizePending
        } else if (runCatching { store.finalize(claim.id, nowMillis) }.getOrDefault(false)) {
            SupplementNotificationDeliveryResult.Delivered
        } else {
            SupplementNotificationDeliveryResult.FinalizePending
        }
    }
}
}

private val supplementDeliveryLocks = Array(32) { Mutex() }

suspend fun deliverSupplementNotification(
    deliveryId: String,
    alreadyDelivered: suspend () -> Boolean,
    postNotification: suspend () -> Unit,
    recordDelivery: suspend () -> Boolean
): SupplementNotificationDeliveryResult = supplementDeliveryLocks[
    (deliveryId.hashCode() and Int.MAX_VALUE) % supplementDeliveryLocks.size
].withLock {
    try {
        if (alreadyDelivered()) return@withLock SupplementNotificationDeliveryResult.AlreadyDelivered
        postNotification()
        if (recordDelivery()) {
            SupplementNotificationDeliveryResult.Delivered
        } else {
            SupplementNotificationDeliveryResult.AlreadyDelivered
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        SupplementNotificationDeliveryResult.Retry
    }
}

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
            val dao = NutRunDatabase.getInstance(applicationContext).dao()
            val settings = dao.supplementReminderSettings(userId)
                ?: SupplementReminderSettingsEntity(userId = userId)
            val authenticatedUserId = AppPreferences(applicationContext).currentSession().authenticatedUserId
            val baseDecision = supplementDeliveryDecision(
                userId = userId,
                authenticatedUserId = authenticatedUserId,
                enabled = settings.enabled,
                intendedDate = intendedDate,
                currentDate = LocalDate.now(settings.zone()),
                minute = minute,
                supplements = emptyList()
            )
            if (baseDecision == SupplementDeliveryDecision.Cancel) {
                scheduler.cancel(userId)
                return Result.success()
            }
            if (baseDecision == SupplementDeliveryDecision.Reschedule &&
                !isSupplementDeliveryDateValid(intendedDate, LocalDate.now(settings.zone()))
            ) {
                scheduler.schedule(userId, settings)
                return Result.success()
            }

            val supplements = dao.observeTrainingState(userId).first()
                ?.let { decodeTrainingState(it.payloadJson, builtInExerciseCatalog()) }
                ?.supplements
                .orEmpty()
            val deliveryDecision = supplementDeliveryDecision(
                userId = userId,
                authenticatedUserId = authenticatedUserId,
                enabled = settings.enabled,
                intendedDate = intendedDate,
                currentDate = LocalDate.now(settings.zone()),
                minute = minute,
                supplements = supplements
            )
            val dueSupplements = when (deliveryDecision) {
                SupplementDeliveryDecision.Cancel -> {
                    scheduler.cancel(userId)
                    return Result.success()
                }
                SupplementDeliveryDecision.Reschedule -> {
                    scheduler.schedule(userId, settings)
                    return Result.success()
                }
                is SupplementDeliveryDecision.Deliver -> deliveryDecision.supplements
            }

            val reminderType = "SUPPLEMENT:$minute"
            if (dao.reminderDelivered(userId, reminderType, intendedDate.toString())) {
                scheduler.schedule(userId, settings)
                return Result.success()
            }

            if (notificationsAllowed()) {
                when (
                    claimedSupplementDelivery(
                        store = RoomSupplementDeliveryStore(dao),
                        claim = SupplementDeliveryClaimRequest(
                            id = supplementDeliveryId(userId, intendedDate, minute),
                            userId = userId,
                            reminderType = reminderType,
                            trainingDate = intendedDate.toString()
                        ),
                        nowMillis = System.currentTimeMillis(),
                        postNotification = { showNotification(dueSupplements, userId, intendedDate, minute) }
                    )
                ) {
                    SupplementNotificationDeliveryResult.Retry -> return Result.retry()
                    SupplementNotificationDeliveryResult.Delivered,
                    SupplementNotificationDeliveryResult.AlreadyDelivered,
                    SupplementNotificationDeliveryResult.FinalizePending -> Unit
                }
            }
            scheduler.schedule(userId, settings)
            return Result.success()
        } catch (error: CancellationException) {
            throw error
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

interface ReminderWorkEnqueuer {
    fun enqueue(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest)
    fun cancel(name: String)
}

class WorkManagerReminderWorkEnqueuer(context: Context) : ReminderWorkEnqueuer {
    private val manager = WorkManager.getInstance(context)

    override fun enqueue(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
        manager.enqueueUniqueWork(name, policy, request)
    }

    override fun cancel(name: String) {
        manager.cancelUniqueWork(name)
    }
}

interface SupplementReminderSchedulerStore {
    suspend fun authenticatedUserId(): String?
    suspend fun supplements(userId: String): List<com.avitoohband.nutrun.Supplement>
}

@Singleton
class SupplementReminderScheduler(
    private val store: SupplementReminderSchedulerStore,
    private val enqueuer: ReminderWorkEnqueuer,
    private val now: (ZoneId) -> ZonedDateTime
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        store = object : SupplementReminderSchedulerStore {
            override suspend fun authenticatedUserId(): String? =
                AppPreferences(context).currentSession().authenticatedUserId

            override suspend fun supplements(userId: String): List<com.avitoohband.nutrun.Supplement> =
                NutRunDatabase.getInstance(context).dao().observeTrainingState(userId).first()
                    ?.let { decodeTrainingState(it.payloadJson, builtInExerciseCatalog()) }
                    ?.supplements
                    .orEmpty()
        },
        enqueuer = WorkManagerReminderWorkEnqueuer(context),
        now = ZonedDateTime::now
    )

    suspend fun schedule(userId: String, settings: SupplementReminderSettingsEntity) {
        val authenticatedUserId = store.authenticatedUserId()
        if (userId.isBlank() || !settings.enabled || authenticatedUserId != userId) {
            cancel(userId)
            return
        }

        val supplements = store.supplements(userId)
        val current = now(settings.zone())
        val next = nextSupplementReminder(supplements, current)
        if (next == null) {
            cancel(userId)
            return
        }

        val decision = supplementSchedulingDecision(userId, authenticatedUserId, settings.enabled, next)
        val enqueue = decision as? SupplementReminderScheduleDecision.Enqueue
        if (enqueue == null) {
            cancel(userId)
            return
        }
        val delay = Duration.between(current, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<SupplementReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    SupplementReminderWorker.KEY_USER_ID to enqueue.userId,
                    SupplementReminderWorker.KEY_DATE to enqueue.intendedDate.toString(),
                    SupplementReminderWorker.KEY_MINUTE to enqueue.minute
                )
            )
            .build()
        enqueuer.enqueue(enqueue.uniqueWorkName, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(userId: String) {
        if (userId.isNotBlank()) enqueuer.cancel(workName(userId))
    }

    companion object {
        fun workName(userId: String): String = supplementReminderWorkName(userId)
    }
}

private class RoomSupplementDeliveryStore(
    private val dao: com.avitoohband.nutrun.data.NutRunDao
) : SupplementDeliveryStore {
    override suspend fun acquire(
        claim: SupplementDeliveryClaimRequest,
        nowMillis: Long
    ): SupplementDeliveryClaim = dao.acquireSupplementDeliveryClaim(
        ReminderDeliveryEntity(
            id = claim.id,
            userId = claim.userId,
            reminderType = claim.reminderType,
            trainingDate = claim.trainingDate,
            deliveredAtMillis = 0,
            state = ReminderDeliveryEntity.STATE_PENDING,
            claimedAtMillis = nowMillis
        ),
        nowMillis - CLAIM_LEASE_MILLIS
    ).let(SupplementDeliveryClaim::valueOf)

    override suspend fun finalize(claimId: String, deliveredAtMillis: Long): Boolean =
        dao.finalizeReminderDelivery(claimId, deliveredAtMillis) == 1

    override suspend fun markPosted(claimId: String): Boolean = dao.markReminderDeliveryPosted(claimId) == 1

    override suspend fun release(claimId: String) {
        dao.releaseReminderDeliveryClaim(claimId)
    }
}

private const val CLAIM_LEASE_MILLIS = 15 * 60_000L

private fun supplementReminderWorkName(userId: String): String = "supplement-reminder:$userId"

private fun SupplementReminderSettingsEntity.zone(): ZoneId =
    runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())

private fun notificationId(userId: String, date: LocalDate, minute: Int): Int =
    supplementDeliveryId(userId, date, minute).hashCode() and Int.MAX_VALUE
