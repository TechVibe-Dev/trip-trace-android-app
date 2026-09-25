package com.techvibedev.triptrace.ui.screens.history

import androidx.compose.foundation.layout.width;
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.techvibedev.triptrace.R
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import com.techvibedev.triptrace.data.model.GpsPointResponse
import com.techvibedev.triptrace.data.model.SegmentResponse
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Same palette the web frontend uses to color a completed trip's route by
// segment (trip-trace-frontend#6) — kept identical across platforms rather
// than picked independently. Amber for slow, the app's own primary blue for
// normal, green for fast.
private val SEGMENT_COLORS = mapOf(
    "SLOW" to Color(0xFFEF9F27),
    "NORMAL" to Color(0xFF378ADD),
    "FAST" to Color(0xFF1D9E75),
)
private val SEGMENT_LABELS = mapOf(
    "SLOW" to "Lento",
    "NORMAL" to "Normal",
    "FAST" to "Rapido",
)

@Composable
fun HistoryScreen(tripRepository: TripRepository) {
    var trips by remember { mutableStateOf<List<TripResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedTripId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = tripRepository.getCompletedTrips()
        isLoading = false
        result.fold(
            onSuccess = { loaded ->
                trips = loaded.sortedByDescending { it.endedAt ?: it.createdAt }
                errorMessage = null
            },
            onFailure = { errorMessage = "No se pudo cargar el historial." },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            trips.isEmpty() -> {
                Text(
                    text = "Todavia no hay viajes completados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trips) { trip ->
                        PastTripCard(
                            trip = trip,
                            tripRepository = tripRepository,
                            expanded = trip.id == expandedTripId,
                            onToggleExpanded = {
                                expandedTripId = if (expandedTripId == trip.id) null else trip.id
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PastTripCard(
    trip: TripResponse,
    tripRepository: TripRepository,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onToggleExpanded),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${trip.originName} \u2192 ${trip.destinationName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatLocalDate(trip.endedAt ?: trip.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "Contraer" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                RealRouteMap(tripId = trip.id, tripRepository = tripRepository)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TripStat(label = "Maxima", value = formatSpeed(trip.maxSpeed))
                    TripStat(label = "Promedio", value = formatSpeed(trip.avgSpeed))
                    TripStat(label = "Distancia", value = formatDistance(trip.distanceKm))
                }
                Spacer(modifier = Modifier.height(10.dp))
                SensorDataCleanupRow(tripId = trip.id)
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = formatDuration(trip.startedAt, trip.endedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDistance(trip.distanceKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TripStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// Manual, user-triggered cleanup for this trip's raw accelerometer/
// gyroscope samples (android#76's evaluation data) — unlike gps_points,
// nothing ever auto-deletes these, since their whole purpose is being
// available for review/extraction after the fact. Only shown once a count
// is loaded and it's above zero, so trips with no sensor data (recorded
// before that PR, or already cleared) don't show an empty/dead control.
@Composable
private fun SensorDataCleanupRow(tripId: String) {
    val context = LocalContext.current
    val sensorReadingDao = remember {
        TripTraceDatabase.getInstance(context.applicationContext).sensorReadingDao()
    }
    val scope = rememberCoroutineScope()
    var readingCount by remember(tripId) { mutableIntStateOf(0) }
    var showConfirmDialog by remember(tripId) { mutableStateOf(false) }
    var isDeleting by remember(tripId) { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        readingCount = sensorReadingDao.countByTripId(tripId)
    }

    if (readingCount > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Datos de sensores: $readingCount registros",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { showConfirmDialog = true }, enabled = !isDeleting) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Borrar datos de sensores de este viaje",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Borrar datos de sensores") },
            text = {
                Text(
                    "Se van a borrar los $readingCount registros de acelerometro/giroscopio " +
                        "grabados para este viaje. No se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        isDeleting = true
                        scope.launch {
                            sensorReadingDao.deleteByTripId(tripId)
                            readingCount = 0
                            isDeleting = false
                        }
                    },
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

// Buckets a trip's recorded points into per-segment runs so each stretch of
// the route can be drawn in its own color — same approach as the web
// frontend (trip-trace-frontend#6). Segments (GET /trips/{id}/segments)
// only carry their own points, no boundary point shared with the next
// segment (see trip_stats_service.py's compute_trip_segments), so each
// segment's line is extended to also touch the FIRST point of the next
// segment — otherwise consecutive colors would visibly not connect.
private data class SegmentGroup(val segmentType: String, val positions: List<LatLng>)

private fun groupPointsBySegment(
    points: List<GpsPointResponse>,
    segments: List<SegmentResponse>,
): List<SegmentGroup> {
    val groups = mutableListOf<SegmentGroup>()
    segments.forEachIndexed { index, segment ->
        val segmentPoints = points.filter {
            it.recordedAt >= segment.startTime && it.recordedAt <= segment.endTime
        }
        if (segmentPoints.isEmpty()) return@forEachIndexed

        val positions = segmentPoints.map { LatLng(it.lat, it.lng) }.toMutableList()
        val nextSegment = segments.getOrNull(index + 1)
        val bridgePoint = nextSegment?.let { next ->
            points.firstOrNull { it.recordedAt >= next.startTime && it.recordedAt <= next.endTime }
        }
        if (bridgePoint != null) {
            positions.add(LatLng(bridgePoint.lat, bridgePoint.lng))
        }

        if (positions.size > 1) {
            groups.add(SegmentGroup(segment.segmentType, positions))
        }
    }
    return groups
}

// Draws the trip's REAL recorded path (GET /trips/{id}/gps-points), colored
// per-segment (GET /trips/{id}/segments, SLOW/NORMAL/FAST) instead of a
// single solid color — same idea as the web frontend's trip detail map
// (trip-trace-frontend#6, android#80). Falls back to a single-color line
// for any stretch not covered by a segment (e.g. no speed data there) or
// for the whole route if there are no segments at all (trip too short).
// Not planned_route_polyline (Google's suggested route at creation time),
// which can diverge from what actually happened. See android#57. Loaded
// lazily, only once the card is expanded. Sized generously (420.dp,
// bumped up from 280.dp per android#63) since it's the main content of the
// expanded card — the card grows to fit it via animateContentSize above.
@Composable
private fun RealRouteMap(tripId: String, tripRepository: TripRepository) {
    var points by remember(tripId) { mutableStateOf<List<GpsPointResponse>>(emptyList()) }
    var segments by remember(tripId) { mutableStateOf<List<SegmentResponse>>(emptyList()) }
    var isLoading by remember(tripId) { mutableStateOf(true) }
    var loadError by remember(tripId) { mutableStateOf(false) }
    val context = LocalContext.current
    // Dark map style (android#68) — Google's default palette is light and
    // clashes with the rest of the (dark-themed) app. Remembered so the
    // same MapStyleOptions instance survives recomposition rather than
    // re-parsing the raw resource every time.
    val mapProperties = remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
    }

    LaunchedEffect(tripId) {
        coroutineScope {
            val pointsDeferred = async { tripRepository.getGpsPoints(tripId) }
            val segmentsDeferred = async { tripRepository.getSegments(tripId) }
            val pointsResult = pointsDeferred.await()
            val segmentsResult = segmentsDeferred.await()
            pointsResult.fold(
                onSuccess = { points = it },
                onFailure = { loadError = true },
            )
            // Best-effort: a failed segments fetch just means the route
            // draws in a single fallback color instead of by segment —
            // not worth failing the whole map over.
            segmentsResult.onSuccess { segments = it }
        }
        isLoading = false
    }

    val segmentGroups = remember(points, segments) { groupPointsBySegment(points, segments) }
    val hasSegmentColoring = segmentGroups.isNotEmpty()

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                loadError -> {
                    Text(
                        text = "No se pudo cargar el mapa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                points.size < 2 -> {
                    Text(
                        text = "Sin puntos suficientes para mostrar la ruta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val routePoints = points.map { LatLng(it.lat, it.lng) }
                    val cameraPositionState = rememberCameraPositionState()

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = mapProperties,
                        // Zoom (pinch + on-screen +/-) enabled per user
                        // request — scroll/rotation/tilt stay off on
                        // purpose: this map sits inside History's
                        // scrollable list, and a one-finger drag should
                        // keep scrolling that list, not pan the map. A
                        // two-finger pinch doesn't conflict with that.
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = true,
                            rotationGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                        ),
                        // Bounds-fitting needs the map to already have real
                        // pixel dimensions, or it throws — onMapLoaded
                        // fires once that's guaranteed, unlike a plain
                        // LaunchedEffect keyed on the points themselves.
                        onMapLoaded = {
                            val bounds = LatLngBounds.Builder().apply {
                                routePoints.forEach { include(it) }
                            }.build()
                            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 48))
                        },
                    ) {
                        if (hasSegmentColoring) {
                            segmentGroups.forEach { group ->
                                Polyline(
                                    points = group.positions,
                                    color = SEGMENT_COLORS[group.segmentType]
                                        ?: MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            Polyline(points = routePoints, color = MaterialTheme.colorScheme.primary)
                        }
                        Marker(state = rememberMarkerState(position = routePoints.first()))
                        Marker(state = rememberMarkerState(position = routePoints.last()))
                    }
                }
            }
        }
        if (hasSegmentColoring) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("SLOW", "NORMAL", "FAST").forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SEGMENT_COLORS.getValue(type)),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = SEGMENT_LABELS.getValue(type),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// The API returns timestamps in UTC — formatting an OffsetDateTime directly
// prints ITS OWN offset's date/hour, not the phone's local one. Without
// converting first, a trip that ended late at night local time could even
// show the WRONG DAY (e.g. 23:30 in UTC-3 is already past midnight in UTC).
// Same bug found and fixed in TripsScreen/ActiveTripScreen.
private fun formatLocalDate(isoDateTime: String?): String {
    if (isoDateTime == null) return "--"
    return try {
        OffsetDateTime.parse(isoDateTime)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM"))
    } catch (e: Exception) {
        "--"
    }
}

private fun formatDuration(startedAt: String?, endedAt: String?): String {
    if (startedAt == null || endedAt == null) return "--"
    return try {
        val duration = Duration.between(OffsetDateTime.parse(startedAt), OffsetDateTime.parse(endedAt))
        val minutes = duration.toMinutes()
        if (minutes >= 60) "${minutes / 60}h ${minutes % 60}min" else "${minutes}min"
    } catch (e: Exception) {
        "--"
    }
}

// max_speed/avg_speed/distance_km already come from the API in the right
// unit (km/h, km) — the m/s-vs-km/h conversion lives server-side
// (trip-trace-api#41), not needed again here.
private fun formatDistance(distanceKm: Double?): String {
    return if (distanceKm != null) "${"%.1f".format(distanceKm)} km" else "--"
}

private fun formatSpeed(speedKmh: Double?): String {
    return if (speedKmh != null) "${speedKmh.toInt()} km/h" else "--"
}
