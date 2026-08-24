package com.avitoohband.nutrun

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.domain.WalkState
import com.avitoohband.nutrun.walk.WalkGpsState
import com.avitoohband.nutrun.walk.WalkRecordingService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun WalkOverviewContent(
    state: NutRunUiState,
    routePoints: List<WalkPointEntity>,
    walkGpsState: WalkGpsState,
    onStartGpsMonitoring: () -> Unit,
    onStopGpsMonitoring: () -> Unit,
    onSelectCompletedWalk: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val active = state.activeWalk
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    val userId = state.session.authenticatedUserId

    DisposableEffect(Unit) {
        onStartGpsMonitoring()
        onDispose { onStopGpsMonitoring() }
    }

    LaunchedEffect(active?.state) {
        if (active?.state == WalkState.ACTIVE.name) {
            while (true) {
                delay(1_000L)
                nowMillis = System.currentTimeMillis()
            }
        } else {
            nowMillis = System.currentTimeMillis()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted) {
            permissionDenied = false
            sendWalkAction(context, WalkRecordingService.ACTION_START, userId)
            onStartGpsMonitoring()
        } else {
            permissionDenied = true
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Location for walk recording") },
            text = {
                Text(
                    "NutRun needs your location to record route and distance. " +
                        "Activity recognition improves step counting when available. " +
                        "A notification keeps recording alive while you walk."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 29) {
                                add(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            if (Build.VERSION.SDK_INT >= 33) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    modifier = Modifier.testTag("walk-permission-continue")
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Not now")
                }
            }
        )
    }

    active?.let { walk ->
        if (showFinishConfirm) {
            val elapsed = activeWalkElapsedMillis(walk, nowMillis)
            AlertDialog(
                onDismissRequest = { showFinishConfirm = false },
                title = { Text("Finish walk?") },
                text = {
                    Text(
                        "${formatWalkDuration(elapsed)} | " +
                            "%.2f km | ${formatWalkStepSummary(walk.steps)}"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFinishConfirm = false
                            sendWalkAction(context, WalkRecordingService.ACTION_FINISH, userId)
                        },
                        modifier = Modifier.testTag("walk-finish-confirm")
                    ) {
                        Text("Finish")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFinishConfirm = false }) {
                        Text("Keep walking")
                    }
                }
            )
        }
        if (showDiscardConfirm) {
            val elapsed = activeWalkElapsedMillis(walk, nowMillis)
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text("Discard walk?") },
                text = {
                    Text(
                        "This permanently removes the route, ${formatWalkDuration(elapsed)}, " +
                            "%.2f km, and ${formatWalkStepSummary(walk.steps)} for this walk."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDiscardConfirm = false
                            sendWalkAction(context, WalkRecordingService.ACTION_DISCARD, userId)
                        },
                        modifier = Modifier.testTag("walk-discard-confirm")
                    ) {
                        Text("Discard walk")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text("Keep walk")
                    }
                }
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            active?.let { walk ->
                WalkActiveControlsBar(
                    walk = walk,
                    nowMillis = nowMillis,
                    walkGpsState = walkGpsState,
                    overflowMenu = overflowMenu,
                    onOverflowMenuChange = { overflowMenu = it },
                    onPauseResume = {
                        sendWalkAction(
                            context,
                            if (walk.state == WalkState.PAUSED.name) {
                                WalkRecordingService.ACTION_RESUME
                            } else {
                                WalkRecordingService.ACTION_PAUSE
                            },
                            userId
                        )
                    },
                    onFinish = { showFinishConfirm = true },
                    onDiscard = { showDiscardConfirm = true }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Recorded walks", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (active == null) {
                        "Route tracking runs only after you press Start."
                    } else if (active.state == WalkState.PAUSED.name) {
                        "Walk paused"
                    } else {
                        "Walk recording"
                    },
                    modifier = Modifier.testTag("walk-recording-status")
                )
            }
            if (active == null) {
                item { WalkGpsStatusLabel(walkGpsState) }
            }
            item { WalkRouteMap(routePoints, testTag = "active-walk-route-map") }
            if (active == null) {
                item {
                    Button(
                        onClick = { showPermissionRationale = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("walk-start-button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start walk")
                    }
                }
            }
            if (permissionDenied) {
                item {
                    Text(
                        "Location permission is required to record a route. " +
                            "History remains available without it.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("walk-permission-denied")
                    )
                }
            }
            item { WalkSectionHeading("History") }
            items(
                state.walks.filter { it.state == WalkState.FINISHED.name },
                key = { it.id }
            ) { walk ->
                WalkHistoryCard(
                    walk = walk,
                    onClick = { onSelectCompletedWalk(walk.id) }
                )
            }
        }
    }
}

