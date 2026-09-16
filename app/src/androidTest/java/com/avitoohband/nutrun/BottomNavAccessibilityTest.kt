package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.avitoohband.nutrun.data.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class BottomNavAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationMarksSelectedTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { AppPreferences(context).signOut() }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("demo-login").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("demo-login").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("bottom-nav-today").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("bottom-nav-today").assertIsSelected()
        composeRule.onNodeWithContentDescription("Today tab").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-nav-training").performClick()
        composeRule.onNodeWithTag("bottom-nav-training").assertIsSelected()
        composeRule.onNodeWithTag("bottom-nav-today").assertIsNotSelected()
    }
}
