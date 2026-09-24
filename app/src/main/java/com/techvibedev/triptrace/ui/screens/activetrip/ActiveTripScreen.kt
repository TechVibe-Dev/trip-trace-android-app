package com.techvibedev.triptrace.ui.screens.activetrip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.techvibedev.triptrace.R
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

// Floating cards sit on top of a full-screen map (agreed design: the map is
// the protagonist of this screen) — a flat surface color would be
// unreadable against arbitrary map tiles underneath, so every card uses
// this translucent version instead.
private val OverlayCardColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

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

    var trip by remember { mutableStateOf<TripResponse?>(null) }
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
                    onSuccess = { trip = it },
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
                onSuccess = { trip = it },
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

    // Stops + destination, always shown together — the destination is
    // always the last row (never "reached" while still in progress; the
    // trip only knows it arrived once the user taps "Finalizar viaje", no
    // proximity detection for the destination itself, unlike intermediate
    // stops). Real intermediate stops come first, in the order the trip
    // has them.
    val currentTrip = trip
    val displayRows = buildList {
        addAll(stops.map { it.toTripStop() })
        if (currentTrip != null) {
            add(
                TripStop(
                    label = "Destino: ${currentTrip.destinationName}",
                    timeLabel = formatLocalTime(liveArrivalAt ?: currentTrip.calculatedArrivalAt),
                    reached = false,
                ),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LiveRouteMap(
            tripId = tripId,
            gpsPointDao = gpsPointDao,
            stops = stops,
            originLat = currentTrip?.originLat,
            originLng = currentTrip?.originLng,
            destinationLat = currentTrip?.destinationLat,
            destinationLng = currentTrip?.destinationLng,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OverlayCardColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Llegada estimada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            // Falls back to the original plan until the
                            // first poll tick comes back (recalculate-eta
                            // needs at least one synced GPS point, which
                            // takes a moment after start).
                            text = formatLocalTime(liveArrivalAt ?: currentTrip?.calculatedArrivalAt),
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
                            text = formatLocalTime(currentTrip?.calculatedArrivalAt),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                        value = formatLocalTime(currentTrip?.startedAt),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OverlayCardColor, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    displayRows.forEachIndexed { index, stop ->
                        StopRow(stop = stop)
                        if (index != displayRows.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
            .background(OverlayCardColor, RoundedCornerShape(10.dp))
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

// BitmapDescriptorFactory.fromResource() doesn't reliably rasterize vector
// drawables (a long-standing platform limitation) — drawing it to a Bitmap
// ourselves first is the standard workaround.
private fun vectorToBitmapDescriptor(context: Context, drawableResId: Int): BitmapDescriptor {
    val drawable = ContextCompat.getDrawable(context, drawableResId)!!
    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888,
    )
    drawable.draw(Canvas(bitmap))
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

// Live map: current position + the route recorded so far, sourced straight
// from Room (observeAllByTripId, ~every 3s as the tracking service
// records) rather than the API — this needs to feel instant, not wait on
// the 30s sync cycle above, which exists to get data to the server, not to
// redraw the phone's own map. Camera follows the latest position, like a
// navigation app, rather than staying fixed on the initial fit. Fills the
// whole screen (agreed design) — the overlay cards in the parent Box sit on
// top of this, not beside it.
@Composable
private fun LiveRouteMap(
    tripId: String,
    gpsPointDao: GpsPointDao,
    stops: List<StopResponse>,
    originLat: Double?,
    originLng: Double?,
    destinationLat: Double?,
    destinationLng: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val points by gpsPointDao.observeAllByTripId(tripId).collectAsState(initial = emptyList())
    val cameraPositionState = rememberCameraPositionState()
    var hasCenteredOnce by remember { mutableStateOf(false) }
    // Dark map style (android#68) — Google's default palette is light and
    // clashes with the rest of the (dark-themed) app. loadRawResourceStyle
    // just parses JSON, no dependency on the Maps system being initialized
    // (unlike BitmapDescriptorFactory below), so this is safe to build
    // unconditionally here.
    val mapProperties = remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
    }
    // Holds the current-position marker's state ourselves rather than using
    // rememberMarkerState(position = ...) — that helper only sets position
    // on the marker's FIRST creation; passing a fresh position on later
    // recompositions is silently ignored, since remember() only re-runs its
    // block when its key changes (no key here means "compute once, ever").
    // That's what left the arrow frozen at the trip's origin during a real
    // drive (a continuous session recomposing many times) — earlier walking
    // tests likely each reopened the screen fresh, masking it, since a
    // fresh composition picks up whatever the latest point was at that
    // moment. Reassigning .position explicitly below, every recomposition,
    // mirrors exactly how the camera above is already kept live.
    val currentPositionMarkerState = remember { MarkerState() }

    LaunchedEffect(points.size) {
        val latest = points.lastOrNull() ?: return@LaunchedEffect
        val latLng = LatLng(latest.lat, latest.lng)
        if (!hasCenteredOnce) {
            // First point: jump straight there — animating from the map's
            // arbitrary default start position would be a pointless pan
            // across the globe.
            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 16f)
            hasCenteredOnce = true
        } else {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng), durationMs = 1000)
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(0.dp))) {
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
            // Computed here, not above the points.isEmpty() check — this is
            // the first point in composition where a GoogleMap is actually
            // about to exist. BitmapDescriptorFactory throws
            // IllegalStateException if called before the Maps system has
            // been initialized (normally triggered by creating a map), so
            // building this any earlier — e.g. unconditionally at the top
            // of this function, which used to crash "Iniciar viaje" every
            // time, since the very first composition always has zero
            // points recorded yet — is not safe.
            val navArrowIcon = remember { vectorToBitmapDescriptor(context, R.drawable.ic_nav_arrow) }
            val latest = points.last()
            // Reassigned every recomposition (every new point), same
            // reasoning as the comment on currentPositionMarkerState above.
            currentPositionMarkerState.position = LatLng(latest.lat, latest.lng)

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
            ) {
                if (points.size >= 2) {
                    Polyline(
                        points = points.map { LatLng(it.lat, it.lng) },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Marker(
                    state = currentPositionMarkerState,
                    icon = navArrowIcon,
                    // GPS bearing is noisy/unreliable at low or zero speed
                    // (a known limitation, not specific to this app) — null
                    // falls back to pointing north rather than a stale or
                    // jittery reading.
                    rotation = latest.bearing?.toFloat() ?: 0f,
                    flat = true,
                    title = "Posicion actual",
                )
                if (originLat != null && originLng != null) {
                    Marker(
                        state = rememberMarkerState(position = LatLng(originLat, originLng)),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        title = "Origen",
                    )
                }
                if (destinationLat != null && destinationLng != null) {
                    Marker(
                        state = rememberMarkerState(position = LatLng(destinationLat, destinationLng)),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                        title = "Destino",
                    )
                }
                stops.forEach { stop ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(stop.lat, stop.lng)),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                        title = stop.name ?: "Parada",
                    )
                }
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
