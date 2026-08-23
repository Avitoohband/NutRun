package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class NutRunComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nutRunMetricMergesValueLabelAndAction() {
        composeRule.setContent {
            NutRunTheme {
                NutRunMetric(
                    value = "500",
                    label = "mL water",
                    icon = Icons.Default.WaterDrop,
                    actionLabel = "Open Nutrition water",
                    onClick = {},
                    testTag = "metric-water"
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Open Nutrition water, 500 mL water")
            .assertIsDisplayed()
    }

    @Test
    fun nutRunScreenBackActionHasAccessibleLabel() {
        composeRule.setContent {
            NutRunTheme {
                NutRunScreen(
                    title = "Notifications",
                    onBack = {},
                    testTag = "notifications-screen"
                ) { }
            }
        }

        composeRule
            .onNodeWithContentDescription("Back from Notifications")
            .assertIsDisplayed()
    }

    @Test
    fun nutRunInlineErrorUsesLiveRegion() {
        composeRule.setContent {
            NutRunTheme {
                NutRunInlineMessage(
                    message = "Unable to save settings",
                    kind = NutRunMessageKind.ERROR,
                    testTag = "settings-error"
                )
            }
        }

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .assertExists()
        composeRule.onNodeWithTag("settings-error").assertIsDisplayed()
    }

    @Test
    fun nutRunLoadingStateAnnouncesMessage() {
        composeRule.setContent {
            NutRunTheme {
                NutRunLoadingState(
                    message = "Loading supplements...",
                    testTag = "supplements-loading"
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Loading supplements...")
            .assertIsDisplayed()
    }

    @Test
    fun nutRunMetricSurvivesLargeFontAtCompactWidth() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                NutRunTheme {
                    Box(Modifier.width(320.dp).height(200.dp)) {
                        NutRunMetric(
                            value = "2,000",
                            label = "mL water today",
                            icon = Icons.Default.WaterDrop,
                            actionLabel = "Open Nutrition water",
                            onClick = {},
                            testTag = "metric-large-font"
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Open Nutrition water, 2,000 mL water today")
            .assertIsDisplayed()
    }

    @Test
    fun nutRunEmptyStateShowsTitleAndAction() {
        composeRule.setContent {
            NutRunTheme {
                NutRunEmptyState(
                    title = "No supplements due today",
                    message = "Nothing scheduled for today.",
                    actionLabel = "Manage supplements",
                    onAction = {},
                    titleTestTag = "empty-supplements"
                )
            }
        }

        composeRule.onNodeWithTag("empty-supplements").assertIsDisplayed()
        composeRule.onNodeWithText("Manage supplements").assertIsDisplayed()
    }
}
