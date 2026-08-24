package com.avitoohband.nutrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.domain.WalkState
import com.avitoohband.nutrun.walk.WalkGpsState
import org.junit.Rule
import org.junit.Test

class WalkRecordingContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preStartShowsGpsStatusAndStartButton() {
        composeRule.setContent {
            NutRunTheme {
                WalkOverviewContent(
                    state = demoWalkState(),
                    routePoints = emptyList(),
                    walkGpsState = WalkGpsState.Acquiring,
                    onStartGpsMonitoring = {},
                    onStopGpsMonitoring = {},
                    onSelectCompletedWalk = {}
                )
            }
        }

        composeRule.onNodeWithTag("walk-gps-acquiring").assertIsDisplayed()
        composeRule.onNodeWithTag("walk-start-button").assertIsDisplayed()
        composeRule.onNodeWithTag("walk-history-card").assertDoesNotExist()
    }

    @Test
    fun startOpensPermissionRationaleBeforeRecording() {
        composeRule.setContent {
            NutRunTheme {
                WalkOverviewContent(
                    state = demoWalkState(),
                    routePoints = emptyList(),
                    walkGpsState = WalkGpsState.PermissionRequired,
                    onStartGpsMonitoring = {},
                    onStopGpsMonitoring = {},
                    onSelectCompletedWalk = {}
                )
            }
        }

        composeRule.onNodeWithTag("walk-start-button").performClick()
        composeRule.onNodeWithTag("walk-permission-continue").assertIsDisplayed()
    }

    @Test
    fun activeRecordingShowsPersistentControls() {
        composeRule.setContent {
            NutRunTheme {
                WalkOverviewContent(
                    state = demoWalkState(
                        activeWalk = WalkSessionEntity(
                            id = "walk-active",
                            userId = "demo",
                            state = WalkState.ACTIVE.name,
                            startedAtMillis = 1_000L,
                            endedAtMillis = null,
                            accumulatedDurationMillis = 120_000L,
                            resumedAtMillis = 1_000L,
                            distanceMeters = 850.0,
                            stepBaseline = 100L,
                            steps = 1_200L
                        )
                    ),
                    routePoints = emptyList(),
                    walkGpsState = WalkGpsState.Ready(12f),
                    onStartGpsMonitoring = {},
                    onStopGpsMonitoring = {},
                    onSelectCompletedWalk = {}
                )
            }
        }

        composeRule.onNodeWithTag("walk-gps-ready").assertIsDisplayed()
        composeRule.onNodeWithTag("walk-elapsed-time").assertIsDisplayed()
        composeRule.onNodeWithTag("walk-pause-resume").assertIsDisplayed()
        composeRule.onNodeWithTag("walk-finish-button").assertIsDisplayed()
        composeRule.onNodeWithTag("walk-overflow-menu").assertIsDisplayed()
    }

  private fun demoWalkState(activeWalk: WalkSessionEntity? = null): NutRunUiState =
        NutRunUiState(
            session = SessionPreferences(authenticatedUserId = "demo"),
            sessionResolved = true,
            hydrationPlan = HydrationPlanEntity(id = "hydration:demo", userId = "demo"),
            activeWalk = activeWalk,
            walks = if (activeWalk == null) {
                emptyList()
            } else {
                listOf(activeWalk)
            }
        )
}
