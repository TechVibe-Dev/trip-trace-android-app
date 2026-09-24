package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

// Deliberately not merged into TripResponse — this is a live snapshot from
// POST /recalculate-eta, never persisted on the trip server-side (see
// trip-trace-api's routers/trips.py), so it has no id/created_at of its own.
data class EtaRecalculationResponse(
    @SerializedName("calculated_arrival_at") val calculatedArrivalAt: String,
    @SerializedName("route_polyline") val routePolyline: String,
)
