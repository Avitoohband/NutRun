package com.avitoohband.nutrun.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avitoohband.nutrun.ActiveWorkoutLayoutMode
import com.avitoohband.nutrun.domain.EntitlementKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("nutrun_settings")

data class SessionPreferences(
    val authenticatedUserId: String? = null,
    val authenticatedEmail: String? = null,
    val trialStartedAtMillis: Long? = null,
    val subscriber: Boolean = false,
    val darkMode: Boolean = false,
    val tutorialAcknowledgedVersion: Int = 0,
    val tutorialCompletedAtMillis: Long? = null,
    val activeWorkoutLayoutMode: ActiveWorkoutLayoutMode = ActiveWorkoutLayoutMode.LIST
) {
    fun entitlement(nowMillis: Long = System.currentTimeMillis()): EntitlementKind {
        if (subscriber) return EntitlementKind.SUBSCRIBER
        val started = trialStartedAtMillis ?: return EntitlementKind.FREE_AD_SUPPORTED
        val expires = Instant.ofEpochMilli(started).plus(Duration.ofDays(30)).toEpochMilli()
        return if (nowMillis < expires) EntitlementKind.TRIAL else EntitlementKind.FREE_AD_SUPPORTED
    }

    fun trialDaysRemaining(nowMillis: Long = System.currentTimeMillis()): Int {
        val started = trialStartedAtMillis ?: return 0
        val expires = Instant.ofEpochMilli(started).plus(Duration.ofDays(30))
        return Duration.between(Instant.ofEpochMilli(nowMillis), expires).toDays()
            .coerceAtLeast(0)
            .toInt()
    }
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val currentUserId = stringPreferencesKey("current_user_id")
        val darkMode = booleanPreferencesKey("dark_mode")
        fun email(userId: String) = stringPreferencesKey("account_${userId}_email")
        fun trialStartedAt(userId: String) = longPreferencesKey("account_${userId}_trial_started_at")
        fun subscriber(userId: String) = booleanPreferencesKey("account_${userId}_subscriber")
        fun reminderRecoverySystems(userId: String) = stringPreferencesKey("account_${userId}_reminder_recovery")
        fun reminderRecoveryClosing(userId: String) = booleanPreferencesKey("account_${userId}_reminder_recovery_closing")
        fun tutorialAcknowledgedVersion(userId: String) =
            intPreferencesKey("account_${userId}_tutorial_ack_version")
        fun tutorialCompletedAt(userId: String) =
            longPreferencesKey("account_${userId}_tutorial_completed_at")
        fun activeWorkoutLayoutMode(userId: String) =
            stringPreferencesKey("account_${userId}_active_workout_layout")
    }

    val session: Flow<SessionPreferences> = context.dataStore.data.map { values ->
        val userId = values[Keys.currentUserId]
        SessionPreferences(
            authenticatedUserId = userId,
            authenticatedEmail = userId?.let { values[Keys.email(it)] },
            trialStartedAtMillis = userId?.let { values[Keys.trialStartedAt(it)] },
            subscriber = userId?.let { values[Keys.subscriber(it)] } ?: false,
            darkMode = values[Keys.darkMode] ?: false,
            tutorialAcknowledgedVersion = userId?.let {
                values[Keys.tutorialAcknowledgedVersion(it)] ?: 0
            } ?: 0,
            tutorialCompletedAtMillis = userId?.let { values[Keys.tutorialCompletedAt(it)] },
            activeWorkoutLayoutMode = userId?.let {
                ActiveWorkoutLayoutMode.fromStoredValue(values[Keys.activeWorkoutLayoutMode(it)])
            } ?: ActiveWorkoutLayoutMode.LIST
        )
    }

    suspend fun currentSession(): SessionPreferences = session.first()

    suspend fun signIn(
        userId: String,
        email: String,
        trialStartedAtMillis: Long,
        subscriber: Boolean
    ) {
        context.dataStore.edit { values ->
            values[Keys.currentUserId] = userId
            values[Keys.email(userId)] = email.trim()
            values[Keys.trialStartedAt(userId)] = trialStartedAtMillis
            values[Keys.subscriber(userId)] = subscriber
        }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.remove(Keys.currentUserId) }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.darkMode] = enabled }
    }

    suspend fun setSubscriber(userId: String, enabled: Boolean) {
        context.dataStore.edit { it[Keys.subscriber(userId)] = enabled }
    }

    suspend fun acknowledgeTutorial(
        userId: String,
        version: Int,
        completedAtMillis: Long? = null
    ) {
        context.dataStore.edit { values ->
            values[Keys.tutorialAcknowledgedVersion(userId)] = version
            if (completedAtMillis != null) {
                values[Keys.tutorialCompletedAt(userId)] = completedAtMillis
            } else {
                values.remove(Keys.tutorialCompletedAt(userId))
            }
        }
    }

    suspend fun setActiveWorkoutLayoutMode(userId: String, mode: ActiveWorkoutLayoutMode) {
        context.dataStore.edit { values ->
            values[Keys.activeWorkoutLayoutMode(userId)] = mode.name
        }
    }

    suspend fun clearAccount(userId: String) {
        context.dataStore.edit { values ->
            values.remove(Keys.email(userId))
            values.remove(Keys.trialStartedAt(userId))
            values.remove(Keys.subscriber(userId))
            values.remove(Keys.reminderRecoverySystems(userId))
            values.remove(Keys.reminderRecoveryClosing(userId))
            values.remove(Keys.tutorialAcknowledgedVersion(userId))
            values.remove(Keys.tutorialCompletedAt(userId))
            values.remove(Keys.activeWorkoutLayoutMode(userId))
            if (values[Keys.currentUserId] == userId) values.remove(Keys.currentUserId)
        }
    }

}
