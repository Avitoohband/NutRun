package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WalkPointEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val walkDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
private val walkTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun activeRouteSessionId(activeWalk: WalkSessionEntity?): String? = activeWalk?.id

fun formatWalkDate(startedAtMillis: Long): String =
    Instant.ofEpochMilli(startedAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(walkDateFormatter)

fun formatWalkTimeRange(walk: WalkSessionEntity): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(walk.startedAtMillis).atZone(zone).format(walkTimeFormatter)
    val end = walk.endedAtMillis
        ?.let { Instant.ofEpochMilli(it).atZone(zone).format(walkTimeFormatter) }
        ?: start
    return "$start - $end"
}

fun formatWalkDuration(durationMillis: Long): String {
    val safeSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = safeSeconds % 3_600L / 60L
    val seconds = safeSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun averageWalkPaceMinutesPerKm(walk: WalkSessionEntity): Double? =
    (walk.distanceMeters / 1_000.0).takeIf { it > 0.0 }
        ?.let { distanceKm -> walk.accumulatedDurationMillis / 60_000.0 / distanceKm }

sealed interface WalkRouteCameraFraming {
    data object None : WalkRouteCameraFraming

    data class Center(
        val latitude: Double,
        val longitude: Double
    ) : WalkRouteCameraFraming

    data class Bounds(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double
    ) : WalkRouteCameraFraming
}

fun walkRouteCameraFraming(points: List<WalkPointEntity>): WalkRouteCameraFraming = when (points.size) {
    0 -> WalkRouteCameraFraming.None
    1 -> points.single().let { WalkRouteCameraFraming.Center(it.latitude, it.longitude) }
    else -> {
        val (west, east) = narrowestLongitudeBounds(points.map(WalkPointEntity::longitude))
        WalkRouteCameraFraming.Bounds(
            south = points.minOf(WalkPointEntity::latitude),
            west = west,
            north = points.maxOf(WalkPointEntity::latitude),
            east = east
        )
    }
}

private fun narrowestLongitudeBounds(longitudes: List<Double>): Pair<Double, Double> {
    val sorted = longitudes.sorted()
    val largestGapIndex = sorted.indices.maxBy { index ->
        val next = sorted.getOrElse(index + 1) { sorted.first() + 360.0 }
        next - sorted[index]
    }
    return sorted[(largestGapIndex + 1) % sorted.size] to sorted[largestGapIndex]
}
