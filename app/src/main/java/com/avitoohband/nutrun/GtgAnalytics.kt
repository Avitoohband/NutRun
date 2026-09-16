package com.avitoohband.nutrun

import java.time.LocalDate

data class GtgDayTotals(
    val sets: Int,
    val reps: Int,
    val holdSeconds: Int
)

fun gtgDayTotals(
    logs: List<GtgLog>,
    planId: String,
    date: LocalDate
): GtgDayTotals {
    val matching = logs
        .asSequence()
        .filter { it.planId == planId && it.performedOn == date }
        .distinctBy(GtgLog::id)
        .toList()
    return GtgDayTotals(
        sets = matching.size,
        reps = matching.sumOf { it.reps ?: 0 },
        holdSeconds = matching.sumOf { it.durationSeconds ?: 0 }
    )
}
