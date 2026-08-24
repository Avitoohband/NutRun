package com.avitoohband.nutrun.walk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.avitoohband.nutrun.isDemoAccount
import com.avitoohband.nutrun.data.NutRunDao
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.SyncOperationEntity
import com.avitoohband.nutrun.domain.RouteSample
import com.avitoohband.nutrun.domain.WalkState
import com.avitoohband.nutrun.domain.acceptedRouteDistanceMeters
import com.avitoohband.nutrun.domain.sessionSteps
import com.avitoohband.nutrun.domain.accumulatedSessionSteps
import com.avitoohband.nutrun.sync.SyncScheduler
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@AndroidEntryPoint
class WalkRecordingService : Service(), SensorEventListener {
    @Inject lateinit var dao: NutRunDao
    @Inject lateinit var syncScheduler: SyncScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var currentSession: WalkSessionEntity? = null
    private var requestedUserId: String? = null
    private var latestSensorValue: Long? = null
    private val updateMutex = Mutex()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::persistLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Preparing walk..."))
        intent?.getStringExtra(EXTRA_USER_ID)?.let { requestedUserId = it }
        scope.launch {
            when (intent?.action) {
                ACTION_START -> startSession()
                ACTION_PAUSE -> pauseSession()
                ACTION_RESUME -> resumeSession()
                ACTION_FINISH -> finishSession()
                ACTION_DISCARD -> discardSession()
                else -> restoreSession()
            }
        }
        return START_STICKY
    }

    private suspend fun startSession() {
        val userId = requestedUserId
        if (userId == null) {
            stopSelf()
            return
        }
        currentSession = dao.activeWalk(userId) ?: WalkSessionEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            state = WalkState.ACTIVE.name,
            startedAtMillis = System.currentTimeMillis(),
            endedAtMillis = null,
            resumedAtMillis = System.currentTimeMillis(),
            stepBaseline = latestSensorValue,
            steps = latestSensorValue?.let { 0 }
        ).also { dao.saveWalk(it) }
        if (currentSession?.state == WalkState.ACTIVE.name) requestLocations()
        updateNotification("Walk recording")
    }

    private suspend fun restoreSession() {
        currentSession = requestedUserId?.let { dao.activeWalk(it) }
            ?: dao.activeWalkForServiceRestore()
        requestedUserId = currentSession?.userId
        if (currentSession == null) {
            stopSelf()
            return
        }
        if (currentSession?.state == WalkState.ACTIVE.name) requestLocations()
        updateNotification(if (currentSession?.state == WalkState.PAUSED.name) "Walk paused" else "Walk recording")
    }

    private suspend fun pauseSession() {
        val userId = requestedUserId ?: return
        val walk = dao.activeWalk(userId) ?: return
        val now = System.currentTimeMillis()
        val steps = activeStepTotal(walk, latestSensorValue)
        currentSession = walk.copy(
            state = WalkState.PAUSED.name,
            accumulatedDurationMillis = walk.accumulatedDurationMillis +
                (walk.resumedAtMillis?.let { now - it } ?: 0),
            resumedAtMillis = null,
            stepOffset = steps ?: walk.stepOffset,
            stepBaseline = null,
            steps = steps
        ).also { dao.updateWalk(it) }
        locationClient.removeLocationUpdates(locationCallback)
        updateNotification("Walk paused")
    }

    private suspend fun resumeSession() {
        val userId = requestedUserId ?: return
        val walk = dao.activeWalk(userId) ?: return
        currentSession = walk.copy(
            state = WalkState.ACTIVE.name,
            resumedAtMillis = System.currentTimeMillis(),
            stepOffset = walk.steps ?: walk.stepOffset,
            stepBaseline = latestSensorValue
        ).also { dao.updateWalk(it) }
        requestLocations()
        updateNotification("Walk recording")
    }

    private suspend fun finishSession() {
        val userId = requestedUserId
        if (userId == null) {
            stopForegroundAndSelf()
            return
        }
        val walk = dao.activeWalk(userId)
        if (walk == null) {
            stopForegroundAndSelf()
            return
        }
        val now = System.currentTimeMillis()
        val finished = walk.copy(
                state = WalkState.FINISHED.name,
                endedAtMillis = now,
                accumulatedDurationMillis = walk.accumulatedDurationMillis +
                    (walk.resumedAtMillis?.let { now - it } ?: 0),
                resumedAtMillis = null,
                steps = if (walk.state == WalkState.ACTIVE.name) {
                    activeStepTotal(walk, latestSensorValue)
                } else {
                    walk.steps
                }
            )
        dao.updateWalk(finished)
        enqueueFinishedWalk(finished)
        currentSession = null
        stopForegroundAndSelf()
    }

    private suspend fun discardSession() {
        val userId = requestedUserId
        if (userId == null) {
            stopForegroundAndSelf()
            return
        }
        val walk = dao.activeWalk(userId)
        if (walk == null) {
            stopForegroundAndSelf()
            return
        }
        dao.discardActiveWalk(userId, walk.id)
        currentSession = null
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        locationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestLocations() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000)
            .setMinUpdateDistanceMeters(5f)
            .setMinUpdateIntervalMillis(2_500)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun persistLocation(location: Location) {
        scope.launch {
            updateMutex.withLock {
                val userId = requestedUserId ?: return@withLock
                val walk = currentSession?.let { dao.walk(userId, it.id) }
                    ?: dao.activeWalk(userId)
                    ?: return@withLock
                if (walk.state != WalkState.ACTIVE.name) return@withLock
                val last = dao.lastWalkPoint(userId, walk.id)
                val previous = last?.let {
                    RouteSample(it.latitude, it.longitude, it.accuracyMeters, it.recordedAtMillis)
                }
                val sample = RouteSample(
                    location.latitude,
                    location.longitude,
                    location.accuracy,
                    location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                )
                val acceptedDistance = acceptedRouteDistanceMeters(previous, sample) ?: return@withLock
                val point = WalkPointEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    sessionId = walk.id,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    accuracyMeters = sample.accuracyMeters,
                    recordedAtMillis = sample.timestampMillis
                )
                dao.saveWalkPoint(point)
                currentSession = walk.copy(
                    distanceMeters = walk.distanceMeters + acceptedDistance,
                    stepBaseline = walk.stepBaseline ?: latestSensorValue,
                    steps = activeStepTotal(walk, latestSensorValue)
                ).also { dao.updateWalk(it) }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull()?.toLong() ?: return
        latestSensorValue = value
        scope.launch {
            updateMutex.withLock {
                val sessionId = currentSession?.id ?: return@withLock
                val userId = requestedUserId ?: return@withLock
                val walk = dao.walk(userId, sessionId) ?: return@withLock
                if (walk.state != WalkState.ACTIVE.name) return@withLock
                val baseline = walk.stepBaseline ?: value
                currentSession = walk.copy(
                    stepBaseline = baseline,
                    steps = walk.stepOffset + (sessionSteps(baseline, value) ?: 0)
                ).also { dao.updateWalk(it) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Walk recording", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("NutRun")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun activeStepTotal(walk: WalkSessionEntity, sensorValue: Long?): Long? {
        return accumulatedSessionSteps(
            walk.stepOffset,
            walk.stepBaseline,
            sensorValue
        ) ?: walk.steps
    }

    private suspend fun enqueueFinishedWalk(walk: WalkSessionEntity) {
        if (isDemoAccount(walk.userId)) return
        val points = dao.walkPoints(walk.userId, walk.id)
        val route = JSONArray().apply {
            points.forEach { point ->
                put(
                    JSONObject()
                        .put("latitude", point.latitude)
                        .put("longitude", point.longitude)
                        .put("accuracyMeters", point.accuracyMeters)
                        .put("recordedAtMillis", point.recordedAtMillis)
                )
            }
        }
        val payload = JSONObject()
            .put("startedAtMillis", walk.startedAtMillis)
            .put("endedAtMillis", walk.endedAtMillis)
            .put("durationMillis", walk.accumulatedDurationMillis)
            .put("distanceMeters", walk.distanceMeters)
            .put("steps", walk.steps)
            .put("route", route)
        dao.enqueueSync(
            SyncOperationEntity(
                id = "${walk.userId}:walks:${walk.id}",
                userId = walk.userId,
                entityType = "walks",
                entityId = walk.id,
                operation = "UPSERT",
                payloadJson = payload.toString(),
                createdAtMillis = System.currentTimeMillis()
            )
        )
        syncScheduler.schedule()
    }

    companion object {
        const val ACTION_START = "com.avitoohband.nutrun.walk.START"
        const val ACTION_PAUSE = "com.avitoohband.nutrun.walk.PAUSE"
        const val ACTION_RESUME = "com.avitoohband.nutrun.walk.RESUME"
        const val ACTION_FINISH = "com.avitoohband.nutrun.walk.FINISH"
        const val ACTION_DISCARD = "com.avitoohband.nutrun.walk.DISCARD"
        const val EXTRA_USER_ID = "user_id"
        private const val CHANNEL_ID = "walk-recording"
        private const val NOTIFICATION_ID = 1_001
    }
}
