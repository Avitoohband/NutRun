package com.avitoohband.nutrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationSettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun waterAndTrainingTimesExposeClockPickersWithPlainLabels() {
        setContent()

        composeRule.onNodeWithTag("water-first-reminder-clock").assertIsDisplayed()
        composeRule.onNodeWithTag("water-last-reminder-clock").assertIsDisplayed()
        composeRule.onNodeWithTag("training-day-before-reminder-clock").assertIsDisplayed()
        composeRule.onNodeWithTag("training-day-reminder-clock").assertIsDisplayed()
        composeRule.onNodeWithText("First reminder (HH:mm)").assertDoesNotExist()
        composeRule.onNodeWithText("Last reminder (HH:mm)").assertDoesNotExist()
        composeRule.onNodeWithText("Previous day (HH:mm)").assertDoesNotExist()
        composeRule.onNodeWithText("Same day (HH:mm)").assertDoesNotExist()
    }

    @Test
    fun disabledSectionsCollapseToSummaryAndRestoreRetainedValues() {
        val supplement = supplement("d3", enabled = true, minute = 9 * 60 + 15)
        val saved = draft(
            waterEnabled = false,
            trainingEnabled = false,
            supplementMasterEnabled = false,
            supplementDrafts = mapOf(
                supplement.id to SupplementReminderDraft(enabled = true, time = "09:15")
            )
        )
        setContent(saved = saved, supplements = listOf(supplement))

        composeRule.onNodeWithTag("water-reminder-summary")
            .assertTextContains("Off - next saved", substring = true)
        composeRule.onNodeWithTag("training-reminder-summary")
            .assertTextContains("Off - next saved", substring = true)
        composeRule.onNodeWithTag("supplement-reminder-summary")
            .assertTextContains("Off - next saved", substring = true)
        composeRule.onNodeWithTag("water-first-reminder").assertDoesNotExist()
        composeRule.onNodeWithTag("training-day-reminder").assertDoesNotExist()
        composeRule.onNodeWithTag("supplement-reminder-d3-time").assertDoesNotExist()

        composeRule.onNodeWithTag("water-reminders-master").performClick()
        composeRule.onNodeWithTag("water-first-reminder").assertTextContains("08:00")
        composeRule.onNodeWithTag("training-reminders-master").performClick()
        composeRule.onNodeWithTag("training-day-reminder").assertTextContains("08:00")
        composeRule.onNodeWithTag("supplement-reminders-master").performClick()
        composeRule.onNodeWithTag("supplement-reminder-d3-time").assertTextContains("09:15")
    }

    @Test
    fun supplementMasterAndToggleAllRemainIndependent() {
        val first = supplement("d3", enabled = true)
        val second = supplement("zinc", enabled = false)
        val saved = draft(
            supplementMasterEnabled = true,
            supplementDrafts = mapOf(
                first.id to SupplementReminderDraft(enabled = true, time = "08:00"),
                second.id to SupplementReminderDraft(enabled = false, time = "08:00")
            )
        )
        setContent(saved = saved, supplements = listOf(first, second))

        composeRule.onNodeWithTag("supplement-reminder-d3-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-zinc-enabled").assertIsOff()
        composeRule.onNodeWithTag("supplement-reminders-toggle-all").performClick()
        composeRule.onNodeWithTag("supplement-reminder-d3-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-zinc-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminders-master").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminders-master").performClick()
        composeRule.onNodeWithTag("supplement-reminders-master").assertIsOff()
        composeRule.onNodeWithTag("supplement-reminders-master").performClick()
        composeRule.onNodeWithTag("supplement-reminder-d3-enabled").assertIsOn()
        composeRule.onNodeWithTag("supplement-reminder-zinc-enabled").assertIsOn()
        composeRule.onNodeWithText(
            "The master switch pauses all supplement reminders without changing individual schedules."
        ).assertIsDisplayed()
    }

    @Test
    fun saveRemainsVisibleWhileScrollingTwentySupplements() {
        val supplements = (0 until 20).map { index ->
            supplement("supplement-$index", enabled = index % 2 == 0)
        }
        val saved = draft(
            supplementMasterEnabled = true,
            supplementDrafts = supplements.associate { supplement ->
                supplement.id to SupplementReminderDraft(
                    enabled = supplement.reminderEnabled,
                    time = "08:00"
                )
            }
        )
        setContent(saved = saved, supplements = supplements)

        composeRule.onNodeWithTag("save-notification-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("supplement-reminder-supplement-19-enabled")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("save-notification-settings").assertIsDisplayed()
    }

    @Test
    fun dirtyBackRequiresConfirmationAndKeepEditingDoesNotNavigate() {
        var backCount = 0
        setContent(onBack = { backCount += 1 })

        composeRule.onNodeWithTag("water-reminders-master").performClick()
        composeRule.onNodeWithTag("notification-settings-back").performClick()
        composeRule.onNodeWithText("Discard notification changes?").assertIsDisplayed()
        composeRule.onNodeWithTag("notification-settings-keep-editing").performClick()
        composeRule.onNodeWithText("Discard notification changes?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, backCount) }

        composeRule.onNodeWithTag("notification-settings-back").performClick()
        composeRule.onNodeWithTag("notification-settings-discard").performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun typedAndPickerBackedTimesReachOneSaveAndSurviveDisabledSections() {
        var persisted: NotificationSettingsDraft? = null
        var saveCalls = 0
        val saved = draft().copy(trainingDayReminder = "07:05")
        setContent(
            saved = saved,
            onPersist = { current ->
                saveCalls += 1
                persisted = current
                NotificationSettingsSaveResult.Success("account-a")
            }
        )

        composeRule.onNodeWithTag("water-first-reminder")
            .performTextClearance()
        composeRule.onNodeWithTag("water-first-reminder")
            .performTextInput("07:30")
        composeRule.onNodeWithTag("training-day-reminder-clock")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("training-day-reminder-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("training-day-reminder-confirm").performClick()
        composeRule.onNodeWithTag("water-reminders-master").performClick()
        composeRule.onNodeWithTag("training-reminders-master").performClick()
        composeRule.onNodeWithTag("save-notification-settings").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { saveCalls == 1 }
        composeRule.runOnIdle {
            assertEquals(false, persisted?.waterEnabled)
            assertEquals("07:30", persisted?.firstReminder)
            assertEquals(false, persisted?.trainingEnabled)
            assertEquals("07:05", persisted?.trainingDayReminder)
            assertEquals(1, saveCalls)
        }
    }

    @Test
    fun deniedPermissionKeepsConfiguredValuesAndProvidesSettingsAction() {
        var permissionRequests = 0
        var settingsOpens = 0
        setContent(
            saved = draft(),
            permissionGranted = false,
            onPermissionRequest = { permissionRequests += 1 },
            onOpenNotificationSettings = { settingsOpens += 1 }
        )

        composeRule.onNodeWithTag("notification-settings-list").performScrollToNode(
            hasText("Notification permission is required.")
        )
        composeRule.onNodeWithText("Notification permission is required.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Open Android notification settings")
            .performClick()
        composeRule.onNodeWithTag("water-reminders-master")
            .performScrollTo()
            .performClick()
            .assertIsOff()
        composeRule.onNodeWithTag("water-reminders-master")
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("water-first-reminder").assertTextContains("08:00")

        composeRule.runOnIdle {
            assertEquals(1, settingsOpens)
            assertEquals(1, permissionRequests)
        }
    }
    private fun setContent(
        saved: NotificationSettingsDraft = draft(),
        supplements: List<Supplement> = emptyList(),
        permissionGranted: Boolean = true,
        onPermissionRequest: () -> Unit = {},
        onOpenNotificationSettings: () -> Unit = {},
        onPersist: suspend (NotificationSettingsDraft) -> NotificationSettingsSaveResult = {
            NotificationSettingsSaveResult.Success("account-a")
        },
        onBack: () -> Unit = {}
    ) {
        composeRule.setContent {
            var current by remember(saved) { mutableStateOf(saved) }
            MaterialTheme {
                NotificationSettingsContent(
                    savedDraft = saved,
                    draft = current,
                    supplements = supplements,
                    accountReady = true,
                    permissionGranted = permissionGranted,
                    onDraftChange = { current = it },
                    onPermissionRequest = onPermissionRequest,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onManageSupplements = {},
                    persist = { onPersist(current) },
                    currentAccountId = { "account-a" },
                    onSaveSuccess = {},
                    onBack = onBack,
                    now = NOW
                )
            }
        }
    }

    private fun draft(
        waterEnabled: Boolean = true,
        trainingEnabled: Boolean = true,
        supplementMasterEnabled: Boolean = false,
        supplementDrafts: Map<String, SupplementReminderDraft> = emptyMap()
    ) = NotificationSettingsDraft(
        waterEnabled = waterEnabled,
        intervalMinutes = "60",
        firstReminder = "08:00",
        lastReminder = "22:00",
        trainingEnabled = trainingEnabled,
        dayBeforeReminder = "20:00",
        trainingDayReminder = "08:00",
        trainingDays = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
        supplementMasterEnabled = supplementMasterEnabled,
        supplementDrafts = supplementDrafts
    )

    private fun supplement(
        id: String,
        enabled: Boolean,
        minute: Int = 8 * 60
    ) = Supplement(
        id = id,
        name = id,
        dose = "1 tablet",
        schedule = SupplementSchedule(
            type = RecurrenceType.DAILY,
            startDate = LocalDate.of(2026, 1, 1)
        ),
        reminderEnabled = enabled,
        reminderMinute = minute
    )

    private companion object {
        val NOW: ZonedDateTime = ZonedDateTime.of(
            2026,
            8,
            22,
            9,
            10,
            0,
            0,
            ZoneId.of("Asia/Jerusalem")
        )
    }
}
