package com.avitoohband.nutrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import com.avitoohband.nutrun.reminders.nextSupplementReminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

internal data class NotificationSettingsDraft(
    val waterEnabled: Boolean,
    val intervalMinutes: String,
    val firstReminder: String,
    val lastReminder: String,
    val trainingEnabled: Boolean,
    val dayBeforeReminder: String,
    val trainingDayReminder: String,
    val trainingDays: Set<DayOfWeek>,
    val supplementMasterEnabled: Boolean,
    val supplementDrafts: Map<String, SupplementReminderDraft>
)

internal data class NotificationReminderSummaries(
    val water: String,
    val training: String,
    val supplements: String
)

internal fun notificationSettingsDirty(
    saved: NotificationSettingsDraft,
    draft: NotificationSettingsDraft
): Boolean = saved != draft

internal fun nextReminderSummary(
    draft: NotificationSettingsDraft,
    supplements: List<Supplement>,
    now: ZonedDateTime
): NotificationReminderSummaries = NotificationReminderSummaries(
    water = waterReminderSummary(draft, now),
    training = trainingReminderSummary(draft, now),
    supplements = supplementReminderSummary(draft, supplements, now)
)

internal fun notificationSettingsDraftValid(draft: NotificationSettingsDraft): Boolean {
    val interval = draft.intervalMinutes.toIntOrNull()
    val firstMinute = parseReminderMinute(draft.firstReminder)
    val lastMinute = parseReminderMinute(draft.lastReminder)
    return interval != null && interval >= 15 &&
        firstMinute != null && lastMinute != null && lastMinute > firstMinute &&
        parseReminderMinute(draft.dayBeforeReminder) != null &&
        parseReminderMinute(draft.trainingDayReminder) != null &&
        draft.supplementDrafts.values.all { parseReminderMinute(it.time) != null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSettingsContent(
    savedDraft: NotificationSettingsDraft,
    draft: NotificationSettingsDraft,
    supplements: List<Supplement>,
    accountReady: Boolean,
    permissionGranted: Boolean,
    onDraftChange: (NotificationSettingsDraft) -> Unit,
    onPermissionRequest: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onManageSupplements: () -> Unit,
    persist: suspend () -> NotificationSettingsSaveResult,
    currentAccountId: suspend () -> String?,
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now()
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val dirty = notificationSettingsDirty(savedDraft, draft)
    val summaries = remember(draft, supplements, now) {
        nextReminderSummary(draft, supplements, now)
    }
    val valid = notificationSettingsDraftValid(draft)

    fun requestBack() {
        if (dirty) showDiscardConfirmation = true else onBack()
    }

    BackHandler(onBack = ::requestBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Notification settings") },
                navigationIcon = {
                    IconButton(
                        onClick = ::requestBack,
                        modifier = Modifier.testTag("notification-settings-back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                NotificationSettingsSaveButton(
                    valid = valid,
                    accountReady = accountReady,
                    persist = persist,
                    currentAccountId = currentAccountId,
                    onSuccess = onSaveSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("notification-settings-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!accountReady) {
                item {
                    NutRunLoadingState(message = "Loading notification settings...")
                }
            }
            item {
                ReminderSectionCard(
                    title = "Water reminders",
                    enabled = draft.waterEnabled,
                    onEnabledChange = { enabled ->
                        onDraftChange(draft.copy(waterEnabled = enabled))
                        if (enabled && !draft.waterEnabled) onPermissionRequest()
                    },
                    accountReady = accountReady,
                    summary = summaries.water,
                    masterTestTag = "water-reminders-master",
                    summaryTestTag = "water-reminder-summary"
                ) {
                    OutlinedTextField(
                        value = draft.intervalMinutes,
                        onValueChange = { value ->
                            onDraftChange(
                                draft.copy(intervalMinutes = value.filter(Char::isDigit))
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accountReady,
                        label = { Text("Interval (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    ReminderTimeInput(
                        value = draft.firstReminder,
                        onValueChange = { onDraftChange(draft.copy(firstReminder = it)) },
                        label = "First reminder",
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "water-first-reminder",
                        enabled = accountReady
                    )
                    ReminderTimeInput(
                        value = draft.lastReminder,
                        onValueChange = { onDraftChange(draft.copy(lastReminder = it)) },
                        label = "Last reminder",
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "water-last-reminder",
                        enabled = accountReady
                    )
                }
            }
            item {
                ReminderSectionCard(
                    title = "Training reminders",
                    enabled = draft.trainingEnabled,
                    onEnabledChange = { enabled ->
                        onDraftChange(draft.copy(trainingEnabled = enabled))
                        if (enabled && !draft.trainingEnabled) onPermissionRequest()
                    },
                    accountReady = accountReady,
                    summary = summaries.training,
                    masterTestTag = "training-reminders-master",
                    summaryTestTag = "training-reminder-summary"
                ) {
                    ReminderTimeInput(
                        value = draft.dayBeforeReminder,
                        onValueChange = { onDraftChange(draft.copy(dayBeforeReminder = it)) },
                        label = "Day-before reminder",
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "training-day-before-reminder",
                        enabled = accountReady
                    )
                    ReminderTimeInput(
                        value = draft.trainingDayReminder,
                        onValueChange = { onDraftChange(draft.copy(trainingDayReminder = it)) },
                        label = "Training-day reminder",
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "training-day-reminder",
                        enabled = accountReady
                    )
                }
            }
            item {
                SupplementReminderSettingsCard(
                    masterEnabled = draft.supplementMasterEnabled,
                    onMasterEnabledChange = { enabled ->
                        onDraftChange(draft.copy(supplementMasterEnabled = enabled))
                    },
                    supplements = supplements,
                    drafts = draft.supplementDrafts,
                    onDraftsChange = { updated ->
                        onDraftChange(draft.copy(supplementDrafts = updated))
                    },
                    onPermissionRequest = onPermissionRequest,
                    onManageSupplements = onManageSupplements,
                    loading = !accountReady,
                    collapsedSummary = summaries.supplements
                )
            }
            val supplementPermissionRequired = draft.supplementMasterEnabled ||
                draft.supplementDrafts.values.any(SupplementReminderDraft::enabled)
            if (
                !permissionGranted &&
                (draft.waterEnabled || draft.trainingEnabled || supplementPermissionRequired)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Notification permission is required.",
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onOpenNotificationSettings) {
                                Text("Open Android notification settings")
                            }
                        }
                    }
                }
            }
            if (!valid) {
                item {
                    Text(
                        "Use valid times and an interval of at least 15 minutes.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("Discard notification changes?") },
            text = { Text("Your reminder changes have not been saved.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmation = false
                        onBack()
                    },
                    modifier = Modifier.testTag("notification-settings-discard")
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirmation = false },
                    modifier = Modifier.testTag("notification-settings-keep-editing")
                ) {
                    Text("Keep editing")
                }
            }
        )
    }
}

@Composable
private fun ReminderSectionCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    accountReady: Boolean,
    summary: String,
    masterTestTag: String,
    summaryTestTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = accountReady,
                    modifier = Modifier.testTag(masterTestTag)
                )
            }
            if (enabled) {
                content()
            } else {
                Text(
                    summary,
                    modifier = Modifier.testTag(summaryTestTag),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun waterReminderSummary(
    draft: NotificationSettingsDraft,
    now: ZonedDateTime
): String {
    val interval = draft.intervalMinutes.toIntOrNull()
    val firstMinute = parseReminderMinute(draft.firstReminder)
    val lastMinute = parseReminderMinute(draft.lastReminder)
    if (
        interval == null || interval < 15 ||
        firstMinute == null || lastMinute == null || lastMinute <= firstMinute
    ) {
        return "Check water reminder times"
    }

    val next = nextWaterReminder(now, firstMinute, lastMinute, interval)
    val summary = "Next ${relativeDateTime(next, now)}"
    return summary.withDisabledPrefix(draft.waterEnabled)
}

private fun nextWaterReminder(
    now: ZonedDateTime,
    firstMinute: Int,
    lastMinute: Int,
    intervalMinutes: Int
): ZonedDateTime {
    var minute = firstMinute
    while (minute <= lastMinute) {
        val candidate = now.toLocalDate().atMinute(minute, now)
        if (candidate.isAfter(now)) return candidate
        minute += intervalMinutes
    }
    return now.toLocalDate().plusDays(1).atMinute(firstMinute, now)
}

private fun trainingReminderSummary(
    draft: NotificationSettingsDraft,
    now: ZonedDateTime
): String {
    val dayBeforeMinute = parseReminderMinute(draft.dayBeforeReminder)
    val trainingDayMinute = parseReminderMinute(draft.trainingDayReminder)
    if (dayBeforeMinute == null || trainingDayMinute == null) {
        return "Check training reminder times"
    }
    if (draft.trainingDays.isEmpty()) {
        return "No training reminders configured".withDisabledPrefix(draft.trainingEnabled)
    }

    val next = (0L..7L)
        .asSequence()
        .map { offset -> now.toLocalDate().plusDays(offset) }
        .filter { date -> date.dayOfWeek in draft.trainingDays }
        .flatMap { trainingDate ->
            sequenceOf(
                TrainingReminderCandidate(
                    at = trainingDate.minusDays(1).atMinute(dayBeforeMinute, now),
                    type = "day before"
                ),
                TrainingReminderCandidate(
                    at = trainingDate.atMinute(trainingDayMinute, now),
                    type = "training day"
                )
            )
        }
        .filter { candidate -> candidate.at.isAfter(now) }
        .minByOrNull { candidate -> candidate.at.toInstant() }

    val summary = next?.let { candidate ->
        "Next ${relativeDateTime(candidate.at, now)} (${candidate.type})"
    } ?: "No training reminders configured"
    return summary.withDisabledPrefix(draft.trainingEnabled)
}

private fun supplementReminderSummary(
    draft: NotificationSettingsDraft,
    supplements: List<Supplement>,
    now: ZonedDateTime
): String {
    var invalidTime = false
    val configured = supplements.map { supplement ->
        val reminderDraft = draft.supplementDrafts[supplement.id]
            ?: SupplementReminderDraft(
                enabled = supplement.reminderEnabled,
                time = formatReminderMinute(supplement.reminderMinute)
            )
        val minute = parseReminderMinute(reminderDraft.time)
        if (reminderDraft.enabled && minute == null) invalidTime = true
        supplement.copy(
            reminderEnabled = reminderDraft.enabled && minute != null,
            reminderMinute = minute ?: supplement.reminderMinute
        )
    }
    if (invalidTime) return "Check supplement reminder times"

    val next = nextSupplementReminder(configured, now)
    val summary = next?.let { "Next ${relativeDateTime(it, now)}" }
        ?: "No supplement reminders configured"
    return summary.withDisabledPrefix(draft.supplementMasterEnabled)
}

private data class TrainingReminderCandidate(
    val at: ZonedDateTime,
    val type: String
)

private fun LocalDate.atMinute(minute: Int, reference: ZonedDateTime): ZonedDateTime =
    ZonedDateTime.of(
        this,
        LocalTime.of(minute / 60, minute % 60),
        reference.zone
    )

private fun relativeDateTime(target: ZonedDateTime, now: ZonedDateTime): String {
    val dateLabel = when (target.toLocalDate()) {
        now.toLocalDate() -> "today"
        now.toLocalDate().plusDays(1) -> "tomorrow"
        else -> target.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }
    return "$dateLabel at ${formatReminderMinute(target.hour * 60 + target.minute)}"
}

private fun String.withDisabledPrefix(enabled: Boolean): String {
    if (enabled) return this
    return when {
        startsWith("Next ") -> "Off - next saved ${removePrefix("Next ").replaceFirstChar(Char::lowercase)}"
        startsWith("No ") -> "Off - ${replaceFirstChar(Char::lowercase)}"
        else -> this
    }
}
