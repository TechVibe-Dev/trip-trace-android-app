package com.techvibedev.triptrace.car

import android.Manifest
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.techvibedev.triptrace.data.model.RouteStepResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

// The trip in progress on the car display: turn-by-turn card with the live
// distance to the next maneuver, arrival time and remaining distance, and a
// simple map behind it. Closing this screen only stops showing navigation
// on the car; the trip keeps recording until it's finished (here, from the
// arrival prompt, or from the phone).
internal class CarActiveTripScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val tripId: String,
) : Screen(carContext) {

    private val renderer = RouteSurfaceRenderer(carContext)
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private var permissionDenied = false

    private val tracker = CarTripTracker(
        context = carContext,
        tripRepository = deps.tripRepository,
        tripId = tripId,
        scope = lifecycleScope,
        onChanged = ::onTrackerChanged,
        onArrived = {
            screenManager.push(CarEndTripScreen(carContext, deps, tripId, arrived = true))
        },
    )

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
                    // Another navigation app took over the car display.
                    // Recording keeps going; only this view goes away.
                    override fun onStopNavigation() {
                        screenManager.popToRoot()
                    }
                })
                navigationManager.navigationStarted()
                startTracking()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                navigationManager.navigationEnded()
                navigationManager.clearNavigationManagerCallback()
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
            }
        })
    }

    private fun startTracking() {
        if (hasLocationPermission(carContext)) {
            tracker.start()
            return
        }
        // Shown on the phone's screen: the car display can't host
        // Android's permission dialog itself.
        carContext.requestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION)) { granted, _ ->
            if (Manifest.permission.ACCESS_FINE_LOCATION in granted) {
                tracker.start()
            } else {
                permissionDenied = true
                invalidate()
            }
        }
    }

    private fun onTrackerChanged() {
        val point = tracker.latestPoint
        val trip = tracker.trip
        renderer.update(
            RouteSnapshot(
                position = point?.let { LatLng(it.lat, it.lng) },
                bearingDegrees = point?.bearing?.toFloat(),
                routePoints = tracker.routePoints,
                recordedPath = tracker.recordedPath,
                destination = trip?.let { LatLng(it.destinationLat, it.destinationLng) },
            ),
        )
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val builder = NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Finalizar")
                            .setOnClickListener {
                                screenManager.push(CarEndTripScreen(carContext, deps, tripId, arrived = false))
                            }
                            .build(),
                    )
                    .build(),
            )

        val errorMessage = when {
            permissionDenied -> "Se necesita permiso de ubicación para grabar el viaje"
            else -> tracker.errorMessage
        }
        val step = tracker.steps.getOrNull(tracker.currentStepIndex)
        val stepDistance = tracker.distanceToNextManeuverMeters()
        when {
            errorMessage != null -> builder.setNavigationInfo(MessageInfo.Builder(errorMessage).build())
            step != null && stepDistance != null -> {
                val routingInfo = RoutingInfo.Builder()
                    .setCurrentStep(carStep(step), carDistance(stepDistance))
                tracker.steps.getOrNull(tracker.currentStepIndex + 1)?.let { next ->
                    routingInfo.setNextStep(carStep(next))
                }
                builder.setNavigationInfo(routingInfo.build())
            }
            else -> builder.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
        }

        travelEstimate()?.let { builder.setDestinationTravelEstimate(it) }
        return builder.build()
    }

    // Google's instruction text is already a complete, localized sentence;
    // shown verbatim as the step's cue, same as the phone's turn card.
    private fun carStep(step: RouteStepResponse): Step =
        Step.Builder(step.instructions)
            .setManeuver(carManeuver(carContext, step.maneuver))
            .build()

    private fun travelEstimate(): TravelEstimate? {
        val arrivalIso = tracker.liveArrivalAt ?: tracker.trip?.calculatedArrivalAt ?: return null
        val remainingMeters = tracker.remainingDistanceMeters() ?: return null
        val arrival = try {
            OffsetDateTime.parse(arrivalIso).atZoneSameInstant(ZoneId.systemDefault())
        } catch (e: Exception) {
            return null
        }
        val remainingSeconds = Duration.between(ZonedDateTime.now(), arrival).seconds.coerceAtLeast(0)
        return TravelEstimate.Builder(carDistance(remainingMeters), DateTimeWithZone.create(arrival))
            .setRemainingTimeSeconds(remainingSeconds)
            .build()
    }
}
