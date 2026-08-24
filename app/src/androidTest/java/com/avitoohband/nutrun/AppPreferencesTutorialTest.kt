package com.avitoohband.nutrun

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avitoohband.nutrun.data.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesTutorialTest {
    private lateinit var preferences: AppPreferences
    private val userId = "tutorial-prefs-user"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = AppPreferences(context)
        runBlocking {
            preferences.signOut()
            preferences.clearAccount(userId)
        }
    }

    @Test
    fun acknowledgeTutorialPersistsVersionAndCompletion() = runBlocking {
        val completedAt = 1_700_000_000_000L
        preferences.signIn(userId, "tutorial@example.com", completedAt, false)
        preferences.acknowledgeTutorial(userId, CURRENT_TUTORIAL_VERSION, completedAt)

        val session = preferences.currentSession()
        assertEquals(CURRENT_TUTORIAL_VERSION, session.tutorialAcknowledgedVersion)
        assertEquals(completedAt, session.tutorialCompletedAtMillis)
    }

    @Test
    fun skipTutorialClearsCompletionTimestamp() = runBlocking {
        preferences.signIn(userId, "tutorial@example.com", System.currentTimeMillis(), false)
        preferences.acknowledgeTutorial(userId, CURRENT_TUTORIAL_VERSION, null)

        val session = preferences.currentSession()
        assertEquals(CURRENT_TUTORIAL_VERSION, session.tutorialAcknowledgedVersion)
        assertNull(session.tutorialCompletedAtMillis)
    }

    @Test
    fun signOutPreservesTutorialStateForAccount() = runBlocking {
        preferences.signIn(userId, "tutorial@example.com", System.currentTimeMillis(), false)
        preferences.acknowledgeTutorial(userId, CURRENT_TUTORIAL_VERSION, 1_700_000_000_000L)
        preferences.signOut()
        preferences.signIn(userId, "tutorial@example.com", System.currentTimeMillis(), false)

        val session = preferences.currentSession()
        assertEquals(CURRENT_TUTORIAL_VERSION, session.tutorialAcknowledgedVersion)
        assertEquals(1_700_000_000_000L, session.tutorialCompletedAtMillis)
    }

    @Test
    fun clearAccountRemovesTutorialState() = runBlocking {
        preferences.signIn(userId, "tutorial@example.com", System.currentTimeMillis(), false)
        preferences.acknowledgeTutorial(userId, CURRENT_TUTORIAL_VERSION, 1_700_000_000_000L)
        preferences.clearAccount(userId)
        preferences.signIn(userId, "tutorial@example.com", System.currentTimeMillis(), false)

        val session = preferences.currentSession()
        assertEquals(0, session.tutorialAcknowledgedVersion)
        assertNull(session.tutorialCompletedAtMillis)
    }
}
