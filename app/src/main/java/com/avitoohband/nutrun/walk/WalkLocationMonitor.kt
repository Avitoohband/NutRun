package com.avitoohband.nutrun.walk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class WalkLocationMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : WalkGpsMonitor {
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<WalkGpsState>(WalkGpsState.PermissionRequired)
    override val state: StateFlow<WalkGpsState> = _state.asStateFlow()

    private var locationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var monitorJob: Job? = null
    private var lastFixMillis: Long? = null
    private var monitorStartedAtMillis: Long = 0L

    @SuppressLint("MissingPermission")
    override fun start() {
        stop()
        if (!hasLocationPermission()) {
            _state.value = WalkGpsState.PermissionRequired
            return
        }
        monitorStartedAtMillis = nowMillis()
        lastFixMillis = null
        _state.value = WalkGpsState.Acquiring
        val client = LocationServices.getFusedLocationProviderClient(context)
        locationClient = client
        client.lastLocation
            .addOnSuccessListener { location -> location?.let(::applyLocationFix) }
            .addOnFailureListener {
                _state.value = WalkGpsState.Unavailable("Location provider unavailable")
            }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::applyLocationFix)
            }
        }
        locationCallback = callback
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMaxUpdates(Int.MAX_VALUE)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        monitorJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val elapsed = nowMillis() - monitorStartedAtMillis
                _state.value = walkGpsStateAfterAcquiringTimeout(_state.value, elapsed)
                val fixAge = lastFixMillis?.let { nowMillis() - it }
                if (fixAge != null && fixAge > WALK_GPS_STALE_MS && _state.value !is WalkGpsState.Unavailable) {
                    _state.value = WalkGpsState.Acquiring
                }
            }
        }
    }

    override fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        locationCallback?.let { callback ->
            locationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
        locationClient = null
        lastFixMillis = null
        _state.value = if (hasLocationPermission()) {
            WalkGpsState.Acquiring
        } else {
            WalkGpsState.PermissionRequired
        }
    }

    private fun applyLocationFix(location: Location) {
        val fixMillis = location.time.takeIf { it > 0L } ?: nowMillis()
        lastFixMillis = fixMillis
        val fixAge = nowMillis() - fixMillis
        _state.value = walkGpsStateFromFix(location.accuracy, fixAge)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
