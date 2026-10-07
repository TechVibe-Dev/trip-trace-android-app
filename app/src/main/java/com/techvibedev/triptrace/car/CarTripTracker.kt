package com.techvibedev.triptrace.car

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.techvibedev.triptrace.data.local.GpsPointEntity
import com.techvibedev.triptrace.data.local.TripTraceDatabase
import com.techvibedev.triptrace.data.model.RouteStepResponse
import com.techvibedev.triptrace.data.model.TripResponse
import com.techvibedev.triptrace.data.repository.TripRepository
import com.techvibedev.triptrace.trip.ARRIVAL_THRESHOLD_METERS
import com.techvibedev.triptrace.trip.MAX_ACCURACY_ALLOWANCE_METERS
import com.techvibedev.triptrace.trip.MIN_REFRESH_GAP_MS
import com.techvibedev.triptrace.trip.OFF_ROUTE_CONFIRM_POINTS
import com.techvibedev.triptrace.trip.OFF_ROUTE_THRESHOLD_METERS
import com.techvibedev.triptrace.trip.POLL_INTERVAL_MS
import com.techvibedev.triptrace.trip.ensureTripSavedLocallyAndStartTracking
import com.techvibedev.triptrace.trip.haversineMeters
import com.techvibedev.triptrace.trip.matchRouteStep
import com.techvibedev.triptrace.trip.syncPendingGpsPoints
import com.techvibedev.triptrace.util.decodePolyline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "CarTripTracker"

// Follows a trip in progress for the car's navigation screen: the same
// steps the phone's ActiveTripScreen runs (GPS sync, live ETA and turn-by-
// turn refresh, advancing the current step locally, rerouting when off
// route, arrival prompt), just without Compose. Everything runs in the
// given scope, so it stops with the screen that owns it; GPS recording
// itself lives in TripTrackingService and keeps going regardless.
internal class CarTripTracker(
    private val context: Context,
    private val tripRepository: TripRepository,
    private val tripId: String,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val onArrived: () -> Unit,
) {
    var trip: TripResponse? = null
        private set
    var latestPoint: GpsPointEntity? = null
        private set
    var recordedPath: List<LatLng> = emptyList()
        private set
    var liveArrivalAt: String? = null
        private set
    var routePoints: List<LatLng> = emptyList()
        private set
    var steps: List<RouteStepResponse> = emptyList()
        private set
    var stepPolylines: List<List<LatLng>> = emptyList()
        private set
    var currentStepIndex = 0
        private set
    var errorMessage: String? = null
        private set

    private var offRoutePointCount = 0
    private var hasRequestedInitialRoute = false
    private var hasPromptedArrival = false

    // Early refresh requests for the poll loop (off route, first GPS fix).
    // Conflated: several requests before the loop gets to them still mean
    // a single refresh.
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        scope.launch {
            // Starting the foreground service can be refused by the system
            // in some background states; reported on screen rather than
            // crashing the car app.
            val result = try {
                ensureTripSavedLocallyAndStartTracking(context, tripId, tripRepository)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start tracking for trip $tripId", e)
                Result.failure(e)
            }
            result.fold(
                onSuccess = { trip = it },
                onFailure = { errorMessage = "No se pudo iniciar la grabación del viaje." },
            )
            onChanged()
        }
        scope.launch { pollLoop() }

        val gpsPointDao = TripTraceDatabase.getInstance(context.applicationContext).gpsPointDao()
        scope.launch {
            gpsPointDao.observeAllByTripId(tripId).collect { points ->
                recordedPath = points.map { LatLng(it.lat, it.lng) }
            }
        }
        scope.launch {
            gpsPointDao.observeLatest(tripId).collect { point ->
                latestPoint = point
                if (point != null) onNewPoint(point)
                onChanged()
            }
        }
    }

    // Distance to the next maneuver from the latest GPS point, falling back
    // to the API's own figure until there's a point to measure from.
    fun distanceToNextManeuverMeters(): Double? {
        val step = steps.getOrNull(currentStepIndex) ?: return null
        val maneuverPoint = stepPolylines.getOrNull(currentStepIndex)?.lastOrNull()
        val point = latestPoint
        if (maneuverPoint == null || point == null) return step.distanceMeters.toDouble()
        return haversineMeters(point.lat, point.lng, maneuverPoint.latitude, maneuverPoint.longitude)
    }

    // Live distance to the next maneuver plus every later step; straight-
    // line distance to the destination when there's no route yet.
    fun remainingDistanceMeters(): Double? {
        val toNext = distanceToNextManeuverMeters()
        if (toNext != null) {
            return toNext + steps.drop(currentStepIndex + 1).sumOf { it.distanceMeters }
        }
        val point = latestPoint ?: return null
        val current = trip ?: return null
        return haversineMeters(point.lat, point.lng, current.destinationLat, current.destinationLng)
    }

    private suspend fun pollLoop() {
        var lastRefreshAtMs = 0L
        while (true) {
            // Whichever comes first: the regular interval, or an early
            // request from the off-route check.
            withTimeoutOrNull(POLL_INTERVAL_MS) { refreshRequests.receive() }
            val sinceLastRefreshMs = SystemClock.elapsedRealtime() - lastRefreshAtMs
            if (sinceLastRefreshMs < MIN_REFRESH_GAP_MS) {
                delay(MIN_REFRESH_GAP_MS - sinceLastRefreshMs)
            }
            // Anything requested while waiting is covered by this refresh.
            refreshRequests.tryReceive()
            lastRefreshAtMs = SystemClock.elapsedRealtime()

            syncPendingGpsPoints(context, tripId, tripRepository)
            // Best-effort: a hiccup just keeps the last route on screen
            // until the next tick.
            tripRepository.recalculateEta(tripId).onSuccess { eta ->
                liveArrivalAt = eta.calculatedArrivalAt
                routePoints = decodePolyline(eta.routePolyline)
                steps = eta.steps
                stepPolylines = eta.steps.map { decodePolyline(it.polyline) }
                currentStepIndex = 0
                offRoutePointCount = 0
                onChanged()
            }
        }
    }

    private fun onNewPoint(point: GpsPointEntity) {
        checkArrival(point)
        if (stepPolylines.isEmpty()) {
            // No route yet: ask for one as soon as there's a position,
            // instead of waiting out the first full interval. Only once —
            // if that fails, the regular interval keeps retrying.
            if (!hasRequestedInitialRoute) {
                hasRequestedInitialRoute = true
                refreshRequests.trySend(Unit)
            }
            return
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

    // Asks once per screen, like the phone does: declining keeps the trip
    // running, and "Finalizar" stays available from the action strip.
    private fun checkArrival(point: GpsPointEntity) {
        if (hasPromptedArrival) return
        val current = trip ?: return
        val distanceMeters = haversineMeters(point.lat, point.lng, current.destinationLat, current.destinationLng)
        if (distanceMeters <= ARRIVAL_THRESHOLD_METERS) {
            hasPromptedArrival = true
            onArrived()
        }
    }
}
