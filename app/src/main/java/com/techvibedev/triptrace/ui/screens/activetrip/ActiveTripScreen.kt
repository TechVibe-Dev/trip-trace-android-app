package com.techvibedev.triptrace.ui.screens.activetrip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.techvibedev.triptrace.data.local.GpsPointDao
import com.techvibedev.triptrace.data.local.TripEntity
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import com.techvibedev.triptrace.data.model.StopResponse
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.service.TripTrackingService
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TripStop(
    val label: String,
    val timeLabel: String,
    val reached: Boolean,
)

// GpsPoint.speed is stored in m/s (Android's Location.getSpeed() unit) —
// same conversion applied server-side in trip-trace-api#41, needed again
// here since this reads Room directly and never goes through the API.
private const val MS_TO_KMH = 3.6

// How often, while a trip is in progress, we (a) upload any GPS points Room
// has recorded since the last tick and (b) refresh the live ETA and stop
// progress from the API. Chosen as a balance: frequent enough that the
// screen feels live, infrequent enough not to hammer Google Routes (each
// recalculate-eta is a billable-ish call, see routing_service.py) or the
// device's radio/battery. Matches the interval agreed on with api#7.
private const val POLL_INTERVAL_MS = 30_000L

@Composable
fun ActiveTripScreen(
    tripId: String,
    tripRepository: TripRepository,
    onTripEnded: () -> Unit,
) {
    val context = LocalContext.current
    val gpsPointDao = remember {
        TripTraceDatabase.getInstance(context.applicationContext).gpsPointDao()
    }
    val latestPoint by gpsPointDao.observeLatest(tripId).collectAsState(initial = null)

    var startedAt by remember { mutableStateOf<String?>(null) }
    var plannedArrivalAt by remember { mutableStateOf<String?>(null) }
    var liveArrivalAt by remember { mutableStateOf<String?>(null) }
    var stops by remember { mutableStateOf<List<StopResponse>>(emptyList()) }
    var isEnding by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                val result = ensureTripSavedLocallyAndStartTracking(context, tripId, tripRepository)
                result.fold(
                    onSuccess = { trip ->
                        startedAt = trip.startedAt
                        plannedArrivalAt = trip.calculatedArrivalAt
                    },
                    onFailure = {
                        errorMessage = "No se pudo cargar el viaje, no se inicio la grabacion."
                    },
                )
            }
        } else {
            errorMessage = "Se necesita permiso de ubicacion para grabar el viaje"
        }
    }

    LaunchedEffect(tripId) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val result = ensureTripSavedLocallyAndStartTracking(context, tripId, tripRepository)
            result.fold(
                onSuccess = { trip ->
                    startedAt = trip.startedAt
                    plannedArrivalAt = trip.calculatedArrivalAt
                },
                onFailure = {
                    errorMessage = "No se pudo cargar el viaje, no se inicio la grabacion."
                },
            )
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Load whatever stops exist right away (all unreached at trip
        // start) rather than waiting a full poll cycle just to show their
        // names — the loop below keeps them current from here on.
        tripRepository.getStops(tripId).onSuccess { stops = it }
    }

    // Live loop while the trip is in progress: every POLL_INTERVAL_MS,
    // upload whatever GPS points Room has recorded and not synced yet
    // (same mechanism as the Sync-on-finish in endTrip() below, just
    // running periodically instead of once), then refresh the live ETA and
    // stop progress. Tied to this composable via LaunchedEffect — cancelled
    // automatically once the trip ends and this screen leaves composition.
    LaunchedEffect(tripId) {
        while (true) {
            delay(POLL_INTERVAL_MS)

            val unsyncedPoints = gpsPointDao.getUnsyncedByTripId(tripId)
            if (unsyncedPoints.isNotEmpty()) {
                // Best-effort: a failed upload leaves these unsynced in
                // Room, so the next tick retries them alongside whatever's
                // been recorded since — no data is lost, just delayed.
                tripRepository.uploadGpsPoints(tripId, unsyncedPoints).onSuccess {
                    gpsPointDao.markSynced(unsyncedPoints.map { point -> point.id })
                }
            }

            // Both best-effort too: a hiccup here just means the screen
            // keeps showing the last value it had until the next tick.
            tripRepository.recalculateEta(tripId).onSuccess { eta ->
                liveArrivalAt = eta.calculatedArrivalAt
            }
            tripRepository.getStops(tripId).onSuccess { stops = it }
        }
    }

    fun endTrip() {
        isEnding = true
        errorMessage = null
        scope.launch {
            // Sync (Room -> API) happens here too, right before finalizing:
            // /finalize computes distance/speed stats from whatever GPS
            // points already exist on the server, so without uploading
            // first, those stats always come back null. The periodic sync
            // above should have already caught most points, but this makes
            // sure anything from the last partial interval isn't lost.
            val unsyncedPoints = gpsPointDao.getUnsyncedByTripId(tripId)
            if (unsyncedPoints.isNotEmpty()) {
                val uploadResult = tripRepository.uploadGpsPoints(tripId, unsyncedPoints)
                uploadResult.onSuccess {
                    gpsPointDao.markSynced(unsyncedPoints.map { point -> point.id })
                }
            }

            val endResult = tripRepository.endTrip(tripId)
            if (endResult.isSuccess) {
                // Also best-effort — a failure here leaves the trip
                // correctly COMPLETED with null stats, same degraded state
                // as before Sync existed, not something worth blocking on.
                tripRepository.finalizeTrip(tripId)
            }

            TripTrackingService.stop(context)
            isEnding = false
            endResult.fold(
                onSuccess = { onTripEnded() },
                onFailure = { errorMessage = "No se pudo finalizar el viaje." },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        LiveRouteMap(tripId = tripId, gpsPointDao = gpsPointDao)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Llegada estimada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    // Falls back to the original plan until the first poll
                    // tick comes back (recalculate-eta needs at least one
                    // synced GPS point, which takes a moment after start).
                    text = formatLocalTime(liveArrivalAt ?: plannedArrivalAt),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Planeado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatLocalTime(plannedArrivalAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                label = "Velocidad",
                value = formatSpeed(latestPoint?.speed),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Salida",
                value = formatLocalTime(startedAt),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (stops.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                stops.forEachIndexed { index, stop ->
                    StopRow(stop = stop.toTripStop())
                    if (index != stops.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { endTrip() },
            enabled = !isEnding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isEnding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Finalizar viaje")
            }
        }
    }
}

// GpsPointEntity has a foreign key on tripId pointing at Room's own trips
// table — but trips are otherwise only known through the API, never
// inserted into Room. Without this, the very first point the tracking
// service tries to save crashes the app with SQLiteConstraintException
// (FOREIGN KEY constraint failed). Fetching and saving the trip here,
// before starting the service, closes that gap.
private suspend fun ensureTripSavedLocallyAndStartTracking(
    context: Context,
    tripId: String,
    tripRepository: TripRepository,
): Result<TripResponse> {
    val result = tripRepository.getTrip(tripId)
    result.onSuccess { trip ->
        val tripDao = TripTraceDatabase.getInstance(context.applicationContext).tripDao()
        tripDao.insert(trip.toEntity(syncedAt = OffsetDateTime.now().toString()))
        TripTrackingService.start(context, tripId)
    }
    return result
}

private fun TripResponse.toEntity(syncedAt: String): TripEntity {
    return TripEntity(
        id = id,
        userId = userId,
        originName = originName,
        originLat = originLat,
        originLng = originLng,
        destinationName = destinationName,
        destinationLat = destinationLat,
        destinationLng = destinationLng,
        plannedRoutePolyline = plannedRoutePolyline,
        status = status,
        plannedDepartureAt = plannedDepartureAt,
        desiredArrivalAt = desiredArrivalAt,
        calculatedArrivalAt = calculatedArrivalAt,
        startedAt = startedAt,
        endedAt = endedAt,
        distanceKm = distanceKm,
        maxSpeed = maxSpeed,
        minSpeed = minSpeed,
        avgSpeed = avgSpeed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
    )
}

// actual_arrival_at is set server-side once a synced GPS point lands within
// 100m of the stop (trip-trace-api's stop_detection_service) — this is a
// pure display mapping, no client-side proximity logic.
private fun StopResponse.toTripStop(): TripStop {
    return TripStop(
        label = name ?: "Parada",
        timeLabel = formatLocalTime(actualArrivalAt ?: plannedArrivalAt),
        reached = actualArrivalAt != null,
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun StopRow(stop: TripStop) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (stop.reached) Icons.Filled.Check else Icons.Filled.Flag,
                contentDescription = null,
                tint = if (stop.reached) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stop.label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stop.timeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Live map: current position + the route recorded so far, sourced straight
// from Room (observeAllByTripId, ~every 10s as the tracking service
// records) rather than the API — this needs to feel instant, not wait on
// the 30s sync cycle above, which exists to get data to the server, not to
// redraw the phone's own map. Camera follows the latest position, like a
// navigation app, rather than staying fixed on the initial fit.
@Composable
private fun LiveRouteMap(tripId: String, gpsPointDao: GpsPointDao) {
    val points by gpsPointDao.observeAllByTripId(tripId).collectAsState(initial = emptyList())
    val cameraPositionState = rememberCameraPositionState()
    var hasCenteredOnce by remember { mutableStateOf(false) }

    LaunchedEffect(points.size) {
        val latest = points.lastOrNull() ?: return@LaunchedEffect
        val latLng = LatLng(latest.lat, latest.lng)
        if (!hasCenteredOnce) {
            // First point: jump straight there — animating from the map's
            // arbitrary default start position would be a pointless pan
            // across the globe.
            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 17f)
            hasCenteredOnce = true
        } else {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng), durationMs = 1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Esperando ubicacion...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
            ) {
                if (points.size >= 2) {
                    Polyline(
                        points = points.map { LatLng(it.lat, it.lng) },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Marker(
                    state = rememberMarkerState(
                        position = LatLng(points.last().lat, points.last().lng),
                    ),
                )
            }
        }
    }
}

private fun formatSpeed(speedMs: Double?): String {
    if (speedMs == null) return "-- km/h"
    return "${(speedMs * MS_TO_KMH).toInt()} km/h"
}

// The API returns timestamps in UTC — formatting an OffsetDateTime directly
// prints ITS OWN offset's hour, not the phone's local one. Converting to
// the system zone first avoids showing e.g. "20:34" when the phone's real
// local time is 17:34 (UTC-3) — same bug found and fixed in TripsScreen.
private fun formatLocalTime(isoDateTime: String?): String {
    if (isoDateTime == null) return "--:--"
    return try {
        OffsetDateTime.parse(isoDateTime)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "--:--"
    }
}
