package com.avitoohband.nutrun

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
