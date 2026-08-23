package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class BottomNavAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationMarksSelectedTab() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("demo-login").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("Today's training").fetchSemanticsNodes().isNotEmpty()
            }
        }

        composeRule.onNodeWithTag("bottom-nav-today").assertIsSelected()
        composeRule.onNodeWithContentDescription("Today tab").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-nav-training").performClick()
        composeRule.onNodeWithTag("bottom-nav-training").assertIsSelected()
        composeRule.onNodeWithTag("bottom-nav-today").assertIsNotSelected()
    }
}
