package com.techvibedev.triptrace.ui.screens.activetrip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.techvibedev.triptrace.data.local.GpsPointEntity
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import com.techvibedev.triptrace.data.model.RouteStepResponse
import com.techvibedev.triptrace.data.model.StopResponse
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.trip.ARRIVAL_THRESHOLD_METERS
import com.techvibedev.triptrace.trip.MAX_ACCURACY_ALLOWANCE_METERS
import com.techvibedev.triptrace.trip.MIN_REFRESH_GAP_MS
import com.techvibedev.triptrace.trip.OFF_ROUTE_CONFIRM_POINTS
import com.techvibedev.triptrace.trip.OFF_ROUTE_THRESHOLD_METERS
import com.techvibedev.triptrace.trip.POLL_INTERVAL_MS
import com.techvibedev.triptrace.trip.ensureTripSavedLocallyAndStartTracking
import com.techvibedev.triptrace.trip.finishTrip
import com.techvibedev.triptrace.trip.haversineMeters
import com.techvibedev.triptrace.trip.matchRouteStep
import com.techvibedev.triptrace.trip.syncPendingGpsPoints
import com.techvibedev.triptrace.trip.trimRouteBehindPosition
import com.techvibedev.triptrace.ui.components.ManeuverIcon
import com.techvibedev.triptrace.util.decodePolyline
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class TripStop(
    val label: String,
    val timeLabel: String,
    val reached: Boolean,
)

// GpsPoint.speed is stored in m/s (Android's Location.getSpeed() unit) —
// same conversion applied server-side, needed again here since this reads
// Room directly and never goes through the API.
private const val MS_TO_KMH = 3.6

// Close, tilted, bearing-following camera — replaces the old "fit the whole
// recorded route" behavior, so this screen reads as a close-up navigation
// view (Waze/Maps-style) instead of a route overview. The overview is
// still available: History shows a trip's complete real route after the
// fact, unaffected by this.
private const val NAV_ZOOM = 18.5f
private const val NAV_TILT = 50f

