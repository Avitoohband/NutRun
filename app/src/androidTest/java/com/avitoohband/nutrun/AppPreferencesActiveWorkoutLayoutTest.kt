package com.avitoohband.nutrun

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avitoohband.nutrun.data.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesActiveWorkoutLayoutTest {
    private lateinit var preferences: AppPreferences
    private val userA = "layout-user-a"
    private val userB = "layout-user-b"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = AppPreferences(context)
        runBlocking {
            preferences.signOut()
            preferences.clearAccount(userA)
            preferences.clearAccount(userB)
        }
    }

    @Test
    fun defaultsToListMode() = runBlocking {
        preferences.signIn(userA, "a@example.com", System.currentTimeMillis(), false)
        assertEquals(ActiveWorkoutLayoutMode.LIST, preferences.currentSession().activeWorkoutLayoutMode)
    }

    @Test
    fun persistsGridModePerAccount() = runBlocking {
        preferences.signIn(userA, "a@example.com", System.currentTimeMillis(), false)
        preferences.setActiveWorkoutLayoutMode(userA, ActiveWorkoutLayoutMode.GRID)
        preferences.signIn(userB, "b@example.com", System.currentTimeMillis(), false)
        preferences.setActiveWorkoutLayoutMode(userB, ActiveWorkoutLayoutMode.LIST)

        assertEquals(ActiveWorkoutLayoutMode.LIST, preferences.currentSession().activeWorkoutLayoutMode)

        preferences.signIn(userA, "a@example.com", System.currentTimeMillis(), false)
        assertEquals(ActiveWorkoutLayoutMode.GRID, preferences.currentSession().activeWorkoutLayoutMode)
    }

    @Test
    fun signOutPreservesLayoutModeForAccount() = runBlocking {
        preferences.signIn(userA, "a@example.com", System.currentTimeMillis(), false)
        preferences.setActiveWorkoutLayoutMode(userA, ActiveWorkoutLayoutMode.GRID)
        preferences.signOut()
        preferences.signIn(userA, "a@example.com", System.currentTimeMillis(), false)
        assertEquals(ActiveWorkoutLayoutMode.GRID, preferences.currentSession().activeWorkoutLayoutMode)
    }

    @Test
    fun clearAccountRemovesLayoutMode() = runBlocking {
        preferences.signIn(userA, "a@example.com", System.currentTimeMillis(), false)
        preferences.setActiveWorkoutLayoutMode(userA, ActiveWorkoutLayoutMode.GRID)
        preferences.clearAccount(userA)
        preferences.signIn(userA, "a@example.com", System.currentTimeMillis(), false)
        assertEquals(ActiveWorkoutLayoutMode.LIST, preferences.currentSession().activeWorkoutLayoutMode)
    }
}
