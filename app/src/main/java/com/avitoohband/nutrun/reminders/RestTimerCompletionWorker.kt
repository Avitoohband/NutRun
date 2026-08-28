package com.avitoohband.nutrun.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.avitoohband.nutrun.builtInExerciseCatalog
import com.avitoohband.nutrun.data.AppPreferences
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.decodeTrainingState
import com.avitoohband.nutrun.shouldDeliverRestTimerCompletion
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

interface RestTimerWorkerEntryPointAccessor {
    fun restTimerNotificationCoordinator(): RestTimerNotificationCoordinator
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RestTimerWorkerHiltEntryPoint : RestTimerWorkerEntryPointAccessor

class RestTimerCompletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val expectedUserId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        val expectedActiveWorkoutId = inputData.getString(KEY_ACTIVE_WORKOUT_ID) ?: return Result.success()
        val expectedEndAtMillis = inputData.getLong(KEY_END_AT_MILLIS, -1L)
        if (expectedEndAtMillis <= 0L) return Result.success()

        val preferences = AppPreferences(applicationContext)
        val currentUserId = preferences.currentSession().authenticatedUserId
        val stateEntity = NutRunDatabase.getInstance(applicationContext)
            .dao()
            .observeTrainingState(expectedUserId)
            .first()
        val activeWorkout = stateEntity
            ?.let { decodeTrainingState(it.payloadJson, builtInExerciseCatalog()) }
            ?.activeWorkout

        if (
            !shouldDeliverRestTimerCompletion(
                expectedUserId = expectedUserId,
                expectedActiveWorkoutId = expectedActiveWorkoutId,
                expectedEndAtMillis = expectedEndAtMillis,
                currentUserId = currentUserId,
                currentActiveWorkout = activeWorkout,
                nowMillis = System.currentTimeMillis()
            )
        ) {
            return Result.success()
        }

        val coordinator = EntryPointAccessors.fromApplication(
            applicationContext,
            RestTimerWorkerHiltEntryPoint::class.java
        ).restTimerNotificationCoordinator()
        coordinator.cancel(expectedUserId)
        coordinator.showCompletionNotification()
        return Result.success()
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ACTIVE_WORKOUT_ID = "active_workout_id"
        const val KEY_END_AT_MILLIS = "end_at_millis"
    }
}
