package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.domain.UserProfile
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialStateTest {
    private val profile = UserProfile(
        email = "user@example.com",
        birthDate = LocalDate.of(1990, 1, 1),
        biologicalSex = BiologicalSex.MALE,
        heightCm = 175.0,
        weightKg = 75.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = HealthGoal.MAINTAIN,
        unitSystem = UnitSystem.METRIC,
        calorieTarget = 2200
    )

    @Test
    fun welcomePromptShowsForNonDemoAccountsBelowCurrentVersion() {
        val session = SessionPreferences(
            authenticatedUserId = "user-1",
            authenticatedEmail = profile.email,
            tutorialAcknowledgedVersion = 0
        )
        assertTrue(shouldShowTutorialWelcomePrompt(profile, session))
    }

    @Test
    fun welcomePromptHiddenForDemoAccounts() {
        val session = SessionPreferences(
            authenticatedUserId = DEMO_USER_ID,
            authenticatedEmail = DEMO_EMAIL,
            tutorialAcknowledgedVersion = 0
        )
        assertFalse(shouldShowTutorialWelcomePrompt(defaultDemoProfile(), session))
    }

    @Test
    fun welcomePromptHiddenAfterVersionAcknowledged() {
        val session = SessionPreferences(
            authenticatedUserId = "user-1",
            authenticatedEmail = profile.email,
            tutorialAcknowledgedVersion = CURRENT_TUTORIAL_VERSION
        )
        assertFalse(shouldShowTutorialWelcomePrompt(profile, session))
    }

    @Test
    fun tutorialDefinesFiveOrderedPages() {
        assertEquals(5, tutorialPages.size)
        assertEquals(listOf("today", "training", "nutrition", "walk", "progress"), tutorialPages.map { it.id })
    }
}