@Composable
private fun WalkActiveControlsBar(
    walk: WalkSessionEntity,
    nowMillis: Long,
    walkGpsState: WalkGpsState,
    overflowMenu: Boolean,
    onOverflowMenuChange: (Boolean) -> Unit,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit
) {
    val elapsed = activeWalkElapsedMillis(walk, nowMillis)
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WalkGpsStatusLabel(walkGpsState)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WalkMetricCard(
                    value = formatWalkDuration(elapsed),
                    label = "elapsed",
                    modifier = Modifier.weight(1f),
                    testTag = "walk-elapsed-time"
                )
                WalkMetricCard(
                    value = "%.2f".format(walk.distanceMeters / 1_000),
                    label = "km",
                    modifier = Modifier.weight(1f),
                    testTag = "walk-distance-km"
                )
                WalkMetricCard(
                    value = walk.steps?.toString() ?: "--",
                    label = "steps",
                    modifier = Modifier.weight(1f),
                    testTag = "walk-step-count"
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walk-pause-resume")
                ) {
                    Icon(
                        if (walk.state == WalkState.PAUSED.name) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Text(if (walk.state == WalkState.PAUSED.name) "Resume" else "Pause")
                }
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walk-finish-button")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("Finish")
                }
                Box {
                    IconButton(
                        onClick = { onOverflowMenuChange(true) },
                        modifier = Modifier.testTag("walk-overflow-menu")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Walk actions")
                    }
                    DropdownMenu(
                        expanded = overflowMenu,
                        onDismissRequest = { onOverflowMenuChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Discard walk") },
                            onClick = {
                                onOverflowMenuChange(false)
                                onDiscard()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WalkGpsStatusLabel(state: WalkGpsState) {
    val (label, testTag) = when (state) {
        WalkGpsState.PermissionRequired -> "Location needed to check GPS" to "walk-gps-permission-required"
        WalkGpsState.Acquiring -> "Acquiring GPS..." to "walk-gps-acquiring"
        is WalkGpsState.Ready -> "GPS ready (${state.accuracyMeters.roundToInt()} m)" to "walk-gps-ready"
        is WalkGpsState.Weak -> "Weak GPS (${state.accuracyMeters.roundToInt()} m)" to "walk-gps-weak"
        is WalkGpsState.Unavailable -> state.reason to "walk-gps-unavailable"
    }
    Text(
        label,
        modifier = Modifier.testTag(testTag),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
}

@Composable
private fun WalkMetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val tagged = testTag?.let { modifier.testTag(it) } ?: modifier
    Card(
        tagged,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WalkHistoryCard(
    walk: WalkSessionEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("walk-history-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("%.2f km".format(walk.distanceMeters / 1_000), fontWeight = FontWeight.Bold)
                Text(
                    "${formatWalkDate(walk.startedAtMillis)} | " +
                        "${formatWalkDuration(walk.accumulatedDurationMillis)} | " +
                        formatWalkStepSummary(walk.steps),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WalkSectionHeading(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
}

@Composable
internal fun WalkRouteMap(points: List<WalkPointEntity>, testTag: String? = null) {
    val modifier = (testTag?.let(Modifier::testTag) ?: Modifier).semantics {
        contentDescription = if (points.size > 1) {
            "Saved route with ${points.size} points"
        } else {
            "No saved route"
        }
    }
    Card(
        modifier.fillMaxWidth().height(240.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (BuildConfig.MAPS_CONFIGURED) {
            val cameraPositionState = rememberCameraPositionState()
            val framing = walkRouteCameraFraming(points)
            var mapLoaded by remember { mutableStateOf(false) }
            var mapSize by remember { mutableStateOf(IntSize.Zero) }
            LaunchedEffect(mapLoaded, mapSize, framing) {
                if (!mapLoaded) return@LaunchedEffect
                when (framing) {
                    WalkRouteCameraFraming.None -> Unit
                    is WalkRouteCameraFraming.Center -> cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(framing.latitude, framing.longitude),
                            16f
                        )
                    )
                    is WalkRouteCameraFraming.Bounds -> {
                        if (mapSize.width <= 192 || mapSize.height <= 192) return@LaunchedEffect
                        val bounds = LatLngBounds(
                            LatLng(framing.south, framing.west),
                            LatLng(framing.north, framing.east)
                        )
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngBounds(
                                bounds,
                                mapSize.width,
                                mapSize.height,
                                96
                            )
                        )
                    }
                }
            }
            GoogleMap(
                modifier = Modifier.fillMaxSize().onSizeChanged { mapSize = it },
                cameraPositionState = cameraPositionState,
                onMapLoaded = { mapLoaded = true }
            ) {
                if (points.size > 1) {
                    Polyline(points = points.map { LatLng(it.latitude, it.longitude) })
                }
            }
        } else {
            WalkRouteFallback(points)
        }
    }
}

@Composable
private fun WalkRouteFallback(points: List<WalkPointEntity>) {
    Box(Modifier.fillMaxSize().background(Color(0xFFE9EEF1)), contentAlignment = Alignment.Center) {
        if (points.size < 2) {
            Text("Your route will appear here", color = Color(0xFF4F5B62))
        } else {
            Canvas(Modifier.fillMaxSize().padding(20.dp)) {
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
                val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 1.0
                points.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        color = Color(0xFF0B6E69),
                        start = Offset(
                            ((a.longitude - minLon) / lonRange * size.width).toFloat(),
                            (size.height - (a.latitude - minLat) / latRange * size.height).toFloat()
                        ),
                        end = Offset(
                            ((b.longitude - minLon) / lonRange * size.width).toFloat(),
                            (size.height - (b.latitude - minLat) / latRange * size.height).toFloat()
                        ),
                        strokeWidth = 7f
                    )
                }
            }
        }
    }
}

internal fun sendWalkAction(context: Context, action: String, userId: String?) {
    val intent = Intent(context, WalkRecordingService::class.java)
        .setAction(action)
        .putExtra(WalkRecordingService.EXTRA_USER_ID, userId)
    ContextCompat.startForegroundService(context, intent)
}