// How long to animate the marker/camera from one GPS point to the next,
// instead of snapping — kept close to TripTrackingService's own GPS
// interval (2s as of this writing) so the transition finishes right as the
// next point tends to arrive, rather than visibly catching up or sitting
// idle. Not read from that constant directly (different module, no shared
// config today) — if one changes, it's worth revisiting the other.
private const val POSITION_ANIMATION_DURATION_MS = 2_000L
private const val POSITION_ANIMATION_FRAME_MS = 50L

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
    // Skips points with a null speed reading (see GpsPointDao) rather than
    // always using the single latest point — GPS speed drops out in short
    // bursts (turns, braking, patchy sky visibility) even while position
    // stays fine, confirmed across a real drive. Using the latest point
    // unconditionally meant the card could flash "--" for a few seconds
    // even with a real reading moments earlier.
    val latestPointWithSpeed by gpsPointDao.observeLatestWithSpeed(tripId).collectAsState(initial = null)
    // Separate from the speed-filtered one above — arrival detection only
    // needs position, so it shouldn't wait out the same null-speed bursts.
    val latestPoint by gpsPointDao.observeLatest(tripId).collectAsState(initial = null)

    var trip by remember { mutableStateOf<TripResponse?>(null) }
    var liveArrivalAt by remember { mutableStateOf<String?>(null) }
    // Route from the current position to the destination, and its turn-by-
    // turn steps — both refreshed on every recalculate-eta poll. steps[0]
    // is always "the next maneuver from here", since the route was just
    // computed FROM the current position — no step-matching needed on this
    // side.
    var routePolyline by remember { mutableStateOf<String?>(null) }
    var routeSteps by remember { mutableStateOf<List<RouteStepResponse>>(emptyList()) }
    // Each step's own road geometry, decoded once per response. Used to
    // work out locally which step the car is on, so the turn card moves on
    // to the next maneuver right after a turn instead of waiting for the
    // next recalculate-eta response.
    val stepPolylines = remember(routeSteps) { routeSteps.map { decodePolyline(it.polyline) } }
    // Index into routeSteps of the maneuver being approached. Reset to 0 on
    // every new response (that route starts at the car's position), only
    // ever moves forward in between.
    var currentStepIndex by remember { mutableStateOf(0) }
    var offRoutePointCount by remember { mutableStateOf(0) }
    var hasRequestedInitialRoute by remember { mutableStateOf(false) }
    // Early refresh requests for the poll loop below (off route, first GPS
    // fix). Conflated: several requests before the loop gets to them still
    // mean a single refresh.
    val refreshRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    var stops by remember { mutableStateOf<List<StopResponse>>(emptyList()) }
    var isEnding by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Confirmation before the manual "Finalizar viaje" button actually ends
    // the trip, so a misclick doesn't close it out by accident.
    var showEndTripConfirmDialog by remember { mutableStateOf(false) }
    // Prompts once, automatically, on getting within ARRIVAL_THRESHOLD_METERS
    // of the destination — not a hard auto-finish, since the trip genuinely
    // might continue past this point (a stop just short of the actual
    // destination, or the user driving further for some other reason) or
    // the user might want to linger before ending it. Deliberately
    // fire-once per screen session via hasPromptedArrival: sitting right at
    // the destination would otherwise re-trigger on every new point for as
    // long as the trip stays open. If dismissed with "Seguir viaje", it
    // does not ask again — the manual button (with its own new
    // confirmation above) is still right there whenever the user does want
    // to finish, covering both "kept driving" and "never really arrives".
    var hasPromptedArrival by remember { mutableStateOf(false) }
    var showArrivalDialog by remember { mutableStateOf(false) }
    // Updated every animation frame by LiveRouteMap (via onPositionUpdate)
    // — used below to compute a live "distance to next turn" that ticks
    // down smoothly instead of only jumping every 30s poll, same idea as
    // the route trimming inside LiveRouteMap, just surfaced up here since
    // TurnInstructionCard lives in this composable, not that one.
    var currentAnimatedPosition by remember { mutableStateOf<LatLng?>(null) }
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
                        errorMessage = "No se pudo cargar el viaje, no se inició la grabación."
                    },
                )
            }
        } else {
            errorMessage = "Se necesita permiso de ubicación para grabar el viaje"
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
                    errorMessage = "No se pudo cargar el viaje, no se inició la grabación."
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
    // running periodically instead of once), then refresh the live ETA,
    // turn-by-turn steps, and stop progress. Tied to this composable via
    // LaunchedEffect — cancelled automatically once the trip ends and this
    // screen leaves composition.
    LaunchedEffect(tripId) {
        var lastRefreshAtMs = 0L
        while (true) {
            // Whichever comes first: the regular interval, or an early
            // request from the off-route check below.
            withTimeoutOrNull(POLL_INTERVAL_MS) { refreshRequests.receive() }
            val sinceLastRefreshMs = SystemClock.elapsedRealtime() - lastRefreshAtMs
            if (sinceLastRefreshMs < MIN_REFRESH_GAP_MS) {
                delay(MIN_REFRESH_GAP_MS - sinceLastRefreshMs)
            }
            // Anything requested while waiting is covered by this refresh.
            refreshRequests.tryReceive()
            lastRefreshAtMs = SystemClock.elapsedRealtime()

            syncPendingGpsPoints(context, tripId, tripRepository)

            // Both best-effort too: a hiccup here just means the screen
            // keeps showing the last value it had until the next tick.
            tripRepository.recalculateEta(tripId).onSuccess { eta ->
                liveArrivalAt = eta.calculatedArrivalAt
                routePolyline = eta.routePolyline
                routeSteps = eta.steps
                currentStepIndex = 0
                offRoutePointCount = 0
            }
            tripRepository.getStops(tripId).onSuccess { stops = it }
        }
    }

    // On every new GPS point: advance the turn card when the car has moved
    // onto a later step, or ask for a new route once it's clearly off the
    // current one. Uses the raw point rather than the animated position —
    // scanning step polylines every frame would be wasted work, and GPS
    // only changes every couple of seconds anyway.
    LaunchedEffect(latestPoint, stepPolylines) {
        val point = latestPoint ?: return@LaunchedEffect
        if (stepPolylines.isEmpty()) {
            // No route yet: ask for one as soon as there's a position,
            // instead of waiting out the first full interval. Only once —
            // if that fails, the regular interval keeps retrying.
            if (!hasRequestedInitialRoute) {
                hasRequestedInitialRoute = true
                refreshRequests.trySend(Unit)
            }
            return@LaunchedEffect
        }
        val match = matchRouteStep(stepPolylines, currentStepIndex, LatLng(point.lat, point.lng))
        val accuracyAllowance = (point.accuracy ?: 0.0).coerceIn(0.0, MAX_ACCURACY_ALLOWANCE_METERS)
        if (match == null || match.distanceMeters > OFF_ROUTE_THRESHOLD_METERS + accuracyAllowance) {
            offRoutePointCount += 1
            if (offRoutePointCount >= OFF_ROUTE_CONFIRM_POINTS) {
                offRoutePointCount = 0
                refreshRequests.trySend(Unit)
            }
        } else {
            offRoutePointCount = 0
            if (match.stepIndex > currentStepIndex) {
                currentStepIndex = match.stepIndex
            }
        }
    }

    // Checked on every new point (roughly every 3s, matching
    // TripTrackingService's recording interval) against the trip's
    // destination. Only runs while this screen is actually composed (i.e.
    // in the foreground); with the screen off or the app backgrounded, GPS
    // recording itself keeps going via the foreground service, but this
    // particular prompt does not fire until the screen is reopened and
    // notices the trip already sitting within range. A background,
    // notification-based version of this same check is a reasonable
    // follow-up, not included here.
    LaunchedEffect(latestPoint, trip) {
        val point = latestPoint ?: return@LaunchedEffect
        val destLat = trip?.destinationLat ?: return@LaunchedEffect
        val destLng = trip?.destinationLng ?: return@LaunchedEffect
        if (hasPromptedArrival) return@LaunchedEffect
        val distanceMeters = haversineMeters(point.lat, point.lng, destLat, destLng)
        if (distanceMeters <= ARRIVAL_THRESHOLD_METERS) {
            hasPromptedArrival = true
            showArrivalDialog = true
        }
    }

    fun endTrip() {
        isEnding = true
        errorMessage = null
        scope.launch {
            val endResult = finishTrip(context, tripId, tripRepository)
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
    val nextStep = routeSteps.getOrNull(currentStepIndex)
    // The maneuver point is always the LAST point of the current step's own
    // polyline — a step's road segment ends exactly where its maneuver
    // happens (the next step's polyline picks up from there).
    val nextManeuverPoint = stepPolylines.getOrNull(currentStepIndex)?.lastOrNull()
    // Ticks down between polls using the same animated position driving the
    // map (android#113) — falls back to the API's own last-known distance
    // before the first frame, or if a maneuver point isn't available yet.
    val liveStepDistanceMeters = currentAnimatedPosition?.let { position ->
        nextManeuverPoint?.let { maneuverPoint ->
            haversineMeters(
                position.latitude,
                position.longitude,
                maneuverPoint.latitude,
                maneuverPoint.longitude,
            ).roundToInt()
        }
    } ?: nextStep?.distanceMeters

    Box(modifier = Modifier.fillMaxSize()) {
        LiveRouteMap(
            tripId = tripId,
            gpsPointDao = gpsPointDao,
            stops = stops,
            routePolyline = routePolyline,
            originLat = currentTrip?.originLat,
            originLng = currentTrip?.originLng,
            destinationLat = currentTrip?.destinationLat,
            destinationLng = currentTrip?.destinationLng,
            onPositionUpdate = { currentAnimatedPosition = it },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                if (nextStep != null) {
                    TurnInstructionCard(step = nextStep, distanceMeters = liveStepDistanceMeters ?: nextStep.distanceMeters)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OverlayCardColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            style = MaterialTheme.typography.titleLarge,
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(
                        label = "Velocidad",
                        value = formatSpeed(latestPointWithSpeed?.speed),
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
                    onClick = { showEndTripConfirmDialog = true },
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

    if (showEndTripConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndTripConfirmDialog = false },
            title = { Text("Finalizar viaje") },
            text = { Text("¿Seguro que querés finalizar el viaje?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndTripConfirmDialog = false
                        endTrip()
                    },
                ) {
                    Text("Finalizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTripConfirmDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = { Text("Llegaste a destino") },
            text = { Text("¿Querés finalizar el viaje?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArrivalDialog = false
                        endTrip()
                    },
                ) {
                    Text("Finalizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArrivalDialog = false }) {
                    Text("Seguir viaje")
                }
            },
        )
    }
}

// actual_arrival_at is set server-side once a synced GPS point lands within
// 100m of the stop — this is a pure display mapping, no client-side
// proximity logic.
private fun StopResponse.toTripStop(): TripStop {
    return TripStop(
        label = name ?: "Parada",
        timeLabel = formatLocalTime(actualArrivalAt ?: plannedArrivalAt),
        reached = actualArrivalAt != null,
    )
}

// Plain linear interpolation — fine for lat/lng over the short distance
// covered between two consecutive GPS points (at most a few dozen meters).
private fun lerp(start: Double, end: Double, fraction: Float): Double =
    start + (end - start) * fraction

// Interpolating bearing needs to go the short way around the compass —
// naively lerping 350 -> 10 would sweep through 180 (the long way) instead
// of through 0 (20 degrees, the actual short way). Normalizing the delta
// into (-180, 180] first fixes that; the final `+ 360 % 360` just keeps the
// result in the conventional 0-360 range.
private fun lerpBearing(start: Float, end: Float, fraction: Float): Float {
    var delta = (end - start) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return (start + delta * fraction + 360f) % 360f
}

// Google's navigationInstruction.instructions is already a complete,
// localized, ready-to-show sentence — shown verbatim, not reassembled from
// the maneuver type. distanceMeters is passed in separately rather than
// read from step.distanceMeters directly, so the caller can supply a live,
// locally-recomputed value (android#113) instead of the raw, only-every-
// 30s API figure. Matches the confirmed mockup:
// https://claude.ai/artifact/A5pGFSAbfkXbaDuWYJiqtG.
@Composable
private fun TurnInstructionCard(step: RouteStepResponse, distanceMeters: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManeuverIcon(
            maneuver = step.maneuver,
            color = MaterialTheme.colorScheme.onPrimary,
            iconSize = 32.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = step.instructions,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = formatStepDistance(distanceMeters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        }
    }
}

private fun formatStepDistance(distanceMeters: Int): String {
    return if (distanceMeters >= 1000) {
        "en %.1f km".format(distanceMeters / 1000.0)
    } else {
        "en $distanceMeters m"
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(OverlayCardColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
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

// Live map: current position, close/tilted/bearing-following like a
// navigation app, sourced straight from Room (observeAllByTripId, ~every 2s
// as the tracking service records) rather than the API — this needs to
// feel instant, not wait on the 30s sync cycle above, which exists to get
// data to the server, not to redraw the phone's own map. Fills the whole
// screen (agreed design) — the overlay cards in the parent Box sit on top
// of this, not beside it.
@Composable
private fun LiveRouteMap(
    tripId: String,
    gpsPointDao: GpsPointDao,
    stops: List<StopResponse>,
    routePolyline: String?,
    originLat: Double?,
    originLng: Double?,
    destinationLat: Double?,
    destinationLng: Double?,
    // Called every animation frame with wherever the marker/camera
    // currently are — lets ActiveTripScreen compute a live distance to the
    // next turn (android#113) without duplicating the animation loop.
    onPositionUpdate: (LatLng) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val points by gpsPointDao.observeAllByTripId(tripId).collectAsState(initial = emptyList())
    val cameraPositionState = rememberCameraPositionState()
    // Dark map style — Google's default palette is light and clashes with
    // the rest of the (dark-themed) app. loadRawResourceStyle just parses
    // JSON, no dependency on the Maps system being initialized (unlike
    // BitmapDescriptorFactory below), so this is safe to build
    // unconditionally here.
    val mapProperties = remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
    }
    // Google draws its own compass button (appears automatically once the
    // map's bearing isn't north-up, which is now always true here) and its
    // own "my location" button at fixed corners that know nothing about
    // this screen's own overlay cards, ending up stuck half-hidden behind
    // them. Neither is needed anyway — the ManeuverIcon/stat cards already
    // show heading and position — so both stay off.
    val mapUiSettings = remember {
        MapUiSettings(zoomControlsEnabled = false, compassEnabled = false, myLocationButtonEnabled = false)
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
    // moment. Reassigning .position explicitly below, every frame, mirrors
    // exactly how the camera is kept live.
    val currentPositionMarkerState = remember { MarkerState() }

    // Where the interpolation loop below currently has the marker/camera —
    // read by the route-trimming logic further down, so the suggested-
    // route line's cut point always matches exactly where the (smoothly
    // animating) arrow visually is, instead of the raw, jumpier GPS point.
    // Null only before the very first point arrives.
    var animatedPosition by remember { mutableStateOf<LatLng?>(null) }
    var animatedBearing by remember { mutableStateOf(0f) }
    // The GPS point the current animation is animating FROM — the marker
    // was found landing on it after each update (a real driving test on
    // android#111), since a new point simply replaced the previous one
    // outright rather than transitioning between them.
    var previousPoint by remember { mutableStateOf<GpsPointEntity?>(null) }

    // Suggested route ahead, from the latest recalculate-eta poll — the
    // primary route line in this close-up view, replacing the previous
    // "whole recorded trail" Polyline. flat markers' rotation is in the
    // same absolute-bearing coordinate frame the camera's own bearing
    // rotates the canvas by, so setting both to the same heading makes the
    // arrow point straight up on screen, same as any navigation app — no
    // extra math needed to keep the two in sync.
    val suggestedRoutePoints = remember(routePolyline) {
        routePolyline?.let { decodePolyline(it) } ?: emptyList()
    }
    // Cut to start where the car currently is, instead of always drawing
    // the whole thing from where it was at the last 30s poll — recomputed
    // on every animation frame, using animatedPosition rather than the raw
    // latest GPS point so the line's cut point and the arrow never visibly
    // disagree with each other.
    val trimmedRoutePoints = animatedPosition?.let { position ->
        if (suggestedRoutePoints.size >= 2) {
            trimRouteBehindPosition(suggestedRoutePoints, position)
        } else {
            suggestedRoutePoints
        }
    } ?: suggestedRoutePoints

    // Animates the marker and camera together, frame by frame, instead of
    // snapping to each new GPS point — the car moves continuously, but GPS
    // updates only arrive as discrete points every couple of seconds.
    // Restarts cleanly if a new point arrives before this finishes:
    // LaunchedEffect cancels and re-runs this whole block on every
    // points.size change, and since previousPoint is reassigned right at
    // the top, the next segment just continues on from wherever this one
    // was headed, not from whatever frame the animation happened to reach.
    LaunchedEffect(points.size) {
        val newest = points.lastOrNull() ?: return@LaunchedEffect
        val newestLatLng = LatLng(newest.lat, newest.lng)
        val newestBearing = newest.bearing?.toFloat() ?: animatedBearing

        val previous = previousPoint
        previousPoint = newest

        if (previous == null) {
            // First point ever: jump straight there — animating from the
            // map's arbitrary default start position would be a pointless
            // pan across the globe.
            animatedPosition = newestLatLng
            animatedBearing = newestBearing
            currentPositionMarkerState.position = newestLatLng
            cameraPositionState.position = CameraPosition.Builder()
                .target(newestLatLng)
                .zoom(NAV_ZOOM)
                .tilt(NAV_TILT)
                .bearing(newestBearing)
                .build()
            onPositionUpdate(newestLatLng)
            return@LaunchedEffect
        }

        val fromLatLng = LatLng(previous.lat, previous.lng)
        val fromBearing = animatedBearing
        val startTime = System.currentTimeMillis()

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val fraction = (elapsed.toFloat() / POSITION_ANIMATION_DURATION_MS).coerceIn(0f, 1f)

            val position = LatLng(
                lerp(fromLatLng.latitude, newestLatLng.latitude, fraction),
                lerp(fromLatLng.longitude, newestLatLng.longitude, fraction),
            )
            val bearing = lerpBearing(fromBearing, newestBearing, fraction)

            animatedPosition = position
            animatedBearing = bearing
            currentPositionMarkerState.position = position
            cameraPositionState.position = CameraPosition.Builder()
                .target(position)
                .zoom(NAV_ZOOM)
                .tilt(NAV_TILT)
                .bearing(bearing)
                .build()
            onPositionUpdate(position)

            if (fraction >= 1f) break
            delay(POSITION_ANIMATION_FRAME_MS)
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
                    text = "Esperando ubicación...",
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

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
            ) {
                // Suggested route once available; falls back to the trail
                // recorded so far for the brief gap before the first
                // recalculate-eta response comes back after starting a trip.
                if (trimmedRoutePoints.size >= 2) {
                    Polyline(
                        points = trimmedRoutePoints,
                        color = MaterialTheme.colorScheme.primary,
                        width = 16f,
                    )
                } else if (points.size >= 2) {
                    Polyline(
                        points = points.map { LatLng(it.lat, it.lng) },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Marker(
                    state = currentPositionMarkerState,
                    icon = navArrowIcon,
                    rotation = animatedBearing,
                    flat = true,
                    title = "Posición actual",
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
