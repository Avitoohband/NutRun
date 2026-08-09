package com.avitoohband.nutrun.reminders

import com.avitoohband.nutrun.Supplement
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

fun nextSupplementReminder(
    supplements: List<Supplement>,
    now: ZonedDateTime
): ZonedDateTime? {
    val eligibleSupplements = supplements.filter {
        it.reminderEnabled && it.reminderMinute in 0 until 24 * 60
    }
    if (eligibleSupplements.isEmpty()) return null

    val startDate = now.toLocalDate()
    val endDate = startDate.plusYears(10)
    var date = startDate
    while (!date.isAfter(endDate)) {
        val reminder = eligibleSupplements
            .asSequence()
            .filter { it.schedule.isDueOn(date) }
            .filter { it.completedOn != date }
            .mapNotNull { supplement ->
                val target = ZonedDateTime.of(
                    date,
                    LocalTime.of(supplement.reminderMinute / 60, supplement.reminderMinute % 60),
                    now.zone
                )
                target.takeIf { it.isAfter(now) }
            }
            .minByOrNull { it.toInstant() }
        if (reminder != null) return reminder
        date = date.plusDays(1)
    }
    return null
}

fun supplementsDueForReminder(
    supplements: List<Supplement>,
    date: LocalDate,
    minute: Int
): List<Supplement> {
    if (minute !in 0 until 24 * 60) return emptyList()
    return supplements.filter { supplement ->
        supplement.reminderEnabled &&
            supplement.reminderMinute == minute &&
            supplement.schedule.isDueOn(date) &&
            supplement.completedOn != date
    }
}
