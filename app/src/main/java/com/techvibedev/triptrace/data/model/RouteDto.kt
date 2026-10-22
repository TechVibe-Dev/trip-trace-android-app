package com.techvibedev.triptrace.data.model

import com.google.gson.annotations.SerializedName

// A point the route must pass through (not a stop: the car isn't expected
// to halt there). The API sends these to Google as pass-through waypoints,
// so the route follows the roads the user picked.
data class ViaPointDto(
    val name: String?,
    val lat: Double,
    val lng: Double,
)

data class SavedRouteResponse(
    val id: String,
    val name: String,
    @SerializedName("origin_name") val originName: String,
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double,
    @SerializedName("destination_name") val destinationName: String,
    @SerializedName("destination_lat") val destinationLat: Double,
    @SerializedName("destination_lng") val destinationLng: Double,
    @SerializedName("via_points") val viaPoints: List<ViaPointDto>,
)

data class SavedRouteCreateRequest(
    val name: String,
    @SerializedName("origin_name") val originName: String,
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double,
    @SerializedName("destination_name") val destinationName: String,
    @SerializedName("destination_lat") val destinationLat: Double,
    @SerializedName("destination_lng") val destinationLng: Double,
    @SerializedName("via_points") val viaPoints: List<ViaPointDto>,
)

data class RoutePreviewRequest(
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double,
    @SerializedName("destination_lat") val destinationLat: Double,
    @SerializedName("destination_lng") val destinationLng: Double,
    @SerializedName("via_points") val viaPoints: List<ViaPointDto> = emptyList(),
    @SerializedName("planned_departure_at") val plannedDepartureAt: String? = null,
    val alternatives: Boolean = false,
)

data class RoutePreviewResponse(
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("distance_meters") val distanceMeters: Int,
    @SerializedName("calculated_arrival_at") val calculatedArrivalAt: String,
    @SerializedName("route_polyline") val routePolyline: String,
    // Google's short label for the route. Only filled for alternatives.
    val description: String,
)
