package com.avitoohband.nutrun.walk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface WalkGpsMonitor {
    val state: StateFlow<WalkGpsState>
    fun start()
    fun stop()
}

sealed interface WalkGpsState {
    data object PermissionRequired : WalkGpsState
    data object Acquiring : WalkGpsState
    data class Ready(val accuracyMeters: Float) : WalkGpsState
    data class Weak(val accuracyMeters: Float) : WalkGpsState
    data class Unavailable(val reason: String) : WalkGpsState
}

const val WALK_GPS_READY_ACCURACY_METERS = 25f
const val WALK_GPS_STALE_MS = 30_000L
const val WALK_GPS_ACQUIRING_TIMEOUT_MS = 30_000L

fun walkGpsStateFromFix(
    accuracyMeters: Float,
    fixAgeMillis: Long
): WalkGpsState {
    if (fixAgeMillis > WALK_GPS_STALE_MS) return WalkGpsState.Acquiring
    return if (accuracyMeters <= WALK_GPS_READY_ACCURACY_METERS) {
        WalkGpsState.Ready(accuracyMeters)
    } else {
        WalkGpsState.Weak(accuracyMeters)
    }
}

fun walkGpsStateAfterAcquiringTimeout(
    current: WalkGpsState,
    elapsedSinceStartMillis: Long
): WalkGpsState {
    if (current !is WalkGpsState.Acquiring) return current
    return if (elapsedSinceStartMillis >= WALK_GPS_ACQUIRING_TIMEOUT_MS) {
        WalkGpsState.Unavailable("GPS signal not found")
    } else {
        current
    }
}
