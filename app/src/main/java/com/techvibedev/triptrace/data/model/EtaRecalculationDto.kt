package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

// Deliberately not merged into TripResponse — this is a live snapshot from
// POST /recalculate-eta, never persisted on the trip server-side (see
// trip-trace-api's routers/trips.py), so it has no id/created_at of its own.
data class EtaRecalculationResponse(
    @SerializedName("calculated_arrival_at") val calculatedArrivalAt: String,
    @SerializedName("route_polyline") val routePolyline: String,
    // steps[0] is always "the next maneuver from here" — this route was
    // just computed FROM the trip's current position, so there's no
    // separate step-matching to do here.
    val steps: List<RouteStepResponse>,
)

data class RouteStepResponse(
    // Google's own enum value verbatim (e.g. "TURN_RIGHT",
    // "ROUNDABOUT_LEFT") — see ManeuverIcon for how this maps to a drawn
    // icon; an unrecognized value there just falls back to a straight
    // arrow rather than failing.
    val maneuver: String,
    val instructions: String,
    @SerializedName("distance_meters") val distanceMeters: Int,
    val polyline: String,
)
